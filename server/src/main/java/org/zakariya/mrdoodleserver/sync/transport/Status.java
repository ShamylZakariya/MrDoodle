package org.zakariya.mrdoodleserver.sync.transport;

import java.util.ArrayList;
import java.util.List;

/**
 * Status
 * POJO sent to notify clients of the current timestamp head, and lock status of items
 */
public final class Status {
	public String deviceId;
	public long timestampHeadSeconds = 0;
	public List<String> grantedLockedDocumentIds = new ArrayList<>();
	public List<String> foreignLockedDocumentIds = new ArrayList<>();
}
