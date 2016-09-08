package org.zakariya.mrdoodle.net.transport;

/**
 * Created by szakariy on 9/7/16.
 */
public class TimestampRecordEntry {

	public static enum Action {
		WRITE,
		DELETE
	}

	public String uuid;
	public long timestampSeconds;
	public int action;

	public TimestampRecordEntry() {
	}

	public TimestampRecordEntry(String uuid, long timestampSeconds, int action) {
		this.uuid = uuid;
		this.timestampSeconds = timestampSeconds;
		this.action = action;
	}

	@Override
	public String toString() {
		return "[TimestampRecordEntry uuid: " + uuid + " timestampSeconds: " + timestampSeconds + " action: " + Action.values()[action] + "]";
	}
}
