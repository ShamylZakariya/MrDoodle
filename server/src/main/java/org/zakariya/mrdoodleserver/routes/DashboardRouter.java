package org.zakariya.mrdoodleserver.routes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zakariya.mrdoodleserver.auth.Authenticator;
import org.zakariya.mrdoodleserver.auth.User;
import org.zakariya.mrdoodleserver.services.WebSocketConnection;
import org.zakariya.mrdoodleserver.sync.UserRecordAccess;
import org.zakariya.mrdoodleserver.transport.UserConnectionInfo;
import org.zakariya.mrdoodleserver.transport.UserPage;
import org.zakariya.mrdoodleserver.transport.UserStatus;
import org.zakariya.mrdoodleserver.util.Configuration;
import redis.clients.jedis.JedisPool;
import spark.Request;
import spark.Response;

import java.util.*;

import static spark.Spark.before;
import static spark.Spark.get;

/**
 * DashboardRouter
 * Establishes the REST routes for the html dashboard to consume
 * TODO: Add some kind of authentication, presumably we'll use Google API OAuth on client side
 */
public class DashboardRouter extends Router {
	private static final String REQUEST_HEADER_AUTH = "Authorization";

	private static final Logger logger = LoggerFactory.getLogger(DashboardRouter.class);
	private Authenticator authenticator;
	private UserRecordAccess userRecordAccess;
	private Set<String> userEmailWhitelist;
	private static final int USER_PAGE_SIZE = 100;

	public DashboardRouter(JedisPool jedisPool, String storagePrefix, String apiVersion, Authenticator authenticator, List<String> userEmailWhitelist) {
		super(jedisPool, storagePrefix, apiVersion);
		this.authenticator = authenticator;
		this.userRecordAccess = new UserRecordAccess(getJedisPool(), getStoragePrefix());
		this.userEmailWhitelist = userEmailWhitelist != null ? new HashSet<>(userEmailWhitelist) : Collections.emptySet();
	}

	@Override
	public Logger getLogger() {
		return logger;
	}

	public void initializeRoutes() {
		String basePath = getBasePath();

		before(basePath + "/*", this::authenticate);

		// get list of all users who have used this service - returns UserPage
		get(basePath + "/users", this::getUsers, getJsonResponseTransformer());

		// get info on specific user, returns UserConnectionInfo
		get(basePath + "/users/:userId", this::getUserConnectionInfo, getJsonResponseTransformer());

		// get general info on user count, connected count, etc
		get(basePath + "/userStatus", this::getUserStatus, getJsonResponseTransformer());
	}

	private void authenticate(Request request, Response response) {
		String authToken = request.headers(REQUEST_HEADER_AUTH);
		if (authToken == null || authToken.isEmpty()) {
			sendErrorAndHalt(response, 401, "SyncRouter::authenticate - Missing authorization token");
		} else {
			User user;

			try {
				user = authenticator.verify(authToken);
				if (user == null) {
					sendErrorAndHalt(response, 500, "SyncRouter::authenticate - Unable to parse authorization token");
					return;
				}

				String email = user.getEmail();
				if (email == null) {
					sendErrorAndHalt(response, 401, "SyncRouter::authenticate - No user email available from auth token, can't verify if user is in whitelist");
					return;
				}

				if (!userEmailWhitelist.contains(email)) {
					sendErrorAndHalt(response, 401, "SyncRouter::authenticate - Unauthorized dashboard user: \"" + email + "\"");
				}

			} catch (Exception e) {
				sendErrorAndHalt(response, 500, "SyncRouter::authenticate - Unable to verify authorization token, error: " + e.getLocalizedMessage(), e);
			}
		}
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
			Collections.sort(userPage.users, (o1, o2) -> o1.getAccountId().compareTo(o2.getAccountId()));
		}

		return userPage;
	}

	private UserStatus getUserStatus(Request request, Response response) {
		UserStatus status = new UserStatus();
		status.totalUsers = (int)userRecordAccess.getUserCount();
		status.totalConnectedUsers = WebSocketConnection.getInstance().getConnectedAccountIds().size();
		status.totalConnectedDevices = WebSocketConnection.getInstance().getTotalConnectedDeviceCount();
		return status;
	}

	private UserConnectionInfo getUserConnectionInfo(Request request, Response response) {

		String userId = request.params("userId");
		if (userId == null || userId.length() == 0) {
			sendErrorAndHalt(response, 400, "Missing \"userId\" param");
			return null;
		}

		User user = userRecordAccess.getUser(userId);
		if (user == null) {
			sendErrorAndHalt(response, 404, "Unrecognized userId: \"" + userId + "\"");
			return null;
		}

		UserConnectionInfo info = new UserConnectionInfo();
		info.user = user;

		WebSocketConnection connection = WebSocketConnection.getInstance();
		if (connection != null) {
			info.connectedDevices = connection.getTotalConnectedDevicesForAccountId(userId);
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
