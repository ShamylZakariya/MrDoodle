package org.zakariya.mrdoodle.net.transport;

import android.text.TextUtils;

/**
 * Created by shamyl on 11/19/16.
 */

@SuppressWarnings("WeakerAccess")
public class ServiceStatus {

	public enum ServerStatus {
		RUNNING,
		DOWNTIME,
		DISCONTINUED
	}

	public int serverStatus;
	public String serverStatusMessage = null;
	public String alertMessage = null;

	@Override
	public String toString() {
		return "[ServiceStatus serverStatus: " + ServerStatus.values()[serverStatus]
				+ " serverStatusMessage: \"" + serverStatusMessage + "\""
				+ " alertMessage: \"" + alertMessage + "\""
				+ "]";
	}

	public boolean serviceShouldBeRunning() {
		return serverStatus == ServerStatus.RUNNING.ordinal();
	}

	public boolean serviceIsInScheduledDowntime() {
		return serverStatus == ServerStatus.DOWNTIME.ordinal();
	}

	public boolean serviceIsDiscontinued() {
		return serverStatus == ServerStatus.DISCONTINUED.ordinal();
	}

	public boolean hasAlertMessage() {
		return !TextUtils.isEmpty(alertMessage);
	}

	public boolean hasServerStatusMessage() {
		return !TextUtils.isEmpty(serverStatusMessage);
	}

}
