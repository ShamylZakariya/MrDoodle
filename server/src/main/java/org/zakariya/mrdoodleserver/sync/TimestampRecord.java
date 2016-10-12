package org.zakariya.mrdoodleserver.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zakariya.mrdoodleserver.sync.transport.TimestampRecordEntry;
import org.zakariya.mrdoodleserver.util.Debouncer;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public class TimestampRecord {

	private static final Logger logger = LoggerFactory.getLogger(TimestampRecord.class);

	public enum Action {
		WRITE,
		DELETE
	}

	private static final int DEBOUNCE_MILLIS = 3000;

	private JedisPool jedisPool;
	private String namespace;
	private String accountId;
	private Map<String, TimestampRecordEntry> entriesByDocumentId = new HashMap<>();
	private TimestampRecordEntry head;
	private Debouncer.Function<Void> debouncedSave;

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

	public JedisPool getJedisPool() {
		return jedisPool;
	}

	public String getAccountId() {
		return accountId;
	}

	public String getNamespace() {
		return namespace;
	}

	/**
	 * Record an event into the timestamp record
	 *
	 * @param documentId   id of the thing that the action happened to
	 * @param documentType the model class of the thing represented by the modelId
	 * @param seconds      the timestamp, in seconds, of the event
	 * @param action       the type of event (write/delete)
	 * @return the entry that was created
	 */
	public TimestampRecordEntry record(String documentId, String documentType, long seconds, Action action) {
		TimestampRecordEntry entry = new TimestampRecordEntry(documentId, documentType, seconds, action.ordinal());
		entriesByDocumentId.put(documentId, entry);

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
	 * Get the timestampSeconds for a given document
	 *
	 * @param documentId the id of the document to query
	 * @return the timestamp seconds associated with the document id, or -1 if none is set
	 */
	long getTimestampSeconds(String documentId) {
		return entriesByDocumentId.containsKey(documentId) ? entriesByDocumentId.get(documentId).getTimestampSeconds() : -1;
	}

	/**
	 * Get a record of all events after sinceTimestampSeconds
	 *
	 * @param sinceTimestampSeconds a timestamp in seconds
	 * @return map of modelId->Entry of all events which occurred after said timestamp
	 */
	public Map<String, TimestampRecordEntry> getEntriesSince(long sinceTimestampSeconds) {
		if (sinceTimestampSeconds > 0) {

			// filter to subset of modelId:timestampSeconds pairs AFTER `sinceTimestampSeconds
			Map<String, TimestampRecordEntry> entries = new HashMap<>();
			for (String documentId : entriesByDocumentId.keySet()) {
				TimestampRecordEntry entry = entriesByDocumentId.get(documentId);
				if (entry.getTimestampSeconds() >= sinceTimestampSeconds) {
					entries.put(documentId, entry);
				}
			}

			return entries;

		} else {
			return new HashMap<>(entriesByDocumentId);
		}
	}

	/**
	 * @return Get all entries in record, mapping the event's modelId to the event
	 */
	Map<String, TimestampRecordEntry> getEntries() {
		return getEntriesSince(-1);
	}

	/**
	 * @return the Entry representing the most recent event to be added to the record
	 */
	TimestampRecordEntry getTimestampHead() {
		if (head == null) {
			head = findHeadEntry();
		}

		return head;
	}


	boolean isEmpty() {
		return entriesByDocumentId.isEmpty();
	}

	private String getJedisKey() {
		return getJedisKey(namespace, accountId);
	}

	static String getJedisKey(String namespace, String accountId) {
		return namespace + "/" + accountId + "/timestamps";
	}

	private void markDirty() {
		if (jedisPool == null) {
			return;
		}

		if (debouncedSave == null) {
			debouncedSave = Debouncer.debounce(aVoid -> save(), DEBOUNCE_MILLIS);
		}

		debouncedSave.apply(null);
	}

	/**
	 * Write this TimestampRecord's values onto the target
	 *
	 * @param target the TimestampRecord which will receive this TimestampRecord's values
	 */
	void save(TimestampRecord target) {
		for (TimestampRecordEntry entry : entriesByDocumentId.values()) {
			target.entriesByDocumentId.put(entry.getDocumentId(), entry);
		}

		target.head = null; // invalidate
		target.save();
	}

	void save() {
		if (jedisPool == null) {
			return;
		}

		try (Jedis jedis = jedisPool.getResource()) {
			String jsonString = objectMapper.writeValueAsString(entriesByDocumentId);
			jedis.set(getJedisKey(), jsonString);
		} catch (JsonProcessingException e) {
			logger.error("TimestampRecord::save - unable to serialize entriesByDocumentId map to JSON", e);
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
					entriesByDocumentId = objectMapper.reader()
							.forType(new TypeReference<Map<String, TimestampRecordEntry>>() {
							})
							.readValue(jsonString);

					head = findHeadEntry();
				} catch (IOException e) {
					logger.error("TimestampRecord::load - unable to create entriesByDocumentId map from JSON", e);
				}
			}
		}
	}

	/**
	 * Walk the entriesByDocumentId map and find the newest entry, and assign it to `head
	 */
	private TimestampRecordEntry findHeadEntry() {
		TimestampRecordEntry headEntry = null;
		long headTimestamp = 0;

		for (String documentId : entriesByDocumentId.keySet()) {
			TimestampRecordEntry entry = entriesByDocumentId.get(documentId);
			if (entry.getTimestampSeconds() > headTimestamp) {
				headEntry = entry;
				headTimestamp = entry.getTimestampSeconds();
			}
		}

		return headEntry;
	}

}
