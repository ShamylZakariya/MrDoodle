package org.zakariya.mrdoodleserver.services;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.jetbrains.annotations.Nullable;
import org.zakariya.mrdoodleserver.auth.Authenticator;
import org.zakariya.mrdoodleserver.util.Configuration;

import java.io.IOException;
import java.util.*;

import static org.zakariya.mrdoodleserver.util.Preconditions.checkNotNull;

/**
 * WebSocketConnection
 * This sync server uses websockets solely as a signalling mechanism. The only message clients will send
 * to the server is an authentication, which is a JSON object that looks like so:
 * {
 * "auth":"Some Google ID Token"
 * }
 * <p>
 * The websocket connection will respond with:
 * {
 * "authorized":true | false
 * }
 * <p>
 * Subsequently, clients should just listen for messages from the websocket connection. The messages will
 * describe when the server's "truth" store for a google id has changed and clients should initiate a sync,
 * as well as messages describing changes to the lock status of documents. Perhaps more.
 */

@WebSocket
public class WebSocketConnection {

	public interface WebSocketConnectionCreatedListener {
		void onWebSocketConnectionCreated(WebSocketConnection connection);
	}

	public interface OnUserSessionStatusChangeListener {
		/**
		 * Invoked when a user/device is connected and authenticated
		 *
		 * @param connection the websocket connection
		 * @param session  the user/device websocket session
		 * @param googleId the google id of the connected user/device
		 */
		void onUserSessionConnected(WebSocketConnection connection, Session session, String googleId);

		/**
		 * Invoked when a user/device disconnects
		 *
		 * @param connection the websocket connection
		 * @param session  the user/device websocket session
		 * @param googleId the google id of the connected user/device
		 */
		void onUserSessionDisconnected(WebSocketConnection connection, Session session, String googleId);
	}

	private static class UserGroup {
		String googleId;
		Set<Session> userSessions = new HashSet<>();

		UserGroup(String googleId) {
			this.googleId = googleId;
		}
	}

	@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
	private static class AuthenticationResponse {
		boolean authorized;

		public AuthenticationResponse() {
		}

		AuthenticationResponse(boolean authorized) {
			this.authorized = authorized;
		}

		boolean isAuthorized() {
			return authorized;
		}
	}

	public static String getRoute(Configuration configuration) {
		return "/api/" + configuration.get("version") + "/connect";
	}

	/**
	 * The Authenticator must be assigned before the WebSocketConnection is used.
	 * Since Spark's websocket functionality doesn't allow passing a created object,
	 * we must treat this as a static and assign it before hooking up websocket handling.
	 * This is ugly, but the right way is not possible, and other ways are equally ugly.
	 */
	public static Authenticator authenticator;

	private static WebSocketConnection instance;
	private static List<WebSocketConnectionCreatedListener> webSocketConnectionCreatedListeners = new ArrayList<>();

	private ObjectMapper objectMapper = new ObjectMapper();
	private Map<String, UserGroup> authenticatedUserGroupsByGoogleId = new HashMap<>();
	private Map<Session, String> googleIdByUserSession = new HashMap<>();
	private List<OnUserSessionStatusChangeListener> userSessionStatusChangeListeners = new ArrayList<>();

	public WebSocketConnection() {
		checkNotNull(authenticator, "Authenticator must be assigned");
		instance = this;
		for (WebSocketConnectionCreatedListener listener : webSocketConnectionCreatedListeners) {
			listener.onWebSocketConnectionCreated(this);
		}

		webSocketConnectionCreatedListeners.clear();
	}

	public static WebSocketConnection getInstance() {
		return instance;
	}

	public static void addOnWebSocketConnectionCreatedListener(WebSocketConnectionCreatedListener listener) {
		// if we've already been created, just immediately notify
		if (instance != null) {
			listener.onWebSocketConnectionCreated(instance);
		} else {
			webSocketConnectionCreatedListeners.add(listener);
		}
	}

	public static void removeOnWebSocketConnectionCreatedListener(WebSocketConnectionCreatedListener listener) {
		webSocketConnectionCreatedListeners.remove(listener);
	}

	public void addUserSessionStatusChangeListener(OnUserSessionStatusChangeListener listener) {
		userSessionStatusChangeListeners.add(listener);
	}

	public void removeUserSessionStatusChangeListener(OnUserSessionStatusChangeListener listener) {
		userSessionStatusChangeListeners.remove(listener);
	}


	/**
	 * @return set containing google id of all connected users
	 */
	public Set<String> getConnectedUserGoogleIds() {
		return authenticatedUserGroupsByGoogleId.keySet();
	}

	/**
	 * @return return count of connected devices. Since users may use multiple devices, this may be larger than number of users.
	 */
	public int getTotalConnectedDeviceCount() {
		int count = 0;
		for (String googleId : authenticatedUserGroupsByGoogleId.keySet()) {
			count += authenticatedUserGroupsByGoogleId.get(googleId).userSessions.size();
		}

		return count;
	}

	/**
	 * @param googleId google id of user in question
	 * @return the number of devices this user has connected right now to sync service
	 */
	public int getTotalConnectedDevicesForGoogleId(String googleId) {
		UserGroup group = authenticatedUserGroupsByGoogleId.get(googleId);
		if (group != null) {
			return group.userSessions.size();
		}

		return 0;
	}

	@OnWebSocketConnect
	public void onConnect(Session userSession) throws Exception {
	}

	@OnWebSocketClose
	public void onClose(Session userSession, int statusCode, String reason) {

		// clean up
		String googleId = googleIdByUserSession.get(userSession);
		if (googleId != null) {
			googleIdByUserSession.remove(userSession);
			UserGroup userGroup = authenticatedUserGroupsByGoogleId.get(googleId);
			if (userGroup != null) {
				userGroup.userSessions.remove(userSession);
			}

			for (OnUserSessionStatusChangeListener listener : userSessionStatusChangeListeners) {
				listener.onUserSessionDisconnected(this, userSession, googleId);
			}
		}

		System.out.println("onClose status: " + statusCode + " reason: " + reason + " after cleanup, we have: " + getTotalConnectedDeviceCount() + " connected devices");
	}

	@OnWebSocketMessage
	public void onMessage(Session userSession, String message) {
		try {
			JsonNode rootNode = objectMapper.readTree(message);

			JsonNode authTokenNode = rootNode.get("auth");
			if (authTokenNode == null) {
				sendAuthenticationResponse(userSession, false);
				return;
			}

			String authToken = authTokenNode.asText();

			if (authToken == null || authToken.isEmpty()) {
				sendAuthenticationResponse(userSession, false);
				return;
			}

			boolean didAuthenticate = false;
			if (!isSessionAuthenticated(userSession)) {
				String googleId = authenticate(userSession, authToken);
				sendAuthenticationResponse(userSession, googleId  != null);

				if (googleId != null) {
					didAuthenticate = true;

					// notify
					for (OnUserSessionStatusChangeListener listener : userSessionStatusChangeListeners) {
						listener.onUserSessionConnected(this, userSession, googleId);
					}
				}
				
				System.out.println("WebSocketConnection::onMessage - after handling authentication, we have: " + getTotalConnectedDeviceCount() + " connected devices");
			}

			// user may have successfully authenticated, so we can proceed

			if (didAuthenticate || isSessionAuthenticated(userSession)) {

				// if we didn't authenticate a new session just now
				// we need to confirm that user's auth is still valid
				if (!didAuthenticate) {
					String googleId = authenticator.verify(authToken);
					if (googleId == null) {

						// the authorization must have expired
						deauthenticate(userSession);
						sendAuthenticationResponse(userSession, false);

						// we're done here
						return;
					}
				}

				// if we're here, we can process any commands the user sends

			}
		} catch (IOException e) {
			System.err.println("Unable to parse message as JSON");
			e.printStackTrace();
		}
	}

	@Nullable
	private String authenticate(Session userSession, String authToken) {
		if (authToken != null && !authToken.isEmpty()) {

			String googleId = authenticator.verify(authToken);
			if (googleId != null && !googleId.isEmpty()) {

				// and move this session to our authenticated region
				googleIdByUserSession.put(userSession, googleId);

				UserGroup userGroup = authenticatedUserGroupsByGoogleId.get(googleId);
				if (userGroup == null) {
					userGroup = new UserGroup(googleId);
					authenticatedUserGroupsByGoogleId.put(googleId, userGroup);
				}

				userGroup.userSessions.add(userSession);
				return googleId;
			} else {

				// couldn't extract an ID from the token
				System.err.println("Unable to extract google ID from auth token: " + authToken);
				return null;
			}

		} else {
			deauthenticate(userSession);
			return null;
		}
	}

	/**
	 * Mark that a session's authentication has failed
	 *
	 * @param userSession the user session
	 */
	private void deauthenticate(Session userSession) {
		// move this session from authenticated region to unauthenticated
		String googleId = googleIdByUserSession.get(userSession);
		if (googleId != null) {
			UserGroup userGroup = authenticatedUserGroupsByGoogleId.get(googleId);
			if (userGroup != null) {
				userGroup.userSessions.remove(userSession);
			}

			for (OnUserSessionStatusChangeListener listener : userSessionStatusChangeListeners) {
				listener.onUserSessionDisconnected(this, userSession, googleId);
			}
		}

		googleIdByUserSession.remove(userSession);
	}

	/**
	 * Send an AuthenticationResponse JSON message to a user
	 *
	 * @param userSession   the user session
	 * @param authenticated authentication status
	 */
	private void sendAuthenticationResponse(Session userSession, boolean authenticated) {
		send(userSession, new AuthenticationResponse(authenticated));
	}

	/**
	 * Check if a given Session is authenticated
	 *
	 * @param userSession a websocket session
	 * @return true if the session has been authenticated
	 */
	private boolean isSessionAuthenticated(Session userSession) {
		return googleIdByUserSession.containsKey(userSession);
	}

	/**
	 * Send the message object POJO as JSON to the specific user/device represented by userSession
	 *
	 * @param userSession   a specific user/device connected to this server
	 * @param messageObject a POJO to serialize and send as JSON
	 */
	public void send(Session userSession, Object messageObject) {
		if (!userSession.isOpen()) {
			return;
		}

		try {
			String json = objectMapper.writeValueAsString(messageObject);
			userSession.getRemote().sendString(json);
		} catch (JsonProcessingException e) {
			System.err.println("Unable to serialize message POJO to JSON");
			e.printStackTrace();
		} catch (IOException e) {
			System.err.println("Unable to send message JSON to user session remote endpoint");
			e.printStackTrace();
		}
	}

	/**
	 * Broadcast the message object as JSON to every user connected to this service authenticated by a given google id
	 *
	 * @param googleId      the google id representing a number of connected users using same sign-in
	 * @param messageObject and arbitrary POJO to send
	 */
	public void broadcast(String googleId, Object messageObject) {
		UserGroup group = authenticatedUserGroupsByGoogleId.get(googleId);
		if (group != null) {
			try {
				String messageJsonString = objectMapper.writeValueAsString(messageObject);
				group.userSessions.stream().filter(Session::isOpen).forEach(session -> {
					try {
						session.getRemote().sendString(messageJsonString);
					} catch (IOException e) {
						System.err.println("Unable to send message JSON to session: " + session);
						e.printStackTrace();
					}
				});
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}
		}
	}


}
