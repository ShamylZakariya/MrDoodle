package org.zakariya.mrdoodleserver.sync;

import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by shamyl on 8/25/16.
 */
class BlobStore {

	static class Entry {
		byte[] data;
		String id;
		String type;
		long timestamp;

		Entry(String id, String type, long timestamp, byte[] data) {
			this.data = data;
			this.id = id;
			this.type = type;
			this.timestamp = timestamp;
		}

		byte[] getData() {
			return data;
		}

		String getId() {
			return id;
		}

		String getType() {
			return type;
		}

		long getTimestamp() {
			return timestamp;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj != null && obj instanceof Entry) {
				Entry other = (Entry) obj;
				return id.equals(other.id) && type.equals(other.type) && Arrays.equals(data, other.data);
			} else {
				return false;
			}
		}
	}

	private String accountId;
	private String namespace;
	private JedisPool jedisPool;
	private Set<String> writes = new HashSet<>();
	private Set<String> deletions = new HashSet<>();

	/**
	 * Create a BlobStore which will persist to a given redis connection.
	 * @param jedisPool pool brokering access to a redis connection
	 * @param namespace the top-level namespace under which blobs will be persisted
	 * @param accountId the user account for the blobs which will be persisted
	 */
	BlobStore(JedisPool jedisPool, String namespace, String accountId) {
		this.jedisPool = jedisPool;
		this.accountId = accountId;
		this.namespace = namespace;
	}

	String getAccountId() {
		return accountId;
	}

	JedisPool getJedisPool() {
		return jedisPool;
	}

	String getNamespace() {
		return namespace;
	}

	/**
	 * Persist an entry to the store
	 * @param e a BlobStore.Entry to persist
	 */
	void set(Entry e) {
		set(e.getId(), e.getType(), e.getTimestamp(), e.getData());
	}

	/**
	 * Persist a blob and associated data to the store
	 * @param id the id of the blob
	 * @param type the "type" of blob - this might map to a java object on the client side
	 * @param timestamp the timestamp (generally in seconds) of the data
	 * @param data the actual blob data
	 */
	void set(String id, String type, long timestamp, byte[] data) {
		try (Jedis jedis = jedisPool.getResource()) {
			Transaction transaction = jedis.multi();
			transaction.set(getEntryIdKey(accountId, namespace, id), id);
			transaction.set(getEntryModelClassKey(accountId, namespace, id), type);
			transaction.set(getEntryTimestampKey(accountId, namespace, id), Long.toString(timestamp));
			transaction.set(getEntryDataKey(accountId, namespace, id).getBytes(), data);
			transaction.exec();
			writes.add(id);
		}
	}

	/**
	 * @param id the id of the blob
	 * @return the Entry for a given blob in the store
	 */
	@Nullable
	Entry get(String id) {
		try (Jedis jedis = jedisPool.getResource()) {
			Transaction transaction = jedis.multi();
			Response<String> idResponse = transaction.get(getEntryIdKey(accountId, namespace, id));
			Response<String> modelClassResponse = transaction.get(getEntryModelClassKey(accountId, namespace, id));
			Response<String> timestampResponse = transaction.get(getEntryTimestampKey(accountId, namespace, id));
			Response<byte[]> byteResponse = transaction.get(getEntryDataKey(accountId, namespace, id).getBytes());

			transaction.exec();

			String id2 = idResponse.get();
			String modelClass = modelClassResponse.get();
			String timestampString = timestampResponse.get();
			byte[] data = byteResponse.get();

			if (id2 != null && id2.equals(id) && modelClass != null && !modelClass.isEmpty() && timestampString != null && !timestampString.isEmpty()) {
				long timestamp = Long.parseLong(timestampString);
				return new Entry(id2, modelClass, timestamp, data);
			} else {
				return null;
			}
		}
	}

	/**
	 * Check if a given blob id is accessible to this store
	 * @param id the id of a blob
	 * @return true if this store has the given blob
	 */
	boolean has(String id) {
		try (Jedis jedis = jedisPool.getResource()) {
			return jedis.exists(
					getEntryIdKey(accountId, namespace, id),
					getEntryModelClassKey(accountId, namespace, id),
					getEntryTimestampKey(accountId, namespace, id),
					getEntryDataKey(accountId, namespace, id)) == 4;
		}
	}

	/**
	 * Remove a blob and associated data from the store
	 * @param id the id of the blob to remove
	 */
	void delete(String id) {
		try (Jedis jedis = jedisPool.getResource()) {
			Transaction transaction = jedis.multi();
			transaction.del(getEntryIdKey(accountId, namespace, id));
			transaction.del(getEntryModelClassKey(accountId, namespace, id));
			transaction.del(getEntryTimestampKey(accountId, namespace, id));
			transaction.del(getEntryDataKey(accountId, namespace, id));
			transaction.exec();
			deletions.add(id);
		}
	}

	/**
	 * Deletes ALL blobs and associated data for this blob store's account and namespace
	 */
	void discard() {
		try (Jedis jedis = jedisPool.getResource()) {
			Set<String> keys = jedis.keys(getEntryRootKey(accountId,namespace) + "*");
			keys.forEach(jedis::del);
		}

		deletions.clear();
		writes.clear();
	}

	/**
	 * Save this BlobStore's writes and deletes to another BlobStore. What this means is, if we wrote a blob 'A', and deleted a blob 'B',
	 * and the destination store has no blob 'A', and DOES have a blob 'B', after the save, the destination store will have 'A', and will
	 * no longer have a blob 'B'. The purpose of this is to enable one blob store to represent a batch of "temp" writes and deletes, which
	 * can be committed at a later date to the "real" blob store for that account.
	 * @param store the store to copy changes from this store to
	 */
	void save(BlobStore store) {
		try (Jedis jedis = store.getJedisPool().getResource()) {

			// rename all our writes from our temp namespace to the actual one
			if (!writes.isEmpty()) {
				Transaction writeTransaction = jedis.multi();
				for (String id : writes) {
					writeTransaction.rename(getEntryIdKey(accountId, namespace, id), getEntryIdKey(store.getAccountId(), store.getNamespace(), id));
					writeTransaction.rename(getEntryModelClassKey(accountId, namespace, id), getEntryModelClassKey(store.getAccountId(), store.getNamespace(), id));
					writeTransaction.rename(getEntryTimestampKey(accountId, namespace, id), getEntryTimestampKey(store.getAccountId(), store.getNamespace(), id));
					writeTransaction.rename(getEntryDataKey(accountId, namespace, id), getEntryDataKey(store.getAccountId(), store.getNamespace(), id));
				}
				writeTransaction.exec();
				store.writes.addAll(writes);
			}

			// now delete everything from our deletions record
			if (!deletions.isEmpty()) {
				Transaction deleteTransaction = jedis.multi();
				for (String id : deletions) {
					deleteTransaction.del(getEntryIdKey(store.getAccountId(), store.getNamespace(), id));
					deleteTransaction.del(getEntryModelClassKey(store.getAccountId(), store.getNamespace(), id));
					deleteTransaction.del(getEntryTimestampKey(store.getAccountId(), store.getNamespace(), id));
					deleteTransaction.del(getEntryDataKey(store.getAccountId(), store.getNamespace(), id));
				}
				deleteTransaction.exec();
				store.deletions.addAll(deletions);
			}
		}
	}

	private static String getEntryRootKey(String accountId, String namespace) {
		return namespace + "/" + accountId + "/blob/";
	}

	static String getEntryIdKey(String accountId, String namespace, String id) {
		return getEntryRootKey(accountId, namespace) + id + ":id";
	}

	static String getEntryDataKey(String accountId, String namespace, String id) {
		return getEntryRootKey(accountId, namespace) + id + ":data";
	}

	static String getEntryModelClassKey(String accountId, String namespace, String id) {
		return getEntryRootKey(accountId, namespace) + id + ":type";
	}

	static String getEntryTimestampKey(String accountId, String namespace, String id) {
		return getEntryRootKey(accountId, namespace) + id + ":timestamp";
	}

}
