package org.zakariya.mrdoodle.net;

import org.zakariya.mrdoodle.net.api.StatusApiService;
import org.zakariya.mrdoodle.net.transport.ServiceStatus;

import java.util.concurrent.Callable;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;
import rx.Observable;

/**
 * Created by shamyl on 11/19/16.
 */

public class StatusApi {
	private static final String TAG = StatusApi.class.getSimpleName();

	private StatusApiService statusApiService;

	public StatusApi(StatusApiConfiguration configuration) {
		OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();
		OkHttpClient httpClient = httpClientBuilder.build();

		Retrofit retrofit = new Retrofit.Builder()
				.baseUrl(configuration.getBaseUrl())
				.addConverterFactory(ScalarsConverterFactory.create())
				.addConverterFactory(GsonConverterFactory.create())
				.client(httpClient)
				.build();

		statusApiService = retrofit.create(StatusApiService.class);
	}

	public Observable<ServiceStatus> getServiceStatus() {
		return Observable.fromCallable(new Callable<ServiceStatus>() {
			@Override
			public ServiceStatus call() throws Exception {
				return statusApiService.getServiceStatus().execute().body();
			}
		});
	}
}
