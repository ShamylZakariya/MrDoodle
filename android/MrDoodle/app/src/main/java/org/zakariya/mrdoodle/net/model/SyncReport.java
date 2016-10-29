package org.zakariya.mrdoodle.net.model;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * SyncReport is a "report" of what was changed by syncing to remote state.
 */

public class SyncReport {
	private List<RemoteChangeReport> remoteChangeReports;
	private long timestampHeadSeconds = 0;
	private boolean didResetLocalStore;

	public SyncReport(long timestampHeadSeconds) {
		this.timestampHeadSeconds = timestampHeadSeconds;
		remoteChangeReports = new ArrayList<>(); // empty
	}

	public SyncReport(List<RemoteChangeReport> remoteChangeReports, long timestampHeadSeconds) {
		this.remoteChangeReports = remoteChangeReports;
		this.timestampHeadSeconds = timestampHeadSeconds;
	}

	public List<RemoteChangeReport> getRemoteChangeReports() {
		return remoteChangeReports;
	}

	public long getTimestampHeadSeconds() {
		return timestampHeadSeconds;
	}

	public boolean didResetLocalStore() {
		return didResetLocalStore;
	}

	public void setDidResetLocalStore(boolean didResetLocalStore) {
		this.didResetLocalStore = didResetLocalStore;
	}

	@Override
	public String toString() {
		String changes = TextUtils.join(", ", remoteChangeReports);
		return "[SyncReport timestampHeadSeconds: " + timestampHeadSeconds + " changes: " + changes + "]";
	}
}
