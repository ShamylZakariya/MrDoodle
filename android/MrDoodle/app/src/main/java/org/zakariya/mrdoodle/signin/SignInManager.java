package org.zakariya.mrdoodle.signin;

import android.support.annotation.Nullable;

import org.zakariya.mrdoodle.signin.model.SignInAccount;

/**
 * SignInManager is a singleton which manages the signing in of a user to some authentication service,
 * and provides account info to use to identify the user to the sync service.
 * SignInManager simply provides access to a SignInTechnique which does the actual work (using
 * GoogleSignIn services, for example)
 */
public class SignInManager {

	private static final String TAG = "SignInManager";
	private static SignInManager instance;

	private SignInTechnique technique;

	public static void init(SignInTechnique technique) {
		instance = new SignInManager(technique);
	}

	private SignInManager(SignInTechnique technique) {
		this.technique = technique;
	}

	public static SignInManager getInstance() {
		return instance;
	}

	public SignInTechnique getSignInTechnique() {
		return technique;
	}

	public void connect() {
		technique.connect();
	}

	public void disconnect() {
		technique.disconnect();
	}

	public void signOut() {
		technique.signOut();
	}

	public boolean isConnected() {
		return technique.isConnected();
	}

	@Nullable
	public SignInAccount getAccount() {
		return technique.getAccount();
	}
}
