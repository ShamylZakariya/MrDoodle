package org.zakariya.mrdoodleserver.transport;

import org.zakariya.mrdoodleserver.auth.User;

import java.util.List;

/**
 * Represents one "page" of users for the Dashboard's getUsers route
 */
public class UserPage {

	// this page [0..pageCount)
	public int page;

	// the number of pages
	public int pageCount;

	// the users for this page
	public List<User> users;

}
