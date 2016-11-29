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

	public ServiceStatus() {
	}

	public ServiceStatus(int serverStatus, String serverStatusMessage, String alertMessage) {
		this.serverStatus = serverStatus;
		this.serverStatusMessage = serverStatusMessage;
		this.alertMessage = alertMessage;
	}

	/**
	 * Create a mock ServiceStatus for the default state (server is running, no alert)
	 * @return a ServiceStatus
	 */
	public static ServiceStatus createRunningStatus() {
		return new ServiceStatus(ServerStatus.RUNNING.ordinal(), null, null);
	}

	/**
	 * Create a mock ServiceStatus for the downtime state (server is in downtime, no alert)
	 * @return a ServiceStatus
	 */
	public static ServiceStatus createDowntimeServiceStatus() {
		return new ServiceStatus(ServerStatus.DOWNTIME.ordinal(), "TEST: ServerStatus.DOWNTIME message", null);
	}

	/**
	 * Create a mock ServiceStatus for the discontinued state (server is discontinued, no alert)
	 * @return a ServiceStatus
	 */
	public static ServiceStatus createDiscontinuedServiceStatus() {
		return new ServiceStatus(ServerStatus.DISCONTINUED.ordinal(), "TEST: ServerStatus.DISCONTINUED message", null);
	}

	/**
	 * Create a mock ServiceStatus for the alert state (server is running, alert message present)
	 * @return a ServiceStatus
	 */
	public static ServiceStatus createAlertServiceStatus() {
		return new ServiceStatus(ServerStatus.RUNNING.ordinal(), null, "TEST: Alert message here");
	}

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
