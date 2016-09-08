package org.zakariya.mrdoodle.sync.model;

import io.realm.RealmObject;

/**
 * Created by shamyl on 8/13/16.
 */
public class TimestampStore extends RealmObject {
	private byte[] timestampData;

	public byte[] getTimestampData() {
		return timestampData;
	}

	public void setTimestampData(byte[] timestampData) {
		this.timestampData = timestampData;
	}
}
