package org.zakariya.mrdoodle.net.events;

import android.support.annotation.Nullable;

import org.zakariya.mrdoodle.net.SyncServerConnection;

/**
 * SyncServerConnectionStatusEvent
 * Event fired as connection status with server changes
 */
public class SyncServerConnectionStatusEvent {

	public enum Status {
		DISCONNECTED,
		CONNECTING,
		AUTHORIZING,
		CONNECTED
	}

	private Status status;
	private Exception error;

	public SyncServerConnectionStatusEvent(Status status, @Nullable Exception error) {
		this.status = status;
		this.error = error;
	}

	public SyncServerConnectionStatusEvent(SyncServerConnection connection) {
		status = SyncServerConnectionStatusEvent.Status.DISCONNECTED;
		error = connection.getMostRecentError();

		if (connection.isConnecting()) {
			status = SyncServerConnectionStatusEvent.Status.CONNECTING;
		} else if (connection.isConnected()) {
			if (connection.isAuthenticating()) {
				status = SyncServerConnectionStatusEvent.Status.AUTHORIZING;
			} else if (connection.isAuthenticated()) {
				status = SyncServerConnectionStatusEvent.Status.CONNECTED;
			}
		}
	}

	public Status getStatus() {
		return status;
	}

	public boolean isDisconnected() {
		return status == Status.DISCONNECTED;
	}

	public boolean isConnecting() {
		return status == Status.CONNECTING;
	}

	public boolean isAuthorizing() {
		return status == Status.AUTHORIZING;
	}

	public boolean isConnected() {
		return status == Status.CONNECTED;
	}

	public Exception getError() {
		return error;
	}

	@Override
	public String toString() {
		return "[SyncServerConnectionStatusEvent status: " + status + ", error: " + error + "]";
	}
}
