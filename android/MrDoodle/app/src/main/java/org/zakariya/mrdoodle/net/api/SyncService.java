package org.zakariya.mrdoodle.net.api;

import org.zakariya.mrdoodle.net.transport.Status;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

/**
 * Defines the REST API endpoints for Sync
 */
public interface SyncService {

	/**
	 * Get the status for this account.
	 * @param accountId the account in question
	 * @return status, which describes current timestampHead, and document locks
	 */
	@GET("sync/{accountId}/status")
	Call<Status> getStatus(@Path("accountId") String accountId);

}
