package org.zakariya.mrdoodle.net.transport;

import android.text.TextUtils;

/**
 * RemoteStatus
 * Message sent by SyncServer to notify the current timestampHeadSeconds (the timestamp of newest item in sync store)
 * and the list of locked item UUIDs.
 */
public class RemoteStatus {
	public long timestampHeadSeconds = 0;
	public String[] lockedDocumentIds;

	@Override
	public String toString() {
		return "[RemoteStatus timestampHeadSeconds: " + timestampHeadSeconds + " lockedDocumentIds: [" + TextUtils.join(", ", lockedDocumentIds) + "]]";
	}
}
