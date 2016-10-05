package org.zakariya.mrdoodle.sync;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.Log;

import com.squareup.otto.Subscribe;

import org.zakariya.mrdoodle.net.SyncEngine;
import org.zakariya.mrdoodle.net.SyncServerConnection;
import org.zakariya.mrdoodle.net.model.SyncReport;
import org.zakariya.mrdoodle.net.transport.RemoteStatus;
import org.zakariya.mrdoodle.signin.AuthenticationTokenReceiver;
import org.zakariya.mrdoodle.signin.SignInManager;
import org.zakariya.mrdoodle.signin.events.SignInEvent;
import org.zakariya.mrdoodle.signin.events.SignOutEvent;
import org.zakariya.mrdoodle.signin.model.SignInAccount;
import org.zakariya.mrdoodle.sync.model.SyncState;
import org.zakariya.mrdoodle.util.BusProvider;
import org.zakariya.mrdoodle.util.Debouncer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

import io.realm.Realm;
import rx.Observable;
import rx.Observer;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * Top level access point for sync services
 */
@SuppressWarnings("TryFinallyCanBeTryWithResources")
public class SyncManager implements SyncServerConnection.NotificationListener {

	private static final int REMOTE_STATUS_CHANGE_SYNC_TRIGGER_DEBOUNCE_MILLIS = 500;
	private static final int LOCAL_UPDATE_SYNC_TRIGGER_DEBOUNCE_MILLIS = 1000;

	public interface ModelDataAdapter extends SyncEngine.ModelObjectDataConsumer, SyncEngine.ModelObjectDataProvider, SyncEngine.ModelObjectDeleter {
	}

	public interface SyncStateListener {
		void didBeginSync();

		void didCompleteSync(SyncReport report);

		void didFailSync(Throwable failure);
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
	private List<WeakReference<SyncStateListener>> syncStateListeners = new ArrayList<>();
	private Debouncer<RemoteStatus> remoteStatusSyncTriggerDebouncer;
	private Debouncer<Void> localChangeSyncTriggerDebouncer;

	public static void init(Context context, SyncConfiguration syncConfiguration, ModelDataAdapter modelDataAdapter) {
		if (instance == null) {
			instance = new SyncManager(context, syncConfiguration, modelDataAdapter);
		}
	}

	public static SyncManager getInstance() {
		return instance;
	}

	private SyncManager(Context context, SyncConfiguration syncConfiguration, ModelDataAdapter modelDataAdapter) {
		BusProvider.getMainThreadBus().register(this);

		this.syncConfiguration = syncConfiguration;
		this.modelDataAdapter = modelDataAdapter;


		applicationIsActive = true;
		userAccount = SignInManager.getInstance().getAccount();

		syncServerConnection = new SyncServerConnection(syncConfiguration.getSyncServerConnectionUrl());
		syncServerConnection.addNotificationListener(this);

		changeJournal = new ChangeJournal(CHANGE_JOURNAL_PERSIST_PREFIX, true);
		timestampRecorder = new TimestampRecorder();
		syncEngine = new SyncEngine(context, syncConfiguration);

		remoteStatusSyncTriggerDebouncer = new Debouncer<>(REMOTE_STATUS_CHANGE_SYNC_TRIGGER_DEBOUNCE_MILLIS, new Action1<RemoteStatus>() {
			@Override
			public void call(RemoteStatus remoteStatus) {
				triggerBackgroundSyncForRemoteStatusChange(remoteStatus);
			}
		});

		localChangeSyncTriggerDebouncer = new Debouncer<Void>(LOCAL_UPDATE_SYNC_TRIGGER_DEBOUNCE_MILLIS, new Action1<Void>() {
			@Override
			public void call(Void aVoid) {
				triggerBackgroundSyncForLocalStatusChange();
			}
		});

		updateAuthorizationToken();
		startOrStopSyncServices();
	}

	/**
	 * @return true if SyncManager is connected to the sync server
	 */
	public boolean isConnected() {
		return getSyncServerConnection().isConnected();
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

	///////////////////////////////////////////////////////////////////

	public void addSyncStateListener(SyncStateListener listener) {
		syncStateListeners.add(new WeakReference<>(listener));
	}

	public void removeSyncStateListener(SyncStateListener listener) {
		int index = -1;
		for (int i = 0, n = syncStateListeners.size(); i < n; i++) {
			WeakReference<SyncStateListener> listenerWeakReference = syncStateListeners.get(i);
			SyncStateListener l = listenerWeakReference.get();
			if (l == listener) {
				index = i;
				break;
			}
		}

		if (index >= 0) {
			syncStateListeners.remove(index);
		}
	}

	public boolean isSyncing() {
		return syncEngine.isSyncing();
	}

	/**
	 * Using Rx, return an observable which calls performSync()
	 *
	 * @return an observable bearing Status
	 */
	public Observable<SyncReport> sync() {
		return Observable.fromCallable(new Callable<SyncReport>() {
			@Override
			public SyncReport call() throws Exception {
				return performSync();
			}
		});
	}

	/**
	 * Using Rx, return an observable which calls resetAndPerformSync()
	 *
	 * @return an observable bearing Status
	 */
	public Observable<SyncReport> resetAndSync(final LocalStoreDeleter deleter) {
		return Observable.fromCallable(new Callable<SyncReport>() {
			@Override
			public SyncReport call() throws Exception {
				return resetAndPerformSync(deleter);
			}
		});
	}

	/**
	 * Perform sync, synchronously. Sync will either complete successfully, or throw an exception.
	 * When sync is complete without an exception, all local changes will have been pushed to
	 * the server, and remote changes will have been pulled from the server.
	 */
	private SyncReport performSync() throws Exception {

		// notify listeners that sync is starting
		for (WeakReference<SyncStateListener> listenerWeakReference : syncStateListeners) {
			SyncStateListener listener = listenerWeakReference.get();
			if (listener != null) {
				listener.didBeginSync();
			}
		}

		SyncState syncState = getSyncState();


		// 0) Make a copy of our ChangeJournal, and clear it.
		//  User may modify documents while a sync is in operation. After sync succeeds or fails
		//  merge what's left in the copy back to the "live" one

		ChangeJournal syncChangeJournal = ChangeJournal.createSilentInMemoryCopy(this.changeJournal);

		// silently clear change journal
		changeJournal.clear(false);

		try {
			SyncReport syncReport = syncEngine.sync(userAccount,
					syncState,
					syncChangeJournal,
					timestampRecorder,
					modelDataAdapter,
					modelDataAdapter,
					modelDataAdapter);

			// save the new syncState
			persistSyncState(syncState);

			// notify listeners of completion
			for (WeakReference<SyncStateListener> listenerWeakReference : syncStateListeners) {
				SyncStateListener listener = listenerWeakReference.get();
				if (listener != null) {
					listener.didCompleteSync(syncReport);
				}
			}

			return syncReport;
		} catch (Exception e) {

			// notify listeners of error
			for (WeakReference<SyncStateListener> listenerWeakReference : syncStateListeners) {
				SyncStateListener listener = listenerWeakReference.get();
				if (listener != null) {
					listener.didFailSync(e);
				}
			}

			throw e;
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
	 *                state, e.g., clearing documents from your realm instance
	 */
	private SyncReport resetAndPerformSync(LocalStoreDeleter deleter) throws Exception {

		// delete/clear all the things
		deleter.deleteLocalStore();
		persistSyncState(new SyncState());
		timestampRecorder.clear();
		changeJournal.clear(false);

		// this will now pull the "truth" from the server since we have no local state
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

	/**
	 * Record a local data store document creation event in the change journal, possibly kicking off a sync
	 * @param id document id
	 * @param type document type
	 */
	public void markLocalDocumentCreation(String id, String type) {
		markLocalDocumentModification(id, type);
	}

	/**
	 * Record a local data store document edit event in the change journal, possibly kicking off a sync
	 * @param id document id
	 * @param type document type
	 */
	public void markLocalDocumentModification(String id, String type) {
		getChangeJournal().markModified(id, type);
		localChangeSyncTriggerDebouncer.send(null);
	}

	/**
	 * Record a local data store document deletion event in the change journal, possibly kicking off a sync
	 * @param id document id
	 * @param type document type
	 */
	public void markLocalDocumentDeletion(String id, String type) {
		getChangeJournal().markDeleted(id, type);
		localChangeSyncTriggerDebouncer.send(null);
	}

	///////////////////////////////////////////////////////////////////

	private void connect() {
		if (!running) {
			running = true;
			Log.d(TAG, "connect:");
			syncServerConnection.connect();
		}
	}

	private void disconnect() {
		if (running) {
			Log.d(TAG, "disconnect:");
			syncServerConnection.disconnect();
			running = false;
		}
	}

	///////////////////////////////////////////////////////////////////

	private void performBackgroundSync() {

		if (!isConnected()) {
			return;
		}

		Log.i(TAG, "performBackgroundSync: kicking off background sync");
		sync().subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(new Observer<SyncReport>() {
					@Override
					public void onCompleted() {
					}

					@Override
					public void onError(Throwable e) {
						// TODO: Show some kind of unobtrusive error message?
						Log.e(TAG, "performBackgroundSync - onError: ", e);
					}

					@Override
					public void onNext(SyncReport syncReport) {
						Log.i(TAG, "performBackgroundSync - onNext: Sync completed successfully: " + syncReport);
					}
				});
	}

	private void triggerBackgroundSyncForLocalStatusChange() {
		if (!isSyncing()) {
			Log.i(TAG, "triggerBackgroundSyncForLocalStatusChange:");
			performBackgroundSync();
		} else {
			// we're currently syncing - try again in the future. the debouncer will
			// re-call this method in LOCAL_UPDATE_SYNC_TRIGGER_DEBOUNCE_MILLIS
			Log.i(TAG, "triggerBackgroundSyncForLocalStatusChange: sync in progress, scheduling re-attempt...");
			localChangeSyncTriggerDebouncer.send(null);
		}
	}

	private void triggerBackgroundSyncForRemoteStatusChange(RemoteStatus remoteStatus) {
		SyncState currentLocalSyncState = getSyncState();

		// remote timestamp head is newer than ours - looks like we need to sync
		if (remoteStatus.timestampHeadSeconds > currentLocalSyncState.getTimestampHeadSeconds()) {

			if (!isSyncing()) {
				Log.i(TAG, "triggerBackgroundSyncForRemoteStatusChange: remote timestampHeadSeconds (" + remoteStatus.timestampHeadSeconds + ") is newer than ours (" + currentLocalSyncState.getTimestampHeadSeconds() + ") - starting sync.");
				performBackgroundSync();
			} else {

				// we're currently syncing - try again in the future. the debouncer will
				// re-deliver remote status to us in REMOTE_STATUS_CHANGE_SYNC_TRIGGER_DEBOUNCE_MILLIS

				Log.i(TAG, "triggerBackgroundSyncForRemoteStatusChange: sync in progress, scheduling re-attempt...");
				remoteStatusSyncTriggerDebouncer.send(remoteStatus);
			}

		}
	}

	private void startOrStopSyncServices() {
		if (applicationIsActive && userAccount != null) {
			connect();
		} else {
			disconnect();
		}
	}

	private void updateAuthorizationToken() {
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
	private SyncState getSyncState() {

		Realm realm = Realm.getDefaultInstance();
		try {
			SyncState syncState = realm.where(SyncState.class).findFirst();

			// if none was available, create a reasonable default
			if (syncState == null) {
				realm.beginTransaction();

				syncState = realm.createObject(SyncState.class);
				syncState.setTimestampHeadSeconds(0);
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
	 *
	 * @param syncState the instance to persist
	 */
	private void persistSyncState(SyncState syncState) {
		Realm realm = Realm.getDefaultInstance();
		try {

			SyncState ss = realm.where(SyncState.class).findFirst();
			if (ss == null) {
				realm.beginTransaction();
				ss = realm.createObject(SyncState.class);
				ss.setTimestampHeadSeconds(syncState.getTimestampHeadSeconds());
				ss.setLastSyncDate(syncState.getLastSyncDate());
				realm.commitTransaction();
			} else {
				realm.beginTransaction();
				ss.setTimestampHeadSeconds(syncState.getTimestampHeadSeconds());
				ss.setLastSyncDate(syncState.getLastSyncDate());
				realm.commitTransaction();
			}

		} finally {
			realm.close();
		}
	}

	///////////////////////////////////////////////////////////////////

	@Override
	public void onRemoteStatusReceived(RemoteStatus remoteStatus) {
		Log.i(TAG, "onRemoteStatusReceived: received Status notification from web socket connection: " + remoteStatus.toString());
		syncEngine.setDeviceId(remoteStatus.deviceId);
		remoteStatusSyncTriggerDebouncer.send(remoteStatus);
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
