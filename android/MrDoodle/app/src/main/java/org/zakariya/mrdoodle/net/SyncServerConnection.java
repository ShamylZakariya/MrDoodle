package org.zakariya.mrdoodle.net;

import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.neovisionaries.ws.client.WebSocket;
import com.squareup.otto.Subscribe;

import org.zakariya.mrdoodle.events.ApplicationDidBackgroundEvent;
import org.zakariya.mrdoodle.events.ApplicationDidResumeEvent;
import org.zakariya.mrdoodle.events.GoogleSignInEvent;
import org.zakariya.mrdoodle.events.GoogleSignOutEvent;
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
	private static SyncServerConnection instance;
	private boolean authenticating;
	private boolean authenticated;
	private boolean applicationIsActive;
	private boolean userIsSignedIn;


	public static void init(SyncServerConfiguration configuration, boolean userIsSignedIn) {
		instance = new SyncServerConnection(configuration.getSyncServerConnectionUrl(), userIsSignedIn);
	}

	public static SyncServerConnection getInstance() {
		return instance;
	}

	private SyncServerConnection(String host, boolean userIsSignedIn) {
		super(host);
		BusProvider.getBus().register(this);

		applicationIsActive = true;
		this.userIsSignedIn = userIsSignedIn;
		updateConnection();
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
			BusProvider.postOnMainThread(BusProvider.getBus(), new SyncServerConnectionStatusEvent(SyncServerConnectionStatusEvent.Status.CONNECTED));
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
				BusProvider.postOnMainThread(BusProvider.getBus(),new SyncServerConnectionStatusEvent(SyncServerConnectionStatusEvent.Status.AUTHORIZING));
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

	protected void updateConnection() {
		if (applicationIsActive && userIsSignedIn) {
			Log.d(TAG, "updateConnection: active && signedIn! CONNECTING...");
			connect();
		} else {
			Log.d(TAG, "updateConnection: applicationIsActive: " + applicationIsActive + " userIsSignedIn: " + userIsSignedIn + " - DISCONNECTING");
			disconnect();
		}
	}

	@Subscribe
	public void onApplicationResumed(ApplicationDidResumeEvent event) {
		Log.d(TAG, "onApplicationResumed() called with: " + "event = [" + event + "]");
		applicationIsActive = true;
		resetExponentialBackoff();
		updateConnection();
	}

	@Subscribe
	public void onApplicationBackgrounded(ApplicationDidBackgroundEvent event) {
		Log.d(TAG, "onApplicationBackgrounded() called with: " + "event = [" + event + "]");
		applicationIsActive = false;
		updateConnection();
	}

	@Subscribe
	public void onSignedIn(GoogleSignInEvent event) {
		Log.d(TAG, "onSignedIn() called with: " + "event = [" + event + "]");
		userIsSignedIn = true;
		resetExponentialBackoff();
		updateConnection();
	}

	@Subscribe
	public void onSignedOut(GoogleSignOutEvent event) {
		Log.d(TAG, "onSignedOut() called with: " + "event = [" + event + "]");
		userIsSignedIn = false;
		updateConnection();
	}

	@Override
	protected void onConnectionStatusChanged(WebSocketConnection.ConnectionStatus previousStatus, WebSocketConnection.ConnectionStatus newStatus) {

		switch( newStatus) {
			case CONNECTING:
				BusProvider.postOnMainThread(BusProvider.getBus(), new SyncServerConnectionStatusEvent(SyncServerConnectionStatusEvent.Status.CONNECTING));
				break;

			case DISCONNECTED:
				authenticating = false;
				authenticated = false;
				BusProvider.postOnMainThread(BusProvider.getBus(), new SyncServerConnectionStatusEvent(SyncServerConnectionStatusEvent.Status.DISCONNECTED));
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
