package org.zakariya.mrdoodleserver.routes;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.zakariya.mrdoodleserver.sync.UserRecordAccess;
import org.zakariya.mrdoodleserver.util.Configuration;
import redis.clients.jedis.JedisPool;
import spark.ResponseTransformer;

/**
 * DashboardRouter
 * Establishes the REST routes for the html dashboard to consume
 */
public class DashboardRouter extends Router {

	private UserRecordAccess userRecordAccess;

	public DashboardRouter(Configuration configuration, JedisPool jedisPool) {
		super(configuration, jedisPool);
		this.userRecordAccess = new UserRecordAccess(getJedisPool(), getStoragePrefix());
	}

	public void configureRoutes() {

	}

	private String getBasePath() {
		return "/api/" + getApiVersion() + "/dashboard";
	}


}
