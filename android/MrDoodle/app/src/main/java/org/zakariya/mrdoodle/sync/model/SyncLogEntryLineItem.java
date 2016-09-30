package org.zakariya.mrdoodle.sync.model;

import android.text.TextUtils;

import java.util.Date;

import io.realm.RealmObject;

/**
 * Created by shamyl on 9/30/16.
 */

public class SyncLogEntryLineItem extends RealmObject {

	private int phase;
	private Date timestamp;
	private String lineItem;
	private String failure;

	public SyncLogEntryLineItem() {
	}

	SyncLogEntryLineItem(int phase, Date timestamp, String lineItem) {
		this(phase, timestamp, lineItem, null);
	}

	SyncLogEntryLineItem(int phase, Date timestamp, String lineItem, String failure) {
		this.phase = phase;
		this.timestamp = timestamp;
		this.lineItem = lineItem;
		this.failure = failure;
	}

	public boolean hasFailure() {
		return !TextUtils.isEmpty(failure);
	}

	public String getLineItem() {
		return lineItem;
	}

	public void setLineItem(String lineItem) {
		this.lineItem = lineItem;
	}

	public int getPhase() {
		return phase;
	}

	public void setPhase(int phase) {
		this.phase = phase;
	}

	public Date getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Date timestamp) {
		this.timestamp = timestamp;
	}

	public String getFailure() {
		return failure;
	}

	public void setFailure(String failure) {
		this.failure = failure;
	}

	@Override
	public String toString() {
		SyncLogEntry.Phase phase = SyncLogEntry.Phase.values()[getPhase()];
		String desc = phase + " : " + timestamp + " : " + lineItem;
		if (!TextUtils.isEmpty(failure)) {
			desc = "FAILURE\t" + desc + " : " + failure;
		}
		return desc;
	}
}
