package org.zakariya.mrdoodle.net;

import android.content.Context;

import org.zakariya.mrdoodle.net.api.SyncService;
import org.zakariya.mrdoodle.sync.SyncConfiguration;
import org.zakariya.mrdoodle.util.AsyncExecutor;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by shamyl on 8/23/16.
 */
public class SyncEngine {

	private static final String TAG = SyncEngine.class.getSimpleName();
	private static final String REQUEST_HEADER_AUTH = "Authorization";
	private static final String REQUEST_HEADER_USER_AGENT = "User-Agent";

	private Context context;
	private SyncConfiguration syncConfiguration;
	private OkHttpClient httpClient;
	private Retrofit retrofit;
	private String googleIdToken;
	private SyncService syncService;
	private AsyncExecutor executor;

	public SyncEngine(Context context, SyncConfiguration syncConfiguration) {
		this.context = context;
		this.syncConfiguration = syncConfiguration;
		this.executor = new AsyncExecutor();

		// set up an interceptor to add Authorization headers
		OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();
		httpClientBuilder.addInterceptor(new Interceptor() {
			@Override
			public Response intercept(Chain chain) throws IOException {
				Request original = chain.request();
				Request request = original.newBuilder()
						.header(REQUEST_HEADER_USER_AGENT, "MrDoodle")
						.header(REQUEST_HEADER_AUTH, getGoogleIdToken())
						.build();

				return chain.proceed(request);
			}
		});

		httpClient = httpClientBuilder.build();
		retrofit = new Retrofit.Builder()
				.baseUrl(syncConfiguration.getSyncServiceUrl())
				.addConverterFactory(GsonConverterFactory.create())
				.client(httpClient)
				.build();

		syncService = retrofit.create(SyncService.class);
	}

	public Context getContext() {
		return context;
	}

	public SyncConfiguration getSyncConfiguration() {
		return syncConfiguration;
	}

	public OkHttpClient getHttpClient() {
		return httpClient;
	}

	public Retrofit getRetrofit() {
		return retrofit;
	}

	public SyncService getSyncService() {
		return syncService;
	}

	public String getGoogleIdToken() {
		return googleIdToken;
	}

	public AsyncExecutor getExecutor() {
		return executor;
	}

	/**
	 * Set the google id token for authorization. This is called automatically by SyncManager when the
	 * auth token becomes available, and when it's updated.
	 * @param googleIdToken the google authorization JWT token
	 */
	public void setGoogleIdToken(String googleIdToken) {
		this.googleIdToken = googleIdToken;
	}
}
