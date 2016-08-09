package org.zakariya.mrdoodleserver;

import static spark.Spark.*;

import org.zakariya.mrdoodleserver.auth.Authenticator;
import org.zakariya.mrdoodleserver.auth.Whitelist;
import org.zakariya.mrdoodleserver.services.WebSocketConnection;
import org.zakariya.mrdoodleserver.util.Configuration;

/**
 * Created by shamyl on 8/1/16.
 */
public class SyncServer {


	public static void main(String[] args) {

		staticFiles.location("/public"); //served at localhost:4567 (default port)
		staticFiles.expireTime(600);

		Configuration configuration = new Configuration();
		configuration.addConfigJsonFilePath("configuration.json");
		configuration.addConfigJsonFilePath("configuration_secret.json");

		Whitelist whitelist = new Whitelist(configuration.getInt("authenticator/whitelist_grace_period_seconds", 60));
		Authenticator authenticator = new Authenticator(configuration.get("authenticator/oauth_server_id"), whitelist);

		WebSocketConnection.authenticator = authenticator;
		webSocket("/connect", WebSocketConnection.class);

		init();
	}

}
