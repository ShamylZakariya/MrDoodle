package org.zakariya.mrdoodleserver.sync;

import org.eclipse.jetty.websocket.api.Session;

public interface DeviceIdManagerInterface {
	boolean isValidDeviceId(String deviceId);
	String getDeviceIdForWebSocketSession(Session session);
	void unregisterDeviceId(String deviceId);
}
