package org.zakariya.mrdoodleserver.sync;

import java.util.*;

/**
 * LockManager
 */
public class LockManager {

	public interface Listener {
		/**
		 * Called when a lock is acquired
		 * @param deviceId the id of the device which got the lock
		 * @param documentId the id of the document which was locked
		 */
		void onLockAcquired(String deviceId, String documentId);

		/**
		 * Called when a lock is released
		 * @param deviceId the id of the device which released the lock
		 * @param documentId the id of the document which was unlocked
		 */
		void onLockReleased(String deviceId, String documentId);
	}

	private static class DeviceLocks {
		private String deviceId;
		private Set<String> documentIds;

		DeviceLocks(String deviceId) {
			this.deviceId = deviceId;
			this.documentIds = new HashSet<>();
		}

		String getDeviceId() {
			return deviceId;
		}

		Set<String> getDocumentIds() {
			return documentIds;
		}
	}

	private Map<String, DeviceLocks> locks;
	private Set<String> lockedDocumentIds;
	private List<Listener> listeners = new ArrayList<>();

	LockManager() {
		locks = new HashMap<>();
		lockedDocumentIds = new HashSet<>();
	}

	void addListener(Listener listener) {
		listeners.add(listener);
	}

	void removeListener(Listener listener) {
		listeners.remove(listener);
	}

	/**
	 * Request a lock for a specific document id for a device
	 *
	 * @param deviceId   the id issued by the WebSocketConnection to a specific device
	 * @param documentId the id of a document
	 * @return true if the lock was acquired, false if the lock was already taken by another device
	 */
	synchronized public boolean lock(String deviceId, String documentId) {
		if (!lockedDocumentIds.contains(documentId)) {
			lockedDocumentIds.add(documentId);
			getDeviceLocks(deviceId).getDocumentIds().add(documentId);

			for (Listener listener : listeners) {
				listener.onLockAcquired(deviceId, documentId);
			}

			return true;
		} else {
			return false;
		}
	}

	/**
	 * Release a lock of a specific document by a specific device
	 *
	 * @param deviceId   the id issued by the WebSocketConnection to a specific device
	 * @param documentId the id of a document to unlock
	 */
	synchronized public void unlock(String deviceId, String documentId) {
		DeviceLocks deviceLocks = getDeviceLocks(deviceId);
		if (deviceLocks.getDocumentIds().contains(documentId)) {
			deviceLocks.getDocumentIds().remove(documentId);
			lockedDocumentIds.remove(documentId);

			for (Listener listener : listeners) {
				listener.onLockReleased(deviceId, documentId);
			}
		}
	}

	/**
	 * Unlock all locks held by a specific device
	 *
	 * @param deviceId the id issued by the WebSocketConnection to a specific device
	 */
	synchronized public void unlock(String deviceId) {
		DeviceLocks deviceLocks = getDeviceLocks(deviceId);
		List<String> locks = new ArrayList<>(deviceLocks.getDocumentIds());

		// unlock all
		for (String documentId : deviceLocks.getDocumentIds()) {
			lockedDocumentIds.remove(documentId);
		}
		deviceLocks.getDocumentIds().clear();

		// notify
		for (Listener listener : listeners) {
			for (String documentId : locks) {
				listener.onLockReleased(deviceId, documentId);
			}
		}
	}

	/**
	 * Check if a device is holding a lock on a document
	 * @param deviceId the id issued by the WebSocketConnection to a specific device
	 * @param documentId the document id in question
	 * @return true iff the lock is held by the device
	 */
	synchronized public boolean hasLock(String deviceId, String documentId) {
		DeviceLocks deviceLocks = getDeviceLocks(deviceId);
		 return deviceLocks.getDocumentIds().contains(documentId);
	}

	/**
	 * @param documentId the id of a specific document
	 * @return true if that document is locked, false if it's open
	 */
	public boolean isLocked(String documentId) {
		return lockedDocumentIds.contains(documentId);
	}

	/**
	 * @return a set of all locked document ids
	 */
	public Set<String> getLockedDocumentIds() {
		return new HashSet<>(lockedDocumentIds);
	}

	/**
	 * @param deviceId id of a specific device
	 * @return set of locked document ids for a specific device
	 */
	public Set<String> getLockedDocumentIds(String deviceId) {
		DeviceLocks locks = getDeviceLocks(deviceId);
		return locks.getDocumentIds();
	}

	private DeviceLocks getDeviceLocks(String deviceId) {
		DeviceLocks deviceLocks = locks.get(deviceId);
		if (deviceLocks == null) {
			deviceLocks = new DeviceLocks(deviceId);
			locks.put(deviceId, deviceLocks);
		}
		return deviceLocks;
	}

}
