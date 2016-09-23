package org.zakariya.mrdoodleserver.sync.transport;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.zakariya.mrdoodleserver.sync.TimestampRecord;

/**
 * Represents a single entry in the TimestampRecord, and meant for serializing to json
 */
public class TimestampRecordEntry {
	@JsonProperty
	private String modelId;

	@JsonProperty
	private String modelClass;

	@JsonProperty
	private long timestampSeconds;

	@JsonProperty
	private int action;

	public TimestampRecordEntry() {
		super();
	}

	public TimestampRecordEntry(String modelId, String modelClass, long timestampSeconds, int action) {
		this.modelId = modelId;
		this.modelClass = modelClass;
		this.timestampSeconds = timestampSeconds;
		this.action = action;
	}

	public String getModelId() {
		return modelId;
	}

	public String getModelClass() {
		return modelClass;
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
			return modelId.equals(other.modelId) &&
					modelClass.equals(other.modelClass) &&
					timestampSeconds == other.timestampSeconds &&
					action == other.action;
		}
		return false;
	}
}
