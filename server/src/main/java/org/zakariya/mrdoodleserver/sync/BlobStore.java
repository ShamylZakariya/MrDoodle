package org.zakariya.mrdoodleserver.sync;

import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.*;

import java.util.HashSet;
import java.util.Set;

import static org.zakariya.mrdoodleserver.sync.BlobStore.Mode.DIRECT;
import static org.zakariya.mrdoodleserver.sync.BlobStore.Mode.TEMP;

/**
 * Created by shamyl on 8/25/16.
 */
class BlobStore {

	enum Mode {
		// actions are written directly to account's data store
		DIRECT,

		// actions are written to a temp area in account's data store, and can be committed later via save()
		TEMP
	}

	static class Entry {
		byte [] data;
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
	}

	private String accountId;
	private JedisPool jedisPool;
	private Mode mode;
	private Set<String> writes;
	private Set<String> deletions;

	BlobStore(JedisPool jedisPool, String accountId, Mode mode) {
		this.jedisPool = jedisPool;
		this.accountId = accountId;
		this.mode = mode;

		if (mode == TEMP) {
			deletions = new HashSet<>();
		}
	}

	public String getAccountId() {
		return accountId;
	}

	public JedisPool getJedisPool() {
		return jedisPool;
	}

	public Mode getMode() {
		return mode;
	}

	void set(String uuid, String modelClass, long timestamp, byte[] data) {
		try(Jedis jedis = jedisPool.getResource()) {
			Transaction transaction = jedis.multi();
			transaction.set(getEntryUuidRedisKey(uuid, mode), uuid);
			transaction.set(getEntryModelClassRedisKey(uuid, mode), modelClass);
			transaction.set(getEntryTimestampRedisKey(uuid, mode), Long.toString(timestamp));
			transaction.set(getEntryDataRedisKey(uuid, mode).getBytes(), data);
			transaction.exec();

			if (mode == TEMP) {
				writes.add(uuid);
			}
		}
	}

	@Nullable
	Entry get(String uuid) {
		try(Jedis jedis = jedisPool.getResource()) {
			Transaction transaction = jedis.multi();
			Response<String> uuidResponse = transaction.get(getEntryUuidRedisKey(uuid, mode));
			Response<String> modelClassResponse = transaction.get(getEntryModelClassRedisKey(uuid, mode));
			Response<String> timestampResponse = transaction.get(getEntryTimestampRedisKey(uuid, mode));
			Response<byte[]> byteResponse = transaction.get(getEntryDataRedisKey(uuid, mode).getBytes());
			transaction.exec();

			return new Entry(uuidResponse.get(), modelClassResponse.get(), Long.parseLong(timestampResponse.get()), byteResponse.get());
		}
	}

	void delete(String uuid) {
		try(Jedis jedis = jedisPool.getResource()) {
			Transaction transaction = jedis.multi();
			transaction.del(getEntryUuidRedisKey(uuid, mode));
			transaction.del(getEntryModelClassRedisKey(uuid, mode));
			transaction.del(getEntryTimestampRedisKey(uuid, mode));
			transaction.del(getEntryDataRedisKey(uuid, mode));
			transaction.exec();

			if (mode == TEMP) {
				deletions.add(uuid);
			}
		}
	}

	void save(BlobStore store) {
		if (mode == TEMP) {
			try(Jedis jedis = store.getJedisPool().getResource()) {

				// rename all our writes from our temp namespace to the actual one
				if (!writes.isEmpty()) {
					Transaction writeTransaction = jedis.multi();
					for (String uuid : writes) {
						writeTransaction.rename(getEntryUuidRedisKey(uuid, TEMP), getEntryUuidRedisKey(uuid,DIRECT));
						writeTransaction.rename(getEntryModelClassRedisKey(uuid, TEMP), getEntryModelClassRedisKey(uuid,DIRECT));
						writeTransaction.rename(getEntryTimestampRedisKey(uuid, TEMP), getEntryTimestampRedisKey(uuid,DIRECT));
						writeTransaction.rename(getEntryDataRedisKey(uuid, TEMP), getEntryDataRedisKey(uuid,DIRECT));
					}

					writeTransaction.exec();
				}

				// now delete everything from our deletions record
				if (!deletions.isEmpty()) {
					Transaction deleteTransaction = jedis.multi();
					for (String uuid : deletions) {
						deleteTransaction.del(getEntryUuidRedisKey(uuid, DIRECT));
						deleteTransaction.del(getEntryModelClassRedisKey(uuid, DIRECT));
						deleteTransaction.del(getEntryTimestampRedisKey(uuid, DIRECT));
						deleteTransaction.del(getEntryDataRedisKey(uuid, DIRECT));
					}
					deleteTransaction.exec();
				}
			}
		}
	}

	private String getEntryUuidRedisKey(String uuid, Mode mode) {
		switch (mode) {
			case TEMP:
				return "blob/" + accountId + "/temp/" + uuid + ":uuid";
			case DIRECT:
				return "blob/" + accountId + "/" + uuid + ":uuid";
		}

		throw new IllegalStateException("BlobStore mode MUST be DIRECT or TEMP");
	}

	private String getEntryDataRedisKey(String uuid, Mode mode) {
		switch (mode) {
			case TEMP:
				return "blob/" + accountId + "/temp/" + uuid + ":data";
			case DIRECT:
				return "blob/" + accountId + "/" + uuid + ":data";
		}

		throw new IllegalStateException("BlobStore mode MUST be DIRECT or TEMP");
	}

	private String getEntryModelClassRedisKey(String uuid, Mode mode) {
		switch (mode) {
			case TEMP:
				return "blob/" + accountId + "/temp/" + uuid + ":modelClass";
			case DIRECT:
				return "blob/" + accountId + "/" + uuid + ":modelClass";
		}

		throw new IllegalStateException("BlobStore mode MUST be DIRECT or TEMP");
	}

	private String getEntryTimestampRedisKey(String uuid, Mode mode) {
		switch (mode) {
			case TEMP:
				return "blob/" + accountId + "/temp/" + uuid + ":timestamp";
			case DIRECT:
				return "blob/" + accountId + "/" + uuid + ":timestamp";
		}

		throw new IllegalStateException("BlobStore mode MUST be DIRECT or TEMP");
	}

}
