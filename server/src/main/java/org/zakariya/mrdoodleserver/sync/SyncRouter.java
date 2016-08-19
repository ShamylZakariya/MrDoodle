package org.zakariya.mrdoodleserver.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.websocket.api.Session;
import org.zakariya.mrdoodleserver.auth.Authenticator;
import org.zakariya.mrdoodleserver.services.WebSocketConnection;
import org.zakariya.mrdoodleserver.sync.transport.Status;
import org.zakariya.mrdoodleserver.util.Configuration;
import org.zakariya.mrdoodleserver.util.Preconditions;
import redis.clients.jedis.JedisPool;
import spark.Request;
import spark.Response;

import java.util.HashMap;
import java.util.Map;

import static spark.Spark.*;

/**
 * SyncRouter
 * Establishes the REST routes used for sync operations.
 */
public class SyncRouter implements WebSocketConnection.WebSocketConnectionCreatedListener{

	private static final String REQUEST_HEADER_AUTH = "X-Authorization";

	private Configuration configuration;
	private Authenticator authenticator;
	private JedisPool jedisPool;
	private Map<String, SyncManager> syncManagersByAccountId = new HashMap<>();
	private ObjectMapper objectMapper = new ObjectMapper();
	private boolean authenticationEnabled;

	public SyncRouter(Configuration configuration, Authenticator authenticator, JedisPool jedisPool) {
		this.configuration = configuration;
		this.authenticator = authenticator;
		this.jedisPool = jedisPool;
		this.authenticationEnabled = configuration.getBoolean("authenticator/enabled", true);
	}

	public void configureRoutes(){
		before(getBasePath() + "/*", this::authenticate);
		get(getBasePath() + "/status", this::getStatus);
	}

	///////////////////////////////////////////////////////////////////

	private void authenticate(Request request, Response response) {
		if (authenticationEnabled) {
			String googleIdToken = request.headers(REQUEST_HEADER_AUTH);
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
		}
	}

	@org.jetbrains.annotations.Nullable
	private String getStatus(Request request, Response response) {
		String accountId = request.params("accountId");
		SyncManager syncManager = getSyncManagerForAccount(accountId);
		Status status = syncManager.getStatus();

		try {
			return objectMapper.writeValueAsString(status);
		} catch (JsonProcessingException e) {
			String errorMessage = "Unable to encode SyncManager.Status to JSON string, e: " + e.getLocalizedMessage();
			System.err.println(errorMessage);
			e.printStackTrace();

			halt(500, errorMessage);
		}

		// if we're here we already halted the response
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

		return syncManager;
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
