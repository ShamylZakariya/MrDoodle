package org.zakariya.mrdoodle.net.events;

import org.zakariya.mrdoodle.net.SyncServerConnection;

/**
 * Created by shamyl on 8/10/16.
 */
public class SyncServerConnectionStatusEvent {

	public enum Status {
		DISCONNECTED,
		CONNECTING,
		AUTHORIZING,
		CONNECTED
	}

	Status status;

	public SyncServerConnectionStatusEvent(Status status) {
		this.status = status;
	}

	public SyncServerConnectionStatusEvent(SyncServerConnection connection) {
		status = SyncServerConnectionStatusEvent.Status.DISCONNECTED;

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

	@Override
	public String toString() {
		return "[SyncServerConnectionStatusEvent status: " + status + "]";
	}
}
