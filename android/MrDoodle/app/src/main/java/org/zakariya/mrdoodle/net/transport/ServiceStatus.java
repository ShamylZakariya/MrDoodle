package org.zakariya.mrdoodle.net.transport;

/**
 * Created by shamyl on 11/19/16.
 */

@SuppressWarnings("WeakerAccess")
public class ServiceStatus {
	public boolean isDiscontinued;
	public String discontinuedMessage;

	@Override
	public String toString() {
		return "[ServiceStatus isDiscontinued: " + isDiscontinued + " discontinuedMessage: \"" + discontinuedMessage + "\"]";
	}
}
