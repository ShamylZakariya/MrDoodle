package org.zakariya.mrdoodle.signin;

import android.support.annotation.Nullable;

/**
 * Interface for receiving authentication tokens
 */
public interface AuthenticationTokenReceiver {

	void onAuthenticationTokenAvailable(String idToken);

	void onAuthenticationTokenError(@Nullable String errorMessage);

}
