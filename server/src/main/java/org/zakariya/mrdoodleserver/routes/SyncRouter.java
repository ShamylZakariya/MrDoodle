package org.zakariya.mrdoodleserver.routes;

import org.eclipse.jetty.websocket.api.Session;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zakariya.mrdoodleserver.auth.Authenticator;
import org.zakariya.mrdoodleserver.auth.User;
import org.zakariya.mrdoodleserver.services.WebSocketConnection;
import org.zakariya.mrdoodleserver.sync.*;
import org.zakariya.mrdoodleserver.transport.LockStatus;
import org.zakariya.mrdoodleserver.transport.Status;
import org.zakariya.mrdoodleserver.transport.TimestampRecordEntry;
import org.zakariya.mrdoodleserver.util.Configuration;
import org.zakariya.mrdoodleserver.util.Preconditions;
import redis.clients.jedis.JedisPool;
import spark.Request;
import spark.Response;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static spark.Spark.*;

/**
 * SyncRouter
 * Establishes the REST routes used for sync operations.
 */
public class SyncRouter extends Router implements WebSocketConnection.WebSocketConnectionCreatedListener {

	private static final Logger logger = LoggerFactory.getLogger(SyncRouter.class);

	public static final String REQUEST_HEADER_AUTH = "Authorization";
	public static final String REQUEST_HEADER_DOCUMENT_TYPE = "X-Document-Type";
	public static final String REQUEST_HEADER_WRITE_TOKEN = "X-Write-Token";
	public static final String REQUEST_HEADER_DEVICE_ID = "X-Device-ID";

	private static final boolean READ_WRITE_LOCK_IS_FAIR = true;

	// multiple SyncRouters may exist because of threading, so we need to
	// make certain our syncManagers and locks are unique per account
	private static Map<String, SyncManager> syncManagersByAccountId = new HashMap<>();
	private static Map<String, ReentrantReadWriteLock> readWriteLocksByAccountId = new HashMap<>();

	private Authenticator authenticator;
	private UserRecordAccess userRecordAccess;

	public SyncRouter(Configuration configuration, Authenticator authenticator, JedisPool jedisPool) {
		super(configuration, jedisPool);
		this.authenticator = authenticator;
		userRecordAccess = new UserRecordAccess(getJedisPool(), getStoragePrefix());
	}

	@Override
	public Logger getLogger() {
		return logger;
	}

	public void initializeRoutes() {
		String basePath = getBasePath();

		// all api calls must authenticate
		before(basePath + "/*", this::authenticate);
		before(basePath + "/*", this::checkRequiredPreconditions);
		before(basePath + "/*", this::recordUserVisit);

		get(basePath + "/status", this::getStatus, getJsonResponseTransformer());
		get(basePath + "/changes", this::getChanges, getJsonResponseTransformer());

		// get a write session token
		get(basePath + "/writeSession/start", this::startWriteSession);
		// end a write session using token received above
		delete(basePath + "/writeSession/sessions/:token", this::commitWriteSession, getJsonResponseTransformer());

		// blobs
		get(basePath + "/blob/:blobId", this::getBlob);
		put(basePath + "/blob/:blobId", this::putBlob, getJsonResponseTransformer());
		delete(basePath + "/blob/:blobId", this::deleteBlob, getJsonResponseTransformer());

		// locks
		put(basePath + "/locks/:documentId", this::requestLock, getJsonResponseTransformer());
		delete(basePath + "/locks/:documentId", this::releaseLock, getJsonResponseTransformer());
		get(basePath + "/locks/:documentId", this::isLocked, getJsonResponseTransformer());
	}

	///////////////////////////////////////////////////////////////////

	private void authenticate(Request request, Response response) {
		String authToken = request.headers(REQUEST_HEADER_AUTH);
		if (authToken == null || authToken.isEmpty()) {
			sendErrorAndHalt(response, 401, "SyncRouter::authenticate - Missing authorization token");
		} else {
			User user;

			try {
				user = authenticator.verify(authToken);
			} catch (Exception e) {
				sendErrorAndHalt(response, 500, "SyncRouter::authenticate - Unable to verify authorization token, error: " + e.getLocalizedMessage(), e);
				return;
			}

			String pathAccountId = request.params("accountId");
			if (user != null) {
				// token passed validation, but only allows access to :accountId subpath
				if (!user.getAccountId().equals(pathAccountId)) {
					sendErrorAndHalt(response, 401, "SyncRouter::authenticate - Authorization token for account: " + user.getAccountId() + " is valid, but does not grant access to path: " + pathAccountId);
				}
			} else {
				sendErrorAndHalt(response, 401, "SyncRouter::authenticate - Invalid authorization token");
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

	private void recordUserVisit(Request request, Response response) {

		String authToken = request.headers(REQUEST_HEADER_AUTH);
		User user = authenticator.getUser(authToken);

		// record this user's visit
		userRecordAccess.recordUserVisit(user);
	}

	@Nullable
	private Object getStatus(Request request, Response response) {
		String accountId = request.params("accountId");
		String deviceId = request.headers(REQUEST_HEADER_DEVICE_ID);
		ReadWriteLock lock = getReadWriteLockForAccount(accountId);

		try {
			lock.readLock().lock();

			SyncManager syncManager = getSyncManagerForAccount(accountId);
			Status status = syncManager.getStatus(deviceId);

			response.type(RESPONSE_TYPE_JSON);
			return status;

		} finally {
			lock.readLock().unlock();
		}
	}

	@Nullable
	private Object getChanges(Request request, Response response) {
		String accountId = request.params("accountId");
		ReadWriteLock lock = getReadWriteLockForAccount(accountId);
		try {
			lock.readLock().lock();

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
			lock.readLock().unlock();
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

		ReadWriteLock lock = getReadWriteLockForAccount(accountId);

		try {
			lock.writeLock().lock();
			if (syncManager.commitWriteSession(deviceId, sessionToken)) {

				// notify all clients of updated status
				syncManager.broadcastStatusToConnectedDevices();

				// sync session is complete! time to broadcast status (which includes updated
				// timestampHeadSeconds) to clients
				Status status = syncManager.getStatus(deviceId);

				response.type(RESPONSE_TYPE_JSON);
				return status;
			} else {
				sendErrorAndHalt(response, 403, "SyncRouter::commitWriteSession - The write token provided (" + sessionToken + ") was not valid");
				return null;
			}
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Nullable
	private Object getBlob(Request request, Response response) {
		String accountId = request.params("accountId");
		ReadWriteLock lock = getReadWriteLockForAccount(accountId);

		try {
			lock.readLock().lock();

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
			lock.readLock().unlock();
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
	private LockStatus requestLock(Request request, Response response) {
		String accountId = request.params("accountId");
		String documentId = request.params("documentId");
		String deviceId = request.headers(REQUEST_HEADER_DEVICE_ID);
		ReadWriteLock lock = getReadWriteLockForAccount(accountId);

		try {

			SyncManager syncManager = getSyncManagerForAccount(accountId);
			LockManager lockManager = syncManager.getLockManager();

			lock.writeLock().lock();

			LockStatus lockStatus = new LockStatus();
			lockStatus.documentId = documentId;

			response.type(RESPONSE_TYPE_JSON);

			if (lockManager.hasLock(deviceId, documentId)) {
				// device already has the lock
				lockStatus.locked = true;
				lockStatus.lockHeldByRequestingDevice = true;
			} else {
				lockStatus.lockHeldByRequestingDevice = lockManager.lock(deviceId, documentId);
				lockStatus.locked = lockManager.isLocked(documentId);
			}

			return lockStatus;
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Nullable
	private LockStatus releaseLock(Request request, Response response) {
		String accountId = request.params("accountId");
		String documentId = request.params("documentId");
		String deviceId = request.headers(REQUEST_HEADER_DEVICE_ID);
		ReadWriteLock lock = getReadWriteLockForAccount(accountId);

		try {

			SyncManager syncManager = getSyncManagerForAccount(accountId);
			LockManager lockManager = syncManager.getLockManager();

			lock.writeLock().lock();

			LockStatus lockStatus = new LockStatus();
			lockStatus.documentId = documentId;


			// if this device has the lock and can unlock it, unlock it
			// otherwise just return the lock status
			if (lockManager.hasLock(deviceId, documentId)) {
				lockManager.unlock(deviceId, documentId);
				lockStatus.locked = false;
				lockStatus.lockHeldByRequestingDevice = false;
			} else {
				lockStatus.locked = lockManager.isLocked(documentId);
				lockStatus.lockHeldByRequestingDevice = false;
			}

			response.type(RESPONSE_TYPE_JSON);
			return lockStatus;
		} finally {
			lock.writeLock().unlock();
		}
	}

	@Nullable
	private LockStatus isLocked(Request request, Response response) {
		String accountId = request.params("accountId");
		String documentId = request.params("documentId");
		String deviceId = request.headers(REQUEST_HEADER_DEVICE_ID);
		ReadWriteLock lock = getReadWriteLockForAccount(accountId);

		try {

			SyncManager syncManager = getSyncManagerForAccount(accountId);
			LockManager lockManager = syncManager.getLockManager();

			lock.readLock().lock();

			LockStatus lockStatus = new LockStatus();
			lockStatus.documentId = documentId;
			lockStatus.locked = lockManager.isLocked(documentId);
			lockStatus.lockHeldByRequestingDevice = lockManager.hasLock(deviceId, documentId);

			response.type(RESPONSE_TYPE_JSON);
			return lockStatus;
		} finally {
			lock.readLock().unlock();
		}
	}

	///////////////////////////////////////////////////////////////////

	private String getBasePath() {
		return "/api/" + getApiVersion() + "/sync/:accountId";
	}

	private synchronized ReentrantReadWriteLock getReadWriteLockForAccount(String accountId) {
		ReentrantReadWriteLock lock = readWriteLocksByAccountId.get(accountId);
		if (lock == null) {
			lock = new ReentrantReadWriteLock(READ_WRITE_LOCK_IS_FAIR);
			readWriteLocksByAccountId.put(accountId, lock);
		}

		return lock;
	}

	private SyncManager getSyncManagerForAccount(String accountId) {
		Preconditions.checkNotNull(getJedisPool(), "jedisPool instance must be set");
		Preconditions.checkArgument(accountId != null && !accountId.isEmpty(), "accountId must be non-null and non-empty");

		SyncManager syncManager = syncManagersByAccountId.get(accountId);

		if (syncManager == null) {
			syncManager = new SyncManager(getConfiguration(), getJedisPool(), getStoragePrefix(), accountId);
			syncManagersByAccountId.put(accountId, syncManager);
		}

		return syncManager;
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

				User user = authenticator.getUserByAccountId(accountId);
				if (user != null) {
					userRecordAccess.recordUserVisit(user);
				}
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
