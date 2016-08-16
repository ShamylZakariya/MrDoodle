package org.zakariya.mrdoodle.net;

import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.neovisionaries.ws.client.WebSocket;

import org.zakariya.mrdoodle.events.SyncServerConnectionStatusEvent;
import org.zakariya.mrdoodle.util.BusProvider;
import org.zakariya.mrdoodle.util.GoogleSignInManager;

import java.util.List;
import java.util.Map;

/**
 * SyncServerConnection
 * Maintains (or attempts to maintain) a persistent connection to the MrDoodle Sync Server.
 * Automatically connects and disconnects when app is active and user has signed in via their google id.
 */
public class SyncServerConnection extends WebSocketConnection {

	private static final String TAG = SyncServerConnection.class.getSimpleName();
	private boolean authenticating;
	private boolean authenticated;

	public SyncServerConnection(String host) {
		super(host);
	}

	public boolean isAuthenticated() {
		return authenticated;
	}

	public boolean isAuthenticating() {
		return authenticating;
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
		GoogleSignInManager.getInstance().requestIdToken(new GoogleSignInManager.GoogleIdTokenReceiver() {
			@Override
			public void onIdTokenAvailable(String idToken) {
				Log.d(TAG, "onIdTokenAvailable: authorizing...");

				authenticating = true;
				BusProvider.postOnMainThread(new SyncServerConnectionStatusEvent(SyncServerConnectionStatusEvent.Status.AUTHORIZING));
				send(new AuthorizationPayload(idToken));
			}

			@Override
			public void onIdTokenError() {
				// TODO: Handle error on request of idToken
				Log.e(TAG, "onIdTokenError: Unable to get Google idToken");
			}
		});
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
				setAuthenticated(false);
			}
		}

		if (isAuthenticated()) {
			// TODO: Process messages sent to authenticated clients
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
