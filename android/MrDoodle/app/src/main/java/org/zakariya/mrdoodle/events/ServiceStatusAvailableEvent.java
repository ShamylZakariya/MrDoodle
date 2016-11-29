package org.zakariya.mrdoodle.events;

import org.zakariya.mrdoodle.net.transport.ServiceStatus;

/**
 * Event fired when MrDoodleApplication loads the service status from net (or cache if offline)
 */

public class ServiceStatusAvailableEvent {
	private ServiceStatus serviceStatus;

	public ServiceStatusAvailableEvent(ServiceStatus serviceStatus) {
		this.serviceStatus = serviceStatus;
	}

	public ServiceStatus getServiceStatus() {
		return serviceStatus;
	}
}
