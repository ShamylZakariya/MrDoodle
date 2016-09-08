package org.zakariya.mrdoodle.net;

import android.content.Context;
import android.support.annotation.Nullable;

import org.zakariya.mrdoodle.net.api.SyncService;
import org.zakariya.mrdoodle.net.transport.Status;
import org.zakariya.mrdoodle.sync.ChangeJournal;
import org.zakariya.mrdoodle.sync.SyncConfiguration;
import org.zakariya.mrdoodle.sync.SyncManager;
import org.zakariya.mrdoodle.sync.TimestampRecorder;
import org.zakariya.mrdoodle.util.AsyncExecutor;

import java.io.IOException;
import java.util.Date;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 *
 */
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
			public Response intercept(Chain chain) throws IOException {
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

	public interface BlobDataProvider {
		@Nullable
		byte [] getBlobData(String blobId, String blobClass) throws Exception;
	}

	public interface BlobDataConsumer {
		void setBlobData(String blobId, String blobClass, byte[] blobData) throws Exception;
	}

	/**
	 * Perform a sync pushing local changes to server, and updating local state to match server's
	 *
	 * @param syncState         local timestampHead and lastSyncDate
	 * @param changeJournal     the journal which records local changes since last sync
	 * @param timestampRecorder record of timestamp/modelClass/action
	 * @param blobDataProvider  device which provides bytes representing a blob of a given id and class to send upstream
	 * @param blobDataConsumer  device which takes a blob id, class and bytes and adds it to the app's data store
	 */
	synchronized public void sync(
			SyncManager.SyncStateAccess syncState,
			ChangeJournal changeJournal,
			TimestampRecorder timestampRecorder,
			BlobDataProvider blobDataProvider,
			BlobDataConsumer blobDataConsumer)
			throws Exception {

		// 1) if we have local changes we request a write token, and go to step 2. if not, go to step 5
		// 2) for each change in changeJournal, push the blob, and the server will return a timestamp which we record in timestampRecorder for that blob
		// 3) after pushing all local changes, commit write session
		// 4) erase changeJournal
		// 5) get status since syncState.timestampHead to get remote timestamps
		// 6) for each entry,
		//      if the remote timestamp is newer than local, pull it down, update entry in timestampRecorder
		//      if remote element is a deletion, delete locally and remove entry in timestampRecorder
		// 7) update syncState.timestampHead to status.timestampHead, and update syncState.lastSyncDate to now

		syncing = true;
		try {

			pushLocalChanges(syncState, changeJournal, timestampRecorder, blobDataProvider);
			Status status = pullRemoteChanges(syncState, timestampRecorder, blobDataConsumer);

			syncState.setTimestampHead(status.timestampHead);
			syncState.setLastSyncDate(new Date());

		} finally {
			syncing = false;
		}
	}

	void pushLocalChanges(
			SyncManager.SyncStateAccess syncState,
			ChangeJournal changeJournal,
			TimestampRecorder timestampRecorder,
			BlobDataProvider blobDataProvider)
			throws Exception {



	}

	Status pullRemoteChanges(
			SyncManager.SyncStateAccess syncState,
			TimestampRecorder timestampRecorder,
			BlobDataConsumer blobDataConsumer)
			throws Exception {

		return null;
	}

}
