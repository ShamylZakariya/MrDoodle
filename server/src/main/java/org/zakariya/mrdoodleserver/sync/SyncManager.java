package org.zakariya.mrdoodleserver.sync;

import redis.clients.jedis.JedisPool;

/**
 *
 */
public class SyncManager {

	private JedisPool jedisPool;
	private TimestampRecord timestampRecord;

	public SyncManager(JedisPool jedisPool, String accountId) {
		this.jedisPool = jedisPool;
		this.timestampRecord = new TimestampRecord(jedisPool, accountId);
	}

	public JedisPool getJedisPool() {
		return jedisPool;
	}

	public TimestampRecord getTimestampRecord() {
		return timestampRecord;
	}
}
