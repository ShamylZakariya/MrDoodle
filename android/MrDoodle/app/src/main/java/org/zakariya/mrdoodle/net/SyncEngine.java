package org.zakariya.mrdoodle.net;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.Log;

import org.zakariya.mrdoodle.net.api.SyncService;
import org.zakariya.mrdoodle.net.model.RemoteChangeReport;
import org.zakariya.mrdoodle.net.model.SyncReport;
import org.zakariya.mrdoodle.net.transport.RemoteStatus;
import org.zakariya.mrdoodle.net.transport.TimestampRecordEntry;
import org.zakariya.mrdoodle.signin.model.SignInAccount;
import org.zakariya.mrdoodle.sync.ChangeJournal;
import org.zakariya.mrdoodle.sync.SyncConfiguration;
import org.zakariya.mrdoodle.sync.SyncException;
import org.zakariya.mrdoodle.sync.TimestampRecorder;
import org.zakariya.mrdoodle.sync.model.ChangeJournalItem;
import org.zakariya.mrdoodle.sync.model.ChangeType;
import org.zakariya.mrdoodle.sync.model.SyncLogEntry;
import org.zakariya.mrdoodle.sync.model.SyncState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.realm.Realm;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;

import static org.zakariya.mrdoodle.sync.model.SyncLogEntry.Phase.COMPLETE;
import static org.zakariya.mrdoodle.sync.model.SyncLogEntry.Phase.FAILED;
import static org.zakariya.mrdoodle.sync.model.SyncLogEntry.Phase.PULL_COMPLETE;
import static org.zakariya.mrdoodle.sync.model.SyncLogEntry.Phase.PULL_ITEM;
import static org.zakariya.mrdoodle.sync.model.SyncLogEntry.Phase.PULL_START;
import static org.zakariya.mrdoodle.sync.model.SyncLogEntry.Phase.PUSH_COMPLETE;
import static org.zakariya.mrdoodle.sync.model.SyncLogEntry.Phase.PUSH_ITEM;
import static org.zakariya.mrdoodle.sync.model.SyncLogEntry.Phase.PUSH_START;
import static org.zakariya.mrdoodle.sync.model.SyncLogEntry.Phase.START;

/**
 *
 */
@SuppressWarnings("TryFinallyCanBeTryWithResources") // not for API 17 it can't
public class SyncEngine {

	private static final String TAG = SyncEngine.class.getSimpleName();

	private Context context;
	private SyncConfiguration syncConfiguration;
	private OkHttpClient httpClient;
	private Retrofit retrofit;
	private String authorizationToken;
	private String deviceId;
	private SyncService syncService;
	private boolean syncing;

	public SyncEngine(Context context, SyncConfiguration syncConfiguration) {
		this.context = context;
		this.syncConfiguration = syncConfiguration;

		// set up an interceptor to add Authorization headers
		OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();
		httpClientBuilder.addInterceptor(new Interceptor() {
			@Override
			public okhttp3.Response intercept(Chain chain) throws IOException {
				Request original = chain.request();
				Request request = original.newBuilder()
						.header(SyncService.REQUEST_HEADER_USER_AGENT, "MrDoodle")
						.header(SyncService.REQUEST_HEADER_AUTH, getAuthorizationToken())
						.header(SyncService.REQUEST_HEADER_DEVICE_ID, getDeviceId())
						.build();

				return chain.proceed(request);
			}
		});

		httpClient = httpClientBuilder.build();
		retrofit = new Retrofit.Builder()
				.baseUrl(syncConfiguration.getSyncServiceUrl())
				.addConverterFactory(ScalarsConverterFactory.create())
				.addConverterFactory(GsonConverterFactory.create())
				.client(httpClient)
				.build();

		syncService = retrofit.create(SyncService.class);
	}

	public Context getContext() {
		return context;
	}

	public SyncConfiguration getSyncConfiguration() {
		return syncConfiguration;
	}

	public OkHttpClient getHttpClient() {
		return httpClient;
	}

	public Retrofit getRetrofit() {
		return retrofit;
	}

	public SyncService getSyncService() {
		return syncService;
	}

	@Nullable public String getAuthorizationToken() {
		return authorizationToken;
	}

	@Nullable public String getDeviceId() {
		return deviceId;
	}

	/**
	 * Set the (likely JWT) authorization token. This is called automatically by SyncManager when the
	 * auth token becomes available, and when it's updated.
	 *
	 * @param authorizationToken the user id authorization JWT token
	 */
	public void setAuthorizationToken(String authorizationToken) {
		this.authorizationToken = authorizationToken;
	}

	/**
	 * Set the device ID issued by the sync server on connection. This is called from SyncManager::onSyncServerRemoteStatusReceived iff the payload contains a deviceId
	 * @param deviceId id issued by sync server on connection and authorization
	 */
	public void setDeviceId(@Nullable  String deviceId) {
		this.deviceId = deviceId;
	}

	/**
	 * @return true if a sync is currently in operation
	 */
	synchronized public boolean isSyncing() {
		return syncing;
	}

	public interface ModelObjectDataProvider {
		/**
		 * Given a model object with an id and a specified class, get its byte [] serialization or null
		 *
		 * @param blobId   id of the model object
		 * @param blobType the internal type/name/class of the model object for your reference
		 * @return the referenced model object's byte[] serialized form, or null if no such item exists
		 * @throws Exception
		 */
		@Nullable
		byte[] getModelObjectData(String blobId, String blobType) throws Exception;
	}


	public interface ModelObjectDataConsumer {

		enum Action {
			CREATE,
			UPDATE,
			NOTHING
		}

		/**
		 * Given a byte[] of the serialized form of a model object, and the items id and class,
		 * either update the existing item or create a new one in the application's persistent store
		 *
		 * @param blobId   id of the model object
		 * @param blobType the internal type/name/class of the model object for your reference
		 * @param blobData the byte[] serialized form
		 * @return Action.UPDATE if an existing model object was updated, Action.CREATE if a new one was created, or Action.NOTHING if the blobType was unrecognized
		 * @throws Exception
		 */
		Action setModelObjectData(String blobId, String blobType, byte[] blobData) throws Exception;

	}

	public interface ModelObjectDeleter {
		/**
		 * Delete the model object of a given id and class
		 *
		 * @param modelId   the id of the model object
		 * @param modelType internal name/class of model for your reference
		 * @return true iff the referenced model existed and could be deleted
		 * @throws Exception
		 */
		boolean deleteModelObject(String modelId, String modelType) throws Exception;
	}

	/**
	 * Perform a sync pushing local changes to server, and updating local state to match server's
	 *
	 * @param account                 the account of the logged-in user
	 * @param syncState               local timestampHeadSeconds and lastSyncDate
	 * @param changeJournal           the journal which records local changes since last sync
	 * @param timestampRecorder       record of timestamp/documentType/action
	 * @param modelObjectDataProvider mechanism which provides bytes representing a blob of a given id and class to send upstream
	 * @param modelObjectDataConsumer mechanism which takes a blob id, class and bytes and adds it to the app's data store
	 * @param modelObjectDeleter      mechanism which deletes blobs of a gien id
	 * @return the current status (timestamp head, locked documents, etc)
	 */
	synchronized public SyncReport sync(
			SignInAccount account,
			SyncState syncState,
			ChangeJournal changeJournal,
			TimestampRecorder timestampRecorder,
			ModelObjectDataProvider modelObjectDataProvider,
			ModelObjectDataConsumer modelObjectDataConsumer,
			ModelObjectDeleter modelObjectDeleter)
			throws Exception {


		if (syncing) {
			throw new IllegalStateException("Can't start a sync() when SyncEngine is currently syncing");
		}

		// 1) if we have local changes we request a write token, and go to step 2. if not, go to step 5

		// 2) for each change in changeJournal, push the blob, and the server will return a timestamp which we record in timestampRecorder for that blob

		// 3) after pushing all local changes, commit write session

		// 4) erase changeJournal

		// 5) get changes since syncState.timestampHeadSeconds to get remote timestamps

		// 6) for each entry,
		//      if the remote timestamp is newer than local, pull it down, update entry in timestampRecorder
		//      if remote element is a deletion, delete locally and remove entry in timestampRecorder

		// 7) update syncState.timestampHeadSeconds to status.timestampHeadSeconds, and update syncState.lastSyncDate to now

		syncing = true;

		// all logging for duration of sync will go into here. The log
		// will be copied to the realm at the end of the sync
		SyncLogEntry log = new SyncLogEntry();

		try {

			log(log, START, "Starting sync with current timestampHeadSeconds: " + syncState.getTimestampHeadSeconds());

			RemoteStatus remoteStatus = pushLocalChanges(
					log,
					account,
					changeJournal,
					timestampRecorder,
					modelObjectDataProvider);

			SyncReport syncReport = pullRemoteChanges(
					log,
					remoteStatus,
					account,
					syncState,
					timestampRecorder,
					modelObjectDataConsumer,
					modelObjectDeleter);

			log(log, COMPLETE, "Sync complete, updating local timestamp head to: " + syncReport.getTimestampHeadSeconds());
			syncState.setTimestampHeadSeconds(syncReport.getTimestampHeadSeconds());
			syncState.setLastSyncDate(new Date());

			return syncReport;

		} catch (Exception e) {

			// log & rethrow
			log(log, FAILED, "Sync failed", e);
			throw e;

		} finally {
			log(log, COMPLETE, "DONE");

			Realm realm = Realm.getDefaultInstance();
			realm.beginTransaction();
			realm.copyToRealm(log);
			realm.commitTransaction();
			realm.close();

			syncing = false;
		}
	}

	///////////////////////////////////////////////////////////////////

	@Nullable
	private RemoteStatus pushLocalChanges(
			SyncLogEntry log,
			SignInAccount account,
			ChangeJournal changeJournal,
			TimestampRecorder timestampRecorder,
			ModelObjectDataProvider modelObjectDataProvider)
			throws Exception {

		Set<ChangeJournalItem> localChanges = changeJournal.getChangeJournalItems();

		// nothing to do
		if (localChanges.isEmpty()) {
			log(log, PUSH_COMPLETE, "No local changes to push upstream");
			return null;
		}

		String accountId = account.getId();

		// 1) get a write session token
		String writeSessionToken = startWriteSession(accountId);
		log(log, PUSH_START, "Got write session token: \"" + writeSessionToken + "\" - starting push phase of sync");

		try {

			// 2) Push our changes upstream.
			Set<ChangeJournalItem> changesToPushUpstream = new HashSet<>(changeJournal.getChangeJournalItems());

			for (ChangeJournalItem change : changesToPushUpstream) {

				// push the change and mark that the change has been pushed
				pushLocalChange(log, timestampRecorder, modelObjectDataProvider, accountId, writeSessionToken, change);
				changeJournal.clear(change.getModelObjectId());

			}

			// 3) Commit write session. If we get a status response, the session was closed
			// successfully, and we return it for the push phase to consume

			if (writeSessionToken != null) {
				log(log, PUSH_COMPLETE, "Finished PUSH phase successfully");
				RemoteStatus remoteStatus = commitWriteSession(accountId, writeSessionToken);
				if (remoteStatus != null) {
					writeSessionToken = null; // mark null so finally{} block doesn't try again
					log(log, PUSH_COMPLETE, "Closed write session, push phase of sync complete");
				}

				return remoteStatus;
			}

		} catch (Throwable t) {
			// log and rethrow
			log(log, PUSH_COMPLETE, "Failed push", t);
			throw t;
		} finally {

			// if we failed without closing out write session, just give it a stab - note, we're
			// happy to just consume exceptions here since we shouldn't be firing new exceptions
			// in a finally block. e.g., if sync failed because we're offline, this won't work anyway
			// and that exception will already be in-flight

			if (writeSessionToken != null) {
				log(log, PUSH_COMPLETE, "After failed PUSH phase, attempting to close write session: \"" + writeSessionToken + "\"");
				try {
					syncService.commitWriteSession(accountId, writeSessionToken).execute();
					log(log, PUSH_COMPLETE, "Write session closed.");
				} catch (Exception e) {
					log(log, PUSH_COMPLETE, "Unable to close write session", e);
				}
			}
		}

		return null;
	}

	private String startWriteSession(String accountId) throws IOException, SyncException {
		Response<String> writeTokenResponse = syncService.getWriteSessionToken(accountId).execute();
		if (!writeTokenResponse.isSuccessful()) {
			throw new SyncException("Unable to request write session token", writeTokenResponse);
		}

		return writeTokenResponse.body();
	}

	private RemoteStatus commitWriteSession(String accountId, String writeToken) throws IOException, SyncException {
		Response<RemoteStatus> remoteStatusResponse = syncService.commitWriteSession(accountId, writeToken).execute();
		if (!remoteStatusResponse.isSuccessful()) {
			throw new SyncException("Unable to commit write session", remoteStatusResponse);
		} else {
			return remoteStatusResponse.body();
		}
	}

	private void pushLocalChange(SyncLogEntry log, TimestampRecorder timestampRecorder, ModelObjectDataProvider modelObjectDataProvider, String accountId, String writeToken, ChangeJournalItem change) throws Exception {
		String id = change.getModelObjectId();
		String modelClass = change.getModelObjectClass();
		ChangeType type = ChangeType.values()[change.getChangeType()];
		TimestampRecordEntry timestampRecordEntry = null;

		log(log, PUSH_ITEM, "Pushing change: " + change);

		switch (type) {
			case MODIFY: {

				byte[] bytes = modelObjectDataProvider.getModelObjectData(id, modelClass);

				if (bytes == null) {
					throw new SyncException("Unable to serialize object id: " + id + " class: " + modelClass);
				}

				RequestBody body = RequestBody.create(SyncService.BLOB_MEDIA_TYPE, bytes);
				Response<TimestampRecordEntry> timestampResponse = syncService.putBlob(
						accountId,
						id,
						writeToken,
						modelClass,
						body).execute();

				if (!timestampResponse.isSuccessful()) {
					throw new SyncException("Unable to upload object[id: " + id + " documentType: " + modelClass + "] blob data", timestampResponse);
				}

				timestampRecordEntry = timestampResponse.body();
				break;
			}

			case DELETE: {
				Response<TimestampRecordEntry> timestampResponse = syncService.deleteBlob(
						accountId,
						id,
						writeToken).execute();

				if (timestampResponse.isSuccessful()) {
					timestampRecordEntry = timestampResponse.body();
				} else if (timestampResponse.code() != 404) {
					// 404 is OK, because we might have a record of deleting something which had
					// not yet been pushed to server. Other failures need to be logged.
					throw new SyncException("Unable to delete remote object[id: " + id + " documentType: " + modelClass + "] response: ", timestampResponse);
				}

				break;
			}
		}

		// if the push was a delete of something which was never on the server, we'll have a null timestampRecordEntry
		// which is OK. So, IFF we got a timestampRecordEntry, sanity check it, and record it.

		if (timestampRecordEntry != null) {

			if (!timestampRecordEntry.documentId.equals(id)) {
				throw new SyncException("Mismatched object id in TimestampRecordEntry. Expected: " + id + " got: " + timestampRecordEntry.documentId);
			}

			if (timestampRecordEntry.action != change.getChangeType()) {
				throw new SyncException("Mismatched action type in TimestampRecordEntry. Expected: " + change.getChangeType() + " got: " + timestampRecordEntry.action);
			}

			// record response
			timestampRecorder.setTimestamp(id, timestampRecordEntry.timestampSeconds);

			log(log, PUSH_ITEM, "Pushed change, response: " + timestampRecordEntry);
		}
	}

	///////////////////////////////////////////////////////////////////

	private SyncReport pullRemoteChanges(
			SyncLogEntry log,
			@Nullable RemoteStatus remoteStatus,
			SignInAccount account,
			SyncState syncState,
			TimestampRecorder timestampRecorder,
			ModelObjectDataConsumer modelObjectDataConsumer,
			ModelObjectDeleter modelObjectDeleter)
			throws Exception {

		String accountId = account.getId();
		List<RemoteChangeReport> remoteChangeReports = new ArrayList<>();

		if (remoteStatus == null) {
			remoteStatus = getStatus(accountId);
		}

		log(log, PULL_START, "Starting pull phase of sync. Remote timestampHeadSeconds: " + remoteStatus.timestampHeadSeconds);

		// early exit if we're up to date
		if (remoteStatus.timestampHeadSeconds <= syncState.getTimestampHeadSeconds()) {
			log(log, PULL_COMPLETE, "Already up to date, finishing.");
			return new SyncReport(remoteStatus.timestampHeadSeconds);
		}

		try {

			// get list of changes since local timestamp head
			Map<String, TimestampRecordEntry> remoteChanges = getRemoteChanges(syncState, accountId);

			// for each change, apply it and record it
			for (TimestampRecordEntry remoteChange : remoteChanges.values()) {
				log(log, PULL_ITEM, "Pulling change: " + remoteChange);
				RemoteChangeReport report = pullRemoteChange(log, timestampRecorder, modelObjectDataConsumer, modelObjectDeleter, accountId, remoteChange);

				if (report != null) {
					remoteChangeReports.add(report);
					log(log, PULL_ITEM, "Pulled remote change - report: " + report);
				} else {
					log(log, PULL_ITEM, "Pulled remote change, no report");
				}
			}

			// we're done
			return new SyncReport(remoteChangeReports, remoteStatus.timestampHeadSeconds);
		} catch (Exception e) {
			// log and rethrow
			log(log, PULL_COMPLETE, "Failed pull", e);
			throw e;
		}
	}

	@Nullable
	private RemoteChangeReport pullRemoteChange(SyncLogEntry log, TimestampRecorder timestampRecorder, ModelObjectDataConsumer modelObjectDataConsumer, ModelObjectDeleter modelObjectDeleter, String accountId, TimestampRecordEntry remoteChange) throws Exception {
		final ChangeType changeType = ChangeType.values()[remoteChange.action];
		final String documentId = remoteChange.documentId;
		final String documentType = remoteChange.documentType;

		switch (changeType) {
			case MODIFY: {
				long localTimestamp = timestampRecorder.getTimestamp(documentId);

				// early exit if remote timestamp is not newer than ours
				if (remoteChange.timestampSeconds <= localTimestamp) {
					log(log, PULL_ITEM, "MODIFY Skipping item " + documentId + " as local item has same or newer timestamp");
					return null;
				}

				log(log, PULL_ITEM, "MODIFY Downloading updated data item " + documentId);

				// get the blob data
				Response<ResponseBody> blobResponse = syncService.getBlob(accountId, documentId).execute();
				if (!blobResponse.isSuccessful()) {
					throw new SyncException("Unable to download blob data for id: " + documentId, blobResponse);
				}

				// create/update a document using the data
				byte[] blobBytes = blobResponse.body().bytes();
				ModelObjectDataConsumer.Action action = modelObjectDataConsumer.setModelObjectData(documentId, documentType, blobBytes);
				timestampRecorder.setTimestamp(documentId, remoteChange.timestampSeconds);

				// now record a change report
				switch (action) {
					case CREATE:
						return new RemoteChangeReport(documentId, RemoteChangeReport.Action.CREATE);
					case UPDATE:
						return new RemoteChangeReport(documentId, RemoteChangeReport.Action.UPDATE);
				}

				break;
			}

			case DELETE: {
				log(log, PULL_ITEM, "DELETE item: " + documentId + " type: " + documentType);
				modelObjectDeleter.deleteModelObject(documentId, documentType);
				timestampRecorder.clearTimestamp(documentId);
				return new RemoteChangeReport(documentId, RemoteChangeReport.Action.DELETE);
			}
		}

		return null;
	}

	private Map<String, TimestampRecordEntry> getRemoteChanges(SyncState syncState, String accountId) throws IOException, SyncException {
		Response<Map<String, TimestampRecordEntry>> remoteChanges = syncService.getChanges(accountId, syncState.getTimestampHeadSeconds()).execute();

		if (!remoteChanges.isSuccessful()) {
			throw new SyncException("Unable to get remote change set", remoteChanges);
		}

		return remoteChanges.body();
	}

	private RemoteStatus getStatus(String accountId) throws IOException, SyncException {
		Response<RemoteStatus> statusResponse = syncService.getRemoteStatus(accountId).execute();
		if (!statusResponse.isSuccessful()) {
			throw new SyncException("Unable to get remote status", statusResponse);
		} else {
			return statusResponse.body();
		}
	}

	private void log(SyncLogEntry log, SyncLogEntry.Phase phase, String message) {
		Log.i(TAG, message);
		log.appendLog(phase, message); // note the log is NOT in the realm until sync completes
	}

	private void log(SyncLogEntry log, SyncLogEntry.Phase phase, String message, Throwable t) {
		Log.e(TAG, message, t);
		log.appendError(phase, message, t);
	}

}
