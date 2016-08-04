package org.zakariya.mrdoodleserver.auth;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by shamyl on 8/4/16.
 */
public class WhitelistTest {


	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {

	}

	@Test
	public void whitelistTests() throws Exception {

		Whitelist whitelist = new Whitelist(1);
		whitelist.addTokenToWhitelist("A");
		whitelist.addTokenToWhitelist("B");

		assertTrue("Token \"A\" is in white list", whitelist.isInWhitelist("A"));
		assertTrue("Token \"B\" is in white list", whitelist.isInWhitelist("B"));

		whitelist.removeTokenFromWhitelist("A");
		assertFalse("Token \"A\" is no longer in white list", whitelist.isInWhitelist("A"));
		assertTrue("Token \"B\" is still in white list", whitelist.isInWhitelist("B"));

		// wait for the whitelist expiration to invalidate "B"
		Thread.sleep(1500);
		assertFalse("Token \"B\" is no longer in white list", whitelist.isInWhitelist("B"));

	}

}