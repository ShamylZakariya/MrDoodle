package org.zakariya.mrdoodleserver.transport;

/**
 * UserStatus
 * Basic info about how many users have used this service, and how many are currently using it
 */
public class UserStatus {

	// total number of users who have used service
	public long totalUsers;

	// total number of users currently using service
	public long totalConnectedUsers;

}
