package org.zakariya.mrdoodleserver.sync;

import org.zakariya.mrdoodleserver.util.Preconditions;
import redis.clients.jedis.JedisPool;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by shamyl on 8/17/16.
 */
public class SyncRouter {

	public static JedisPool jedisPool;
	private static Map<String, SyncManager> syncManagersByAccountId = new HashMap<>();

	static SyncManager getSyncManagerForAccount(String accountId) {
		Preconditions.checkNotNull(jedisPool, "jedisPool instance must be set");

		SyncManager syncManager = syncManagersByAccountId.get(accountId);

		if (syncManager == null) {
			syncManager = new SyncManager(jedisPool, accountId);
			syncManagersByAccountId.put(accountId, syncManager);
		}

		return syncManager;
	}

	// TODO Define get/put/etc routes here as static methods which can be accessed via SyncRouter::get, etc

}
