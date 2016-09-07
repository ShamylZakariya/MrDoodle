package org.zakariya.mrdoodle.signin.events;


import org.zakariya.mrdoodle.signin.model.SignInAccount;

/**
 * Created by szakariy on 9/7/16.
 */
public class SignInEvent {

	SignInAccount account;

	public SignInEvent(SignInAccount account) {
		this.account = account;
	}

	public SignInAccount getAccount() {
		return account;
	}
}
