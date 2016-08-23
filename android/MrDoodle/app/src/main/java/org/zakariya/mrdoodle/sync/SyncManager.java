package org.zakariya.mrdoodle.sync;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.squareup.otto.Subscribe;

import org.zakariya.mrdoodle.events.ApplicationDidBackgroundEvent;
import org.zakariya.mrdoodle.events.ApplicationDidResumeEvent;
import org.zakariya.mrdoodle.events.GoogleSignInEvent;
import org.zakariya.mrdoodle.events.GoogleSignOutEvent;
import org.zakariya.mrdoodle.net.SyncEngine;
import org.zakariya.mrdoodle.net.SyncServerConnection;
import org.zakariya.mrdoodle.net.transport.Status;
import org.zakariya.mrdoodle.util.BusProvider;
import org.zakariya.mrdoodle.util.GoogleSignInManager;

/**
 * Top level access point for sync services
 */
public class SyncManager implements SyncServerConnection.NotificationListener {

	private static final String TAG = SyncManager.class.getSimpleName();

	private static SyncManager instance;

	private GoogleSignInAccount googleSignInAccount;
	private boolean applicationIsActive, running;
	private SyncConfiguration syncConfiguration;
	private Context context;
	private SyncServerConnection syncServerConnection;
	private ChangeJournal changeJournal;
	private TimestampRecorder timestampRecorder;
	private SyncEngine syncEngine;

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
		googleSignInAccount = GoogleSignInManager.getInstance().getGoogleSignInAccount();

		syncServerConnection = new SyncServerConnection(syncConfiguration.getSyncServerConnectionUrl());
		syncServerConnection.addNotificationListener(this);

		changeJournal = new ChangeJournal(context);
		timestampRecorder = new TimestampRecorder(context);
		syncEngine = new SyncEngine(context, syncConfiguration);

		if (googleSignInAccount != null) {
			syncEngine.setGoogleIdToken(googleSignInAccount.getIdToken());
		}

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

	public SyncEngine getSyncEngine() {
		return syncEngine;
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
		if (applicationIsActive && googleSignInAccount != null) {
			start();
		} else {
			stop();
		}
	}

	///////////////////////////////////////////////////////////////////

	@Override
	public void onStatusReceived(Status status) {
		Log.i(TAG, "onStatusReceived: received Status notification from web socket connection: " + status.toString());
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
		googleSignInAccount = event.getGoogleSignInAccount();
		syncServerConnection.resetExponentialBackoff();
		syncEngine.setGoogleIdToken(event.getGoogleSignInAccount().getIdToken());
		startOrStopSyncServices();
	}

	@Subscribe
	public void onSignedOut(GoogleSignOutEvent event) {
		Log.d(TAG, "onSignedOut:");
		googleSignInAccount = null;
		startOrStopSyncServices();
	}



}
