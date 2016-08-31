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
class TimestampRecord {

	enum Action {
		WRITE,
		DELETE
	}

	public static class Entry {
		public String uuid;
		public long timestampSeconds;
		public int action;

		public Entry(){
			super();
		}

		Entry(String uuid, long timestampSeconds, Action action) {
			this.uuid = uuid;
			this.timestampSeconds = timestampSeconds;
			this.action = action.ordinal();
		}

		String getUuid() {
			return uuid;
		}

		long getTimestampSeconds() {
			return timestampSeconds;
		}

		Action getAction() {
			return Action.values()[action];
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof Entry) {
				Entry other = (Entry) obj;
				return uuid.equals(other.uuid) && timestampSeconds == other.timestampSeconds && action == other.action;
			}
			return false;
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

	TimestampRecord(JedisPool jedisPool, String accountId) {
		this.jedisPool = jedisPool;
		this.accountId = accountId;
		load();
	}

	TimestampRecord() {
	}

	JedisPool getJedisPool() {
		return jedisPool;
	}

	String getAccountId() {
		return accountId;
	}

	void record(String uuid, long seconds, Action action) {
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
	long getTimestampSeconds(String uuid) {
		return entriesByUuid.containsKey(uuid) ? entriesByUuid.get(uuid).getTimestampSeconds() : -1;
	}

	Map<String, Entry> getEntriesSince(long since) {
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

	Map<String, Entry> getEntries() {
		return getEntriesSince(-1);
	}

	/**
	 * @return the id/timestampSeconds pair for the newest item in the record
	 */
	Entry getTimestampHead() {
		if (head == null) {
			head = findHeadEntry();
		}

		return head;
	}


	boolean isEmpty() {
		return entriesByUuid.isEmpty();
	}

	private String getJedisKey() {
		return getJedisKey(accountId);
	}

	static String getJedisKey(String accountId) {
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

	/**
	 * Write this TimestampRecord's values onto the target
	 * @param target the TimestampRecord which will receive this TimestampRecord's values
	 */
	void save(TimestampRecord target) {
		for (Entry entry : entriesByUuid.values()) {
			target.entriesByUuid.put(entry.getUuid(), entry);
		}
	}

	void save() {
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

	void load() {
		if (jedisPool == null) {
			return;
		}

		try (Jedis jedis = jedisPool.getResource()) {
			String jsonString = jedis.get(getJedisKey());
			if (jsonString != null && !jsonString.isEmpty()) {
				try {
					entriesByUuid = objectMapper.reader()
							.forType(new TypeReference<Map<String,Entry>>() {})
							.readValue(jsonString);

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
