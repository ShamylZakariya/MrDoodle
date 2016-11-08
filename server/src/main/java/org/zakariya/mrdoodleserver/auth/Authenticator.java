package org.zakariya.mrdoodleserver.auth;

import javax.annotation.Nullable;

/**
 * Interface for JWT token authentication
 */
public interface Authenticator {

	/**
	 * Verifies an auth token, returning the user for said token if the token's valid (or whitelisted), or null if not
	 *
	 * @param token an auth token
	 * @return the user represented by the token, if the token is valid, null otherwise
	 */
	@Nullable
	User verify(String token);

	void addToWhitelist(String token);

	void removeFromWhitelist(String token);

	boolean isInWhitelist(String token);

	@Nullable
	User getUser(String token);

	/**
	 * Get the user associated with a given account id
	 * Note: Authenticator implementations are responsible for caching User objects that have been authenticated.
	 * @param accountId the account id
	 * @return the user associated with this account, or null if none exists, or if this user hasn't been authenticated yet
	 */
	@Nullable
	User getUserByAccountId(String accountId);
}
