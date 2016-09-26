package org.zakariya.mrdoodle.sync;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.Log;

import com.squareup.otto.Subscribe;

import org.zakariya.mrdoodle.net.SyncEngine;
import org.zakariya.mrdoodle.net.SyncServerConnection;
import org.zakariya.mrdoodle.net.transport.Status;
import org.zakariya.mrdoodle.signin.AuthenticationTokenReceiver;
import org.zakariya.mrdoodle.signin.SignInManager;
import org.zakariya.mrdoodle.signin.events.SignInEvent;
import org.zakariya.mrdoodle.signin.events.SignOutEvent;
import org.zakariya.mrdoodle.signin.model.SignInAccount;
import org.zakariya.mrdoodle.sync.model.SyncState;
import org.zakariya.mrdoodle.util.BusProvider;

import java.util.Date;
import java.util.concurrent.Callable;

import io.realm.Realm;
import rx.Observable;

/**
 * Top level access point for sync services
 */
@SuppressWarnings("TryFinallyCanBeTryWithResources")
public class SyncManager implements SyncServerConnection.NotificationListener {

	public interface ModelDataAdapter extends SyncEngine.ModelObjectDataConsumer, SyncEngine.ModelObjectDataProvider, SyncEngine.ModelObjectDeleter {
	}

	private static final String CHANGE_JOURNAL_PERSIST_PREFIX = "SyncChangeJournal";
	private static final String TAG = SyncManager.class.getSimpleName();

	private static SyncManager instance;
	private SignInAccount userAccount;
	private boolean applicationIsActive, running;
	private SyncConfiguration syncConfiguration;
	private SyncServerConnection syncServerConnection;
	private ChangeJournal changeJournal;
	private TimestampRecorder timestampRecorder;
	private SyncEngine syncEngine;
	private ModelDataAdapter modelDataAdapter;

	public static void init(Context context, SyncConfiguration syncConfiguration, ModelDataAdapter modelDataAdapter) {
		if (instance == null) {
			instance = new SyncManager(context, syncConfiguration, modelDataAdapter);
		}
	}

	public static SyncManager getInstance() {
		return instance;
	}

	private SyncManager(Context context, SyncConfiguration syncConfiguration, ModelDataAdapter modelDataAdapter) {
		BusProvider.getBus().register(this);

		this.syncConfiguration = syncConfiguration;
		this.modelDataAdapter = modelDataAdapter;


		applicationIsActive = true;
		userAccount = SignInManager.getInstance().getAccount();

		syncServerConnection = new SyncServerConnection(syncConfiguration.getSyncServerConnectionUrl());
		syncServerConnection.addNotificationListener(this);

		changeJournal = new ChangeJournal(CHANGE_JOURNAL_PERSIST_PREFIX, true);
		timestampRecorder = new TimestampRecorder();
		syncEngine = new SyncEngine(context, syncConfiguration);

		updateAuthorizationToken();
		startOrStopSyncServices();
	}

	public SyncConfiguration getSyncConfiguration() {
		return syncConfiguration;
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

	public boolean isSyncing() {
		return syncEngine.isSyncing();
	}

	/**
	 * Using Rx, return an observable which calls performSync()
	 * @return an observable bearing Status
	 */
	public Observable<Status> sync() {
		return Observable.fromCallable(new Callable<Status>() {
			@Override
			public Status call() throws Exception {
				return performSync();
			}
		});
	}

	/**
	 * Using Rx, return an observable which calls resetAndPerformSync()
	 * @return an observable bearing Status
	 */
	public Observable<Status> resetAndSync(final LocalStoreDeleter deleter) {
		return Observable.fromCallable(new Callable<Status>() {
			@Override
			public Status call() throws Exception {
				return resetAndPerformSync(deleter);
			}
		});
	}

	/**
	 * Perform sync, synchronously. Sync will either complete successfully, or throw an exception.
	 * When sync is complete without an exception, all local changes will have been pushed to
	 * the server, and remote changes will have been pulled from the server.
	 */
	Status performSync() throws Exception {

		SyncState syncState = getSyncState();


		// 0) Make a copy of our ChangeJournal, and clear it.
		//  User may modify documents while a sync is in operation. After sync succeeds or fails
		//  merge what's left in the copy back to the "live" one

		ChangeJournal syncChangeJournal = ChangeJournal.createSilentInMemoryCopy(this.changeJournal);

		// silently clear change journal
		changeJournal.clear(false);

		try {
			Status status = syncEngine.sync(userAccount,
					syncState,
					syncChangeJournal,
					timestampRecorder,
					modelDataAdapter,
					modelDataAdapter,
					modelDataAdapter);

			persistSyncState(syncState);

			return status;
		} finally {
			// merge - sync may have failed leaving some items un-synced, put those back in the persisting change journal
			// to be pushed upstream next time
			changeJournal.merge(syncChangeJournal, false);
		}
	}

	public interface LocalStoreDeleter {
		void deleteLocalStore();
	}

	/**
	 * Delete the local store (e.g., completely reset local state)
	 * and then initiate a sync. This will cause the client to discard
	 * any local changes and simply download remote state.
	 *
	 * @param deleter the deleter is responsible for deleting local
	 *                state, e.g., clearing out your realm instance
	 */
	Status resetAndPerformSync(LocalStoreDeleter deleter) throws Exception {

		// TODO We have a race condition here. When deleting items from local store, events are emitted ON MAIN THREAD which means they go through a Handler, and have a delay. Even though we clear the changeJournal here, it will then receive delete events LATER. Ugh.

		deleter.deleteLocalStore();
		persistSyncState(new SyncState());
		timestampRecorder.clear();
		changeJournal.clear(false);

		return performSync();
	}

	public void start() {
		Log.d(TAG, "start:");
		applicationIsActive = true;
		syncServerConnection.resetExponentialBackoff();
		startOrStopSyncServices();
	}

	public void stop() {
		Log.d(TAG, "stop:");
		applicationIsActive = false;
		startOrStopSyncServices();
	}

	///////////////////////////////////////////////////////////////////

	void connect() {
		if (!running) {
			running = true;
			Log.d(TAG, "connect:");
			syncServerConnection.connect();
		}
	}

	void disconnect() {
		if (running) {
			Log.d(TAG, "disconnect:");
			syncServerConnection.disconnect();
			running = false;
		}
	}

	///////////////////////////////////////////////////////////////////

	void startOrStopSyncServices() {
		if (applicationIsActive && userAccount != null) {
			connect();
		} else {
			disconnect();
		}
	}

	void updateAuthorizationToken() {
		if (userAccount != null) {
			userAccount.getAuthenticationToken(new AuthenticationTokenReceiver() {
				@Override
				public void onAuthenticationTokenAvailable(String idToken) {
					syncEngine.setAuthorizationToken(idToken);
				}

				@Override
				public void onAuthenticationTokenError(@Nullable String errorMessage) {
					Log.e(TAG, "Unable to get authentication token, error: " + errorMessage);
				}
			});
		}
	}

	/**
	 * @return Load the persisted SyncState. Changes made won't be persisted until you call persistSyncState()
	 */
	SyncState getSyncState() {

		Realm realm = Realm.getDefaultInstance();
		try {
			SyncState syncState = realm.where(SyncState.class).findFirst();

			// if none was available, create a reasonable default
			if (syncState == null) {
				realm.beginTransaction();

				syncState = realm.createObject(SyncState.class);
				syncState.setTimestampHead(0);
				syncState.setLastSyncDate(new Date(0));

				realm.commitTransaction();
			}

			return realm.copyFromRealm(syncState);
		} finally {
			realm.close();
		}
	}

	/**
	 * Persist changes made to a SyncState instance
	 * @param syncState the instance to persist
	 */
	void persistSyncState(SyncState syncState) {
		Realm realm = Realm.getDefaultInstance();
		try {

			SyncState ss = realm.where(SyncState.class).findFirst();
			if (ss == null) {
				realm.beginTransaction();
				ss = realm.createObject(SyncState.class);
				ss.setTimestampHead(syncState.getTimestampHead());
				ss.setLastSyncDate(syncState.getLastSyncDate());
				realm.commitTransaction();
			} else {
				realm.beginTransaction();
				ss.setTimestampHead(syncState.getTimestampHead());
				ss.setLastSyncDate(syncState.getLastSyncDate());
				realm.commitTransaction();
			}

		} finally {
			realm.close();
		}
	}

	///////////////////////////////////////////////////////////////////

	@Override
	public void onStatusReceived(Status status) {
		Log.i(TAG, "onStatusReceived: received Status notification from web socket connection: " + status.toString());
	}

	///////////////////////////////////////////////////////////////////

	@Subscribe
	public void onSignedIn(SignInEvent event) {
		Log.d(TAG, "onSignedIn:");
		userAccount = event.getAccount();
		syncServerConnection.resetExponentialBackoff();
		updateAuthorizationToken();
		startOrStopSyncServices();
	}

	@Subscribe
	public void onSignedOut(SignOutEvent event) {
		Log.d(TAG, "onSignedOut:");
		userAccount = null;
		startOrStopSyncServices();
	}
}
