package org.zakariya.mrdoodle.sync.model;

import java.util.Date;

import io.realm.RealmObject;

/**
 * SyncState is persistent state for SyncManager
 */
public class SyncState extends RealmObject {
	private long timestampHead;
	private Date lastSyncDate;

	public long getTimestampHead() {
		return timestampHead;
	}

	public void setTimestampHead(long timestampHead) {
		this.timestampHead = timestampHead;
	}

	public Date getLastSyncDate() {
		return lastSyncDate;
	}

	public void setLastSyncDate(Date lastSyncDate) {
		this.lastSyncDate = lastSyncDate;
	}
}
