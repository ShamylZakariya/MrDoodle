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

	@Override
	public String toString() {
		String changes = TextUtils.join(", ", remoteChangeReports);
		return "[SyncReport timestampHeadSeconds: " + timestampHeadSeconds + " changes: " + changes + "]";
	}
}
