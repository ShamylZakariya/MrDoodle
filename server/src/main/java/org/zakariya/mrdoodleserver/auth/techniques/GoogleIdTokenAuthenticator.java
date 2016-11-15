package org.zakariya.mrdoodleserver.auth.techniques;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zakariya.mrdoodleserver.auth.Authenticator;
import org.zakariya.mrdoodleserver.auth.User;
import org.zakariya.mrdoodleserver.auth.Whitelist;

import javax.annotation.Nullable;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;

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
	private static final ObjectMapper mapper = new ObjectMapper();

	private GoogleIdTokenVerifier googleIdTokenVerifier;
	private Whitelist whitelist;
	private Whitelist verifiedTokensWhitelist;
	private Map<String, User> usersByToken = new HashMap<>();
	private Map<String, User> usersByAccountId = new HashMap<>();

	public GoogleIdTokenAuthenticator(String oathClientId, String issuer, @Nullable Whitelist whitelist) {
		checkArgument(oathClientId != null && oathClientId.length() > 0, "oath client id must be non-null & non-empty");

		googleIdTokenVerifier = new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
				.setAudience(Collections.singletonList(oathClientId))
				.setIssuer(issuer)
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

	@Nullable
	public User verify(String token) {
		checkArgument(token != null && token.length() > 0, "token must be non-null & non-empty");

		// check if whitelist verifies this token
		if (isInWhitelist(token)) {
			return getUser(token);
		}

		// if this token was previously valid, and hasn't expired yet, skip the expensive tests
		if (verifiedTokensWhitelist.contains(token)) {
			return getUser(token);
		}

		try {
			// Verify ID Token
			GoogleIdToken idToken = googleIdTokenVerifier.verify(token);
			if (idToken != null) {
				long expirationSeconds = idToken.getPayload().getExpirationTimeSeconds();
				long nowSeconds = (new Date()).getTime() / 1000;
				if (expirationSeconds > nowSeconds) {
					verifiedTokensWhitelist.add(token, expirationSeconds - nowSeconds);
					return recordUser(token, idToken);
				}
			}

			verifiedTokensWhitelist.remove(token);
			return null;

		} catch (GeneralSecurityException | IOException e) {
			logger.error("GoogleIdTokenAuthenticator::verify - unable to parse/verify token", e);
		}

		return null;
	}

	/**
	 * Create a User object from info in the token, without verifying it
	 *
	 * @param token the google JWT auth token string
	 * @return a User object
	 */
	@Nullable
	public User getUser(String token) {

		if (token == null || token.length() == 0) {
			return null;
		}

		if (usersByToken.containsKey(token)) {
			return usersByToken.get(token);
		}

		try {
			GoogleIdToken idToken = GoogleIdToken.parse(jsonFactory, token);
			if (idToken != null) {
				return recordUser(token, idToken);
			}
		} catch (IOException e) {
			logger.error("getUser: Could not parse ID Token", e);
		}
		return null;
	}

	private User recordUser(String token, GoogleIdToken idToken) {

		User user = usersByToken.get(token);
		if (user != null) {
			return user;
		}

		// for some reason, GoogleIdToken doesn't parse the 'picture' element
		// from the payload so we have to do it ourselves

		UserInfoPayload payload = null;
		try {
			// user info is stored in the second chunk, which has to be base64 decoded
			String bits[] = token.split("\\.");
			byte[] userInfoPayload = Base64.getDecoder().decode(bits[1]);
			payload = mapper.reader().forType(UserInfoPayload.class).readValue(userInfoPayload);
		} catch (IOException e) {
			e.printStackTrace();
		}

		String accountId = idToken.getPayload().getSubject();
		String email = idToken.getPayload().getEmail();
		String avatarUrl = payload != null ? payload.picture : null;
		user = new User(accountId, email, avatarUrl);

		usersByToken.put(token, user);
		usersByAccountId.put(user.getAccountId(), user);

		return user;
	}

	@Nullable
	@Override
	public User getUserByAccountId(String accountId) {
		return usersByAccountId.get(accountId);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class UserInfoPayload {
		@JsonProperty
		public String sub;

		@JsonProperty
		public String picture;
	}

}
