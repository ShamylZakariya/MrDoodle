package org.zakariya.mrdoodle.net.transport;

import android.text.TextUtils;

/**
 * Status
 * Message sent by SyncServer to notify the current timestampHead (the timestamp of newest item in sync store)
 * and the list of locked item UUIDs.
 */
public class Status {
	public long timestampHead = 0;
	public String[] lockedUUIDs;

	@Override
	public String toString() {
		return "[Status timestampHead: " + timestampHead + " lockedUUIDs: [" + TextUtils.join(", ", lockedUUIDs) + "]]";
	}
}
