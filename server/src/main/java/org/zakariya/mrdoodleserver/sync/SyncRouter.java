package org.zakariya.mrdoodleserver.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.util.ByteStreams;
import com.google.common.net.MediaType;
import com.sun.xml.internal.messaging.saaj.util.ByteInputStream;
import org.eclipse.jetty.websocket.api.Session;
import org.jetbrains.annotations.Nullable;
import org.zakariya.mrdoodleserver.auth.Authenticator;
import org.zakariya.mrdoodleserver.services.WebSocketConnection;
import org.zakariya.mrdoodleserver.sync.transport.Status;
import org.zakariya.mrdoodleserver.util.Configuration;
import org.zakariya.mrdoodleserver.util.Preconditions;
import redis.clients.jedis.JedisPool;
import redis.clients.util.IOUtils;
import spark.Request;
import spark.Response;

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

	private static final String REQUEST_HEADER_AUTH = "Authorization";
	private static final String REQUEST_HEADER_MODEL_CLASS = "X-Model-Class";
	private static final boolean READ_WRITE_LOCK_IS_FAIR = true;

	private Configuration configuration;
	private Authenticator authenticator;
	private JedisPool jedisPool;
	private Map<String, SyncManager> syncManagersByAccountId = new HashMap<>();
	private ObjectMapper objectMapper = new ObjectMapper();
	private boolean authenticationEnabled;
	private ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock(READ_WRITE_LOCK_IS_FAIR);

	public SyncRouter(Configuration configuration, Authenticator authenticator, JedisPool jedisPool) {
		this.configuration = configuration;
		this.authenticator = authenticator;
		this.jedisPool = jedisPool;
		this.authenticationEnabled = configuration.getBoolean("authenticator/enabled", true);
	}

	public void configureRoutes() {
		String basePath = getBasePath();
		before(basePath + "/*", this::authenticate);
		get(basePath + "/status", this::getStatus);
		get(basePath + "/changes", this::getChanges);

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
				halt(401, "Missing authorization token");
			} else {
				try {
					String verifiedId = this.authenticator.verify(googleIdToken);
					String pathAccountId = request.params("accountId");
					if (verifiedId != null) {
						// token passed validation, but only allows access to :accountId subpath
						if (!verifiedId.equals(pathAccountId)) {
							halt(401, "Authorization token for account: " + verifiedId + " is valid, but does not grant access to account: " + pathAccountId);
						}
					} else {
						halt(401, "Invalid authorization token");
					}
				} catch (Exception e) {
					haltWithError500("SyncRouter::authenticate - Unable to verify authorization token, error: " + e.getLocalizedMessage(), e);
				}
			}
		}
	}

	@Nullable
	private String getStatus(Request request, Response response) {
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
				}
			}

			Map<String, Long> timestamps = timestampRecord.getTimestampsSince(sinceTimestamp);
			try {
				return objectMapper.writeValueAsString(timestamps);
			} catch (JsonProcessingException e) {
				haltWithError500("SyncRouter::getChanges - unable to serialize timestamps map to JSON", e);
				return null;
			}
		} finally {
			readWriteLock.readLock().unlock();
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
			haltWithError500("Unable to copy blob bytes to response", e);
		} finally {
			readWriteLock.readLock().unlock();
		}

		return null;
	}

	@Nullable
	private String putBlob(Request request, Response response) {
		try {
			readWriteLock.writeLock().lock();
			request.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/temp"));

			String modelClass = request.headers(REQUEST_HEADER_MODEL_CLASS);
			if (modelClass == null || modelClass.isEmpty()) {
				halt(400, "Missing \"" + REQUEST_HEADER_MODEL_CLASS + "\" model class header attribute");
				return null;
			}

			String accountId = request.params("accountId");
			String blobId = request.params("blobId");
			SyncManager syncManager = getSyncManagerForAccount(accountId);
			TimestampRecord timestampRecord = syncManager.getTimestampRecord();
			BlobStore blobStore = syncManager.getBlobStore();


			try (InputStream is = request.raw().getPart("blob").getInputStream()) {

				long timestamp = syncManager.getTimestampSeconds();
				timestampRecord.setTimestamp(blobId, timestamp);

				byte[] data = org.apache.commons.io.IOUtils.toByteArray(is);
				blobStore.set(blobId, modelClass, timestamp, data);

				// Use the input stream to create a file
			} catch (ServletException | IOException e) {
				haltWithError500("Unable to read blob data from request", e);
			}

			return null;
		} finally {
			readWriteLock.writeLock().unlock();
		}
	}

	@Nullable
	private String deleteBlob(Request request, Response response) {
		try {
			readWriteLock.writeLock().lock();

			String accountId = request.params("accountId");
			String blobId = request.params("blobId");

			SyncManager syncManager = getSyncManagerForAccount(accountId);
			TimestampRecord timestampRecord = syncManager.getTimestampRecord();
			BlobStore blobStore = syncManager.getBlobStore();

			timestampRecord.removeTimestamp(blobId);
			blobStore.delete(blobId);

			return null;
		} finally {
			readWriteLock.writeLock().unlock();
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
			syncManager = new SyncManager(jedisPool, accountId);
			syncManagersByAccountId.put(accountId, syncManager);
		}

		return syncManager;
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
