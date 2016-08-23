package org.zakariya.mrdoodle.net;

import android.content.Context;
import android.util.Log;

import org.zakariya.mrdoodle.net.api.SyncService;
import org.zakariya.mrdoodle.sync.SyncConfiguration;

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

	private Context context;
	private SyncConfiguration syncConfiguration;
	private OkHttpClient httpClient;
	private Retrofit retrofit;
	private String googleIdToken;
	private SyncService syncService;

	public SyncEngine(Context context, SyncConfiguration syncConfiguration) {
		this.context = context;
		this.syncConfiguration = syncConfiguration;

		OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();
		httpClientBuilder.addInterceptor(new Interceptor() {
			@Override
			public Response intercept(Chain chain) throws IOException {
				Request original = chain.request();

				Log.d(TAG, "intercept: url: " + original.url());

				Request request = original.newBuilder()
						.header("User-Agent", "MrDoodle")
						.header("Authorization", getGoogleIdToken())
						.build();

				return chain.proceed(request);
			}
		});

		Log.i(TAG, "SyncEngine: syncConfiguration.getSyncServiceUrl():" + syncConfiguration.getSyncServiceUrl());
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

	/**
	 * Set the google id token for authorization. This is called automatically by SyncManager when the
	 * auth token becomes available, and when it's updated.
	 * @param googleIdToken the google authorization JWT token
	 */
	public void setGoogleIdToken(String googleIdToken) {
		this.googleIdToken = googleIdToken;
	}
}
