package org.zakariya.mrdoodle;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.zakariya.mrdoodle.events.ApplicationDidBackgroundEvent;
import org.zakariya.mrdoodle.events.ApplicationDidResumeEvent;
import org.zakariya.mrdoodle.signin.SignInManager;
import org.zakariya.mrdoodle.signin.techniques.MockSignInTechnique;
import org.zakariya.mrdoodle.sync.SyncConfiguration;
import org.zakariya.mrdoodle.sync.SyncManager;
import org.zakariya.mrdoodle.util.BusProvider;
import org.zakariya.mrdoodle.util.DoodleThumbnailRenderer;
import org.zakariya.mrdoodle.signin.techniques.GoogleSignInTechnique;

import io.realm.Realm;
import io.realm.RealmConfiguration;

/**
 * Created by shamyl on 12/20/15.
 */
public class MrDoodleApplication extends android.app.Application {

	private static final String TAG = "MrDoodleApplication";

	private static MrDoodleApplication instance;

	private SyncManager syncManager;
	private BackgroundWatcher backgroundWatcher;
	private RealmConfiguration realmConfiguration;

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
	}

	public void onApplicationResumed() {
		SignInManager.getInstance().connect();
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

		SyncManager.init(this, new SyncConfiguration());
	}


	public static class BackgroundWatcher implements MrDoodleApplication.ActivityLifecycleCallbacks {

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
