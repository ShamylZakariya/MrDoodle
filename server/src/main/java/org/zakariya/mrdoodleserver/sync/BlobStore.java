package org.zakariya.mrdoodleserver.sync;

import com.sun.xml.internal.xsom.impl.scd.Iterators;
import org.jetbrains.annotations.Nullable;
import org.zakariya.mrdoodleserver.util.Configuration;
import redis.clients.jedis.*;

import java.util.List;

/**
 * Created by shamyl on 8/25/16.
 */
public class BlobStore {

	public static class Entry {
		byte [] data;
		String uuid;
		String modelClass;
		long timestamp;

		public Entry(String uuid, String modelClass, long timestamp, byte[] data) {
			this.data = data;
			this.uuid = uuid;
			this.modelClass = modelClass;
			this.timestamp = timestamp;
		}

		public byte[] getData() {
			return data;
		}

		public String getUuid() {
			return uuid;
		}

		public String getModelClass() {
			return modelClass;
		}

		public long getTimestamp() {
			return timestamp;
		}
	}

	private String accountId;
	private JedisPool jedisPool;

	public BlobStore(JedisPool jedisPool, String accountId) {
		this.jedisPool = jedisPool;
		accountId = accountId;
	}

	public String getAccountId() {
		return accountId;
	}

	public JedisPool getJedisPool() {
		return jedisPool;
	}

	public void set(String uuid, String modelClass, long timestamp, byte[] data) {
		try(Jedis jedis = jedisPool.getResource()) {
			Transaction transaction = jedis.multi();
			transaction.set(getEntryUuidRedisKey(uuid), uuid);
			transaction.set(getEntryModelClassRedisKey(uuid), modelClass);
			transaction.set(getEntryTimestampRedisKey(uuid), Long.toString(timestamp));
			transaction.set(getEntryDataRedisKey(uuid).getBytes(), data);
			transaction.exec();
		}
	}

	@Nullable
	public Entry get(String uuid) {
		try(Jedis jedis = jedisPool.getResource()) {
			Transaction transaction = jedis.multi();
			Response<String> uuidResponse = transaction.get(getEntryUuidRedisKey(uuid));
			Response<String> modelClassResponse = transaction.get(getEntryModelClassRedisKey(uuid));
			Response<String> timestampResponse = transaction.get(getEntryTimestampRedisKey(uuid));
			Response<byte[]> byteResponse = transaction.get(getEntryDataRedisKey(uuid).getBytes());
			transaction.exec();

			return new Entry(uuidResponse.get(), modelClassResponse.get(), Long.parseLong(timestampResponse.get()), byteResponse.get());
		}
	}

	public void delete(String uuid) {
		try(Jedis jedis = jedisPool.getResource()) {
			Transaction transaction = jedis.multi();
			transaction.del(getEntryUuidRedisKey(uuid));
			transaction.del(getEntryModelClassRedisKey(uuid));
			transaction.del(getEntryTimestampRedisKey(uuid));
			transaction.del(getEntryDataRedisKey(uuid));
			transaction.exec();
		}
	}

	private String getEntryUuidRedisKey(String uuid) {
		return "blob/" + accountId + "/" + uuid + ":uuid";
	}

	private String getEntryDataRedisKey(String uuid) {
		return "blob/" + accountId + "/" + uuid + ":data";
	}

	private String getEntryModelClassRedisKey(String uuid) {
		return "blob/" + accountId + "/" + uuid + ":modelClass";
	}

	private String getEntryTimestampRedisKey(String uuid) {
		return "blob/" + accountId + "/" + uuid + ":timestamp";
	}

}
