package org.zakariya.mrdoodle.sync;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.zakariya.mrdoodle.sync.model.TimestampStore;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

import io.realm.Realm;

/**
 * Created by shamyl on 8/13/16.
 */
public class TimestampRecorder {

	private static final String TAG = "TimestampRecorder";
	private final static int COMMIT_DEBOUNCE_DELAY_MILLIS = 2 * 1000;

	private Handler delayedCommitHandler;
	private Runnable delayedCommit;
	private boolean dirty;

	HashMap<String, Long> timestamps = new HashMap<>();


	@SuppressWarnings("unchecked")
	public TimestampRecorder() {
		this.dirty = false;

		Realm realm = Realm.getDefaultInstance();
		TimestampStore timestampStore = getTimestampStore(realm);
		byte[] timestampData = timestampStore.getTimestampData();

		if (timestampData != null && timestampData.length > 0) {
			try {
				ByteArrayInputStream byteStream = new ByteArrayInputStream(timestampData);
				ObjectInputStream objectInputStream = new ObjectInputStream(byteStream);
				timestamps = (HashMap<String, Long>) objectInputStream.readObject();

				objectInputStream.close();
				byteStream.close();
			} catch (Exception e) {
				Log.e(TAG, "Unable to inflate timestamps from byte array: " + e);
			}
		}

		realm.close();

		delayedCommitHandler = new Handler(Looper.getMainLooper());
		delayedCommit = new Runnable() {
			@Override
			public void run() {
				TimestampRecorder.this.commit();
			}
		};
	}

	public void stop() {
		commit();
	}

	public Map<String, Long> getTimestamps() {
		return timestamps;
	}

	private void commit() {
		if (dirty) {
			Realm realm = Realm.getDefaultInstance();
			TimestampStore timestampStore = getTimestampStore(realm);
			byte[] data = serialize();

			if (data != null) {
				realm.beginTransaction();
				timestampStore.setTimestampData(data);
				realm.commitTransaction();
			}

			realm.close();
			dirty = false;
		}
	}

	private byte[] serialize() {
		try {
			ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(byteStream);
			oos.writeObject(timestamps);
			oos.close();
			byteStream.close();

			return byteStream.toByteArray();
		} catch (IOException e) {
			Log.e(TAG, "Unable to serialize timestamps to byte array: " + e);
			return null;
		}
	}

	private void scheduleCommit() {
		delayedCommitHandler.removeCallbacks(delayedCommit);
		delayedCommitHandler.postDelayed(delayedCommit, COMMIT_DEBOUNCE_DELAY_MILLIS);
	}

	/**
	 * Set the timestamp for a particular objectId
	 *
	 * @param objectId  the object who's timestamp is being recorded
	 * @param timestamp the timestamp
	 */
	public void setTimestamp(String objectId, long timestamp) {
		timestamps.put(objectId, timestamp);

		dirty = true;
		scheduleCommit();
	}

	/**
	 * Get the timestamp for a particular objectId
	 *
	 * @param objectId the object who's timestamp is being queried
	 * @return the object's timestamp, or 0 if none has been recorded
	 */
	public long getTimestamp(String objectId) {
		if (timestamps.containsKey(objectId)) {
			return timestamps.get(objectId);
		} else {
			return 0;
		}
	}

	public void clearTimestamp(String objectId) {
		timestamps.remove(objectId);
	}

	/**
	 * Clear all timestamp data
	 */
	public void clear() {
		timestamps.clear();
		commit();
	}

	private TimestampStore getTimestampStore(Realm realm) {
		TimestampStore timestampStore = realm.where(TimestampStore.class).findFirst();
		if (timestampStore == null) {
			realm.beginTransaction();
			timestampStore = realm.createObject(TimestampStore.class);
			realm.commitTransaction();
		}
		return timestampStore;
	}


}
