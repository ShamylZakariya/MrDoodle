package org.zakariya.mrdoodleserver.auth.techniques;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zakariya.mrdoodleserver.auth.Authenticator;
import org.zakariya.mrdoodleserver.auth.Whitelist;

import javax.annotation.Nullable;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.zakariya.mrdoodleserver.util.Preconditions.checkArgument;


/**
 * GoogleIdTokenAuthenticator
 * GoogleIdTokenAuthenticator simply wraps GoogleIdTokenVerifier, and a whitelist. When a token is offered for
 * verification, it will be checked first against the whitelist
 */
public class GoogleIdTokenAuthenticator implements Authenticator {

	private static final Logger logger = LoggerFactory.getLogger(GoogleIdTokenAuthenticator.class);

	private static final HttpTransport transport = new NetHttpTransport();
	private static final JsonFactory jsonFactory = new JacksonFactory();
	private GoogleIdTokenVerifier googleIdTokenVerifier;
	private Whitelist whitelist;
	private Whitelist verifiedTokensWhitelist;
	private Map<String, String> googleIdsByToken = new HashMap<>();

	public GoogleIdTokenAuthenticator(String oathClientId, @Nullable Whitelist whitelist) {
		checkArgument(oathClientId != null && oathClientId.length() > 0, "oath client id must be non-null & non-empty");

		googleIdTokenVerifier = new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
				.setAudience(Collections.singletonList(oathClientId))
				.setIssuer("https://accounts.google.com")
				.build();

		this.whitelist = whitelist;
		this.verifiedTokensWhitelist = new Whitelist(0);
	}

	@Override
	public void addToWhitelist(String token) {
		if (whitelist != null) {
			whitelist.add(token);
		}
	}

	@Override
	public void removeFromWhitelist(String token) {
		if (whitelist != null) {
			whitelist.remove(token);
		}
	}

	@Override
	public boolean isInWhitelist(String token) {
		return whitelist != null && whitelist.contains(token);
	}

	/**
	 * Verifies a google JWT token, returning the google user ID if the token's valid (or whitelisted), or null if not
	 *
	 * @param token a google ID token
	 * @return the google user's ID if the token is valid, null otherwise
	 */
	@Nullable
	public String verify(String token) {
		checkArgument(token != null && token.length() > 0, "token must be non-null & non-empty");

		// check if whitelist verifies this token
		if (isInWhitelist(token)) {
			return getUserId(token);
		}

		// if this token was previously valid, and hasn't expired yet, skip the expensive tests
		if (verifiedTokensWhitelist.contains(token)) {
			return getUserId(token);
		}

		try {
			// Verify ID Token
			GoogleIdToken idToken = googleIdTokenVerifier.verify(token);
			if (idToken != null) {
				long expirationSeconds = idToken.getPayload().getExpirationTimeSeconds();
				long nowSeconds = (new Date()).getTime() / 1000;
				if (expirationSeconds > nowSeconds) {
					verifiedTokensWhitelist.add(token, expirationSeconds - nowSeconds);
					return getUserId(token);
				}
			}

			verifiedTokensWhitelist.remove(token);
			return null;

		} catch (GeneralSecurityException | IOException e) {
			logger.error("Authenticator::verify - unable to verify token", e);
		}

		return null;
	}

	/**
	 * Get the google user ID from a google auth token, without verifying token
	 *
	 * @param token the google jwt auth token string
	 * @return the google id encoded in the token, or null if it couldn't parse
	 */
	@Nullable
	private String getUserId(String token) {

		if (token == null || token.length() == 0) {
			return null;
		}

		if (googleIdsByToken.containsKey(token)) {
			return googleIdsByToken.get(token);
		}

		try {
			GoogleIdToken idToken = GoogleIdToken.parse(jsonFactory, token);
			String id = idToken.getPayload().getSubject();
			googleIdsByToken.put(token, id);
			return id;
		} catch (IOException e) {
			logger.error("IDToken Audience: Could not parse ID Token", e);
			return null;
		}
	}

}
