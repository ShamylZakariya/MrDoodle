package org.zakariya.mrdoodleserver;

import static spark.Spark.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zakariya.mrdoodleserver.auth.Authenticator;
import org.zakariya.mrdoodleserver.auth.Whitelist;
import org.zakariya.mrdoodleserver.services.WebSocketConnection;
import org.zakariya.mrdoodleserver.sync.SyncRouter;
import org.zakariya.mrdoodleserver.util.Configuration;
import redis.clients.jedis.JedisPool;

/**
 * Created by shamyl on 8/1/16.
 */
public class SyncServer {

	static final Logger logger = LoggerFactory.getLogger(SyncServer.class);

	public static void main(String[] args) {

		logger.info("Starting SyncServer");

		Configuration configuration = new Configuration();
		configuration.addConfigJsonFilePath("configuration.json");
		configuration.addConfigJsonFilePath("configuration_secret.json");

		Whitelist whitelist = new Whitelist(configuration.getInt("authenticator/whitelist_grace_period_seconds", 60));
		Authenticator authenticator = new Authenticator(configuration.get("authenticator/oauth_server_id"), whitelist);

		String redisHost = configuration.get("redis/host");
		int redisPort = configuration.getInt("redis/port",-1);
		JedisPool jedisPool;
		if (redisPort != -1) {
			logger.info("Building jedisPool with host {} and port {}", redisHost, redisPort);
			jedisPool = new JedisPool(redisHost, redisPort);
		} else {
			logger.info("Building jedisPool with host {} and default port", redisHost);
			jedisPool = new JedisPool(redisHost);
		}

		// build the syncRouter. note, we have no control over when WebSocketConnection is created,
		// so set up our router to be notified when it happens
		SyncRouter syncRouter = new SyncRouter(configuration, authenticator, jedisPool);
		WebSocketConnection.addOnWebSocketConnectionCreatedListener(syncRouter);

		WebSocketConnection.authenticator = authenticator;
		webSocket(WebSocketConnection.getRoute(configuration), WebSocketConnection.class);

		// Static files are not used for sync, just for test html files
		staticFiles.location("/public"); //served at localhost:4567 (default port)
		staticFiles.expireTime(600);

		syncRouter.configureRoutes();
		init();
	}

}
