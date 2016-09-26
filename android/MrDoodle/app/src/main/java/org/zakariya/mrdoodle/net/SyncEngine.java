package org.zakariya.mrdoodle.net;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.Log;

import org.zakariya.mrdoodle.net.api.SyncService;
import org.zakariya.mrdoodle.net.transport.Status;
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
import java.util.Date;
import java.util.HashSet;
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

	public String getAuthorizationToken() {
		return authorizationToken;
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
		/**
		 * Given a byte[] of the serialized form of a model object, and the items id and class,
		 * either update the existing item or create a new one in the application's persistent store
		 *
		 * @param blobId   id of the model object
		 * @param blobType the internal type/name/class of the model object for your reference
		 * @param blobData the byte[] serialized form
		 * @throws Exception
		 */
		void setModelObjectData(String blobId, String blobType, byte[] blobData) throws Exception;

	}

	public interface ModelObjectDeleter {
		/**
		 * Delete the model object of a given id and class
		 *
		 * @param modelId   the id of the model object
		 * @param modelType internal name/class of model for your reference
		 * @throws Exception
		 */
		void deleteModelObject(String modelId, String modelType) throws Exception;
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
	synchronized public Status sync(
			SignInAccount account,
			SyncState syncState,
			ChangeJournal changeJournal,
			TimestampRecorder timestampRecorder,
			ModelObjectDataProvider modelObjectDataProvider,
			ModelObjectDataConsumer modelObjectDataConsumer,
			ModelObjectDeleter modelObjectDeleter)
			throws Exception {

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

			log(log, "Starting sync with current timestampHeadSeconds: " + syncState.getTimestampHead());
			Status status = pushLocalChanges(
					log,
					account,
					changeJournal,
					timestampRecorder,
					modelObjectDataProvider);

			status = pullRemoteChanges(
					log,
					status,
					account,
					syncState,
					timestampRecorder,
					modelObjectDataConsumer,
					modelObjectDeleter);

			if (status != null) {
				log(log, "Sync complete, updating local timestamp head to: " + status.timestampHeadSeconds);
				syncState.setTimestampHead(status.timestampHeadSeconds);
				syncState.setLastSyncDate(new Date());
			}

			return status;

		} finally {
			log(log, "DONE");

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
	private Status pushLocalChanges(
			SyncLogEntry log,
			SignInAccount account,
			ChangeJournal changeJournal,
			TimestampRecorder timestampRecorder,
			ModelObjectDataProvider modelObjectDataProvider)
			throws Exception {

		Set<ChangeJournalItem> localChanges = changeJournal.getChangeJournalItems();

		// nothing to do
		if (localChanges.isEmpty()) {
			log(log, "No local changes to push upstream");
			return null;
		}

		String accountId = account.getId();

		// 1) get a write session token
		String writeSessionToken = startWriteSession(accountId);
		log(log, "Got write session token: \"" + writeSessionToken + "\" - starting push phase of sync");

		try {

			// 2) Push our changes upstream.
			Set<ChangeJournalItem> changesToPushUpstream = new HashSet<>(changeJournal.getChangeJournalItems());

			for (ChangeJournalItem change : changesToPushUpstream) {

				// push the change and mark that the change has been pushed
				pushLocalChange(timestampRecorder, modelObjectDataProvider, accountId, writeSessionToken, change);
				log(log, "Pushed item: \"" + change.getModelObjectId() + " upstream");
				changeJournal.clear(change.getModelObjectId());
			}

			// 3) Commit write session. If we get a status response, the session was closed
			// successfully, and we return it for the push phase to consume

			if (writeSessionToken != null) {
				Status status = commitWriteSession(accountId, writeSessionToken);
				if (status != null) {
					writeSessionToken = null; // mark null so finally{} block doesn't try again
					log(log, "Closed write session, push phase of sync complete");
				}

				return status;
			}

		} catch (Throwable t) {
			// log and rethrow
			log(log, "Sync failed", t);
		} finally {

			// if we failed without closing out write session, just give it a stab - note, we're
			// happy to just consume exceptions here since we shouldn't be firing new exceptions
			// in a finally block. e.g., if sync failed because we're offline, this won't work anyway
			// and that exception will already be in-flight

			if (writeSessionToken != null) {
				log(log, "After failed sync, attempting to close write session: \"" + writeSessionToken + "\"");
				try {
					syncService.commitWriteSession(accountId, writeSessionToken).execute();
					log(log, "Write session closed.");
				} catch (Exception e) {
					log(log, "Unable to close write session", e);
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

	private Status commitWriteSession(String accountId, String writeToken) throws IOException, SyncException {
		Response<Status> remoteStatusResponse = syncService.commitWriteSession(accountId, writeToken).execute();
		if (!remoteStatusResponse.isSuccessful()) {
			throw new SyncException("Unable to commit write session", remoteStatusResponse);
		} else {
			return remoteStatusResponse.body();
		}
	}

	private void pushLocalChange(TimestampRecorder timestampRecorder, ModelObjectDataProvider modelObjectDataProvider, String accountId, String writeToken, ChangeJournalItem change) throws Exception {
		String id = change.getModelObjectId();
		String modelClass = change.getModelObjectClass();
		ChangeType type = ChangeType.values()[change.getChangeType()];
		TimestampRecordEntry timestampRecordEntry = null;

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
				if (!timestampResponse.isSuccessful()) {
					throw new SyncException("Unable to delete remote object[id: " + id + " documentType: " + modelClass + "] blob data", timestampResponse);
				}

				timestampRecordEntry = timestampResponse.body();
				break;
			}
		}

		// sanity check our response

		if (timestampRecordEntry == null) {
			throw new SyncException("Didn't receive a TimestampRecordEntry from push operation");
		}

		if (!timestampRecordEntry.documentId.equals(id)) {
			throw new SyncException("Mismatched object id in TimestampRecordEntry. Expected: " + id + " got: " + timestampRecordEntry.documentId);
		}

		if (timestampRecordEntry.action != change.getChangeType()) {
			throw new SyncException("Mismatched action type in TimestampRecordEntry. Expected: " + change.getChangeType() + " got: " + timestampRecordEntry.action);
		}

		// record response
		timestampRecorder.setTimestamp(id, timestampRecordEntry.timestampSeconds);
	}

	///////////////////////////////////////////////////////////////////

	private Status pullRemoteChanges(
			SyncLogEntry log,
			@Nullable Status status,
			SignInAccount account,
			SyncState syncState,
			TimestampRecorder timestampRecorder,
			ModelObjectDataConsumer modelObjectDataConsumer,
			ModelObjectDeleter modelObjectDeleter)
			throws Exception {

		String accountId = account.getId();

		if (status == null) {
			status = getStatus(accountId);
		}

		log(log, "Starting pull phase of sync. Remote timestampHeadSeconds: " + status.timestampHeadSeconds);

		// early exit if we're up to date
		if (status.timestampHeadSeconds <= syncState.getTimestampHead()) {
			log(log, "We're up to date, finishing.");
			return status;
		}

		// now get list of changes since local timestamp head
		Map<String, TimestampRecordEntry> remoteChanges = getRemoteChanges(syncState, accountId);

		for (String id : remoteChanges.keySet()) {
			TimestampRecordEntry remoteChange = remoteChanges.get(id);
			if (pullRemoteChange(timestampRecorder, modelObjectDataConsumer, modelObjectDeleter, accountId, id, remoteChange)) {
				log(log, "Pulled remote change to object: " + id + " to local");
			} else {
				log(log, "Skipped remote change to object: " + id + " as we're up to date on this object");
			}
		}

		// we're done

		return status;
	}

	private boolean pullRemoteChange(TimestampRecorder timestampRecorder, ModelObjectDataConsumer modelObjectDataConsumer, ModelObjectDeleter modelObjectDeleter, String accountId, String id, TimestampRecordEntry remoteChange) throws Exception {
		ChangeType changeType = ChangeType.values()[remoteChange.action];
		switch (changeType) {
			case MODIFY:
				long localTimestamp = timestampRecorder.getTimestamp(id);

				// early exit if remote timestamp is not newer than ours
				if (remoteChange.timestampSeconds <= localTimestamp) {
					return false;
				}

				Response<ResponseBody> blobResponse = syncService.getBlob(accountId, remoteChange.documentId).execute();
				if (!blobResponse.isSuccessful()) {
					throw new SyncException("Unable to download blob data for id: " + remoteChange.documentId, blobResponse);
				}

				byte[] blobBytes = blobResponse.body().bytes();
				modelObjectDataConsumer.setModelObjectData(remoteChange.documentId, remoteChange.documentType, blobBytes);
				timestampRecorder.setTimestamp(id, remoteChange.timestampSeconds);
				break;

			case DELETE:
				modelObjectDeleter.deleteModelObject(remoteChange.documentId, remoteChange.documentType);
				timestampRecorder.clearTimestamp(id);
				break;
		}

		return true;
	}

	private Map<String, TimestampRecordEntry> getRemoteChanges(SyncState syncState, String accountId) throws IOException, SyncException {
		Response<Map<String, TimestampRecordEntry>> remoteChanges = syncService.getChanges(accountId, syncState.getTimestampHead()).execute();

		if (!remoteChanges.isSuccessful()) {
			throw new SyncException("Unable to get remote change set", remoteChanges);
		}

		return remoteChanges.body();
	}

	private Status getStatus(String accountId) throws IOException, SyncException {
		Response<Status> statusResponse = syncService.getStatus(accountId).execute();
		if (!statusResponse.isSuccessful()) {
			throw new SyncException("Unable to get remote status", statusResponse);
		} else {
			return statusResponse.body();
		}
	}

	private void log(SyncLogEntry log, String message) {
		Log.i(TAG, message);
		log.appendLog(message); // note the log is NOT in the realm until sync completes
	}

	private void log(SyncLogEntry log, String message, Throwable t) {
		Log.e(TAG, message, t);
		log.appendError(message, t);
	}

}
