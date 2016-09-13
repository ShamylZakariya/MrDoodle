package org.zakariya.mrdoodleserver.auth.techniques;

import org.zakariya.mrdoodleserver.auth.Authenticator;
import org.zakariya.mrdoodleserver.auth.Whitelist;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * MockAuthenticator
 * A mock authenticator technique for testing
 */
public class MockAuthenticator implements Authenticator{

	private Map<String,String> tokenToIdMap;
	private Set<String> whitelist = new HashSet<>();

	/**
	 * Create a mock authenticator which will reject any auth request
	 */
	public MockAuthenticator() {
		tokenToIdMap = new HashMap<>();
	}

	/**
	 * Create a mock authenticator which will accept a fake auth token, and return a user id.
	 * @param tokenToIdMap map of fake tokens to fake user ids
	 */
	public MockAuthenticator(Map<String,String> tokenToIdMap) {
		this.tokenToIdMap = new HashMap<>(tokenToIdMap);
	}

	@Nullable
	@Override
	public String verify(String token) {
		return tokenToIdMap.get(token);
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
}
