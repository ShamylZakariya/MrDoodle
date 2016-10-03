package org.zakariya.mrdoodleserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zakariya.mrdoodleserver.auth.Authenticator;
import org.zakariya.mrdoodleserver.auth.Whitelist;
import org.zakariya.mrdoodleserver.auth.techniques.GoogleIdTokenAuthenticator;
import org.zakariya.mrdoodleserver.auth.techniques.MockAuthenticator;
import org.zakariya.mrdoodleserver.routes.SyncRouter;
import org.zakariya.mrdoodleserver.services.WebSocketConnection;
import org.zakariya.mrdoodleserver.util.Configuration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static spark.Spark.init;
import static spark.Spark.webSocket;

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

		Authenticator authenticator = buildAuthenticator(configuration);
		JedisPool jedisPool = buildJedisPool(configuration);

		if (flushStorage) {
			String prefix = configuration.get("prefix");
			flushStorage(jedisPool, prefix);
		}

		// build the syncRouter. note, we have no control over when WebSocketConnection is created,
		// so set up our router to be notified when it happens
		SyncRouter syncRouter = new SyncRouter(configuration, authenticator, jedisPool);
		WebSocketConnection.addOnWebSocketConnectionCreatedListener(syncRouter);

		WebSocketConnection.authenticator = authenticator;
		webSocket(WebSocketConnection.getRoute(configuration), WebSocketConnection.class);

		syncRouter.configureRoutes();
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

	private static JedisPool buildJedisPool(Configuration configuration) {
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

	private static Authenticator buildAuthenticator(Configuration configuration) {
		if (configuration.getBoolean("authenticator/useMockAuthenticator", false)) {

			Map<String, Object> tokensMap = configuration.getMap("authenticator/mock/tokens");
			if (tokensMap == null) {
				return new MockAuthenticator();
			}

			// we need to convert Map<String,Object> -> Map<String,String>
			Map<String, String> tokens = tokensMap
					.entrySet()
					.stream()
					.collect(Collectors.toMap(Map.Entry::getKey, e -> (String) e.getValue()));

			return new MockAuthenticator(tokens);

		} else {

			String oauthServerId = configuration.get("authenticator/google/oauth_server_id");
			int whitelistGraceperiodSeconds = configuration.getInt("authenticator/whitelist_grace_period_seconds", 60);

			return new GoogleIdTokenAuthenticator(oauthServerId, new Whitelist(whitelistGraceperiodSeconds));
		}
	}

}
