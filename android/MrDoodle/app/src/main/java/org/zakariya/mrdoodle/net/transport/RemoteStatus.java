package org.zakariya.mrdoodle.net.transport;

import android.text.TextUtils;

/**
 * RemoteStatus
 * Message sent by SyncServer to notify the current timestampHeadSeconds (the timestamp of newest item in sync store)
 * and the list of locked item UUIDs.
 */
public class RemoteStatus {

	// a random ID issued by the server which represents this device. This will only be non-null
	// when first connecting to the server
	public String deviceId;

	// the timestamp, in seconds, of the most recent change pushed to the server
	public long timestampHeadSeconds = 0;

	// list of ids of documents locked by this device
	public String[] grantedLockedDocumentIds;

	// list of ids of documents locked by other devices
	public String[] foreignLockedDocumentIds;

	@Override
	public String toString() {
		return "[RemoteStatus deviceId: " + deviceId + " timestampHeadSeconds: " + timestampHeadSeconds + " grantedLockedDocumentIds: [" + TextUtils.join(", ", grantedLockedDocumentIds) + "] foreignLockedDocumentIds: [" + TextUtils.join(", ", foreignLockedDocumentIds) + "]]";
	}
}
