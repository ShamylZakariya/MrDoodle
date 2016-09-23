package org.zakariya.mrdoodle.net.transport;

import android.text.TextUtils;

/**
 * Status
 * Message sent by SyncServer to notify the current timestampHeadSeconds (the timestamp of newest item in sync store)
 * and the list of locked item UUIDs.
 */
public class Status {
	public long timestampHeadSeconds = 0;
	public String[] lockedDocumentIds;

	@Override
	public String toString() {
		return "[Status timestampHeadSeconds: " + timestampHeadSeconds + " lockedDocumentIds: [" + TextUtils.join(", ", lockedDocumentIds) + "]]";
	}
}
