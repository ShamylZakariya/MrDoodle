package org.zakariya.mrdoodleserver.routes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.MediaType;
import org.eclipse.jetty.websocket.api.Session;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zakariya.mrdoodleserver.auth.Authenticator;
import org.zakariya.mrdoodleserver.services.WebSocketConnection;
import org.zakariya.mrdoodleserver.sync.BlobStore;
import org.zakariya.mrdoodleserver.sync.LockManager;
import org.zakariya.mrdoodleserver.sync.SyncManager;
import org.zakariya.mrdoodleserver.sync.TimestampRecord;
import org.zakariya.mrdoodleserver.sync.transport.LockResponse;
import org.zakariya.mrdoodleserver.sync.transport.Status;
import org.zakariya.mrdoodleserver.sync.transport.TimestampRecordEntry;
import org.zakariya.mrdoodleserver.util.Configuration;
import org.zakariya.mrdoodleserver.util.Preconditions;
import redis.clients.jedis.JedisPool;
import spark.Request;
import spark.Response;
import spark.ResponseTransformer;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static spark.Spark.*;

/**
 * SyncRouter
 * Establishes the REST routes used for sync operations.
 */
public class SyncRouter implements WebSocketConnection.WebSocketConnectionCreatedListener {

	private static final Logger logger = LoggerFactory.getLogger(SyncRouter.class);

	public static final String REQUEST_HEADER_AUTH = "Authorization";
	public static final String REQUEST_HEADER_DOCUMENT_TYPE = "X-Document-Type";
	public static final String REQUEST_HEADER_WRITE_TOKEN = "X-Write-Token";
	public static final String REQUEST_HEADER_DEVICE_ID = "X-Device-ID";

	private static final String DEFAULT_STORAGE_PREFIX = "dev";
	private static final boolean READ_WRITE_LOCK_IS_FAIR = true;

	private static final String RESPONSE_TYPE_JSON = MediaType.JSON_UTF_8.toString();
	private static final String RESPONSE_TYPE_TEXT = MediaType.PLAIN_TEXT_UTF_8.toString();
	private static final String RESPONSE_TYPE_OCTET_STREAM = MediaType.OCTET_STREAM.toString();

	private Configuration configuration;
	private Authenticator authenticator;
	private JedisPool jedisPool;
	private Map<String, SyncManager> syncManagersByAccountId = new HashMap<>();
	private boolean authenticationEnabled;
	private String storagePrefix;
	private ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock(READ_WRITE_LOCK_IS_FAIR);
	private ResponseTransformer jsonResponseTransformer = new JsonResponseTransformer();

	public SyncRouter(Configuration configuration, Authenticator authenticator, JedisPool jedisPool) {
		this.configuration = configuration;
		this.authenticator = authenticator;
		this.jedisPool = jedisPool;
		this.authenticationEnabled = configuration.getBoolean("authenticator/enabled", true);
		this.storagePrefix = configuration.get("prefix", DEFAULT_STORAGE_PREFIX);
	}

	public void configureRoutes() {
		String basePath = getBasePath();

		// all api calls must authenticate
		before(basePath + "/*", this::authenticate);
		before(basePath + "/*", this::checkRequiredPreconditions);

		get(basePath + "/status", this::getStatus, jsonResponseTransformer);
		get(basePath + "/changes", this::getChanges, jsonResponseTransformer);

		// get a write session token
		get(basePath + "/writeSession/start", this::startWriteSession);
		// end a write session using token received above
		delete(basePath + "/writeSession/sessions/:token", this::commitWriteSession, jsonResponseTransformer);

		// blobs
		get(basePath + "/blob/:blobId", this::getBlob);
		put(basePath + "/blob/:blobId", this::putBlob, jsonResponseTransformer);
		delete(basePath + "/blob/:blobId", this::deleteBlob, jsonResponseTransformer);

		// locks
		put(basePath + "/locks/:documentId", this::requestLock, jsonResponseTransformer);
		delete(basePath + "/locks/:documentId", this::releaseLock, jsonResponseTransformer);
		get(basePath + "/locks/:documentId", this::isLocked, jsonResponseTransformer);
	}

	///////////////////////////////////////////////////////////////////

	private void authenticate(Request request, Response response) {
		if (authenticationEnabled) {
			String authToken = request.headers(REQUEST_HEADER_AUTH);
			if (authToken == null || authToken.isEmpty()) {
				sendErrorAndHalt(response, 401, "SyncRouter::authenticate - Missing authorization token");
			} else {
				String verifiedId = null;

				try {
					verifiedId = authenticator.verify(authToken);
				} catch (Exception e) {
					sendErrorAndHalt(response, 500, "SyncRouter::authenticate - Unable to verify authorization token, error: " + e.getLocalizedMessage(), e);
					return;
				}

				String pathAccountId = request.params("accountId");
				if (verifiedId != null) {
					// token passed validation, but only allows access to :accountId subpath
					if (!verifiedId.equals(pathAccountId)) {
						sendErrorAndHalt(response, 401, "SyncRouter::authenticate - Authorization token for account: " + verifiedId + " is valid, but does not grant access to account: " + pathAccountId);
					}
				} else {
					sendErrorAndHalt(response, 401, "SyncRouter::authenticate - Invalid authorization token");
				}
			}
		}
	}

	private void checkRequiredPreconditions(Request request, Response response) {

		// all requests require a device id
		String deviceId = request.headers(REQUEST_HEADER_DEVICE_ID);
		if (deviceId == null || deviceId.isEmpty()) {
			sendErrorAndHalt(response, 400, "SyncRouter::checkRequiredPreconditions - Missing deviceId token");
			return;
		}

		// now confirm deviceId is valid
		String accountId = request.params("accountId");
		if (accountId != null && !accountId.isEmpty()) {
			SyncManager syncManager = getSyncManagerForAccount(accountId);
			if (!syncManager.isValidDeviceId(deviceId)) {
				sendErrorAndHalt(response, 400, "SyncRouter::checkRequiredPreconditions - The deviceId provided \"" + deviceId + "\" is not valid.");
			}
		} else {
			sendErrorAndHalt(response, 400, "SyncRouter::checkRequiredPreconditions - Missing accountId");
		}
	}

	@Nullable
	private Object getStatus(Request request, Response response) {
		try {
			readWriteLock.readLock().lock();
			String accountId = request.params("accountId");
			SyncManager syncManager = getSyncManagerForAccount(accountId);

			response.type(RESPONSE_TYPE_JSON);
			return syncManager.getStatus();

		} finally {
			readWriteLock.readLock().unlock();
		}
	}

	@Nullable
	private Object getChanges(Request request, Response response) {
		try {
			readWriteLock.readLock().lock();

			String accountId = request.params("accountId");
			SyncManager syncManager = getSyncManagerForAccount(accountId);
			TimestampRecord timestampRecord = syncManager.getTimestampRecord();

			String since = request.queryParams("since");
			long sinceTimestamp = 0;
			if (since != null && !since.isEmpty()) {
				try {
					sinceTimestamp = Long.parseLong(since);
				} catch (NumberFormatException nfe) {
					sendErrorAndHalt(response, 500, "SyncRouter::getChanges - Unable to parse 'since' query parameter \"" + since + "\" as a number. error: " + nfe.getLocalizedMessage(), nfe);
					return null;
				}
			}

			response.type(RESPONSE_TYPE_JSON);
			return timestampRecord.getEntriesSince(sinceTimestamp);

		} finally {
			readWriteLock.readLock().unlock();
		}
	}

	@Nullable
	private String startWriteSession(Request request, Response response) {
		String accountId = request.params("accountId");
		String deviceId = request.headers(REQUEST_HEADER_DEVICE_ID);

		SyncManager syncManager = getSyncManagerForAccount(accountId);
		SyncManager.WriteSession session = syncManager.startWriteSession(deviceId);

		// for the duration of a write session, whitelist the token
		String authToken = request.headers(REQUEST_HEADER_AUTH);
		authenticator.addToWhitelist(authToken);

		response.type(RESPONSE_TYPE_TEXT);
		return session.getToken();
	}

	@Nullable
	private Object commitWriteSession(Request request, Response response) {
		String accountId = request.params("accountId");
		String sessionToken = request.params("token");
		String deviceId = request.headers(REQUEST_HEADER_DEVICE_ID);
		SyncManager syncManager = getSyncManagerForAccount(accountId);

		// write session is finished, we can remove auth token from whitelist
		String authToken = request.headers(REQUEST_HEADER_AUTH);
		authenticator.removeFromWhitelist(authToken);

		try {
			readWriteLock.writeLock().lock();
			if (syncManager.commitWriteSession(deviceId, sessionToken)) {

				// sync session is complete! time to broadcast status (which includes updated
				// timestampHeadSeconds) to clients
				Status status = syncManager.getStatus();

				WebSocketConnection connection = WebSocketConnection.getInstance();
				connection.broadcast(accountId, status);

				response.type(RESPONSE_TYPE_JSON);
				return status;
			} else {
				sendErrorAndHalt(response, 403, "SyncRouter::commitWriteSession - The write token provided (" + sessionToken + ") was not valid");
				return null;
			}
		} finally {
			readWriteLock.writeLock().unlock();
		}
	}

	@Nullable
	private Object getBlob(Request request, Response response) {

		try {
			readWriteLock.readLock().lock();

			String accountId = request.params("accountId");
			String blobId = request.params("blobId");
			SyncManager syncManager = getSyncManagerForAccount(accountId);
			BlobStore blobStore = syncManager.getBlobStore();
			BlobStore.Entry entry = blobStore.get(blobId);

			if (entry != null) {
				byte[] blobBytes = entry.getData();

				response.raw().setContentLength(blobBytes.length);
				response.status(200);

				ServletOutputStream os = response.raw().getOutputStream();
				org.apache.commons.io.IOUtils.write(blobBytes, os);
				os.flush();
				os.close();

				response.type(RESPONSE_TYPE_OCTET_STREAM);
				return response.raw();
			} else {
				sendErrorAndHalt(response, 404, "Unknown blob ID");
			}

		} catch (IOException e) {
			sendErrorAndHalt(response, 500, "SyncRouter::getBlob - Unable to copy blob bytes to response", e);
		} finally {
			readWriteLock.readLock().unlock();
		}

		return null;
	}

	@Nullable
	private Object putBlob(Request request, Response response) {

		// we need to do this to extract the blob form data
		request.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/temp"));

		String modelClass = request.headers(REQUEST_HEADER_DOCUMENT_TYPE);
		if (modelClass == null || modelClass.isEmpty()) {
			sendErrorAndHalt(response, 400, "SyncRouter::putBlob - Missing model class header attribute (\"" + REQUEST_HEADER_DOCUMENT_TYPE + "\")");
			return null;
		}

		String writeToken = request.headers(REQUEST_HEADER_WRITE_TOKEN);
		if (writeToken == null || writeToken.isEmpty()) {
			sendErrorAndHalt(response, 400, "SyncRouter::putBlob - Missing write token(\"" + REQUEST_HEADER_WRITE_TOKEN + "\"); writes are disallowed without a write token");
			return null;
		}

		String accountId = request.params("accountId");
		String blobId = request.params("blobId");
		SyncManager syncManager = getSyncManagerForAccount(accountId);
		SyncManager.WriteSession session = syncManager.getWriteSession(writeToken);

		if (session == null) {
			sendErrorAndHalt(response, 403, "SyncRouter::putBlob - The write token provided is not valid");
			return null;
		}

		// now get timestamp record and blobstore for this write session.
		// note: because writes go into the session, we don't need to wrap this in a writeLock
		TimestampRecord timestampRecord = session.getTimestampRecord();
		BlobStore blobStore = session.getBlobStore();

		try (InputStream is = request.raw().getPart("blob").getInputStream()) {

			long timestamp = syncManager.getTimestampSeconds();
			TimestampRecordEntry entry = timestampRecord.record(blobId, modelClass, timestamp, TimestampRecord.Action.WRITE);

			byte[] data = org.apache.commons.io.IOUtils.toByteArray(is);
			blobStore.set(blobId, modelClass, timestamp, data);

			response.type(RESPONSE_TYPE_JSON);
			return entry;

		} catch (ServletException | IOException e) {
			sendErrorAndHalt(response, 500, "SyncRouter::putBlob - Unable to read blob data from request", e);
		}

		return null;
	}

	@Nullable
	private Object deleteBlob(Request request, Response response) {
		String accountId = request.params("accountId");
		String blobId = request.params("blobId");

		String writeToken = request.headers(REQUEST_HEADER_WRITE_TOKEN);
		if (writeToken == null || writeToken.isEmpty()) {
			sendErrorAndHalt(response, 400, "SyncRouter::deleteBlob - Missing write token(" + REQUEST_HEADER_DOCUMENT_TYPE + "); writes are disallowed without a write token");
			return null;
		}


		SyncManager syncManager = getSyncManagerForAccount(accountId);
		SyncManager.WriteSession session = syncManager.getWriteSession(writeToken);

		if (session == null) {
			sendErrorAndHalt(response, 403, "SyncRouter::deleteBlob - The write token provided is not valid");
			return null;
		}

		// now get timestamp record and blobstore for this write session
		// note: because writes go into the session, we don't need to wrap this in a writeLock
		TimestampRecord timestampRecord = session.getTimestampRecord();
		BlobStore blobStore = session.getBlobStore();

		// find whether blob lives in committed main store or this write session's store, and
		// extract the document type.

		String blobType;
		if (syncManager.getBlobStore().has(blobId)) {
			blobType = syncManager.getBlobStore().getType(blobId);
		} else if (blobStore.has(blobId)) {
			blobType = blobStore.getType(blobId);
		} else {
			sendErrorAndHalt(response, 404, "SyncRouter::deleteBlob - blob id \"" + blobId + "\" is not valid");
			return null;
		}


		// delete blob - note the session blob store may not actually have the blob,
		// but the deletion will be recorded and applied when merged with the committed store
		blobStore.delete(blobId);

		// record deletion.
		long timestamp = syncManager.getTimestampSeconds();
		TimestampRecordEntry entry = timestampRecord.record(blobId, blobType, timestamp, TimestampRecord.Action.DELETE);

		logger.info("Deleted blob: {} type: {} timestamp: {}", blobId, blobType, timestamp);

		response.type(RESPONSE_TYPE_JSON);
		return entry;
	}

	@Nullable
	private LockResponse requestLock(Request request, Response response) {
		try {
			String accountId = request.params("accountId");
			String documentId = request.params("documentId");
			String deviceId = request.headers(REQUEST_HEADER_DEVICE_ID);

			SyncManager syncManager = getSyncManagerForAccount(accountId);
			LockManager lockManager = syncManager.getLockManager();

			readWriteLock.writeLock().lock();

			LockResponse lockResponse = new LockResponse();
			lockResponse.documentId = documentId;

			response.type(RESPONSE_TYPE_JSON);

			if (lockManager.hasLock(deviceId, documentId)) {
				lockResponse.locked = true;
				return lockResponse;
			} else {
				lockResponse.locked = lockManager.lock(deviceId, documentId);
				return lockResponse;
			}

		} finally {
			readWriteLock.writeLock().unlock();
		}
	}

	@Nullable
	private LockResponse releaseLock(Request request, Response response) {
		try {
			String accountId = request.params("accountId");
			String documentId = request.params("documentId");
			String deviceId = request.headers(REQUEST_HEADER_DEVICE_ID);

			SyncManager syncManager = getSyncManagerForAccount(accountId);
			LockManager lockManager = syncManager.getLockManager();

			readWriteLock.writeLock().lock();

			LockResponse lockResponse = new LockResponse();
			lockResponse.documentId = documentId;


			if (lockManager.hasLock(deviceId, documentId)) {
				lockManager.unlock(deviceId, documentId);
				lockResponse.locked = false;
				response.type(RESPONSE_TYPE_JSON);
				return lockResponse;
			} else {
				sendErrorAndHalt(response, 400, "Cannot unlock document locked by another device.");
				return null;
			}

		} finally {
			readWriteLock.writeLock().unlock();
		}
	}

	@Nullable
	private LockResponse isLocked(Request request, Response response) {
		try {
			String accountId = request.params("accountId");
			String documentId = request.params("documentId");
			String deviceId = request.headers(REQUEST_HEADER_DEVICE_ID);

			SyncManager syncManager = getSyncManagerForAccount(accountId);
			LockManager lockManager = syncManager.getLockManager();

			readWriteLock.readLock().lock();

			LockResponse lockResponse = new LockResponse();
			lockResponse.documentId = documentId;
			lockResponse.locked = lockManager.isLocked(documentId);

			response.type(RESPONSE_TYPE_JSON);
			return lockResponse;
		} finally {
			readWriteLock.readLock().unlock();
		}
	}

	///////////////////////////////////////////////////////////////////

	private String getBasePath() {
		return "/api/" + configuration.get("version") + "/sync/:accountId";
	}

	private SyncManager getSyncManagerForAccount(String accountId) {
		Preconditions.checkNotNull(jedisPool, "jedisPool instance must be set");
		Preconditions.checkArgument(accountId != null && !accountId.isEmpty(), "accountId must be non-null and non-empty");

		SyncManager syncManager = syncManagersByAccountId.get(accountId);

		if (syncManager == null) {
			syncManager = new SyncManager(configuration, jedisPool, storagePrefix, accountId);
			syncManagersByAccountId.put(accountId, syncManager);
		}

		return syncManager;
	}

	private void sendErrorAndHalt(Response response, int code, String message, Exception e) {
		logger.error(message, e);
		response.type(RESPONSE_TYPE_TEXT);
		halt(code, message);
	}

	private void sendErrorAndHalt(Response response, int code, String message) {
		logger.error(message);
		response.type(RESPONSE_TYPE_TEXT);
		halt(code, message);
	}

	///////////////////////////////////////////////////////////////////

	private class JsonResponseTransformer implements ResponseTransformer {

		private ObjectMapper objectMapper = new ObjectMapper();

		@Override
		public String render(Object o) throws Exception {
			return objectMapper.writeValueAsString(o);
		}
	}

	///////////////////////////////////////////////////////////////////

	@Override
	public void onWebSocketConnectionCreated(WebSocketConnection connection) {
		// register to listen for user connect/disconnect, and forward them to the correct SyncManager
		connection.addUserSessionStatusChangeListener(new WebSocketConnection.OnUserSessionStatusChangeListener() {
			@Override
			public void onUserSessionConnected(WebSocketConnection connection, Session session, String accountId) {
				SyncManager syncManager = getSyncManagerForAccount(accountId);
				syncManager.onUserSessionConnected(connection, session, accountId);
			}

			@Override
			public void onUserSessionDisconnected(WebSocketConnection connection, Session session, String accountId) {
				SyncManager syncManager = getSyncManagerForAccount(accountId);
				syncManager.onUserSessionDisconnected(connection, session, accountId);

				// now check if any users of a particular account are still connected. if
				// not, we can free that account's syncManager
				if (connection.getTotalConnectedDevicesForAccountId(accountId) == 0) {
					logger.info("SyncRouter::onWebSocketConnectionCreated#onUserSessionDisconnected - userId: {} has no connected devices. Freeing user's syncManager", accountId);
					syncManager.close();
					syncManagersByAccountId.remove(accountId);
				}
			}
		});
	}
}
