package org.zakariya.mrdoodleserver.auth.techniques;

import org.zakariya.mrdoodleserver.auth.Authenticator;
import org.zakariya.mrdoodleserver.auth.User;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * MockAuthenticator
 * A mock authenticator technique for testing
 */
public class MockAuthenticator implements Authenticator {

	private Map<String, User> tokenToUserMap;
	private Set<String> whitelist = new HashSet<>();

	/**
	 * Create a mock authenticator which will reject any auth request
	 */
	public MockAuthenticator() {
		tokenToUserMap = new HashMap<>();
	}

	/**
	 * Create a mock authenticator which will accept a fake auth token, and return a user id.
	 *
	 * @param tokenToUserMap map of fake tokens to fake user ids
	 */
	public MockAuthenticator(Map<String, User> tokenToUserMap) {
		this.tokenToUserMap = new HashMap<>(tokenToUserMap);
	}

	@Nullable
	@Override
	public User verify(String token) {
		return getUser(token);
	}

	@Override
	public void addToWhitelist(String token) {
		whitelist.add(token);
	}

	@Override
	public void removeFromWhitelist(String token) {
		whitelist.remove(token);
	}

	@Override
	public boolean isInWhitelist(String token) {
		return whitelist.contains(token);
	}

	@Nullable
	@Override
	public User getUser(String token) {
		return tokenToUserMap.get(token);
	}
}
