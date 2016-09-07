package org.zakariya.mrdoodleserver.auth;

import javax.annotation.Nullable;

/**
 * Interface for JWT token authentication
 */
public interface Authenticator {

	/**
	 * Verifies an auth token, returning the user for said token if the token's valid (or whitelisted), or null if not
	 * @param token an auth token
	 * @return the user's ID if the token is valid, null otherwise
	 */
	@Nullable
	String verify(String token);

	void addToWhitelist(String token);

	void removeFromWhitelist(String token);

	boolean isInWhitelist(String token);

}
