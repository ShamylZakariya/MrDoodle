package org.zakariya.mrdoodle.net.api;

import org.zakariya.mrdoodle.net.transport.ServiceStatus;

import retrofit2.Call;
import retrofit2.http.GET;

/**
 * Created by shamyl on 11/19/16.
 */

public interface StatusApiService {

	@GET("status.json")
	Call<ServiceStatus> getServiceStatus();

}
