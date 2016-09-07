package org.zakariya.mrdoodleserver.auth.techniques;

import org.zakariya.mrdoodleserver.auth.Authenticator;
import org.zakariya.mrdoodleserver.auth.Whitelist;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by szakariy on 9/7/16.
 */
public class MockAuthenticator implements Authenticator{

	private Map<String,String> tokenToIdMap;
	private Set<String> whitelist = new HashSet<>();

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
