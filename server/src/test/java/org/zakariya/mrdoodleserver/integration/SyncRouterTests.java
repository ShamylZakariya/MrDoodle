package org.zakariya.mrdoodleserver.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.zakariya.mrdoodleserver.routes.SyncRouter;
import org.zakariya.mrdoodleserver.sync.TimestampRecord;
import org.zakariya.mrdoodleserver.sync.transport.Status;
import org.zakariya.mrdoodleserver.sync.transport.TimestampRecordEntry;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Basic tests of the SyncRouter
 */
public class SyncRouterTests extends BaseIntegrationTest {

	private static final String AUTH_TOKEN = "VALID-MOCK-TOKEN-I-GUESS";
	private static final String ACCOUNT_ID = "12345";


	@BeforeClass
	public static void setup() {
		// this sets up server with mock authentication and "test" storage prefix
		startServer("test-server-configuration.json");
	}

	@AfterClass
	public static void teardown() {
		stopServer();
	}

	private String getPath() {
		return getPath(ACCOUNT_ID);
	}

	private String getPath(String accountId) {
		return "/api/v1/sync/" + accountId + "/";
	}

	private Map<String, String> authHeader() {
		return header("Authorization", AUTH_TOKEN);
	}

	@Test
	public void testAuthentication() {


		TestResponse response = request("GET", getPath() + "status", header("Authorization", AUTH_TOKEN));
		assertEquals("Authorized request response status should be 200", 200, response.getStatus());

		response = request("GET", getPath() + "status", header("Authorization", "BAD_TOKEN"));
		assertEquals("Invalidly authorized request response status should be 401", 401, response.getStatus());

		response = request("GET", getPath() + "status", null);
		assertEquals("non-authorized request response status should be 401", 401, response.getStatus());

		response = request("GET", getPath("345") + "status", header("Authorization", AUTH_TOKEN));
		assertEquals("Authorized request to wrong account response status should be 401", 401, response.getStatus());
	}

	@Test
	public void testBasicSync() {

		// confirm that we have no changes
		TestResponse response = request("GET", getPath() + "status", authHeader());
		assertEquals("Status response code should be 200", 200, response.getStatus());
		Status status = response.getBody(Status.class);
		assertEquals("TimestampHead should be zero for empty store", 0, status.timestampHeadSeconds);

		// confirm that the change list is empty
		final TypeReference timestampRecordTypeReference = new TypeReference<Map<String, TimestampRecordEntry>>() {
		};

		response = request("GET", getPath() + "changes", authHeader());
		assertEquals("Changes response code should be 200", 200, response.getStatus());
		Map<String, TimestampRecordEntry> changes = response.getBody(timestampRecordTypeReference);
		assertEquals("Changes for empty store should be empty", 0, changes.size());

		final byte[] DATA1 = "How-diddly-ho neighborinos".getBytes();
		final byte[] DATA2 = "Feels like I'm wearing nothing at all...".getBytes();
		final String DATA1_ID = "A";
		final String DATA2_ID = "B";
		final String DATA_TYPE = "text/plain";

		Map<String,String> authHeader = this.authHeader();
		Map<String, String> authorizedWriteSessionHeaders = authHeader();
		authorizedWriteSessionHeaders.put(SyncRouter.REQUEST_HEADER_DOCUMENT_TYPE, DATA_TYPE);

		// confirm uploads are denied without a write token
		response = request("PUT", getPath() + "blob/" + DATA1_ID, authorizedWriteSessionHeaders, new BytePart("blob", DATA1));
		assertEquals("Upload without a write token returns 400", 400, response.getStatus());

		// confirm uploads with invalid write token are rejected
		authorizedWriteSessionHeaders.put(SyncRouter.REQUEST_HEADER_WRITE_TOKEN, "blah, blah blah");
		response = request("PUT", getPath() + "blob/" + DATA1_ID, authorizedWriteSessionHeaders, new BytePart("blob", DATA1));
		assertEquals("Upload with an invalid write token returns 403", 403, response.getStatus());


		// now we're going to start a write session
		response = request("GET", getPath() + "writeSession/start", authHeader);
		assertEquals("Starting a write session response code should be 200", 200, response.getStatus());
		String writeSessionToken = response.getBody();
		assertEquals("We should receive a non-null, non-empty write token", true, writeSessionToken != null && writeSessionToken.length() > 0);
		System.out.println("got writeSessionToken: " + writeSessionToken);

		// now uploading some data should work
		authorizedWriteSessionHeaders.put(SyncRouter.REQUEST_HEADER_WRITE_TOKEN, writeSessionToken);

		response = request("PUT", getPath() + "blob/" + DATA1_ID, authorizedWriteSessionHeaders, new BytePart("blob", DATA1));
		assertEquals("Uploading a blob should have status 200", 200, response.getStatus());
		TimestampRecordEntry timestampEntry = response.getBody(TimestampRecordEntry.class);
		assertEquals("Response timestamp entry modelId should match", DATA1_ID, timestampEntry.getDocumentId());
		assertEquals("Response timestamp entry timestampSeconds should be > 0", true, timestampEntry.getTimestampSeconds() > 0);
		assertEquals("Response timestamp entry modelClass should match", DATA_TYPE, timestampEntry.getDocumentType());
		assertEquals("Response timestamp entry action should be WRITE", TimestampRecord.Action.WRITE.ordinal(), timestampEntry.getAction());

		response = request("PUT", getPath() + "blob/" + DATA2_ID, authorizedWriteSessionHeaders, new BytePart("blob", DATA2));
		assertEquals("Uploading a blob should have status 200", 200, response.getStatus());
		timestampEntry = response.getBody(TimestampRecordEntry.class);
		assertEquals("Response timestamp entry modelId should match", DATA2_ID, timestampEntry.getDocumentId());
		assertEquals("Response timestamp entry timestampSeconds should be > 0", true, timestampEntry.getTimestampSeconds() > 0);
		assertEquals("Response timestamp entry modelClass should match", DATA_TYPE, timestampEntry.getDocumentType());
		assertEquals("Response timestamp entry action should be WRITE", TimestampRecord.Action.WRITE.ordinal(), timestampEntry.getAction());


		// confirm change list is still empty since we haven't committed the write session
		response = request("GET", getPath() + "changes", authHeader);
		changes = response.getBody(timestampRecordTypeReference);
		assertEquals("Changes for empty store should still be empty since the write session hasn't been committed", 0, changes.size());

		// commit an incorrect write session, it should fail
		response = request("DELETE", getPath() + "writeSession/sessions/not-a-valid-write-session-token", authHeader);
		assertEquals("Committing a non-valid write session response code should be 403", 403, response.getStatus());

		// commit the actual write session token
		response = request("DELETE", getPath() + "writeSession/sessions/" + writeSessionToken, authHeader);
		assertEquals("Committing a valid write session response code should be 200", 200, response.getStatus());
		status = response.getBody(Status.class);
		assertEquals("After committing write session, status timestampHeadSeconds should be > 0", true, status.timestampHeadSeconds > 0);

		// now confirm change list has the TWO expected entries, both with action WRITE
		response = request("GET", getPath() + "changes", authHeader);
		changes = response.getBody(timestampRecordTypeReference);
		assertNotNull("Having committed write session, changes should include our data", changes.get(DATA1_ID));
		assertNotNull("Having committed write session, changes should include our data", changes.get(DATA2_ID));
		assertEquals("changes should have DATA1_ID as WRITE action", TimestampRecord.Action.WRITE.ordinal(), changes.get(DATA1_ID).getAction());
		assertEquals("changes should have DATA2_ID as WRITE action", TimestampRecord.Action.WRITE.ordinal(), changes.get(DATA2_ID).getAction());

		//
		// So now we have committed two blobs
		//

		// confirm GET of incorrect blob id returns 404
		response = request("GET", getPath() + "blob/" + "invalid-blob-id", authHeader);
		assertEquals("GET of incorrect blob ID should return status 404", 404, response.getStatus());

		// confirm GET of correct blob IDs gets us the right bytes
		response = request("GET", getPath() + "blob/" + DATA1_ID, authHeader);
		assertEquals("GET of valid DATA1_ID should return status 200", 200, response.getStatus());
		assertEquals("GET of DATA1 data should equal data submitted", new String(DATA1), response.getBody());

		response = request("GET", getPath() + "blob/" + DATA2_ID, authHeader);
		assertEquals("GET of valid DATA2_ID should return status 200", 200, response.getStatus());
		assertEquals("GET of DATA2 data should equal data submitted", new String(DATA2), response.getBody());


		// confirm delete of a blob is denied without auth token
		response = request("DELETE", getPath() + "blob/" + DATA1_ID, authHeader);
		assertEquals("DELETE of DATA1_ID should be denied returns status 400", 400, response.getStatus());

		// start a write session so we can delete
		response = request("GET", getPath() + "writeSession/start", authHeader);
		assertEquals("Starting a write session response code should be 200", 200, response.getStatus());
		writeSessionToken = response.getBody();
		assertEquals("We should receive a non-null, non-empty write token", true, writeSessionToken != null && writeSessionToken.length() > 0);
		System.out.println("got seconds writeSessionToken: " + writeSessionToken);
		authorizedWriteSessionHeaders.put(SyncRouter.REQUEST_HEADER_WRITE_TOKEN, writeSessionToken);

		// confirm delete of invalid blob id returns 404
		response = request("DELETE", getPath() + "blob/" + "invalid-blob-id", authorizedWriteSessionHeaders);
		assertEquals("DELETE of invalid blob ID should return status 404", 404, response.getStatus());

		// confirm delete of valid blob id is success
		response = request("DELETE", getPath() + "blob/" + DATA1_ID, authorizedWriteSessionHeaders);
		assertEquals("DELETE of DATA1_ID should return status 200", 200, response.getStatus());
		TimestampRecordEntry deletionTimestamp = response.getBody(TimestampRecordEntry.class);
		assertEquals("DELETE of DATA1_ID should respond with timestamp record entry with same document id", deletionTimestamp.getDocumentId(), DATA1_ID);
		assertEquals("DELETE of DATA1_ID should respond with timestamp record entry with same document type", deletionTimestamp.getDocumentType(), DATA_TYPE);
		assertEquals("DELETE of DATA1_ID should response with timestamp record entry with DELETE action", deletionTimestamp.getAction(), TimestampRecord.Action.DELETE.ordinal());

		// confirm DATA1 is still in store since session isn't committed
		response = request("GET", getPath() + "blob/" + DATA1_ID, authHeader);
		assertEquals("GET of valid DATA1_ID should return status 200", 200, response.getStatus());
		assertEquals("GET of DATA1 data should equal data submitted", new String(DATA1), response.getBody());

		// commit the write session
		response = request("DELETE", getPath() + "writeSession/sessions/" + writeSessionToken, authHeader);
		assertEquals("Committing a valid write session response code should be 200", 200, response.getStatus());
		status = response.getBody(Status.class);
		assertEquals("After committing write session, status timestampHeadSeconds should be > 0", true, status.timestampHeadSeconds > 0);

		// now a get of DATA1 should 404
		response = request("GET", getPath() + "blob/" + DATA1_ID, authHeader);
		assertEquals("GET of deleted DATA1_ID should return status 404", 404, response.getStatus());

		// get changes, confirm DATA1_ID is DELETE, and DATA2_ID remains WRITE
		response = request("GET", getPath() + "changes", authHeader);
		changes = response.getBody(timestampRecordTypeReference);
		assertNotNull("Having committed write session, changes should include our data", changes.get(DATA1_ID));
		assertNotNull("Having committed write session, changes should include our data", changes.get(DATA2_ID));
		assertEquals("changes should have DATA1_ID as DELETE action", TimestampRecord.Action.DELETE.ordinal(), changes.get(DATA1_ID).getAction());
		assertEquals("changes should have DATA1_ID have correct document type", DATA_TYPE, changes.get(DATA1_ID).getDocumentType());
		assertEquals("changes should have DATA2_ID as WRITE action", TimestampRecord.Action.WRITE.ordinal(), changes.get(DATA2_ID).getAction());
		assertEquals("changes should have DATA2_ID have correct document type", DATA_TYPE, changes.get(DATA2_ID).getDocumentType());

	}

}
