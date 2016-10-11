package org.zakariya.mrdoodleserver.sync;

import org.eclipse.jetty.websocket.api.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * DeviceIdManager
 * creates unique random device ids to be used by SyncManager
 */
class DeviceIdManager implements DeviceIdManagerInterface {

	private static final Logger logger = LoggerFactory.getLogger(DeviceIdManager.class);

	private Map<InetSocketAddress, String> deviceIdsByWebsocketRemoteAttdress = new HashMap<>();
	private Map<String, InetSocketAddress> websocketRemoteAddressByDeviceId = new HashMap<>();
	private int issuedDeviceIdCount = 0;


	/**
	 * Check if a given device id is valid - e.g., it was vended by getDeviceIdForWebSocketSession and hasn't been unregistered
	 *
	 * @param deviceId a device id
	 * @return true if the id was vended by getDeviceIdForWebSocketSession and hasn't been unregistered
	 */
	public synchronized boolean isValidDeviceId(String deviceId) {
		return deviceId != null && websocketRemoteAddressByDeviceId.containsKey(deviceId);
	}

	/**
	 * Given a websocket session, get the associated random device id, or generate a fresh one
	 *
	 * @param session a websocket session representing a connected device
	 * @return a unique, random device id associated with this session
	 */
	public synchronized String getDeviceIdForWebSocketSession(Session session) {
		InetSocketAddress remoteAddress = session.getRemoteAddress();
		if (!deviceIdsByWebsocketRemoteAttdress.containsKey(remoteAddress)) {
			String deviceId = getNextDeviceId();
			deviceIdsByWebsocketRemoteAttdress.put(remoteAddress, deviceId);
			websocketRemoteAddressByDeviceId.put(deviceId, remoteAddress);

			return deviceId;
		} else {
			return deviceIdsByWebsocketRemoteAttdress.get(remoteAddress);
		}
	}

	/**
	 * Unregister a device id that was vended by getDeviceIdForWebSocketSession
	 *
	 * @param deviceId the device id to unregister
	 */
	public synchronized void unregisterDeviceId(String deviceId) {
		InetSocketAddress remoteAddress = websocketRemoteAddressByDeviceId.get(deviceId);
		deviceIdsByWebsocketRemoteAttdress.remove(remoteAddress);
		websocketRemoteAddressByDeviceId.remove(deviceId);
	}

	private String getNextDeviceId() {
		return "Device-" + Integer.toString(issuedDeviceIdCount++);
	}


}
