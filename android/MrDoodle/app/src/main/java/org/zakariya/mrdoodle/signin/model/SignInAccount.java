package org.zakariya.mrdoodle.signin.model;

import android.net.Uri;
import android.support.annotation.Nullable;

import org.zakariya.mrdoodle.signin.AuthenticationTokenReceiver;
import org.zakariya.mrdoodle.signin.SignInTechnique;

/**
 * Represents an abstracted user sign in account
 */
public class SignInAccount {

	private SignInTechnique technique;
	private String id;
	private String displayName;
	private String email;
	private Uri photoUrl;

	public SignInAccount(SignInTechnique technique, String id, String displayName, String email, @Nullable Uri photoUrl) {
		this.technique = technique;
		this.id = id;
		this.displayName = displayName;
		this.email = email;
		this.photoUrl = photoUrl;
	}

	/**
	 * @return the stable ID representing this user
	 */
	public String getId() {
		return id;
	}

	/**
	 * To authenticate the user, you need a token. Tokens expire, tokens may need to be
	 * negotiated with the authenticating service. So, to get a token, we implement this
	 * simple async-type callback interface.
	 *
	 * @param receiver is called when the token is available, or if an error occurs
	 */
	public void getAuthenticationToken(AuthenticationTokenReceiver receiver) {
		technique.getAuthenticationToken(receiver);
	}

	/**
	 * @return a display name, e.g., "Jane Doe"
	 */
	public String getDisplayName() {
		return displayName;
	}

	/**
	 * @return the user's email address
	 */
	public String getEmail() {
		return email;
	}

	/**
	 * @return if available, the Uri of an avatar photo
	 */
	@Nullable
	public Uri getPhotoUrl() {
		return photoUrl;
	}
}
