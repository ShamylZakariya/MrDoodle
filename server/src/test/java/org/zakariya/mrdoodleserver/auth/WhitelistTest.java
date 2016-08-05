package org.zakariya.mrdoodleserver.auth;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
		Thread.sleep(1100);
		assertFalse("Token \"B\" is no longer in white list", whitelist.isInWhitelist("B"));

		// test custom grace periods
		whitelist.clear();
		whitelist.setDefaultGraceperiodSeconds(0);
		whitelist.addTokenToWhitelist("C");
		whitelist.addTokenToWhitelist("D", 1);

		Thread.sleep(100);
		assertFalse("Token \"C\" is no longer in white list", whitelist.isInWhitelist("C"));
		assertTrue("Token \"D\" is still in white list", whitelist.isInWhitelist("D"));

		Thread.sleep(1000);
		assertFalse("Token \"D\" is no longer in white list", whitelist.isInWhitelist("D"));

	}

}