package org.zakariya.mrdoodleserver.factories;

import org.zakariya.mrdoodleserver.sync.DeviceIdManagerInterface;
import org.zakariya.mrdoodleserver.sync.SyncManager;
import redis.clients.jedis.JedisPool;

/**
 * SyncManagerFactory
 * Creates a SyncManager instance.
 * I never thought I'd create a java "factory" dingus, but here it is.
 */
public interface SyncManagerFactory {
	SyncManager create(JedisPool jedisPool, String storagePrefix, String accountId);
}
