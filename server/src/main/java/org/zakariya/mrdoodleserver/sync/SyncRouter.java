package org.zakariya.mrdoodleserver.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.MediaType;
import org.eclipse.jetty.websocket.api.Session;
import org.jetbrains.annotations.Nullable;
import org.zakariya.mrdoodleserver.auth.Authenticator;
import org.zakariya.mrdoodleserver.services.WebSocketConnection;
import org.zakariya.mrdoodleserver.sync.transport.Status;
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
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static spark.Spark.*;

/**
 * SyncRouter
 * Establishes the REST routes used for sync operations.
 */
public class SyncRouter implements WebSocketConnection.WebSocketConnectionCreatedListener {

	private static final String REQUEST_HEADER_AUTH = "Authorization";
	private static final String REQUEST_HEADER_MODEL_CLASS = "X-Model-Class";
	private static final String REQUEST_HEADER_WRITE_TOKEN = "X-Write-Token";
	private static final boolean READ_WRITE_LOCK_IS_FAIR = true;

	private Configuration configuration;
	private Authenticator authenticator;
	private JedisPool jedisPool;
	private Map<String, SyncManager> syncManagersByAccountId = new HashMap<>();
	private ObjectMapper objectMapper = new ObjectMapper();
	private boolean authenticationEnabled;
	private ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock(READ_WRITE_LOCK_IS_FAIR);

	private long syncManagerCleanupDelaySeconds = 300;
	private long syncManagerCleanupDelayGracePeriodSeconds = 60;
	private ScheduledExecutorService syncManagerCleanupScheduler = Executors.newSingleThreadScheduledExecutor();
	private Map<String, ScheduledFuture> syncManagerCleanupFuturesByAccountId = new HashMap<>();

	public SyncRouter(Configuration configuration, Authenticator authenticator, JedisPool jedisPool) {
		this.configuration = configuration;
		this.authenticator = authenticator;
		this.jedisPool = jedisPool;
		this.authenticationEnabled = configuration.getBoolean("authenticator/enabled", true);
		syncManagerCleanupDelaySeconds = configuration.getInt("router/inactiveSyncManagerDisposalTimeoutSeconds", (int)syncManagerCleanupDelaySeconds);
		syncManagerCleanupDelayGracePeriodSeconds = configuration.getInt("router/inactiveSyncManagerDisposalGracePeriodSeconds", (int)syncManagerCleanupDelayGracePeriodSeconds);
	}

	public void configureRoutes() {
		String basePath = getBasePath();

		// all api calls must authenticate
		before(basePath + "/*", this::authenticate);

		get(basePath + "/status", this::getStatus);
		get(basePath + "/changes", this::getChanges);

		// get a write session token
		get(basePath + "/writeSession/start", this::startWriteSession);
		// end a write session using token received above
		delete(basePath + "/writeSession/sessions/:token", this::commitWriteSession);

		// blobs
		get(basePath + "/blob/:blobId", this::getBlob);
		put(basePath + "/blob/:blobId", this::putBlob);
		delete(basePath + "/blob/:blobId", this::deleteBlob);
	}

	///////////////////////////////////////////////////////////////////

	private void authenticate(Request request, Response response) {
		if (authenticationEnabled) {
			String googleIdToken = request.headers(REQUEST_HEADER_AUTH);
			if (googleIdToken == null || googleIdToken.isEmpty()) {
				halt(401, "SyncRouter::authenticate - Missing authorization token");
			} else {
				try {
					String verifiedId = this.authenticator.verify(googleIdToken);
					String pathAccountId = request.params("accountId");
					if (verifiedId != null) {
						// token passed validation, but only allows access to :accountId subpath
						if (!verifiedId.equals(pathAccountId)) {
							halt(401, "SyncRouter::authenticate - Authorization token for account: " + verifiedId + " is valid, but does not grant access to account: " + pathAccountId);
						}
					} else {
						halt(401, "SyncRouter::authenticate - Invalid authorization token");
					}
				} catch (Exception e) {
					haltWithError500("SyncRouter::authenticate - Unable to verify authorization token, error: " + e.getLocalizedMessage(), e);
				}
			}
		}
	}

	@Nullable
	private String getStatus(Request request, Response response) {
		try {
			readWriteLock.readLock().lock();
			String accountId = request.params("accountId");
			SyncManager syncManager = getSyncManagerForAccount(accountId);
			Status status = syncManager.getStatus();

			try {
				return objectMapper.writeValueAsString(status);
			} catch (JsonProcessingException e) {
				haltWithError500("SyncRouter::getStatus - Unable to encode SyncManager.Status to JSON string, e: " + e.getLocalizedMessage(), e);
			}

			// if we're here we already halted the response
			return null;
		} finally {
			readWriteLock.readLock().unlock();
		}
	}

	@Nullable
	private String getChanges(Request request, Response response) {
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
					haltWithError500("SyncRouter::getChanges - Unable to parse 'since' query parameter \"" + since + "\" as a number. error: " + nfe.getLocalizedMessage(), nfe);
					return null;
				}
			}

			try {
				Map<String, TimestampRecord.Entry> entries = timestampRecord.getEntriesSince(sinceTimestamp);
				return objectMapper.writeValueAsString(entries);
			} catch (JsonProcessingException e) {
				haltWithError500("SyncRouter::getChanges - unable to serialize timestamps map to JSON", e);
				return null;
			}
		} finally {
			readWriteLock.readLock().unlock();
		}
	}

	@Nullable
	private String startWriteSession(Request request, Response response) {
		String accountId = request.params("accountId");
		SyncManager syncManager = getSyncManagerForAccount(accountId);
		SyncManager.WriteSession session = syncManager.startWriteSession();
		return session.getToken();
	}

	@Nullable
	private String commitWriteSession(Request request, Response response) {
		String accountId = request.params("accountId");
		String token = request.params("token");
		SyncManager syncManager = getSyncManagerForAccount(accountId);

		try {
			readWriteLock.writeLock().lock();
			if (syncManager.commitWriteSession(token)) {

				// sync session is complete! time to broadcast status (which includes updated
				// timestampHead) to clients
				Status status = syncManager.getStatus();

				WebSocketConnection connection = WebSocketConnection.getInstance();
				connection.broadcast(accountId, status);

				try {
					return objectMapper.writeValueAsString(status);
				} catch (JsonProcessingException e) {
					haltWithError500("SyncRouter::commitWriteSession - unable to serialize status to JSON", e);
					return null;
				}

			} else {
				halt(403, "SyncRouter::commitWriteSession - The write token provided (" + token + ") was not valid");
				return null;
			}
		} finally {
			readWriteLock.writeLock().unlock();
		}
	}

	@Nullable
	private String getBlob(Request request, Response response) {
		try {
			readWriteLock.readLock().lock();

			String accountId = request.params("accountId");
			String blobId = request.params("blobId");
			SyncManager syncManager = getSyncManagerForAccount(accountId);
			BlobStore blobStore = syncManager.getBlobStore();
			BlobStore.Entry entry = blobStore.get(blobId);

			if (entry != null) {
				byte[] blobBytes = entry.getData();

				response.type(MediaType.OCTET_STREAM.toString());
				response.raw().setContentLength(blobBytes.length);
				response.status(200);

				ServletOutputStream os = response.raw().getOutputStream();
				org.apache.commons.io.IOUtils.write(blobBytes, os);
				os.close();
			} else {
				halt(404);
			}

		} catch (IOException e) {
			haltWithError500("SyncRouter::getBlob - Unable to copy blob bytes to response", e);
		} finally {
			readWriteLock.readLock().unlock();
		}

		return null;
	}

	@Nullable
	private String putBlob(Request request, Response response) {

		// we need to do this to extract the blob form data
		request.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/temp"));

		String modelClass = request.headers(REQUEST_HEADER_MODEL_CLASS);
		if (modelClass == null || modelClass.isEmpty()) {
			halt(400, "SyncRouter::putBlob - Missing model class header attribute (\"" + REQUEST_HEADER_MODEL_CLASS + "\")");
			return null;
		}

		String writeToken = request.headers(REQUEST_HEADER_WRITE_TOKEN);
		if (writeToken == null || writeToken.isEmpty()) {
			halt(400, "SyncRouter::putBlob - Missing write token(\"" + REQUEST_HEADER_MODEL_CLASS + "\"); writes are disallowed without a write token");
			return null;
		}

		String accountId = request.params("accountId");
		String blobId = request.params("blobId");
		SyncManager syncManager = getSyncManagerForAccount(accountId);
		SyncManager.WriteSession session = syncManager.getWriteSession(writeToken);

		if (session == null) {
			halt(403, "SyncRouter::putBlob - The write token provided is not valid");
			return null;
		}

		// now get timestamp record and blobstore for this write session.
		// note: because writes go into the session, we don't need to wrap this in a writeLock
		TimestampRecord timestampRecord = session.getTimestampRecord();
		BlobStore blobStore = session.getBlobStore();

		try (InputStream is = request.raw().getPart("blob").getInputStream()) {

			long timestamp = syncManager.getTimestampSeconds();
			timestampRecord.record(blobId, timestamp, TimestampRecord.Action.WRITE);

			byte[] data = org.apache.commons.io.IOUtils.toByteArray(is);
			blobStore.set(blobId, modelClass, timestamp, data);

			// Use the input stream to create a file
		} catch (ServletException | IOException e) {
			haltWithError500("SyncRouter::putBlob - Unable to read blob data from request", e);
		}

		return null;
	}

	@Nullable
	private String deleteBlob(Request request, Response response) {

		String accountId = request.params("accountId");
		String blobId = request.params("blobId");

		String writeToken = request.headers(REQUEST_HEADER_WRITE_TOKEN);
		if (writeToken == null || writeToken.isEmpty()) {
			halt(400, "SyncRouter::deleteBlob - Missing write token(" + REQUEST_HEADER_MODEL_CLASS + "); writes are disallowed without a write token");
			return null;
		}


		SyncManager syncManager = getSyncManagerForAccount(accountId);
		SyncManager.WriteSession session = syncManager.getWriteSession(writeToken);

		if (session == null) {
			halt(403, "SyncRouter::deleteBlob - The write token provided is not valid");
			return null;
		}

		// now get timestamp record and blobstore for this write session
		// note: because writes go into the session, we don't need to wrap this in a writeLock
		TimestampRecord timestampRecord = session.getTimestampRecord();
		BlobStore blobStore = session.getBlobStore();

		long timestamp = syncManager.getTimestampSeconds();
		timestampRecord.record(blobId, timestamp, TimestampRecord.Action.DELETE);
		blobStore.delete(blobId);

		return null;
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
			syncManager = new SyncManager(jedisPool, accountId);
			syncManagersByAccountId.put(accountId, syncManager);
		}

		scheduleSyncManagerClose(syncManager);
		return syncManager;
	}

	private void scheduleSyncManagerClose(SyncManager syncManager) {
		String accountId = syncManager.getAccountId();

		// cancel any scheduled cleanup
		ScheduledFuture future = syncManagerCleanupFuturesByAccountId.get(accountId);
		if (future != null && !future.isCancelled()) {

			// early exit if the future still has plenty of time remaining
			// we do this so we don't re-schedule dozens of times for a batch of sync calls.
			if (future.getDelay(TimeUnit.SECONDS) < (syncManagerCleanupDelaySeconds - syncManagerCleanupDelayGracePeriodSeconds)) {
				return;
			}

			// the future is past its grace period. cancel it. We'll schedule a new one below.
			future.cancel(false);
			syncManagerCleanupFuturesByAccountId.remove(accountId);
		}

		future = syncManagerCleanupScheduler.schedule(() -> {
			syncManager.close();
			syncManagerCleanupFuturesByAccountId.remove(accountId);
			syncManagersByAccountId.remove(accountId);
		}, syncManagerCleanupDelaySeconds, TimeUnit.SECONDS);

		syncManagerCleanupFuturesByAccountId.put(accountId, future);
	}

	private void haltWithError500(String message, Exception e) {
		System.err.println(message);
		e.printStackTrace();
		halt(500, message);
	}

	///////////////////////////////////////////////////////////////////


	@Override
	public void onWebSocketConnectionCreated(WebSocketConnection connection) {
		// register to listen for user connect/disconnect, and forward them to the correct SyncManager
		connection.addUserSessionStatusChangeListener(new WebSocketConnection.OnUserSessionStatusChangeListener() {
			@Override
			public void onUserSessionConnected(WebSocketConnection connection, Session session, String googleId) {
				SyncManager syncManager = getSyncManagerForAccount(googleId);
				syncManager.onUserSessionConnected(connection, session, googleId);
			}

			@Override
			public void onUserSessionDisconnected(WebSocketConnection connection, Session session, String googleId) {
				SyncManager syncManager = getSyncManagerForAccount(googleId);
				syncManager.onUserSessionDisconnected(connection, session, googleId);
			}
		});
	}
}
