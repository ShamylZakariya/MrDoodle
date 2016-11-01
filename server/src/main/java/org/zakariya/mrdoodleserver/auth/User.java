package org.zakariya.mrdoodleserver.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;

/**
 * Simple POJO representing a user of the sync service
 */
public class User {

	private String id;
	private String email;
	private String avatarUrl;

	public User(String id, String email, @Nullable String avatarUrl) {
		this.id = id;
		this.email = email;
		this.avatarUrl = avatarUrl;
	}

	public String getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	@Nullable
	public String getAvatarUrl() {
		return avatarUrl;
	}

	@Override
	public String toString() {
		return "[User id: " + id + " email: " + email + " avatarUrl: " + avatarUrl + "]";
	}
}
