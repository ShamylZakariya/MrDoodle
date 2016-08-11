package org.zakariya.mrdoodle.events;

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

	public Status getStatus() {
		return status;
	}

	public boolean isDisconnected(){
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
}
