package org.zakariya.mrdoodleserver.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

/**
 *
 */
public class TimestampRecord {

	public class Entry {
		private String uuid;
		private long timestamp;

		public Entry(String uuid, long timestamp) {
			this.uuid = uuid;
			this.timestamp = timestamp;
		}

		public String getUuid() {
			return uuid;
		}

		public long getTimestamp() {
			return timestamp;
		}
	}

	private static final long DEBOUNCE_MILLIS = 3000;

	private JedisPool jedisPool;
	private String accountId;
	private Map<String, Long> timestampByUuid = new HashMap<>();
	private Entry head;
	private Runnable saver = new Runnable() {
		public void run() {
			save();
		}
	};

	private ScheduledExecutorService debounceScheduler = Executors.newSingleThreadScheduledExecutor();
	private ScheduledFuture debouncedSave;
	private ObjectMapper objectMapper = new ObjectMapper();

	public TimestampRecord(JedisPool jedisPool, String accountId) {
		this.jedisPool = jedisPool;
		this.accountId = accountId;
		load();
	}

	public JedisPool getJedisPool() {
		return jedisPool;
	}

	public String getAccountId() {
		return accountId;
	}

	public void setTimestamp(String uuid, long timestamp) {
		timestampByUuid.put(uuid, timestamp);

		// update head
		if (head == null) {
			findTimestampHead();
		}

		if (timestamp > head.getTimestamp()) {
			head = new Entry(uuid, timestamp);
		}

		markDirty();
	}

	public void removeTimestamp(String uuid) {
		boolean needsUpdateTimestampHead = uuid.equals(getTimestampHead().getUuid());
		timestampByUuid.remove(uuid);
		if (needsUpdateTimestampHead) {
			findTimestampHead();
		}
	}

	/**
	 * Get the timestamp for a given UUID
	 *
	 * @param uuid the uuid
	 * @return the timestamp associated with UUID, or -1 if none is set
	 */
	public long getTimestamp(String uuid) {
		return timestampByUuid.containsKey(uuid) ? timestampByUuid.get(uuid) : -1;
	}

	public Map<String, Long> getTimestampsSince(long since) {
		if (since > 0) {

			// filter to subset of uuid:timestamp pairs AFTER `since
			Map<String, Long> timestamps = new HashMap<>();
			for (String uuid : timestampByUuid.keySet()) {
				long timestamp = timestampByUuid.get(uuid);
				if (timestamp >= since) {
					timestamps.put(uuid, timestamp);
				}
			}

			return timestamps;

		} else {
			return new HashMap<>(timestampByUuid);
		}
	}

	/**
	 * @return the id/timestamp pair for the newest item in the record
	 */
	public Entry getTimestampHead() {
		if (head == null) {
			findTimestampHead();
		}

		return head;
	}

	public Map<String, Long> getTimestamps() {
		return getTimestampsSince(-1);
	}

	public boolean isEmpty() {
		return timestampByUuid.isEmpty();
	}

	private String getJedisKey() {
		return getJedisKey(accountId);
	}

	public static String getJedisKey(String accountId) {
		return accountId + ":timestamps";
	}

	private void markDirty() {
		if (jedisPool == null) {
			return;
		}

		cancelDebouncedSave();
		debouncedSave = debounceScheduler.schedule(saver, DEBOUNCE_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS);
	}

	private void cancelDebouncedSave() {
		if (debouncedSave != null && !debouncedSave.isCancelled()) {
			debouncedSave.cancel(false);
			debouncedSave = null;
		}
	}

	public void save() {
		if (jedisPool == null) {
			return;
		}

		cancelDebouncedSave();
		try (Jedis jedis = jedisPool.getResource()) {
			String jsonString = objectMapper.writeValueAsString(timestampByUuid);
			jedis.set(getJedisKey(), jsonString);
		} catch (JsonProcessingException e) {
			System.err.println("TimestampRecord::save - unable to serialize timestampsByUuid map to JSON");
			e.printStackTrace();
		}
	}

	public void load() {
		if (jedisPool == null) {
			return;
		}

		try (Jedis jedis = jedisPool.getResource()) {
			String jsonString = jedis.get(getJedisKey());
			if (jsonString != null && !jsonString.isEmpty()) {
				try {
					TypeReference<HashMap<String, Long>> typeRef = new TypeReference<HashMap<String, Long>>() {
					};
					timestampByUuid = objectMapper.readValue(jsonString, typeRef);
					findTimestampHead();
				} catch (IOException e) {
					System.err.println("TimestampRecord::load - unable to load timestampsByUuid map from JSON");
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Walk the timestampByUuid map and find the newest entry, and assign it to `head
	 */
	private void findTimestampHead() {
		String headUuid = null;
		long headTimestamp = 0;

		for (String uuid : timestampByUuid.keySet()) {
			long timestamp = timestampByUuid.get(uuid);
			if (timestamp > headTimestamp) {
				headUuid = uuid;
				headTimestamp = timestamp;
			}
		}

		head = new Entry(headUuid, headTimestamp);
	}

}
