package org.zakariya.mrdoodleserver.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	static final Logger logger = LoggerFactory.getLogger(TimestampRecord.class);

	enum Action {
		WRITE,
		DELETE
	}

	public static class Entry {
		public String uuid;
		public String modelClass;
		public long timestampSeconds;
		public int action;

		public Entry() {
			super();
		}

		Entry(String uuid, String modelClass, long timestampSeconds, Action action) {
			this.uuid = uuid;
			this.modelClass = modelClass;
			this.timestampSeconds = timestampSeconds;
			this.action = action.ordinal();
		}

		String getUuid() {
			return uuid;
		}

		public String getModelClass() {
			return modelClass;
		}

		long getTimestampSeconds() {
			return timestampSeconds;
		}

		Action getAction() {
			return Action.values()[action];
		}

		@Override
		public boolean equals(Object obj) {
			if (obj != null && obj instanceof Entry) {
				Entry other = (Entry) obj;
				return uuid.equals(other.uuid) &&
						modelClass.equals(other.modelClass) &&
						timestampSeconds == other.timestampSeconds &&
						action == other.action;
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

	/**
	 * Create a TimestampRecord which persists to redis
	 *
	 * @param jedisPool the pool where jedis instances will be extracted for reads and writes
	 * @param accountId the account namespace for all writes/reads
	 */
	TimestampRecord(JedisPool jedisPool, String accountId) {
		this.jedisPool = jedisPool;
		this.accountId = accountId;
		load();
	}

	/**
	 * Create an in-memory TimestampRecord
	 */
	TimestampRecord() {
	}

	JedisPool getJedisPool() {
		return jedisPool;
	}

	String getAccountId() {
		return accountId;
	}

	/**
	 * Record an event into the timestamp record
	 *
	 * @param uuid       id of the thing that the action happened to
	 * @param modelClass the model class of the thing represented by the uuid
	 * @param seconds    the timestamp, in seconds, of the event
	 * @param action     the type of event (write/delete)
	 * @return the entry that was created
	 */
	Entry record(String uuid, String modelClass, long seconds, Action action) {
		Entry entry = new Entry(uuid, modelClass, seconds, action);
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
		return entry;
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

	/**
	 * Get a record of all events after sinceTimestampSeconds
	 *
	 * @param sinceTimestampSeconds a timestamp in seconds
	 * @return map of uuid->Entry of all events which occurred after said timestamp
	 */
	Map<String, Entry> getEntriesSince(long sinceTimestampSeconds) {
		if (sinceTimestampSeconds > 0) {

			// filter to subset of uuid:timestampSeconds pairs AFTER `sinceTimestampSeconds
			Map<String, Entry> entries = new HashMap<>();
			for (String uuid : entriesByUuid.keySet()) {
				Entry entry = entriesByUuid.get(uuid);
				if (entry.getTimestampSeconds() >= sinceTimestampSeconds) {
					entries.put(uuid, entry);
				}
			}

			return entries;

		} else {
			return new HashMap<>(entriesByUuid);
		}
	}

	/**
	 * @return Get all entries in record, mapping the event's uuid to the event
	 */
	Map<String, Entry> getEntries() {
		return getEntriesSince(-1);
	}

	/**
	 * @return the Entry representing the most recent event to be added to the record
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
	 *
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
			logger.error("TimestampRecord::save - unable to serialize timestampsByUuid map to JSON", e);
		}
	}

	private void load() {
		if (jedisPool == null) {
			return;
		}

		try (Jedis jedis = jedisPool.getResource()) {
			String jsonString = jedis.get(getJedisKey());
			if (jsonString != null && !jsonString.isEmpty()) {
				try {
					entriesByUuid = objectMapper.reader()
							.forType(new TypeReference<Map<String, Entry>>() {
							})
							.readValue(jsonString);

					head = findHeadEntry();
				} catch (IOException e) {
					logger.error("TimestampRecord::load - unable to load timestampsByUuid map from JSON", e);
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
