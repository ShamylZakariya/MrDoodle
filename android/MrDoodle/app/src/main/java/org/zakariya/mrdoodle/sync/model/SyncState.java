package org.zakariya.mrdoodle.sync.model;

import java.util.Date;

import io.realm.RealmObject;

/**
 * SyncState is persistent state for SyncManager
 */
public class SyncState extends RealmObject {
	private long timestampHeadSeconds;
	private Date lastSyncDate;

	public long getTimestampHeadSeconds() {
		return timestampHeadSeconds;
	}

	public void setTimestampHeadSeconds(long timestampHeadSeconds) {
		this.timestampHeadSeconds = timestampHeadSeconds;
	}

	public Date getLastSyncDate() {
		return lastSyncDate;
	}

	public void setLastSyncDate(Date lastSyncDate) {
		this.lastSyncDate = lastSyncDate;
	}
}
