package org.zakariya.mrdoodle.sync.events;

import org.zakariya.mrdoodle.net.model.RemoteChangeReport;

/**
 * Created by shamyl on 10/16/16.
 */

public class RemoteChangeEvent {
	private RemoteChangeReport report;

	public RemoteChangeEvent(RemoteChangeReport report) {
		this.report = report;
	}

	public RemoteChangeReport getReport() {
		return report;
	}
}
