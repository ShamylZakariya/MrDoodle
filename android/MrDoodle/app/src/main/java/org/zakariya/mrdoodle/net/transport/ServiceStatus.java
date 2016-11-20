package org.zakariya.mrdoodle.net.transport;

/**
 * Created by shamyl on 11/19/16.
 */

@SuppressWarnings("WeakerAccess")
public class ServiceStatus {
	public boolean isDiscontinued;
	public String discontinuedMessage;
	public boolean isAlert;
	public String alertMessage;

	@Override
	public String toString() {
		return "[ServiceStatus isDiscontinued: " + isDiscontinued
				+ " discontinuedMessage: \"" + discontinuedMessage + "\""
				+ " isAlert: " + isAlert
				+ " alertMessage: \"" + alertMessage + "\""
				+ "]";
	}
}
