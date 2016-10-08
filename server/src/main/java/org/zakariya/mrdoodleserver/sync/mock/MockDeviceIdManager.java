package org.zakariya.mrdoodleserver.sync.mock;

import org.eclipse.jetty.websocket.api.Session;
import org.zakariya.mrdoodleserver.sync.DeviceIdManagerInterface;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * A mock device id manager for testing
 */
public class MockDeviceIdManager implements DeviceIdManagerInterface {

	private Set<String> deviceIds = new HashSet<>();

	public MockDeviceIdManager(Collection<String> deviceIds) {
		this.deviceIds = new HashSet<>(deviceIds);
	}

	@Override
	public boolean isValidDeviceId(String deviceId) {
		return deviceIds.contains(deviceId);
	}

	@Override
	public String getDeviceIdForWebSocketSession(Session session) {
		throw new UnsupportedOperationException("MockDeviceIdManager doesn't handle websocket connections");
	}

	@Override
	public void unregisterDeviceId(String deviceId) {
		deviceIds.remove(deviceId);
	}
}
