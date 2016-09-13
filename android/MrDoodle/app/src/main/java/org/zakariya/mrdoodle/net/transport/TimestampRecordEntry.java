package org.zakariya.mrdoodle.net.transport;

/**
 * Created by szakariy on 9/7/16.
 */
public class TimestampRecordEntry {

	public static enum Action {
		WRITE,
		DELETE
	}

	public String modelId;
	public String modelClass;
	public long timestampSeconds;
	public int action;

	public TimestampRecordEntry() {
	}

	public TimestampRecordEntry(String modelId, long timestampSeconds, int action) {
		this.modelId = modelId;
		this.timestampSeconds = timestampSeconds;
		this.action = action;
	}

	@Override
	public String toString() {
		return "[TimestampRecordEntry modelId: " + modelId + " timestampSeconds: " + timestampSeconds + " action: " + Action.values()[action] + "]";
	}
}
