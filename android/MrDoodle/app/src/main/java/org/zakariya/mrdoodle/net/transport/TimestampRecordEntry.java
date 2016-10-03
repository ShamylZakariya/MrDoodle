package org.zakariya.mrdoodle.net.transport;

/**
 * Created by szakariy on 9/7/16.
 */
public class TimestampRecordEntry {

	public enum Action {
		WRITE,
		DELETE
	}

	public String documentId;
	public String documentType;
	public long timestampSeconds;
	public int action;

	public TimestampRecordEntry() {
	}

	public TimestampRecordEntry(String documentId, String documentType, long timestampSeconds, int action) {
		this.documentId = documentId;
		this.documentType = documentType;
		this.timestampSeconds = timestampSeconds;
		this.action = action;
	}

	@Override
	public String toString() {
		return "[TimestampRecordEntry action: " + Action.values()[action] + " documentId: " + documentId + " documentType: " + documentType + " timestampSeconds: " + timestampSeconds + "]";
	}
}
