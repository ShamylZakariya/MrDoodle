package org.zakariya.mrdoodleserver.sync;

import org.eclipse.jetty.websocket.api.Session;

import java.util.*;

/**
 * DeviceIdManager
 * creates unique random device ids to be used by SyncManager
 */
class DeviceIdManager implements DeviceIdManagerInterface {

	private Map<Session,String> deviceIdsByWebsocketSession = new HashMap<>();
	private Map<String,Session> websocketSessionsByDeviceId = new HashMap<>();


	/**
	 * Check if a given device id is valid - e.g., it was vended by getDeviceIdForWebSocketSession and hasn't been unregistered
	 * @param deviceId a device id
	 * @return true if the id was vended by getDeviceIdForWebSocketSession and hasn't been unregistered
	 */
	public synchronized boolean isValidDeviceId(String deviceId) {
		return deviceId != null && websocketSessionsByDeviceId.containsKey(deviceId);
	}

	/**
	 * Given a websocket session, get the associated random device id, or generate a fresh one
	 * @param session a websocket session representing a connected device
	 * @return a unique, random device id associated with this session
	 */
	public synchronized String getDeviceIdForWebSocketSession(Session session) {
		if (!deviceIdsByWebsocketSession.containsKey(session)) {
			String deviceId = UUID.randomUUID().toString();
			deviceIdsByWebsocketSession.put(session, deviceId);
			websocketSessionsByDeviceId.put(deviceId, session);
			return deviceId;
		} else {
			return deviceIdsByWebsocketSession.get(session);
		}
	}

	/**
	 * Unregister a device id that was vended by getDeviceIdForWebSocketSession
	 * @param deviceId the device id to unregister
	 */
	public synchronized void unregisterDeviceId(String deviceId) {
		Session session = websocketSessionsByDeviceId.get(deviceId);
		deviceIdsByWebsocketSession.remove(session);
		websocketSessionsByDeviceId.remove(deviceId);
	}

}
