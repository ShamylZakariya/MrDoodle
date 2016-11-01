package org.zakariya.mrdoodleserver.routes;

import org.zakariya.mrdoodleserver.sync.UserRecordAccess;
import org.zakariya.mrdoodleserver.sync.transport.User;
import org.zakariya.mrdoodleserver.util.Configuration;
import redis.clients.jedis.JedisPool;
import spark.Request;
import spark.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static spark.Spark.get;

/**
 * DashboardRouter
 * Establishes the REST routes for the html dashboard to consume
 * TODO: Add some kind of authentication, presumably we'll use Google API OAuth on client side
 */
public class DashboardRouter extends Router {

	private UserRecordAccess userRecordAccess;

	public DashboardRouter(Configuration configuration, JedisPool jedisPool) {
		super(configuration, jedisPool);
		this.userRecordAccess = new UserRecordAccess(getJedisPool(), getStoragePrefix());
	}

	public void configureRoutes() {
		String basePath = getBasePath();

		// get list of all users who have used this service - returns User[]
		get(basePath + "/users", this::getUsers, getJsonResponseTransformer());
	}

	private List<User> getUsers(Request request, Response response) {
		List<User> result = new ArrayList<>();
		Set<org.zakariya.mrdoodleserver.auth.User> users = userRecordAccess.getUsers();
		if (users != null) {
			for (org.zakariya.mrdoodleserver.auth.User user : users) {
				long timestamp = userRecordAccess.getUserVisitTimestampSeconds(user.getId());
				result.add(new org.zakariya.mrdoodleserver.sync.transport.User(user.getId(), user.getEmail(), user.getAvatarUrl(), timestamp));
			}
		}
		return result;
	}

	///////////////////////////////////////////////////////////////////

	private String getBasePath() {
		return "/api/" + getApiVersion() + "/dashboard";
	}


}
