package org.zakariya.mrdoodleserver.sync.transport;

import java.util.ArrayList;
import java.util.List;

/**
 * Status
 * POJO sent to notify clients of the current timestamp head, and lock status of items
 */
public final class Status {
	public long timestampHead = 0;
	public List<String> lockedUUIDs = new ArrayList<>();
}
