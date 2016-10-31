package org.zakariya.mrdoodle.net;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.gson.Gson;
import com.neovisionaries.ws.client.PayloadGenerator;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Represents a connection via websocket to a remote host.
 * WebSocketConnection wraps a websocket and will attempt via exponential backoff to reconnect
 * to the host if the connection fails.
 */
public class WebSocketConnection extends WebSocketAdapter {

	private static final long MAX_RECONNECT_DELAY_MILLIS = 1000 * 60 * 5;
	private static final int TIMEOUT = 5000;

	enum ConnectionStatus {
		NONE,
		DISCONNECTED,
		CONNECTING,
		CONNECTED,
		DISCONNECTING
	}

	private static final String TAG = "WebSocketConnection";
	private String host;
	private WebSocket webSocket;
	private ConnectionStatus connectionStatus = ConnectionStatus.NONE;
	private Exception error;
	Gson gson = new Gson();

	private int failureCount = 0;
	private Handler reconnectHandler = new Handler(Looper.getMainLooper());
	private Runnable reconnectAction = new Runnable() {
		@Override
		public void run() {
			connect();
		}
	};

	WebSocketConnection(String host) {
		Log.d(TAG, "WebSocketConnection() called with: " + "host = [" + host + "]");
		this.host = host;
		setConnectionStatus(ConnectionStatus.DISCONNECTED);
	}

	/**
	 * @return the host this WebSocketConnection connects to
	 */
	public String getHost() {
		return host;
	}

	/**
	 * Get the most recently reported error, for error reporting. The error is only assigned in an
	 * error case (e.g. bad network, bad authorization, etc). If a subsequent connection attempt
	 * succeeds, the error will still be set. Do not use the presence of the exception as indicative
	 * of an error state. This merely reports the last recorded error state. One exception: calling disconnect()
	 * will null the error state.
	 * @return an Exception describing the last connection error, or null if connection is valid.
	 */
	@Nullable
	public Exception getMostRecentError() {
		return error;
	}

	/**
	 * Attempt to connect to the host. When connection succeeds, or fails, etc, the on* methods will be called.
	 */
	public void connect() {

		if (isDisconnected()) {
			setConnectionStatus(ConnectionStatus.CONNECTING);
			if (webSocket == null) {
				try {
					webSocket = new WebSocketFactory()
							.setConnectionTimeout(TIMEOUT)
							.createSocket(host);

					webSocket.addListener(this);
					webSocket.setPingPayloadGenerator(new PayloadGenerator() {
						@Override
						public byte[] generate() {
							return new Date().toString().getBytes();
						}
					});
					webSocket.setPingInterval(60 * 1000);
					webSocket.connectAsynchronously();
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else {
				try {
					webSocket = webSocket.recreate().connectAsynchronously();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Disconnect from host, and null the error state.
	 */
	public void disconnect() {

		error = null;
		clearScheduledReconnect();

		if (isConnected() || isConnecting()) {
			setConnectionStatus(ConnectionStatus.DISCONNECTING);
			if (webSocket != null) {
				webSocket.disconnect();
			}
		}
	}

	private void setConnectionStatus(ConnectionStatus connectionStatus) {
		if (connectionStatus != this.connectionStatus) {
			ConnectionStatus previousStatus = this.connectionStatus;
			this.connectionStatus = connectionStatus;
			onConnectionStatusChanged(previousStatus, this.connectionStatus);
		}
	}

	/**
	 * Called when connection status to host changes
	 *
	 * @param previousStatus the previous connection status
	 * @param newStatus      the new connection status
	 */
	protected void onConnectionStatusChanged(ConnectionStatus previousStatus, ConnectionStatus newStatus) {
	}


	public WebSocket getWebSocket() {
		return webSocket;
	}

	public boolean isConnected() {
		return connectionStatus == ConnectionStatus.CONNECTED;
	}

	public boolean isConnecting() {
		return connectionStatus == ConnectionStatus.CONNECTING;
	}

	public boolean isDisconnecting() {
		return connectionStatus == ConnectionStatus.DISCONNECTING;
	}

	public boolean isDisconnected() {
		return connectionStatus == ConnectionStatus.DISCONNECTED;
	}

	/**
	 * Send a simple POJO to the remote host
	 *
	 * @param object simple POJO to serialize to JSON and send over the wire
	 */
	public void send(Object object) {
		if (isConnected()) {
			String text = gson.toJson(object);
			getWebSocket().sendText(text);
		}
	}

	@Override
	public void onConnectError(WebSocket websocket, WebSocketException exception) throws Exception {
		super.onConnectError(websocket, exception);
		error = exception;

		Log.e(TAG, "onConnectError() called with: " + "websocket = [" + websocket + "], exception = [" + exception + "]");

		// if we haven't canceled connect, keep trying
		if (isConnecting()) {
			setConnectionStatus(ConnectionStatus.DISCONNECTED);
			reconnect();
		}
	}

	@Override
	public void onConnected(WebSocket websocket, Map<String, List<String>> headers) throws Exception {
		super.onConnected(websocket, headers);

		Log.d(TAG, "onConnected() called with: " + "websocket = [" + websocket + "], headers = [" + headers + "]");
		resetExponentialBackoff();
		setConnectionStatus(ConnectionStatus.CONNECTED);
	}

	@Override
	public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) throws Exception {
		Log.d(TAG, "onDisconnectedFromSyncServer() called with: " + "websocket = [" + websocket + "], serverCloseFrame = [" + serverCloseFrame + "], clientCloseFrame = [" + clientCloseFrame + "], closedByServer = [" + closedByServer + "]");

		boolean wasIntentionallyDisconnected = isDisconnecting();
		setConnectionStatus(ConnectionStatus.DISCONNECTED);

		// if we didn't explicitly disconnect, that means an error occurred
		// which disconnected us and we should trigger a reconnect attempt
		if (!wasIntentionallyDisconnected) {
			reconnect();
		}
	}

	@Override
	public void onTextMessage(WebSocket websocket, String text) throws Exception {
		super.onTextMessage(websocket, text);
		Log.d(TAG, "onTextMessage() called with: " + "websocket = [" + websocket + "], text = [" + text + "]");
	}

	@Override
	public void onError(WebSocket websocket, WebSocketException cause) throws Exception {
		Log.e(TAG, "onError() called with: " + "websocket = [" + websocket + "], cause = [" + cause + "]");
	}

	@Override
	public void onUnexpectedError(WebSocket websocket, WebSocketException cause) throws Exception {
		Log.e(TAG, "onUnexpectedError() called with: " + "websocket = [" + websocket + "], cause = [" + cause + "]");
	}

	/**
	 * Reset the exponential backoff for connection retries
	 */
	public void resetExponentialBackoff() {
		failureCount = 0;
	}

	/**
	 * Terminates any scheduled reconnects (which are generally a response to a lost connection,
	 * failed auth, etc.
	 */
	void clearScheduledReconnect() {
		reconnectHandler.removeCallbacks(reconnectAction);
	}

	void reconnect() {
		clearScheduledReconnect();

		failureCount++;
		long delayMillis = Math.min((long) (Math.pow(1.4, failureCount) * 1000), MAX_RECONNECT_DELAY_MILLIS);
		Log.d(TAG, "reconnect: scheduling reconnect in " + (delayMillis / 1000.0) + " seconds...");

		reconnectHandler.postDelayed(reconnectAction, delayMillis);
	}

}
