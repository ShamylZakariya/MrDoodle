package org.zakariya.mrdoodleserver.sync.transport;

/**
 * Created by shamyl on 11/1/16.
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
