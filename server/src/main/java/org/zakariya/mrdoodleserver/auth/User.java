package org.zakariya.mrdoodleserver.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.annotation.Nullable;

/**
 * Simple POJO representing a user of the sync service
 */
public class User {

	@JsonProperty
	private String accountId;

	@JsonProperty
	private String email;

	@JsonProperty
	private String avatarUrl;

	@JsonProperty
	private long lastAccessTimestampSeconds;

	public User(String accountId, String email, @Nullable String avatarUrl) {
		this.accountId = accountId;
		this.email = email;
		this.avatarUrl = avatarUrl;
		this.lastAccessTimestampSeconds = -1;
	}

	public User(String accountId, String email, @Nullable String avatarUrl, long lastAccessTimestampSeconds) {
		this.accountId = accountId;
		this.email = email;
		this.avatarUrl = avatarUrl;
		this.lastAccessTimestampSeconds = lastAccessTimestampSeconds;
	}

	public String getAccountId() {
		return accountId;
	}

	public String getEmail() {
		return email;
	}

	@Nullable
	public String getAvatarUrl() {
		return avatarUrl;
	}

	public long getLastAccessTimestampSeconds() {
		return lastAccessTimestampSeconds;
	}

	@Override
	public String toString() {
		return "[User id: " + accountId + " email: " + email + " avatarUrl: " + avatarUrl + "]";
	}
}
