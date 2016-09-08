package org.zakariya.mrdoodle.net.api;

import org.zakariya.mrdoodle.net.transport.Status;
import org.zakariya.mrdoodle.net.transport.TimestampRecordEntry;

import java.util.Map;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Multipart;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Defines the REST API endpoints for Sync
 */
public interface SyncService {

	String REQUEST_HEADER_AUTH = "Authorization";
	String REQUEST_HEADER_USER_AGENT = "User-Agent";
	String REQUEST_HEADER_MODEL_CLASS = "X-Model-Class";
	String REQUEST_HEADER_WRITE_TOKEN = "X-Write-Token";
	MediaType BLOB_MEDIA_TYPE = MediaType.parse("application/octet-stream");


	/**
	 * Get the status for this account.
	 *
	 * @param accountId the account id of the signed in user
	 * @return status, which describes current timestampHead, and document locks
	 */
	@GET("sync/{accountId}/status")
	Call<Status> getStatus(@Path("accountId") String accountId);

	/**
	 * Get the change set for this account, describing the changes in the remote store since a given timestamp in seconds
	 *
	 * @param accountId    the account id of the signed in user
	 * @param sinceSeconds if > 0, the remote store's timestamp records will be filtered to be >= this value
	 * @return map of document uuid to timestamp record entries
	 */
	@GET("sync/{accountId}/changes")
	Call<Map<String, TimestampRecordEntry>> getChanges(@Path("accountId") String accountId, @Query("since") long sinceSeconds);


	/**
	 * Get a token for performing a writing sync session. Syncs which only pull down remote data don't need to start
	 * a write session, but data can only be pushed upstream if the request includes the write token as a header
	 * SyncService.REQUEST_HEADER_WRITE_TOKEN. A write session must be committed by calling commitWriteSession(), else
	 * the data pushed upstream will not be committed and made visible to other clients.
	 *
	 * @param accountId the account id of the signed in user
	 * @return a string containing the write token to use for pushing data upstream
	 */
	@GET("sync/{accountId}/writeSession/start")
	Call<String> getWriteSessionToken(@Path("accountId") String accountId);

	/**
	 * Commit a write session for which you have requested a write session token via getWriteSessionToken
	 *
	 * @param accountId  the account id of the signed in user
	 * @param writeToken the write session token received from getWriteSessionToken
	 * @return status, which describes current timestampHead, and document locks
	 */
	@DELETE("sync/{accountId}/writeSession/sessions/{writeToken}")
	Call<Status> commitWriteSession(@Path("accountId") String accountId, @Path("writeToken") String writeToken);


	/**
	 * Get a blob from upstream
	 *
	 * @param accountId the account id of the signed in user
	 * @param blobId    the id of the blob to download
	 * @return ResponseBody (use response.bytes() or some such to get data)
	 */
	@GET("sync/{accountId}/blob/{blobId}")
	Call<ResponseBody> getBlob(@Path("accountId") String accountId, @Path("blobId") String blobId);

	/**
	 * Send a blob upstream
	 *
	 * @param accountId  the account id of the signed in user
	 * @param blobId     the id of the blob to upload
	 * @param writeToken the write token. Can't push changes without first requesting a write token via getWriteSessionToken()
	 * @param modelClass the "class" of the data. this will be visible to clients via the getChanges() call
	 * @param blob       the blob to upload. Create via RequestBody.create(SyncService.BLOB_MEDIA_TYPE, myBlobBytes)
	 * @return a TimestampRecordEntry to mark the timestamp of the deletion
	 */
	@Multipart
	@PUT("sync/{accountId}/blob/{blobId}")
	Call<TimestampRecordEntry> putBlob(
			@Path("accountId") String accountId,
			@Path("blobId") String blobId,
			@Header(REQUEST_HEADER_WRITE_TOKEN) String writeToken,
			@Header(REQUEST_HEADER_MODEL_CLASS) String modelClass,
			@Part("blob") RequestBody blob);


	/**
	 * Delete a blob upstream
	 *
	 * @param accountId  the account id of the signed in user
	 * @param blobId     the id of the blob to delete
	 * @param writeToken the write token. Can't push changes without first requesting a write token via getWriteSessionToken()
	 * @return a TimestampRecordEntry to mark the timestamp of the deletion
	 */
	@DELETE("sync/{accountId}/blob/{blobId}")
	Call<TimestampRecordEntry> deleteBlob(
			@Path("accountId") String accountId,
			@Path("blobId") String blobId,
			@Header(REQUEST_HEADER_WRITE_TOKEN) String writeToken);
}
