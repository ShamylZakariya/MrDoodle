package org.zakariya.mrdoodleserver.integration;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.zakariya.mrdoodleserver.sync.transport.Status;
import static org.junit.Assert.*;

/**
 * Basic tests of the SyncRouter
 */
public class SyncRouterTests extends BaseIntegrationTest {

	@BeforeClass
	public static void setup() {
		// this sets up mock authentication
		startServer("test-server-configuration.json");
	}

	@AfterClass
	public static void teardown() {
		stopServer();
	}

	private String getPath(String accountId) {
		return "/api/v1/sync/" + accountId + "/";
	}

	@Test
	public void testAuthentication() {

		final String token = "VALID-MOCK-TOKEN-I-GUESS";
		final String accountId = "12345";

		TestResponse response = request("GET", getPath(accountId) + "status", header("Authorization", token));
		assertEquals("Authorized request response status should be 200", 200, response.getStatus());

		response = request("GET", getPath(accountId) + "status", header("Authorization", "BAD_TOKEN"));
		assertEquals("Invalidly authorized request response status should be 401", 401, response.getStatus());

		response = request("GET", getPath(accountId) + "status", null);
		assertEquals("non-authorized request response status should be 401", 401, response.getStatus());

		response = request("GET", getPath("345") + "status", header("Authorization", token));
		assertEquals("Authorized request to wrong account response status should be 401", 401, response.getStatus());
	}

}
