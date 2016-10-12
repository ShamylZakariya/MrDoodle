package org.zakariya.mrdoodle.net.transport;

/**
 * RemoteLockStatus
 * Response payload to SyncApiService::isLocked, SyncApiService::requestLock, and SyncApiService::releaseLock
 */

public class RemoteLockStatus {

	/**
	 * Id of the document which was being locked, queried, or unlocked
	 */
	public String documentId;

	/**
	 * Whether that document is locked (by anybody: this device or other devices)
	 */
	public boolean locked;

	/**
	 * True if the document is locked and the lock was made by this device
	 */
	public boolean lockHeldByRequestingDevice;
}
