package org.zakariya.mrdoodleserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zakariya.mrdoodleserver.auth.Authenticator;
import org.zakariya.mrdoodleserver.auth.User;
import org.zakariya.mrdoodleserver.auth.Whitelist;
import org.zakariya.mrdoodleserver.auth.techniques.GoogleIdTokenAuthenticator;
import org.zakariya.mrdoodleserver.auth.techniques.MockAuthenticator;
import org.zakariya.mrdoodleserver.factories.SyncManagerFactory;
import org.zakariya.mrdoodleserver.routes.DashboardRouter;
import org.zakariya.mrdoodleserver.routes.Router;
import org.zakariya.mrdoodleserver.routes.SyncRouter;
import org.zakariya.mrdoodleserver.services.WebSocketConnection;
import org.zakariya.mrdoodleserver.sync.DeviceIdManager;
import org.zakariya.mrdoodleserver.sync.DeviceIdManagerInterface;
import org.zakariya.mrdoodleserver.sync.SyncManager;
import org.zakariya.mrdoodleserver.sync.mock.MockDeviceIdManager;
import org.zakariya.mrdoodleserver.util.Configuration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static spark.Spark.*;

/**
 * Top level SyncServer application.
 */
public class SyncServer {

	private static final Logger logger = LoggerFactory.getLogger(SyncServer.class);

	/**
	 * Start the server
	 * pass:
	 * --config | -c for path to a config json file
	 *
	 * @param args command line args
	 */
	public static void main(String[] args) {

		Configuration configuration = new Configuration();
		boolean flushStorage = false;

		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			switch (arg) {

				case "-c":
				case "--config":
					configuration.addConfigJsonFilePath(args[++i]);
					break;

				case "-f":
				case "--flushStorage":
					flushStorage = true;

				default:
					break;
			}
		}

		start(configuration, flushStorage);
	}

	/**
	 * Start the server with a given configuration
	 *
	 * @param configuration a configuration
	 * @param flushStorage  if true, all storage under the configuration's prefix will be deleted
	 */
	public static void start(Configuration configuration, boolean flushStorage) {
		logger.info("Starting SyncServer");

		Authenticator syncAuthenticator = buildSyncAuthenticator(configuration);
		Authenticator dashboardAuthenticator = buildDashboardAuthenticator(configuration);
		JedisPool jedisPool = buildJedisPool(configuration);

		if (flushStorage) {
			String prefix = configuration.get("prefix");
			flushStorage(jedisPool, prefix);
		}

		// set static files location
		externalStaticFileLocation(configuration.get("staticFiles"));

		// build routers
		String storagePrefix = configuration.get("jedisStoragePrefix");
		String apiVersion = configuration.get("apiVersion");
		SyncManagerFactory syncManagerFactory = buildSyncManagerFactory(configuration);
		List<String> dashboardUserWhitelist = configuration.getArray("dashboard/whitelist");

		SyncRouter syncRouter = new SyncRouter(jedisPool, storagePrefix, apiVersion, syncAuthenticator, syncManagerFactory);
		DashboardRouter dashboardRouter = new DashboardRouter(jedisPool, storagePrefix, apiVersion, dashboardAuthenticator, dashboardUserWhitelist);
		Router routers[] = {syncRouter, dashboardRouter};

		// set up the WebSocketConnection. Note, since Spark lazily creates it, we can't pass
		// values to a constructor! So we need to use static values, which is hideous.
		WebSocketConnection.authenticator = syncAuthenticator;
		WebSocketConnection.addOnWebSocketConnectionCreatedListener(syncRouter);
		webSocket(WebSocketConnection.getRoute(apiVersion), WebSocketConnection.class);

		// routers can't init their routes until the websocket connection is built
		for (Router router : routers) {
			router.initializeRoutes();
		}

		init();
	}

	/**
	 * Delete storage for a given configuration
	 *
	 * @param configuration a configuration describing the jedis store, prefix, etc
	 */
	public static void flushStorage(Configuration configuration) {
		String prefix = configuration.get("prefix");
		if (prefix != null) {
			JedisPool pool = buildJedisPool(configuration);
			flushStorage(pool, prefix);
		}
	}

	private static void flushStorage(JedisPool pool, String prefix) {
		logger.info("Deleting all storage under the {}* namespace", prefix);
		try (Jedis jedis = pool.getResource()) {
			Set<String> keys = jedis.keys(prefix + "*");
			keys.forEach(jedis::del);
		}
	}

	public static JedisPool buildJedisPool(Configuration configuration) {
		JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
		jedisPoolConfig.setMaxTotal(128);

		// TODO Figure out how to implement WHEN_EXHAUSTED_GROW, if possible
		jedisPoolConfig.setBlockWhenExhausted(true);

		String redisHost = configuration.get("redis/host");
		int redisPort = configuration.getInt("redis/port", -1);
		if (redisPort != -1) {
			logger.info("Building jedisPool with host {} and port {}", redisHost, redisPort);
			return new JedisPool(jedisPoolConfig, redisHost, redisPort);
		} else {
			logger.info("Building jedisPool with host {} and default port", redisHost);
			return new JedisPool(jedisPoolConfig, redisHost);
		}
	}

	private static Authenticator buildSyncAuthenticator(Configuration configuration) {
		if (configuration.getBoolean("sync/authenticator/useMockAuthenticator", false)) {

			Map<String, Object> tokensMap = configuration.getMap("sync/authenticator/mock/tokens");
			if (tokensMap == null) {
				return new MockAuthenticator();
			}

			Map<String, User> tokens = new HashMap<>();
			for (String token : tokensMap.keySet()) {
				String value = tokensMap.get(token).toString();
				String bits[] = value.split("\\|");
				tokens.put(token, new User(bits[0], bits[1], bits[2]));
			}

			return new MockAuthenticator(tokens);
		} else {
			String oauthServerId = configuration.get("sync/authenticator/google/oauth_client_id");
			String issuer = configuration.get("sync/authenticator/google/issuer");
			int whitelistGraceperiodSeconds = configuration.getInt("sync/authenticator/whitelist_grace_period_seconds", 60);
			return new GoogleIdTokenAuthenticator(oauthServerId, issuer, new Whitelist(whitelistGraceperiodSeconds));
		}
	}

	private static Authenticator buildDashboardAuthenticator(Configuration configuration) {
		String oauthServerId = configuration.get("dashboard/authenticator/google/oauth_client_id");
		String issuer = configuration.get("dashboard/authenticator/google/issuer");
		int whitelistGraceperiodSeconds = configuration.getInt("dashboard/authenticator/whitelist_grace_period_seconds", 60);
		return new GoogleIdTokenAuthenticator(oauthServerId, issuer, new Whitelist(whitelistGraceperiodSeconds));
	}

	private static SyncManagerFactory buildSyncManagerFactory(Configuration configuration) {

		final List<String> deviceIds = configuration.getArray("syncManager/deviceIdManager/mock/deviceIds");
		if (deviceIds != null) {
			logger.debug("SyncManager instances will be using MockDeviceIdManager with mock device ids: {}", deviceIds);
		}

		return (jedisPool, storagePrefix, accountId) -> {

			DeviceIdManagerInterface deviceIdManager;
			if (deviceIds != null) {
				deviceIdManager = new MockDeviceIdManager(deviceIds);
			} else {
				deviceIdManager = new DeviceIdManager();
			}

			return new SyncManager(jedisPool, deviceIdManager, storagePrefix, accountId);
		};
	}

}
