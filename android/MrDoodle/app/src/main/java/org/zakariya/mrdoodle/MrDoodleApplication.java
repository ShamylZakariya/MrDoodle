package org.zakariya.mrdoodle;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.Log;

import com.squareup.otto.Subscribe;

import org.zakariya.mrdoodle.events.ApplicationDidBackgroundEvent;
import org.zakariya.mrdoodle.events.ApplicationDidResumeEvent;
import org.zakariya.mrdoodle.events.DoodleDocumentCreatedEvent;
import org.zakariya.mrdoodle.events.DoodleDocumentWillBeDeletedEvent;
import org.zakariya.mrdoodle.events.DoodleDocumentEditedEvent;
import org.zakariya.mrdoodle.model.DoodleDocument;
import org.zakariya.mrdoodle.signin.SignInManager;
import org.zakariya.mrdoodle.signin.techniques.MockSignInTechnique;
import org.zakariya.mrdoodle.sync.SyncConfiguration;
import org.zakariya.mrdoodle.sync.SyncManager;
import org.zakariya.mrdoodle.util.BusProvider;
import org.zakariya.mrdoodle.util.DoodleThumbnailRenderer;

import io.realm.Realm;
import io.realm.RealmConfiguration;

/**
 * Created by shamyl on 12/20/15.
 */
public class MrDoodleApplication extends android.app.Application {

	private static final String TAG = "MrDoodleApplication";

	private static MrDoodleApplication instance;

	private BackgroundWatcher backgroundWatcher;
	private RealmConfiguration realmConfiguration;
	private ChangeJournalNotifier changeJournalNotifier;

	@Override
	public void onCreate() {
		super.onCreate();
		instance = this;

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
		SyncManager.init(this, new SyncConfiguration(), new SyncManager.ModelDataAdapter() {

			@SuppressWarnings("TryFinallyCanBeTryWithResources") // not for API 17 it's can't
			@Override
			public void setModelObjectData(String blobId, String blobClass, byte[] blobData) throws Exception {
				switch (blobClass) {
					case DoodleDocument.BLOB_TYPE: {

						Realm realm = Realm.getDefaultInstance();
						try {
							DoodleDocument.createOrUpdate(MrDoodleApplication.this, realm, blobData);
						} finally {
							realm.close();
						}

						break;
					}
				}

			}

			@SuppressWarnings("TryFinallyCanBeTryWithResources")
			@Nullable
			@Override
			public byte[] getModelObjectData(String blobId, String blobClass) throws Exception {
				switch (blobClass) {
					case DoodleDocument.BLOB_TYPE: {

						Realm realm = Realm.getDefaultInstance();
						try {
							DoodleDocument document = DoodleDocument.byUUID(realm, blobId);
							if (document != null) {
								return document.serialize(MrDoodleApplication.this);
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

			@SuppressWarnings("TryFinallyCanBeTryWithResources")
			@Override
			public void deleteModelObject(String modelId, String modelClass) throws Exception {
				switch(modelClass) {
					case DoodleDocument.BLOB_TYPE:
						Realm realm = Realm.getDefaultInstance();
						try {
							DoodleDocument doc = DoodleDocument.byUUID(realm, modelId);
							DoodleDocument.delete(MrDoodleApplication.this, realm, doc);
						} finally {
							realm.close();
						}
						break;
				}
			}
		});

		// set up notifier to let change journal capture model events
		changeJournalNotifier = new ChangeJournalNotifier(SyncManager.getInstance());
	}

	/**
	 * ChangeJournalNotifier
	 * Listens to model events (write/delete/etc) and forwards them to the change journal to record
	 */
	static class ChangeJournalNotifier {

		private SyncManager syncManager;

		public ChangeJournalNotifier(SyncManager syncManager) {
			this.syncManager = syncManager;
			BusProvider.getBus().register(this);
		}

		@Subscribe
		public void onDoodleDocumentCreated(DoodleDocumentCreatedEvent event) {
			syncManager.getChangeJournal().markModified(event.getUuid(), DoodleDocument.BLOB_TYPE);
		}

		@Subscribe
		public void onDoodleDocumentWillBeDeleted(DoodleDocumentWillBeDeletedEvent event) {
			syncManager.getChangeJournal().markDeleted(event.getUuid(), DoodleDocument.BLOB_TYPE);
		}

		@Subscribe
		public void onDoodleDocumentModified(DoodleDocumentEditedEvent event) {
			syncManager.getChangeJournal().markModified(event.getUuid(), DoodleDocument.BLOB_TYPE);
		}

	}

	/**
	 * BackgroundWatcher monitors activity foreground/background state and notifies application
	 * when app is fully backgrounded or returns/begins being foreground
	 */
	static class BackgroundWatcher implements MrDoodleApplication.ActivityLifecycleCallbacks {

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
					BusProvider.getBus().post(new ApplicationDidBackgroundEvent());
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
				BusProvider.getBus().post(new ApplicationDidResumeEvent());
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
