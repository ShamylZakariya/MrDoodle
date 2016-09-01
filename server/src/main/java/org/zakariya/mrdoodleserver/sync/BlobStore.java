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

	BlobStore(JedisPool jedisPool, String accountId, String namespace) {
		this.jedisPool = jedisPool;
		this.accountId = accountId;
		this.namespace = namespace;
	}

	BlobStore(JedisPool jedisPool, String accountId) {
		this(jedisPool, accountId, "default");
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

	void set(Entry e) {
		set(e.getUuid(), e.getModelClass(), e.getTimestamp(), e.getData());
	}

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

	void flush() {
		try (Jedis jedis = jedisPool.getResource()) {
			Set<String> keys = jedis.keys(getEntryRootKey(accountId,namespace) + "*");
			keys.forEach(jedis::del);
		}

		deletions.clear();
		writes.clear();
	}

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

	static String getEntryRootKey(String accountId, String namespace) {
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
