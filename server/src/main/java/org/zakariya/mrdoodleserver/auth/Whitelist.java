package org.zakariya.mrdoodleserver.auth;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import static org.zakariya.mrdoodleserver.util.Preconditions.*;

/**
 * Whitelist
 * Add a token to the whitelist, and then query if the token is in. If the token is present, and the query is done
 * before the expiration of the defaultGraceperiodSeconds, the token is considered valid.
 * Whitelist exists to handle a corner case where a client is syncing with a token that is just about to expire. By
 * adding to the whitelist, we can say that that token, which *was* valid when sync started, can be considered valid
 * until the syn ends, or the grace period ends.
 * <p>
 * Obviously, this requires a well thought out grace period, like 1 hour.
 */
public class Whitelist {

	private static final int CHECK_COUNT_PER_PRUNE_PASS = 100;

	private long defaultGraceperiodSeconds;
	private HashMap<String, Long> expirationTimestampsByToken = new HashMap<>();
	private int checkCount = 0;

	public Whitelist(long defaultGraceperiodSeconds) {
		this.defaultGraceperiodSeconds = defaultGraceperiodSeconds;
	}

	public long getDefaultGraceperiodSeconds() {
		return defaultGraceperiodSeconds;
	}

	public void setDefaultGraceperiodSeconds(long defaultGraceperiodSeconds) {
		this.defaultGraceperiodSeconds = defaultGraceperiodSeconds;
	}

	/**
	 * Add a token to the white list. The token will be considered in the whitelist until the grace period has expired
	 *
	 * @param token the token to add
	 */
	public void addTokenToWhitelist(String token) {
		addTokenToWhitelist(token, this.getDefaultGraceperiodSeconds());
	}

	public void addTokenToWhitelist(String token, long customGraceperiodSeconds) {
		checkArgument(token != null && token.length() > 0, "token must be non-null & non-empty");

		long expiration = nowSeconds() + defaultGraceperiodSeconds;
		expirationTimestampsByToken.put(token, expiration);
	}

	public void removeTokenFromWhitelist(String token) {
		checkArgument(token != null && token.length() > 0, "token must be non-null & non-empty");
		expirationTimestampsByToken.remove(token);
	}

	/**
	 * Check if the token is in the whitelist, and the grace period has not expired
	 *
	 * @param token the token to check
	 * @return true iff the token has been added to the whitelist and the grace period has not expired
	 */
	public boolean isInWhitelist(String token) {
		checkArgument(token != null && token.length() > 0, "token must be non-null & non-empty");

		// periodically prune the whitelist
		checkCount++;
		if (checkCount > CHECK_COUNT_PER_PRUNE_PASS) {
			checkCount = 0;
			prune();
		}

		if (expirationTimestampsByToken.containsKey(token)) {
			long expiration = expirationTimestampsByToken.get(token);
			if (nowSeconds() > expiration) {
				// token expired, remove it
				expirationTimestampsByToken.remove(token);
				return false;
			} else {
				return true;
			}
		} else {
			return false;
		}
	}

	private static long nowSeconds(){
		return (new Date()).getTime() / 1000;
	}

	/**
	 * Walk the whitelist and remove any expired tokens
	 */
	private void prune() {

		long now = nowSeconds();
		ArrayList<String> expiredTokens = new ArrayList<>();
		for (String token : expirationTimestampsByToken.keySet()) {
			if (expirationTimestampsByToken.get(token) > now) {
				expiredTokens.add(token);
			}
		}

		for (String expiredToken : expiredTokens) {
			expirationTimestampsByToken.remove(expiredToken);
		}
	}

}
