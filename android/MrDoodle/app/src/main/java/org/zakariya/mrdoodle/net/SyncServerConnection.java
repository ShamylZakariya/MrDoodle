package org.zakariya.mrdoodle.net;

import android.support.annotation.Nullable;
import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.neovisionaries.ws.client.WebSocket;

import org.zakariya.mrdoodle.events.SyncServerConnectionStatusEvent;
import org.zakariya.mrdoodle.net.transport.Status;
import org.zakariya.mrdoodle.signin.AuthenticationTokenReceiver;
import org.zakariya.mrdoodle.signin.SignInManager;
import org.zakariya.mrdoodle.signin.model.SignInAccount;
import org.zakariya.mrdoodle.util.BusProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SyncServerConnection
 * Maintains (or attempts to maintain) a persistent connection to the MrDoodle Sync Server.
 * Automatically connects and disconnects when app is active and user has signed in via their google id.
 */
public class SyncServerConnection extends WebSocketConnection {

	public interface NotificationListener {
		void onStatusReceived(Status status);
	}

	private static final String TAG = SyncServerConnection.class.getSimpleName();
	private boolean authenticating;
	private boolean authenticated;
	private List<NotificationListener> notificationListeners = new ArrayList<>();

	public SyncServerConnection(String host) {
		super(host);
	}

	public boolean isAuthenticated() {
		return authenticated;
	}

	public boolean isAuthenticating() {
		return authenticating;
	}

	/**
	 * Add a listener to be notified as messages arrive from the web socket connection
	 * @param listener listener to be notified as messages arrive from the web socket connection
	 */
	public void addNotificationListener(NotificationListener listener) {
		notificationListeners.add(listener);
	}

	/**
	 * @param listener listener to remove from notification list
	 */
	public void removeNotificationListener(NotificationListener listener) {
		notificationListeners.remove(listener);
	}

	protected void setAuthenticated(boolean authenticated) {
		this.authenticating = false;
		this.authenticated = authenticated;
		if (authenticated) {
			BusProvider.postOnMainThread(new SyncServerConnectionStatusEvent(SyncServerConnectionStatusEvent.Status.CONNECTED));
		} else {
			Log.d(TAG, "setAuthenticated: NOT AUTHENTICATED, scheduling reconnect");
			resetExponentialBackoff();
			reconnect();
		}
	}

	@Override
	public void onConnected(final WebSocket websocket, Map<String, List<String>> headers) throws Exception {
		super.onConnected(websocket, headers);

		// we're connected, we need to authorize before the server will consider us connected
		Log.d(TAG, "onConnected: connected to server, authorizing...");

		SignInAccount account = SignInManager.getInstance().getAccount();
		if (account != null) {
			account.getAuthenticationToken(new AuthenticationTokenReceiver() {
				@Override
				public void onAuthenticationTokenAvailable(String authenticationToken) {
					Log.d(TAG, "onIdTokenAvailable: authorizing...");

					authenticating = true;
					BusProvider.postOnMainThread(new SyncServerConnectionStatusEvent(SyncServerConnectionStatusEvent.Status.AUTHORIZING));
					send(new AuthorizationPayload(authenticationToken));
				}

				@Override
				public void onAuthenticationTokenError(@Nullable String errorMessage) {
					// TODO: Handle error on request of idToken
					Log.e(TAG, "onIdTokenError: Unable to get authentication token, error: " + errorMessage);
				}
			});
		}
	}

	@Override
	public void onTextMessage(WebSocket websocket, String text) throws Exception {
		super.onTextMessage(websocket, text);

		// when we've first connected, we send an authentication request, and
		// the first thing we'll receive from the server is a message as to whether
		// we've authenticated or not.

		if (!isAuthenticated()) {
			try {
				JsonParser parser = new JsonParser();
				JsonObject message = parser.parse(text).getAsJsonObject();
				JsonElement authElement = message.get("authorized");
				if (authElement != null) {
					boolean authorized = authElement.getAsBoolean();
					Log.d(TAG, "onTextMessage: authorized: " + authorized);
					setAuthenticated(authorized);
				} else {
					setAuthenticated(false);
				}
			} catch (JsonSyntaxException e) {
				Log.e(TAG, "onTextMessage: unable to parse " + text + " as JSON", e);
				e.printStackTrace();
				setAuthenticated(false);
			}
		}

		if (isAuthenticated()) {

			// right now, the only message ever sent by the server to the client over websocket
			// is the net.transport.Status message
			try {
				Status status = gson.fromJson(text, Status.class);
				if (status != null) {
					for (NotificationListener listener : notificationListeners) {
						listener.onStatusReceived(status);
					}
				}
			} catch (JsonSyntaxException e) {
				Log.e(TAG, "onTextMessage: unable to parse " + text + " as JSON", e );
				e.printStackTrace();
			}
		}
	}

	@Override
	protected void onConnectionStatusChanged(WebSocketConnection.ConnectionStatus previousStatus, WebSocketConnection.ConnectionStatus newStatus) {

		switch( newStatus) {
			case CONNECTING:
				BusProvider.postOnMainThread(new SyncServerConnectionStatusEvent(SyncServerConnectionStatusEvent.Status.CONNECTING));
				break;

			case DISCONNECTED:
				authenticating = false;
				authenticated = false;
				BusProvider.postOnMainThread(new SyncServerConnectionStatusEvent(SyncServerConnectionStatusEvent.Status.DISCONNECTED));
				break;

			default: break;
		}
	}

	static final class AuthorizationPayload {
		public String auth;

		public AuthorizationPayload(String auth) {
			this.auth = auth;
		}
	}

}
