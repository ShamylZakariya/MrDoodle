package org.zakariya.mrdoodle.net;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;

import java.io.IOException;
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

	protected enum ConnectionStatus {
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
	protected Gson gson = new Gson();

	private int failureCount = 0;
	private Handler reconnectHandler = new Handler(Looper.getMainLooper());
	private Runnable reconnectAction = new Runnable() {
		@Override
		public void run() {
			connect();
		}
	};

	public WebSocketConnection(String host) {
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
	 * Disconnect from host
	 */
	public void disconnect() {
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
	 * @param previousStatus the previous connection status
	 * @param newStatus the new connection status
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
		Log.d(TAG, "onDisconnected() called with: " + "websocket = [" + websocket + "], serverCloseFrame = [" + serverCloseFrame + "], clientCloseFrame = [" + clientCloseFrame + "], closedByServer = [" + closedByServer + "]");

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

	protected void resetExponentialBackoff() {
		failureCount = 0;
	}

	protected void reconnect() {
		// clean up any lingering reconnect
		reconnectHandler.removeCallbacks(reconnectAction);

		failureCount++;
		long delayMillis = Math.min((long) (Math.pow(1.4, failureCount) * 1000), MAX_RECONNECT_DELAY_MILLIS);
		Log.d(TAG, "reconnect: scheduling reconnect in " + (delayMillis / 1000.0) + " seconds...");

		reconnectHandler.postDelayed(reconnectAction, delayMillis);
	}

}
