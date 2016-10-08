package org.zakariya.mrdoodleserver.sync.mock;

import org.eclipse.jetty.websocket.api.Session;
import org.zakariya.mrdoodleserver.sync.DeviceIdManagerInterface;

/**
 * A mock device id manager for testing
 */
public class MockDeviceIdManager implements DeviceIdManagerInterface {

	private String deviceId;

	public MockDeviceIdManager(String deviceId) {
		this.deviceId = deviceId;
	}

	@Override
	public boolean isValidDeviceId(String deviceId) {
		return this.deviceId != null && this.deviceId.equals(deviceId);
	}

	@Override
	public String getDeviceIdForWebSocketSession(Session session) {
		throw new UnsupportedOperationException("MockDeviceIdManager doesn't handle websocket connections");
	}

	@Override
	public void unregisterDeviceId(String deviceId) {
		this.deviceId = null;
	}
}
