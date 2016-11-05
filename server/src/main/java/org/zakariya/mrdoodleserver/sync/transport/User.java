package org.zakariya.mrdoodleserver.sync.transport;

/**
 * Simple User object vended by the DashboardRouter
 */
public class User {

	public String id;
	public String email;
	public String avatarUrl;
	public long lastAccessTimestamp;

	public User(String id, String email, String avatarUrl, long lastAccessTimestamp) {
		this.id = id;
		this.email = email;
		this.avatarUrl = avatarUrl;
		this.lastAccessTimestamp = lastAccessTimestamp;
	}
}
