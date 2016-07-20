package org.zakariya.mrdoodle.activities;

import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import org.zakariya.mrdoodle.BuildConfig;
import org.zakariya.mrdoodle.MrDoodleApplication;
import org.zakariya.mrdoodle.events.ApplicationDidBackgroundEvent;
import org.zakariya.mrdoodle.events.ApplicationDidResumeEvent;
import org.zakariya.mrdoodle.util.BusProvider;

/**
 * Created by shamyl on 1/4/16.
 */
public class BaseActivity extends AppCompatActivity {

	private boolean paused;
	private static BackgroundWatcher backgroundWatcher;

	@Override
	protected void onStart() {
		super.onStart();
		getBackgroundWatcher().onActivityStart();
	}

	@Override
	protected void onStop() {
		super.onStop();
		getBackgroundWatcher().onActivityStop();
	}

	@Override
	protected void onResume() {
		super.onResume();
		paused = false;
	}

	@Override
	protected void onPause() {
		paused = true;
		super.onPause();
	}

	/**
	 * Determine if the current activity is paused (meaning, it's not the topmost activity receiving user input)
	 *
	 * @return true if the activity is active and not paused
	 */
	public boolean isPaused() {
		return paused;
	}

	private synchronized static BackgroundWatcher getBackgroundWatcher() {
		if (backgroundWatcher == null) {
			backgroundWatcher = new BackgroundWatcher();
		}

		return backgroundWatcher;
	}


	private static class BackgroundWatcher {

		private static final int BACKGROUND_DELAY_MILLIS = 1000; // 1 second
		private static final String TAG = "BackgroundWatcher";

		boolean didFireBackgroundingEvent;
		private Handler delayHandler;
		private Runnable action;
		private int count;

		private BackgroundWatcher() {
			delayHandler = new Handler(Looper.getMainLooper());
			action = new Runnable() {
				@Override
				public void run() {
					Log.i(TAG, "onActivityStop - firing ApplicationDidBackgroundEvent");
					BusProvider.getBus().post(new ApplicationDidBackgroundEvent());
					MrDoodleApplication.getInstance().onApplicationBackgrounded();
					didFireBackgroundingEvent = true;
				}
			};
		}

		public void onActivityStart() {
			count++;
			//Log.i(TAG, "onActivityStart count: " + count);

			delayHandler.removeCallbacks(action);

			if (count == 1 && didFireBackgroundingEvent) {
				Log.i(TAG, "onActivityStart - firing ApplicationDidResumeEvent");
				MrDoodleApplication.getInstance().onApplicationResumed();
				BusProvider.getBus().post(new ApplicationDidResumeEvent());
			}
		}

		public void onActivityStop() {
			if (BuildConfig.DEBUG && count <= 0) {
				throw new AssertionError("BackgroundWatcher active count is zero and can't be decremented further.");
			}

			count--;
			//Log.i(TAG, "onActivityStop: count: " + count);

			if (count == 0) {
				//Log.i(TAG, "onActivityStop: - scheduling fire of ApplicationDidBackgroundEvent...");
				didFireBackgroundingEvent = false;
				delayHandler.postDelayed(action, BACKGROUND_DELAY_MILLIS);
			}
		}
	}


}
