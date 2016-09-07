package org.zakariya.mrdoodle.signin.techniques;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.annotation.Nullable;
import android.util.Log;

import org.zakariya.mrdoodle.signin.AuthenticationTokenReceiver;
import org.zakariya.mrdoodle.signin.SignInTechnique;
import org.zakariya.mrdoodle.signin.events.SignInEvent;
import org.zakariya.mrdoodle.signin.events.SignOutEvent;
import org.zakariya.mrdoodle.signin.model.SignInAccount;
import org.zakariya.mrdoodle.util.BusProvider;

/**
 * Implements a "mock" sign-in, suitable for testing
 */
public class MockSignInTechnique implements SignInTechnique {

	private static final String TAG = "MockSignInTechnique";
	private static final String PREFS_NAME = "MockSignInTechnique";
	private static final String PREF_KEY_SIGNED_IN = "MockSignInTechnique.PREF_KEY_SIGNED_IN";

	SignInAccount account;
	Context context;
	boolean signedIn;
	boolean connected = false;

	public MockSignInTechnique(Context context) {
		this.context = context;

		Uri photoUri = Uri.parse("https://avatars0.githubusercontent.com/u/1743604");
		this.account = new SignInAccount(this,"12345", "Shamyl Zakariya", "shamyl@gmail.com", photoUri);

		SharedPreferences prefs = getPreferences();
		this.signedIn = prefs.getBoolean(PREF_KEY_SIGNED_IN, false);

		connect();
	}

	@Override
	public void connect() {
		if (signedIn && !isConnected()) {
			connected = true;
			BusProvider.postOnMainThread(BusProvider.getBus(), new SignInEvent(getAccount()));
		}
	}

	@Override
	public void disconnect() {
		if (isConnected()) {
			connected = false;
			BusProvider.postOnMainThread(BusProvider.getBus(), new SignOutEvent());
		}
	}

	@Override
	public void signOut() {
		signedIn = false;
		getPreferences().edit().putBoolean(PREF_KEY_SIGNED_IN, signedIn).commit();
		disconnect();
	}

	@Override
	public boolean isConnected() {
		return connected;
	}

	@Nullable
	@Override
	public SignInAccount getAccount() {
		return isConnected() ? account : null;
	}

	@Override
	public void getAuthenticationToken(AuthenticationTokenReceiver receiver) {
		receiver.onAuthenticationTokenAvailable("VALID-MOCK-TOKEN-I-GUESS");
	}

	@Override
	public boolean requiresSignInIntent() {
		return false;
	}

	@Override
	public void signIn() {
		signedIn = true;
		getPreferences().edit().putBoolean(PREF_KEY_SIGNED_IN, signedIn).commit();
		connect();
	}

	@Override
	public Intent getSignInIntent() {
		throw new UnsupportedOperationException("MockSignInTechnique doesn't use a sign-in intent");
	}

	@Override
	public void handleSignInIntentResult(Intent data) {
		throw new UnsupportedOperationException("MockSignInTechnique doesn't use a sign-in intent");
	}

	SharedPreferences getPreferences() {
		return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
	}
}
