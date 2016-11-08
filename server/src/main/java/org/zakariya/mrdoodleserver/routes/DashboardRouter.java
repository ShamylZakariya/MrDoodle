package org.zakariya.mrdoodleserver.routes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zakariya.mrdoodleserver.auth.User;
import org.zakariya.mrdoodleserver.services.WebSocketConnection;
import org.zakariya.mrdoodleserver.sync.UserRecordAccess;
import org.zakariya.mrdoodleserver.transport.UserConnectionInfo;
import org.zakariya.mrdoodleserver.transport.UserPage;
import org.zakariya.mrdoodleserver.util.Configuration;
import redis.clients.jedis.JedisPool;
import spark.Request;
import spark.Response;

import java.util.*;

import static spark.Spark.get;

/**
 * DashboardRouter
 * Establishes the REST routes for the html dashboard to consume
 * TODO: Add some kind of authentication, presumably we'll use Google API OAuth on client side
 */
public class DashboardRouter extends Router {
	private static final Logger logger = LoggerFactory.getLogger(DashboardRouter.class);
	private UserRecordAccess userRecordAccess;
	private static final int USER_PAGE_SIZE = 100;

	public DashboardRouter(Configuration configuration, JedisPool jedisPool) {
		super(configuration, jedisPool);
		this.userRecordAccess = new UserRecordAccess(getJedisPool(), getStoragePrefix());
	}

	@Override
	public Logger getLogger() {
		return logger;
	}

	public void initializeRoutes() {
		String basePath = getBasePath();

		// get list of all users who have used this service - returns User[]
		get(basePath + "/users", this::getUsers, getJsonResponseTransformer());

		get(basePath + "/users/:userId", this::getUserConnectionInfo, getJsonResponseTransformer());
	}

	private UserPage getUsers(Request request, Response response) {
		UserPage userPage = new UserPage();
		userPage.users = new ArrayList<>();

		int page = intQueryParam(request, "page", -1);
		int pageSize = intQueryParam(request, "pageSize", USER_PAGE_SIZE);

		if (page >= 0) {
			userPage.page = page;
			userPage.pageCount = (int) Math.ceil((double)userRecordAccess.getUserCount() / (double)pageSize);
			userPage.users = userRecordAccess.getUsers(userPage.page, pageSize);
		} else {
			userPage.page = 0;
			userPage.pageCount = 0;
			userPage.users = new ArrayList<>(userRecordAccess.getUsers());
			Collections.sort(userPage.users, (o1, o2) -> o1.getId().compareTo(o2.getId()));
		}

		return userPage;
	}

	private UserConnectionInfo getUserConnectionInfo(Request request, Response response) {
		UserConnectionInfo info = new UserConnectionInfo();
		WebSocketConnection connection = WebSocketConnection.getInstance();
		if (connection != null) {
			String userId = request.params("userId");
			if (userId != null) {
				if (userRecordAccess.isUser(userId)) {
					info.connectedDevices = connection.getTotalConnectedDevicesForAccountId(userId);
				} else {
					sendErrorAndHalt(response, 404, "Unrecognized userId: \"" + userId + "\"");
					return null;
				}
			} else {
				sendErrorAndHalt(response, 400, "Missing \"userId\" param");
				return null;
			}
		}

		return info;
	}

	private int intQueryParam(Request request, String param, int fallback) {
		String strValue = request.queryParams(param);
		if (strValue != null && strValue.length() > 0) {
			try {
				return Integer.parseInt(strValue);
			} catch (NumberFormatException ignored) {
			}
		}
		return fallback;
	}

	///////////////////////////////////////////////////////////////////

	private String getBasePath() {
		return "/api/" + getApiVersion() + "/dashboard";
	}


}
