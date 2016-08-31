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

	public enum Action {
		WRITE,
		DELETE
	}

	public class Entry {
		private String uuid;
		private long timestampSeconds;
		private int action;

		public Entry(String uuid, long timestampSeconds, Action action) {
			this.uuid = uuid;
			this.timestampSeconds = timestampSeconds;
			this.action = action.ordinal();
		}

		public String getUuid() {
			return uuid;
		}

		public long getTimestampSeconds() {
			return timestampSeconds;
		}

		public Action getAction() {
			return Action.values()[action];
		}
	}

	private static final long DEBOUNCE_MILLIS = 3000;

	private JedisPool jedisPool;
	private String accountId;
	private Map<String, Entry> entriesByUuid = new HashMap<>();
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

	public void record(String uuid, long seconds, Action action) {
		Entry entry = new Entry(uuid, seconds, action);
		entriesByUuid.put(uuid, entry);

		// update head
		if (head == null) {
			head = findHeadEntry();
		}

		// this is the new head
		if (seconds > head.getTimestampSeconds()) {
			head = entry;
		}

		markDirty();
	}

	/**
	 * Get the timestampSeconds for a given UUID
	 *
	 * @param uuid the uuid
	 * @return the timestamp seconds associated with UUID, or -1 if none is set
	 */
	public long getTimestampSeconds(String uuid) {
		return entriesByUuid.containsKey(uuid) ? entriesByUuid.get(uuid).getTimestampSeconds() : -1;
	}

	public Map<String, Entry> getEntriesSince(long since) {
		if (since > 0) {

			// filter to subset of uuid:timestampSeconds pairs AFTER `since
			Map<String, Entry> entries = new HashMap<>();
			for (String uuid : entriesByUuid.keySet()) {
				Entry entry  = entriesByUuid.get(uuid);
				if (entry.getTimestampSeconds() >= since) {
					entries.put(uuid, entry);
				}
			}

			return entries;

		} else {
			return new HashMap<>(entriesByUuid);
		}
	}

	public Map<String, Entry> getEntries() {
		return getEntriesSince(-1);
	}

	/**
	 * @return the id/timestampSeconds pair for the newest item in the record
	 */
	public Entry getTimestampHead() {
		if (head == null) {
			head = findHeadEntry();
		}

		return head;
	}


	public boolean isEmpty() {
		return entriesByUuid.isEmpty();
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
			String jsonString = objectMapper.writeValueAsString(entriesByUuid);
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
					entriesByUuid = objectMapper.readValue(jsonString, typeRef);
					head = findHeadEntry();
				} catch (IOException e) {
					System.err.println("TimestampRecord::load - unable to load timestampsByUuid map from JSON");
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Walk the entriesByUuid map and find the newest entry, and assign it to `head
	 */
	private Entry findHeadEntry() {
		Entry headEntry = null;
		long headTimestamp = 0;

		for (String uuid : entriesByUuid.keySet()) {
			Entry entry = entriesByUuid.get(uuid);
			if (entry.getTimestampSeconds() > headTimestamp) {
				headEntry = entry;
				headTimestamp = entry.getTimestampSeconds();
			}
		}

		return headEntry;
	}

}
