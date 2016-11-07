package org.zakariya.mrdoodleserver.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;

/**
 * Simple POJO representing a user of the sync service
 */
public class User {

	@JsonProperty
	private String id;

	@JsonProperty
	private String email;

	@JsonProperty
	private String avatarUrl;

	@JsonProperty
	private long lastAccessTimestamp;

	public User(String id, String email, @Nullable String avatarUrl) {
		this.id = id;
		this.email = email;
		this.avatarUrl = avatarUrl;
		this.lastAccessTimestamp = -1;
	}

	public User(String id, String email, @Nullable String avatarUrl, long lastAccessTimestamp) {
		this.id = id;
		this.email = email;
		this.avatarUrl = avatarUrl;
		this.lastAccessTimestamp = lastAccessTimestamp;
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

	public long getLastAccessTimestamp() {
		return lastAccessTimestamp;
	}

	@Override
	public String toString() {
		return "[User id: " + id + " email: " + email + " avatarUrl: " + avatarUrl + "]";
	}
}
