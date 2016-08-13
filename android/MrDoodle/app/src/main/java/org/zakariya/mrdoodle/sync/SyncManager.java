package org.zakariya.mrdoodle.sync;

import android.content.Context;

import org.zakariya.mrdoodle.net.SyncServerConnection;
import org.zakariya.mrdoodle.util.GoogleSignInManager;

/**
 * Top level access point for sync services
 */
public class SyncManager {

	private static SyncManager instance;
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

		boolean userIsSignedIn = GoogleSignInManager.getInstance().getGoogleSignInAccount() != null;
		syncServerConnection = new SyncServerConnection(syncConfiguration.getSyncServerConnectionUrl(), userIsSignedIn);

		changeJournal = new ChangeJournal(context);
		timestampRecorder = new TimestampRecorder(context);
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
}
