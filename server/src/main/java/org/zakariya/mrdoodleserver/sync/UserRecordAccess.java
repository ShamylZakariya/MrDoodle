package org.zakariya.mrdoodleserver.sync;

import org.zakariya.mrdoodleserver.auth.User;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.params.Params;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * UserRecordAccess
 * Provides access to the user record. Record user visits, get list of user ids, get users by id.
 */
public class UserRecordAccess {

	private static final String FIELD_USER_EMAIL = "email";
	private static final String FIELD_USER_AVATAR_URL = "avatarUrl";
	private static final String FIELD_USER_TIMESTAMP_SECONDS = "timestampSeconds";

	private JedisPool jedisPool;
	private String namespace;

	public UserRecordAccess(JedisPool jedisPool, String namespace) {
		this.jedisPool = jedisPool;
		this.namespace = namespace;
	}

	/**
	 * Records a timestamp for the user's visit. This is called in SyncRouter
	 * @param user a user who has just made an API call to the service
	 */
	public void recordUserVisit(User user) {
		try (Jedis jedis = jedisPool.getResource()) {
			jedis.sadd(getUserSetJedisKey(), user.getId());

			String hashKey = getUserInfoHashJedisKey(user.getId());
			Transaction transaction = jedis.multi();
			transaction.hset(hashKey, FIELD_USER_EMAIL, safe(user.getEmail()));
			transaction.hset(hashKey, FIELD_USER_AVATAR_URL, safe(user.getAvatarUrl()));
			transaction.hset(hashKey, FIELD_USER_TIMESTAMP_SECONDS, Long.toString(getTimestampSeconds()));
			transaction.exec();
		}
	}

	/**
	 * @return a set of the ids of all users who have used this service
	 */
	public Set<String> getUserIds() {
		try (Jedis jedis = jedisPool.getResource()) {
			return jedis.smembers(getUserSetJedisKey());
		}
	}

	public boolean isUser(String userId) {
		try (Jedis jedis = jedisPool.getResource()) {
			return jedis.sismember(getUserSetJedisKey(), userId);
		}
	}

	/**
	 * @return the number of users who have used this service
	 */
	public long getUserCount() {
		try (Jedis jedis = jedisPool.getResource()) {
			return jedis.scard(getUserSetJedisKey());
		}
	}

	/**
	 * @return a set of all users who have used this service
	 */
	public Set<User> getUsers() {
		return getUserIds().stream().map(this::getUser).collect(Collectors.toSet());
	}

	/**
	 * Instead of a list of all users who have used this service, this will return a slice into that set.
	 * @param page the page index
	 * @param countPerPage the number of users per page
	 * @return a list of users, sorted by id
	 */
	public List<User> getUsers(int page, int countPerPage) {
		List<String> sortedUserIds = new ArrayList<>(getUserIds());
		Collections.sort(sortedUserIds);
		int start = page * countPerPage;
		int end = start + countPerPage;
		if (start < sortedUserIds.size()) {
			end = Math.min(end, sortedUserIds.size());
			List<String> userIds = sortedUserIds.subList(start, end);
			return userIds.stream().map(this::getUser).collect(Collectors.toList());
		} else {
			return Collections.emptyList();
		}
	}

	/**
	 * Get info on a specific user by id
	 * @param userId the id of the user
	 * @return the User with the given id, or null
	 */
	@Nullable
	public User getUser(String userId) {
		try (Jedis jedis = jedisPool.getResource()) {
			String hashKey = getUserInfoHashJedisKey(userId);

			Transaction transaction = jedis.multi();
			Response<String> email = transaction.hget(hashKey, FIELD_USER_EMAIL);
			Response<String> avatarUrl = transaction.hget(hashKey, FIELD_USER_AVATAR_URL);
			Response<String> timestampSeconds = transaction.hget(hashKey, FIELD_USER_TIMESTAMP_SECONDS);
			transaction.exec();

			if (email.get() != null && email.get().length() > 0) {
				long timestamp;
				try {
					timestamp = Long.parseLong(timestampSeconds.get());
				} catch(NumberFormatException e) {
					timestamp = 0;
				}

				return new User(userId, email.get(), avatarUrl.get(), timestamp);
			}
		}
		return null;
	}

	/**
	 * Get a timestamp in seconds for the last time a given user used this service
	 * @param userId the id of the user
	 * @return the timestamp in seconds, or 0 if the user doesn't exist, or hasn't connected
	 */
	public long getUserVisitTimestampSeconds(String userId) {
		try (Jedis jedis = jedisPool.getResource()) {
			String hashKey = getUserInfoHashJedisKey(userId);
			String timestampSeconds = jedis.hget(hashKey, FIELD_USER_TIMESTAMP_SECONDS);
			try {
				return Long.parseLong(timestampSeconds);
			} catch (NumberFormatException nfe) {
				return 0;
			}
		}
	}

	private String getBaseJedisKey() {
		return namespace + "/";
	}

	private String getUserSetJedisKey() {
		return getBaseJedisKey() + "users";
	}

	private String getUserInfoHashJedisKey(String userId) {
		return getBaseJedisKey() + userId + "/info";
	}

	private String safe(String string) {
		return string != null && !string.isEmpty() ? string : "";
	}

	private long getTimestampSeconds() {
		return (new Date()).getTime() / 1000;
	}
}
