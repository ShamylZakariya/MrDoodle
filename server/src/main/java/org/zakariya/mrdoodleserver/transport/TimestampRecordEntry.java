package org.zakariya.mrdoodleserver.transport;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a single entry in the TimestampRecord, and meant for serializing to json
 */
public class TimestampRecordEntry {
	@JsonProperty
	private String documentId;

	@JsonProperty
	private String documentType;

	@JsonProperty
	private long timestampSeconds;

	@JsonProperty
	private int action;

	public TimestampRecordEntry() {
		super();
	}

	public TimestampRecordEntry(String documentId, String documentType, long timestampSeconds, int action) {
		this.documentId = documentId;
		this.documentType = documentType;
		this.timestampSeconds = timestampSeconds;
		this.action = action;
	}

	public String getDocumentId() {
		return documentId;
	}

	public String getDocumentType() {
		return documentType;
	}

	public long getTimestampSeconds() {
		return timestampSeconds;
	}

	public int getAction() {
		return action;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj != null && obj instanceof TimestampRecordEntry) {
			TimestampRecordEntry other = (TimestampRecordEntry) obj;
			return documentId.equals(other.documentId) &&
					documentType.equals(other.documentType) &&
					timestampSeconds == other.timestampSeconds &&
					action == other.action;
		}
		return false;
	}
}
