package org.zakariya.mrdoodle.ui;

import android.support.v7.app.AppCompatActivity;

/**
 * Created by shamyl on 1/4/16.
 */
public class BaseActivity extends AppCompatActivity {

	private boolean paused;

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

}
