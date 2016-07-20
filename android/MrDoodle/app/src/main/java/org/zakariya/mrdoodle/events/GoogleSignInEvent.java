package org.zakariya.mrdoodle.events;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

/**
 * Created by shamyl on 1/2/16.
 */
public class GoogleSignInEvent {

	private GoogleSignInAccount googleSignInAccount;

	public GoogleSignInEvent(GoogleSignInAccount googleSignInAccount) {
		this.googleSignInAccount = googleSignInAccount;
	}

	public GoogleSignInAccount getGoogleSignInAccount() {
		return googleSignInAccount;
	}
}
