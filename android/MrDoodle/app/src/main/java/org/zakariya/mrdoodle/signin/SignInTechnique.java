package org.zakariya.mrdoodle.signin;

import android.content.Intent;
import android.support.annotation.Nullable;

import org.zakariya.mrdoodle.signin.model.SignInAccount;

/**
 * Interface for user sign in techniques.
 */
public interface SignInTechnique {

	/**
	 * Attempt automatic connection to the sign in service provider. This will be called when the app starts or is foregrounded.
	 * If the user has previously signed-in, this should automatically succeed.
	 */
	void connect();

	/**
	 * Disconnect without signing out. This will be called when the app is backgrounded or stopped. It does NOT sign out,
	 * it simply disconnects from the authorization provider. After a disconnect, calls to connect() should succeed
	 */
	void disconnect();

	/**
	 * Formally sign out. After signing out, calls to connect will have to show some kind of authorization UI
	 */
	void signOut();

	/**
	 * @return true if connected and authorized
	 */
	boolean isConnected();

	/**
	 * @return if signed in, return the account, otherwise return null
	 */
	@Nullable
	SignInAccount getAccount();

	/**
	 * Get an authentication token to prove this user sign in is valid to the sync service.
	 * Since this may be an async operation (due to complexities of Google sign in API) this is implemented
	 * as an async operation. The callback will always be invoked on the main thread. And if the
	 * auth token is currently valid, it will be called immediately.
	 * @param receiver an implementation ot AuthenticationTokenReceiver to receive the token, or an error
	 */
	void getAuthenticationToken(AuthenticationTokenReceiver receiver);


	/**
	 * If signing in requires showing a UI, techniques should return true here
	 * @return true if signing in requires a UI
	 */
	boolean requiresSignInIntent();

	void signIn();

	/**
	 * Perform a sign in. This may launch an intent showing some kind of user picker, etc. The result
	 * of this must be piped to handleSignInResult.
	 */
	Intent getSignInIntent();

	/**
	 * If signing in required launching an intent, callers must forward their onActivityResult values here
	 * to handle the sign in results
	 * @param data intent data bearing sign in result info
	 */
	void handleSignInIntentResult(Intent data);



}
