package org.zakariya.mrdoodleserver.transport;

/**
 * POJO response to lock requests.
 */
public class LockStatus {
	public String documentId;
	public boolean locked;
	public boolean lockHeldByRequestingDevice;
}
