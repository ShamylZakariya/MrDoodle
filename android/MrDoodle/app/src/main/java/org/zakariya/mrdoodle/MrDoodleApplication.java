package org.zakariya.mrdoodle;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import org.zakariya.mrdoodle.events.ApplicationDidBackgroundEvent;
import org.zakariya.mrdoodle.events.ApplicationDidResumeEvent;
import org.zakariya.mrdoodle.events.DoodleDocumentCreatedEvent;
import org.zakariya.mrdoodle.events.DoodleDocumentEditedEvent;
import org.zakariya.mrdoodle.events.DoodleDocumentStoreWasClearedEvent;
import org.zakariya.mrdoodle.events.DoodleDocumentWasDeletedEvent;
import org.zakariya.mrdoodle.model.DoodleDocument;
import org.zakariya.mrdoodle.model.DoodleDocumentNotFoundException;
import org.zakariya.mrdoodle.net.StatusApi;
import org.zakariya.mrdoodle.net.StatusApiConfiguration;
import org.zakariya.mrdoodle.net.SyncApiConfiguration;
import org.zakariya.mrdoodle.net.model.RemoteChangeReport;
import org.zakariya.mrdoodle.net.model.SyncReport;
import org.zakariya.mrdoodle.net.transport.ServiceStatus;
import org.zakariya.mrdoodle.signin.SignInManager;
import org.zakariya.mrdoodle.signin.techniques.GoogleSignInTechnique;
import org.zakariya.mrdoodle.sync.SyncManager;
import org.zakariya.mrdoodle.util.BusProvider;
import org.zakariya.mrdoodle.util.DoodleThumbnailRenderer;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * MrDoodleApplication
 * Fires up some singleton "services".
 * The MrDoodleApplication is responsible for acting as a "bridge" between the application user data
 * and the sync manager. When the user modifies local data, the application observes this, and forwards
 * edit notifications to the sync manager. When the sync manager finishes a sync, the syn report is consumed
 * by the application and document create/update/delete events are fired for the application UI to
 * observe.
 */
public class MrDoodleApplication extends android.app.Application implements SyncManager.SyncStateListener {

	private static final String TAG = "MrDoodleApplication";

	private static final String PREF_KEY_SERVICE_STATUS = "ServiceStatus";

	private static MrDoodleApplication instance;

	private BackgroundWatcher backgroundWatcher;
	private RealmConfiguration realmConfiguration;
	private LocalChangeListener localChangeListener;
	private Handler handler;
	private RefWatcher refWatcher;

	private ServiceStatus serviceStatus = null;

	public static RefWatcher getRefWatcher(Context context) {
		MrDoodleApplication application = (MrDoodleApplication) context.getApplicationContext();
		return application.refWatcher;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		if (LeakCanary.isInAnalyzerProcess(this)) {
			// This process is dedicated to LeakCanary for heap analysis - we're done here
			return;
		}

		refWatcher = LeakCanary.install(this);

		instance = this;
		handler = new Handler(Looper.getMainLooper());
		backgroundWatcher = new BackgroundWatcher(this, false);
		realmConfiguration = new RealmConfiguration.Builder(this)
				.deleteRealmIfMigrationNeeded()
				.build();

		Realm.setDefaultConfiguration(realmConfiguration);

		initSingletons();
		loadServiceStatus();

		Log.i(TAG, "onCreate: Started MrDoodleApplication version: " + getVersionString());
	}

	public static MrDoodleApplication getInstance() {
		return instance;
	}

	public RealmConfiguration getRealmConfiguration() {
		return realmConfiguration;
	}

	public BackgroundWatcher getBackgroundWatcher() {
		return backgroundWatcher;
	}

	public String getVersionString() {
		try {
			PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
			return info.versionName + " (" + BuildConfig.GitBranch + ")";

		} catch (PackageManager.NameNotFoundException e) {
			Log.e(TAG, "Unable to get app version info!?");
		}

		return null;
	}

	public void onApplicationBackgrounded() {
		SignInManager.getInstance().disconnect();
		SyncManager.getInstance().stop();
	}

	public void onApplicationResumed() {
		SignInManager.getInstance().connect();
		SyncManager.getInstance().start();
	}

	@Override
	public void onTrimMemory(int level) {
		super.onTrimMemory(level);
		DoodleThumbnailRenderer.getInstance().onTrimMemory(level);
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		DoodleThumbnailRenderer.getInstance().onLowMemory();
	}

	@Override
	public void onConfigurationChanged(android.content.res.Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		DoodleThumbnailRenderer.getInstance().onConfigurationChanged(newConfig);
	}

	private void initSingletons() {
		DoodleThumbnailRenderer.init(this);

		SignInManager.init(new GoogleSignInTechnique(this));
		//SignInManager.init(new MockSignInTechnique(this));

		// build the sync manager, providing mechanism for serializing/de-serializing our model type
		SyncApiConfiguration configuration = new SyncApiConfiguration();
		configuration.setUserAgent("MrDoodle-" + getVersionString());
		SyncManager.init(this, configuration, new SyncModelAdapter());

		// set up notifier to let change journal capture model events
		localChangeListener = new LocalChangeListener(SyncManager.getInstance());
		localChangeListener.setActive(true);

		// we need to listen to sync start/stop events to turn on or off the change journal notifier
		SyncManager.getInstance().addSyncStateListener(this);
	}

	private void loadServiceStatus() {
		if (serviceStatus == null) {

			// first load persisted serviceStatus
			SharedPreferences sharedPreferences = getSharedPreferences();
			String statusJson = sharedPreferences.getString(PREF_KEY_SERVICE_STATUS, null);
			if (!TextUtils.isEmpty(statusJson)) {
				Gson gson = new Gson();
				try {
					serviceStatus = gson.fromJson(statusJson, ServiceStatus.class);
				} catch (JsonSyntaxException e) {
					Log.e(TAG, "loadServiceStatus: unable to parse persisted ServiceStatus JSON string", e);
				}
			}

			// go default if needed - this should only happen on first run.
			// assume that service is alive and running.
			if (serviceStatus == null) {
				serviceStatus = new ServiceStatus();
				serviceStatus.isDiscontinued = false;
				serviceStatus.isScheduledDowntime = false;
			}

			// now update serviceStatus from net (if possible)
			StatusApiConfiguration config = new StatusApiConfiguration();
			StatusApi statusApi = new StatusApi(config);
			statusApi.getServiceStatus()
					.subscribeOn(Schedulers.io())
					.observeOn(AndroidSchedulers.mainThread())
					.subscribe(new Subscriber<ServiceStatus>() {
						@Override
						public void onCompleted() {
						}

						@Override
						public void onError(Throwable e) {
							Log.e(TAG, "loadServiceStatus - onError: ", e);
						}

						@Override
						public void onNext(ServiceStatus serviceStatus) {
							setServiceStatus(serviceStatus);
						}
					});
		}
	}

	public ServiceStatus getServiceStatus() {
		return serviceStatus;
	}

	private void setServiceStatus(ServiceStatus status) {

		boolean didChange = status.isDiscontinued != this.serviceStatus.isDiscontinued || status.isScheduledDowntime != this.serviceStatus.isScheduledDowntime;

		this.serviceStatus = status;

		// persist
		Gson gson = new Gson();
		String serviceStatusJson = gson.toJson(serviceStatus);
		SharedPreferences sharedPreferences = getSharedPreferences();
		sharedPreferences.edit().putString(PREF_KEY_SERVICE_STATUS, serviceStatusJson).apply();

		// now act
		if (didChange) {
			// TODO: What do we do? Enable/disable sync??? Post events for UI to act on???
			// if we do want to show a dialog, this returns us to the situation where we need to know the
			// active activity to use as a host
		}
	}

	private SharedPreferences getSharedPreferences() {
		return getSharedPreferences(MrDoodleApplication.class.getSimpleName(), Context.MODE_PRIVATE);
	}

	///////////////////////////////////////////////////////////////////

	@Override
	public void didBeginSync() {
		stopChangeNotifier();
	}

	@Override
	public void didCompleteSync(SyncReport report) {
		startChangeNotifier(report.getRemoteChangeReports(), report.didResetLocalStore());
	}

	@Override
	public void didFailSync(Throwable failure) {
		startChangeNotifier(null, false);
	}

	private void stopChangeNotifier() {
		localChangeListener.setActive(false);
	}

	private void startChangeNotifier(@Nullable final List<RemoteChangeReport> changes, final boolean didResetLocalStore) {

		// this may be called from a background thread. We need to prevent a race condition
		// where using BusProvider.postOnMainThread will cause the events to be posted at a
		// future date, and our localChangeListener has been re-activated, so instead
		// we're just doing the whole thing on the main thread via a handler.

		handler.post(new Runnable() {
			@Override
			public void run() {

				// be certain these events won't be consumed
				localChangeListener.setActive(false);
				Bus bus = BusProvider.getMainThreadBus();

				// notify that the local store was cleared
				if (didResetLocalStore) {
					bus.post(new DoodleDocumentStoreWasClearedEvent());
				}

				if (changes != null) {

					// sort changes to process deletions before the rest. This handles
					// the situation where below, when blasting out all changes, all the
					// deletions are removed from storage (e.g., RecyclerView.Adapter)
					// before additions and updates are processed. This fixes a specific
					// bug where when adding/updating docs the adapter would sort the
					// contents, and might end up touching deleted documents

					Collections.sort(changes, new Comparator<RemoteChangeReport>() {
						@Override
						public int compare(RemoteChangeReport r0, RemoteChangeReport r1) {
							if (r0.getAction() == RemoteChangeReport.Action.DELETE) {
								return -1;
							} else if (r1.getAction() == RemoteChangeReport.Action.DELETE) {
								return +1;
							} else {
								return 0;
							}
						}
					});


					for (RemoteChangeReport report : changes) {
						switch(report.getAction()) {
							case CREATE:
								bus.post(new DoodleDocumentCreatedEvent(report.getDocumentId()));
								break;
							case UPDATE:
								bus.post(new DoodleDocumentEditedEvent(report.getDocumentId()));
								break;

							case DELETE:
								bus.post(new DoodleDocumentWasDeletedEvent(report.getDocumentId()));
								break;
						}
					}
				}

				localChangeListener.setActive(true);
			}
		});
	}

	/**
	 * Simple adapter which adapts our app's data model to be readable/writable to the sync engine
	 */
	@SuppressWarnings("TryFinallyCanBeTryWithResources") // not for API 17 it's can't
	private static class SyncModelAdapter implements SyncManager.ModelDataAdapter {

		@Override
		public Action setModelObjectData(String blobId, String blobType, byte[] blobData) throws IOException {
			switch (blobType) {
				case DoodleDocument.DOCUMENT_TYPE: {

					Realm realm = Realm.getDefaultInstance();
					try {
						if (DoodleDocument.createOrUpdate(
								MrDoodleApplication.getInstance().getApplicationContext(),
								realm,
								blobData)) {
							return Action.UPDATE;
						} else {
							return Action.CREATE;
						}
					} finally {
						realm.close();
					}
				}
			}
			return Action.NOTHING;
		}

		@Nullable
		@Override
		public byte[] getModelObjectData(String blobId, String blobType) throws IOException {
			switch (blobType) {
				case DoodleDocument.DOCUMENT_TYPE: {

					Realm realm = Realm.getDefaultInstance();
					try {
						DoodleDocument document = DoodleDocument.byUuid(realm, blobId);
						if (document != null) {
							return document.serialize(MrDoodleApplication.getInstance().getApplicationContext());
						} else {
							throw new DoodleDocumentNotFoundException("Unable to find DoodleDocument with UUID: " + blobId);
						}
					} finally {
						realm.close();
					}
				}
			}
			return null;
		}

		@Override
		public boolean deleteModelObject(String modelId, String modelType) throws IOException {
			switch (modelType) {
				case DoodleDocument.DOCUMENT_TYPE:
					Realm realm = Realm.getDefaultInstance();
					try {
						DoodleDocument doc = DoodleDocument.byUuid(realm, modelId);
						if (doc != null) {
							DoodleDocument.delete(MrDoodleApplication.getInstance().getApplicationContext(), realm, doc);
							return true;
						} else {
							return false;
						}
					} finally {
						realm.close();
					}
			}
			return false;
		}
	}

	/**
	 * LocalChangeListener
	 * Listens to model events (write/delete/etc) and forwards them to the sync manager
	 */
	private static class LocalChangeListener {

		private SyncManager syncManager;
		private boolean active;

		LocalChangeListener(SyncManager syncManager) {
			this.syncManager = syncManager;
			BusProvider.getMainThreadBus().register(this);
		}

		boolean isActive() {
			return active;
		}

		void setActive(boolean active) {
			this.active = active;
		}

		@Subscribe
		public void onDoodleDocumentCreated(DoodleDocumentCreatedEvent event) {
			if (isActive()) {
				syncManager.markLocalDocumentCreation(event.getUuid(), DoodleDocument.DOCUMENT_TYPE);
			}
		}

		@Subscribe
		public void onDoodleDocumentWasDeleted(DoodleDocumentWasDeletedEvent event) {
			if (isActive()) {
				syncManager.markLocalDocumentDeletion(event.getUuid(), DoodleDocument.DOCUMENT_TYPE);
			}
		}

		@Subscribe
		public void onDoodleDocumentModified(DoodleDocumentEditedEvent event) {
			if (isActive()) {
				syncManager.markLocalDocumentModification(event.getUuid(), DoodleDocument.DOCUMENT_TYPE);
			}
		}

	}

	/**
	 * BackgroundWatcher monitors activity foreground/background state and notifies application
	 * when app is fully backgrounded or returns/begins being foreground
	 */
	private static class BackgroundWatcher implements MrDoodleApplication.ActivityLifecycleCallbacks {

		private static final int BACKGROUND_DELAY_MILLIS = 1000;
		private static final String TAG = "BackgroundWatcher";

		boolean didFireBackgroundEvent;
		private Handler delayHandler;
		private Runnable action;
		private int count;
		private boolean noisy;

		BackgroundWatcher(final MrDoodleApplication application, final boolean noisy) {
			application.registerActivityLifecycleCallbacks(this);
			this.noisy = noisy;

			delayHandler = new Handler(Looper.getMainLooper());
			action = new Runnable() {
				@Override
				public void run() {
					if (noisy) {
						Log.i(TAG, "onActivityPaused - firing ApplicationDidBackgroundEvent");
					}
					BusProvider.getMainThreadBus().post(new ApplicationDidBackgroundEvent());
					application.onApplicationBackgrounded();
					didFireBackgroundEvent = true;
				}
			};
		}

		@Override
		public void onActivityCreated(Activity activity, Bundle bundle) {
		}

		@Override
		public void onActivityStarted(Activity activity) {
			count++;
			if (noisy) {
				Log.i(TAG, "onActivityStarted: count: " + count);
			}

			delayHandler.removeCallbacks(action);

			if (count == 1 && didFireBackgroundEvent) {
				if (noisy) {
					Log.i(TAG, "onActivityStarted - firing ApplicationDidResumeEvent");
				}

				MrDoodleApplication.getInstance().onApplicationResumed();
				BusProvider.getMainThreadBus().post(new ApplicationDidResumeEvent());
			}
		}

		@Override
		public void onActivityResumed(Activity activity) {
		}

		@Override
		public void onActivityPaused(Activity activity) {
		}

		@Override
		public void onActivityStopped(Activity activity) {
			if (count > 0) {
				count--;
			}

			if (noisy) {
				Log.i(TAG, "onActivityStopped: count: " + count);
			}

			if (count == 0) {
				if (noisy) {
					Log.i(TAG, "onActivityStopped: - scheduling fire of ApplicationDidBackgroundEvent...");
				}

				didFireBackgroundEvent = false;
				delayHandler.postDelayed(action, BACKGROUND_DELAY_MILLIS);
			}
		}

		@Override
		public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {
		}

		@Override
		public void onActivityDestroyed(Activity activity) {
		}
	}


}
