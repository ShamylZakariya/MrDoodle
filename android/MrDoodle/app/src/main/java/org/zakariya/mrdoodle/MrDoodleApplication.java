package org.zakariya.mrdoodle;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.Log;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import org.zakariya.mrdoodle.events.ApplicationDidBackgroundEvent;
import org.zakariya.mrdoodle.events.ApplicationDidResumeEvent;
import org.zakariya.mrdoodle.events.DoodleDocumentCreatedEvent;
import org.zakariya.mrdoodle.events.DoodleDocumentEditedEvent;
import org.zakariya.mrdoodle.events.DoodleDocumentWasDeletedEvent;
import org.zakariya.mrdoodle.model.DoodleDocument;
import org.zakariya.mrdoodle.net.model.RemoteChangeReport;
import org.zakariya.mrdoodle.net.model.SyncReport;
import org.zakariya.mrdoodle.signin.SignInManager;
import org.zakariya.mrdoodle.signin.techniques.MockSignInTechnique;
import org.zakariya.mrdoodle.sync.SyncConfiguration;
import org.zakariya.mrdoodle.sync.SyncManager;
import org.zakariya.mrdoodle.util.BusProvider;
import org.zakariya.mrdoodle.util.DoodleThumbnailRenderer;

import java.util.List;

import io.realm.Realm;
import io.realm.RealmConfiguration;

/**
 * Created by shamyl on 12/20/15.
 */
public class MrDoodleApplication extends android.app.Application implements SyncManager.SyncStateListener {

	private static final String TAG = "MrDoodleApplication";

	private static MrDoodleApplication instance;

	private BackgroundWatcher backgroundWatcher;
	private RealmConfiguration realmConfiguration;
	private LocalChangeListener localChangeListener;
	private Handler handler;

	@Override
	public void onCreate() {
		super.onCreate();
		instance = this;

		handler = new Handler(Looper.getMainLooper());
		backgroundWatcher = new BackgroundWatcher(this, false);
		realmConfiguration = new RealmConfiguration.Builder(this)
				.deleteRealmIfMigrationNeeded()
				.build();

		Realm.setDefaultConfiguration(realmConfiguration);

		initSingletons();
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

		//SignInManager.init(new GoogleSignInTechnique(this));
		SignInManager.init(new MockSignInTechnique(this));

		// build the sync manager, providing mechanism for serializing/de-serializing our model type
		SyncManager.init(this, new SyncConfiguration(), new SyncModelAdapter());

		// set up notifier to let change journal capture model events
		localChangeListener = new LocalChangeListener(SyncManager.getInstance());
		localChangeListener.setActive(true);

		// we need to listen to sync start/stop events to turn on or off the change journal notifier
		SyncManager.getInstance().addSyncStateListener(this);
	}

	///////////////////////////////////////////////////////////////////

	@Override
	public void didBeginSync() {
		stopChangeNotifier();
	}

	@Override
	public void didCompleteSync(SyncReport report) {
		startChangeNotifier(report.getRemoteChangeReports());
	}

	@Override
	public void didFailSync(Throwable failure) {
		startChangeNotifier(null);
	}

	private void stopChangeNotifier() {
		localChangeListener.setActive(false);
	}

	private void startChangeNotifier(@Nullable final List<RemoteChangeReport> changes) {

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
				if (changes != null) {
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
		public Action setModelObjectData(String blobId, String blobType, byte[] blobData) throws Exception {
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
		public byte[] getModelObjectData(String blobId, String blobType) throws Exception {
			switch (blobType) {
				case DoodleDocument.DOCUMENT_TYPE: {

					Realm realm = Realm.getDefaultInstance();
					try {
						DoodleDocument document = DoodleDocument.byUUID(realm, blobId);
						if (document != null) {
							return document.serialize(MrDoodleApplication.getInstance().getApplicationContext());
						} else {
							throw new Exception("Unable to find DoodleDocument with UUID: " + blobId);
						}
					} finally {
						realm.close();
					}
				}
			}
			return null;
		}

		@Override
		public boolean deleteModelObject(String modelId, String modelType) throws Exception {
			switch (modelType) {
				case DoodleDocument.DOCUMENT_TYPE:
					Realm realm = Realm.getDefaultInstance();
					try {
						DoodleDocument doc = DoodleDocument.byUUID(realm, modelId);
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

		public BackgroundWatcher(final MrDoodleApplication application, final boolean noisy) {
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
