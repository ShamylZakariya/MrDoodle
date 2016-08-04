package org.zakariya.mrdoodleserver.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import org.eclipse.jetty.websocket.common.util.TextUtil;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Date;

import static org.zakariya.mrdoodleserver.util.Preconditions.*;


/**
 * Authenticator
 * Authenticator simply wraps GoogleIdTokenVerifier, and a whitelist. When a token is offered for
 * verification, it will be checked first against the whitelist
 */
public class Authenticator {

	private static final HttpTransport transport = new NetHttpTransport();
	private static final JsonFactory jsonFactory = new JacksonFactory();
	private GoogleIdTokenVerifier googleIdTokenVerifier;
	private Whitelist whitelist;
	private Whitelist verifiedTokensWhitelist;

	public Authenticator(String oathClientId, Whitelist whitelist) {
		checkArgument(oathClientId != null && oathClientId.length() > 0, "oath client id must be non-null & non-empty");

		googleIdTokenVerifier = new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
				.setAudience(Collections.singletonList(oathClientId))
				.setIssuer("https://accounts.google.com")
				.build();

		this.whitelist = whitelist;
		this.verifiedTokensWhitelist = new Whitelist(0);
	}

	public Whitelist getWhitelist() {
		return whitelist;
	}

	public boolean verify(String token) {
		checkArgument(token != null && token.length() > 0, "token must be non-null & non-empty");

		// check if whitelist verifies this token
		if (getWhitelist() != null && getWhitelist().isInWhitelist(token)) {
			return true;
		}

		// if this token was previously valid, and hasn't expired yet, skip the expensive tests
		if (verifiedTokensWhitelist.isInWhitelist(token)) {
			return true;
		}

		try {
			// Verify ID Token
			GoogleIdToken idToken = googleIdTokenVerifier.verify(token);
			if (idToken != null) {
				long expirationSeconds = idToken.getPayload().getExpirationTimeSeconds();
				long nowSeconds = (new Date()).getTime() / 1000;
				verifiedTokensWhitelist.addTokenToWhitelist(token, expirationSeconds - nowSeconds);
				return true;
			} else {
				verifiedTokensWhitelist.removeTokenFromWhitelist(token);
				return false;
			}

		} catch (GeneralSecurityException e) {
			System.out.println("verifyIdToken:GeneralSecurityException: " + e);
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("verifyIdToken:IOException: " + e);
			e.printStackTrace();
		}
		return false;
	}

}
