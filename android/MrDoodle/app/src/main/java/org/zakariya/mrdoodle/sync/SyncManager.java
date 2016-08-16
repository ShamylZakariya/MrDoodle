package org.zakariya.mrdoodle.sync;

import android.content.Context;
import android.util.Log;

import com.squareup.otto.Subscribe;

import org.zakariya.mrdoodle.events.ApplicationDidBackgroundEvent;
import org.zakariya.mrdoodle.events.ApplicationDidResumeEvent;
import org.zakariya.mrdoodle.events.GoogleSignInEvent;
import org.zakariya.mrdoodle.events.GoogleSignOutEvent;
import org.zakariya.mrdoodle.net.SyncServerConnection;
import org.zakariya.mrdoodle.util.BusProvider;
import org.zakariya.mrdoodle.util.GoogleSignInManager;

/**
 * Top level access point for sync services
 */
public class SyncManager {

	private static final String TAG = SyncManager.class.getSimpleName();

	private static SyncManager instance;

	private boolean applicationIsActive, userIsSignedIn, running;
	private SyncConfiguration syncConfiguration;
	private Context context;
	private SyncServerConnection syncServerConnection;
	private ChangeJournal changeJournal;
	private TimestampRecorder timestampRecorder;

	public static void init(Context context, SyncConfiguration syncConfiguration) {
		if (instance == null) {
			instance = new SyncManager(context, syncConfiguration);
		}
	}

	public static SyncManager getInstance() {
		return instance;
	}

	private SyncManager(Context context, SyncConfiguration syncConfiguration) {
		this.context = context;
		this.syncConfiguration = syncConfiguration;

		applicationIsActive = true;
		userIsSignedIn = GoogleSignInManager.getInstance().getGoogleSignInAccount() != null;

		syncServerConnection = new SyncServerConnection(syncConfiguration.getSyncServerConnectionUrl());
		changeJournal = new ChangeJournal(context);
		timestampRecorder = new TimestampRecorder(context);

		BusProvider.getBus().register(this);
		startOrStopSyncServices();
	}

	public SyncConfiguration getSyncConfiguration() {
		return syncConfiguration;
	}

	public Context getContext() {
		return context;
	}

	public SyncServerConnection getSyncServerConnection() {
		return syncServerConnection;
	}

	public ChangeJournal getChangeJournal() {
		return changeJournal;
	}

	public TimestampRecorder getTimestampRecorder() {
		return timestampRecorder;
	}

	/**
	 * @return true iff connected to server and sync services are running
	 */
	public boolean isRunning() {
		return running;
	}

	///////////////////////////////////////////////////////////////////

	protected void start() {
		if (!running) {
			running = true;
			Log.d(TAG, "start:");
			syncServerConnection.connect();
		}
	}

	protected void stop() {
		if (running) {
			Log.d(TAG, "stop:");
			syncServerConnection.disconnect();
			running = false;
		}
	}

	///////////////////////////////////////////////////////////////////

	private void startOrStopSyncServices() {
		if (applicationIsActive && userIsSignedIn) {
			start();
		} else {
			stop();
		}
	}

	///////////////////////////////////////////////////////////////////

	@Subscribe
	public void onApplicationResumed(ApplicationDidResumeEvent event) {
		Log.d(TAG, "onApplicationResumed:");
		applicationIsActive = true;
		syncServerConnection.resetExponentialBackoff();
		startOrStopSyncServices();
	}

	@Subscribe
	public void onApplicationBackgrounded(ApplicationDidBackgroundEvent event) {
		Log.d(TAG, "onApplicationBackgrounded:");
		applicationIsActive = false;
		startOrStopSyncServices();
	}

	@Subscribe
	public void onSignedIn(GoogleSignInEvent event) {
		Log.d(TAG, "onSignedIn:");
		userIsSignedIn = true;
		syncServerConnection.resetExponentialBackoff();
		startOrStopSyncServices();
	}

	@Subscribe
	public void onSignedOut(GoogleSignOutEvent event) {
		Log.d(TAG, "onSignedOut:");
		userIsSignedIn = false;
		startOrStopSyncServices();
	}



}
