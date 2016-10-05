package org.zakariya.mrdoodle.net.transport;

import android.text.TextUtils;

/**
 * RemoteStatus
 * Message sent by SyncServer to notify the current timestampHeadSeconds (the timestamp of newest item in sync store)
 * and the list of locked item UUIDs.
 */
public class RemoteStatus {

	// a random ID issued by the server which represents this device
	public String deviceId;

	// the timestamp, in seconds, of the most recent change pushed to the server
	public long timestampHeadSeconds = 0;

	// list of ids of locked documents
	public String[] lockedDocumentIds;

	@Override
	public String toString() {
		return "[RemoteStatus deviceId: " + deviceId + " timestampHeadSeconds: " + timestampHeadSeconds + " lockedDocumentIds: [" + TextUtils.join(", ", lockedDocumentIds) + "]]";
	}
}
