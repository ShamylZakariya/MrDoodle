package org.zakariya.mrdoodleserver.sync;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class TimestampRecord {

	static final Logger logger = LoggerFactory.getLogger(TimestampRecord.class);

	public enum Action {
		WRITE,
		DELETE
	}

	public static class Entry {
		@JsonProperty
		String modelId;

		@JsonProperty
		String modelClass;

		@JsonProperty
		long timestampSeconds;

		@JsonProperty
		int action;

		public Entry() {
			super();
		}

		Entry(String modelId, String modelClass, long timestampSeconds, int action) {
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
			if (obj != null && obj instanceof Entry) {
				Entry other = (Entry) obj;
				return modelId.equals(other.modelId) &&
						modelClass.equals(other.modelClass) &&
						timestampSeconds == other.timestampSeconds &&
						action == other.action;
			}
			return false;
		}
	}

	private static final long DEBOUNCE_MILLIS = 3000;

	private JedisPool jedisPool;
	private String namespace;
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
	 * @param namespace the top-level namespace used for storage in redis (all fields will be named namespace/*)
	 * @param accountId the account namespace for all writes/reads
	 */
	TimestampRecord(JedisPool jedisPool, String namespace, String accountId) {
		this.jedisPool = jedisPool;
		this.namespace = namespace;
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

	public String getNamespace() {
		return namespace;
	}

	/**
	 * Record an event into the timestamp record
	 *
	 * @param uuid       id of the thing that the action happened to
	 * @param modelClass the model class of the thing represented by the modelId
	 * @param seconds    the timestamp, in seconds, of the event
	 * @param action     the type of event (write/delete)
	 * @return the entry that was created
	 */
	Entry record(String uuid, String modelClass, long seconds, Action action) {
		Entry entry = new Entry(uuid, modelClass, seconds, action.ordinal());
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
	 * @param uuid the modelId
	 * @return the timestamp seconds associated with UUID, or -1 if none is set
	 */
	long getTimestampSeconds(String uuid) {
		return entriesByUuid.containsKey(uuid) ? entriesByUuid.get(uuid).getTimestampSeconds() : -1;
	}

	/**
	 * Get a record of all events after sinceTimestampSeconds
	 *
	 * @param sinceTimestampSeconds a timestamp in seconds
	 * @return map of modelId->Entry of all events which occurred after said timestamp
	 */
	Map<String, Entry> getEntriesSince(long sinceTimestampSeconds) {
		if (sinceTimestampSeconds > 0) {

			// filter to subset of modelId:timestampSeconds pairs AFTER `sinceTimestampSeconds
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
	 * @return Get all entries in record, mapping the event's modelId to the event
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
		return getJedisKey(namespace, accountId);
	}

	static String getJedisKey(String namespace, String accountId) {
		return namespace + "/" + accountId +  "/timestamps";
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
			target.entriesByUuid.put(entry.getModelId(), entry);
		}

		target.head = null; // invalidate
		target.save();
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
