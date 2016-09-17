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

	public static final String NAMESPACE_DEFAULT = "default";

	static class Entry {
		byte[] data;
		String uuid;
		String modelClass;
		long timestamp;

		Entry(String uuid, String modelClass, long timestamp, byte[] data) {
			this.data = data;
			this.uuid = uuid;
			this.modelClass = modelClass;
			this.timestamp = timestamp;
		}

		byte[] getData() {
			return data;
		}

		String getUuid() {
			return uuid;
		}

		String getModelClass() {
			return modelClass;
		}

		long getTimestamp() {
			return timestamp;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj != null && obj instanceof Entry) {
				Entry other = (Entry) obj;
				return uuid.equals(other.uuid) && modelClass.equals(other.modelClass) && Arrays.equals(data, other.data);
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
	 * @param accountId the user account for the blobs which will be persisted
	 * @param namespace the namespace under which blobs will be persisted
	 */
	BlobStore(JedisPool jedisPool, String accountId, String namespace) {
		this.jedisPool = jedisPool;
		this.accountId = accountId;
		this.namespace = namespace;
	}

	/**
	 * Create a BlobStore which will persist to a given redis connection. Blobs will be saved to the default namespace.
	 * @param jedisPool pool brokering access to a redis connection
	 * @param accountId the user account for the blobs which will be persisted
	 */
	BlobStore(JedisPool jedisPool, String accountId) {
		this(jedisPool, accountId, NAMESPACE_DEFAULT);
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
		set(e.getUuid(), e.getModelClass(), e.getTimestamp(), e.getData());
	}

	/**
	 * Persist a blob and associated data to the store
	 * @param uuid the id of the blob
	 * @param modelClass the "type" of blob - this might map to a java object on the client side
	 * @param timestamp the timestamp (generally in seconds) of the data
	 * @param data the actual blob data
	 */
	void set(String uuid, String modelClass, long timestamp, byte[] data) {
		try (Jedis jedis = jedisPool.getResource()) {
			Transaction transaction = jedis.multi();
			transaction.set(getEntryUuidKey(accountId, namespace, uuid), uuid);
			transaction.set(getEntryModelClassKey(accountId, namespace, uuid), modelClass);
			transaction.set(getEntryTimestampKey(accountId, namespace, uuid), Long.toString(timestamp));
			transaction.set(getEntryDataKey(accountId, namespace, uuid).getBytes(), data);
			transaction.exec();
			writes.add(uuid);
		}
	}

	/**
	 * @param uuid the id of the blob
	 * @return the Entry for a given blob in the store
	 */
	@Nullable
	Entry get(String uuid) {
		try (Jedis jedis = jedisPool.getResource()) {
			Transaction transaction = jedis.multi();
			Response<String> uuidResponse = transaction.get(getEntryUuidKey(accountId, namespace, uuid));
			Response<String> modelClassResponse = transaction.get(getEntryModelClassKey(accountId, namespace, uuid));
			Response<String> timestampResponse = transaction.get(getEntryTimestampKey(accountId, namespace, uuid));
			Response<byte[]> byteResponse = transaction.get(getEntryDataKey(accountId, namespace, uuid).getBytes());

			transaction.exec();

			String uuid2 = uuidResponse.get();
			String modelClass = modelClassResponse.get();
			String timestampString = timestampResponse.get();
			byte[] data = byteResponse.get();

			if (uuid2 != null && uuid2.equals(uuid) && modelClass != null && !modelClass.isEmpty() && timestampString != null && !timestampString.isEmpty()) {
				long timestamp = Long.parseLong(timestampString);
				return new Entry(uuid2, modelClass, timestamp, data);
			} else {
				return null;
			}
		}
	}

	/**
	 * Remove a blob and associated data from the store
	 * @param uuid the id of the blob to remove
	 */
	void delete(String uuid) {
		try (Jedis jedis = jedisPool.getResource()) {
			Transaction transaction = jedis.multi();
			transaction.del(getEntryUuidKey(accountId, namespace, uuid));
			transaction.del(getEntryModelClassKey(accountId, namespace, uuid));
			transaction.del(getEntryTimestampKey(accountId, namespace, uuid));
			transaction.del(getEntryDataKey(accountId, namespace, uuid));
			transaction.exec();
			deletions.add(uuid);
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
				for (String uuid : writes) {
					writeTransaction.rename(getEntryUuidKey(accountId, namespace, uuid), getEntryUuidKey(store.getAccountId(), store.getNamespace(), uuid));
					writeTransaction.rename(getEntryModelClassKey(accountId, namespace, uuid), getEntryModelClassKey(store.getAccountId(), store.getNamespace(), uuid));
					writeTransaction.rename(getEntryTimestampKey(accountId, namespace, uuid), getEntryTimestampKey(store.getAccountId(), store.getNamespace(), uuid));
					writeTransaction.rename(getEntryDataKey(accountId, namespace, uuid), getEntryDataKey(store.getAccountId(), store.getNamespace(), uuid));
				}
				writeTransaction.exec();
				store.writes.addAll(writes);
			}

			// now delete everything from our deletions record
			if (!deletions.isEmpty()) {
				Transaction deleteTransaction = jedis.multi();
				for (String uuid : deletions) {
					deleteTransaction.del(getEntryUuidKey(store.getAccountId(), store.getNamespace(), uuid));
					deleteTransaction.del(getEntryModelClassKey(store.getAccountId(), store.getNamespace(), uuid));
					deleteTransaction.del(getEntryTimestampKey(store.getAccountId(), store.getNamespace(), uuid));
					deleteTransaction.del(getEntryDataKey(store.getAccountId(), store.getNamespace(), uuid));
				}
				deleteTransaction.exec();
				store.deletions.addAll(deletions);
			}
		}
	}

	private static String getEntryRootKey(String accountId, String namespace) {
		return "blob/" + accountId + "/" + namespace + "/";
	}

	static String getEntryUuidKey(String accountId, String namespace, String uuid) {
		return getEntryRootKey(accountId, namespace) + uuid + ":uuid";
	}

	static String getEntryDataKey(String accountId, String namespace, String uuid) {
		return getEntryRootKey(accountId, namespace) + uuid + ":data";
	}

	static String getEntryModelClassKey(String accountId, String namespace, String uuid) {
		return getEntryRootKey(accountId, namespace) + uuid + ":modelClass";
	}

	static String getEntryTimestampKey(String accountId, String namespace, String uuid) {
		return getEntryRootKey(accountId, namespace) + uuid + ":timestamp";
	}

}
