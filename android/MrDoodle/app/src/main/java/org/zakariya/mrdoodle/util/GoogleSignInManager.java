package org.zakariya.mrdoodle.util;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.OptionalPendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import org.zakariya.mrdoodle.R;
import org.zakariya.mrdoodle.events.GoogleSignInEvent;
import org.zakariya.mrdoodle.events.GoogleSignOutEvent;

/**
 * Created by shamyl on 1/2/16.
 */
public class GoogleSignInManager implements GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks {

	private static final String TAG = "GoogleSignInManager";

	private static GoogleSignInManager instance;

	private Context context;
	private GoogleApiClient googleApiClient;
	private GoogleSignInAccount googleSignInAccount;
	private boolean connected;

	public static void init(Context context) {
		instance = new GoogleSignInManager(context);
	}

	public static GoogleSignInManager getInstance() {
		return instance;
	}

	private GoogleSignInManager(Context context) {
		this.context = context;

		String serverClientId = context.getString(R.string.oauth_server_client_id);
		GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
				.requestIdToken(serverClientId)
				.requestEmail()
				.build();

		// Build GoogleAPIClient with the Google Sign-In API and the above options.
		googleApiClient = new GoogleApiClient.Builder(getContext())
				.addOnConnectionFailedListener(this)
				.addConnectionCallbacks(this)
				.addApi(Auth.GOOGLE_SIGN_IN_API, gso)
				.build();

		connected = false;
		connect();
	}

	public Context getContext() {
		return context;
	}

	public GoogleApiClient getGoogleApiClient() {
		return googleApiClient;
	}

	public boolean isConnected() {
		return connected;
	}

	/**
	 * Connect the googleApiClient instance. This should be called when application is resuming
	 */
	public void connect() {
		if (!connected) {
			Log.i(TAG, "connect: ");
			googleApiClient.connect();
		}
	}

	/**
	 * Disconnect the googleApiClient instance. This should be called when application is backgrounded.
	 */
	public void disconnect() {
		if (connected) {
			Log.i(TAG, "disconnect: ");
			googleApiClient.disconnect();
			connected = false;
		}
	}

	@Nullable
	public GoogleSignInAccount getGoogleSignInAccount() {
		return googleSignInAccount;
	}

	public void setGoogleSignInResult(@Nullable GoogleSignInResult googleSignInResult) {
		if (googleSignInResult != null) {
			if (googleSignInResult.isSuccess()) {
				googleSignInAccount = googleSignInResult.getSignInAccount();
				if (googleSignInAccount != null) {
					Log.i(TAG, "setGoogleSignInResult: SIGNED IN to: " + googleSignInAccount.getEmail());
					BusProvider.postOnMainThread(BusProvider.getBus(), new GoogleSignInEvent(googleSignInAccount));
				} else {
					Log.w(TAG, "setGoogleSignInResult: SIGNED IN, but no account data????");
				}
			} else {
				googleSignInAccount = null;
				BusProvider.postOnMainThread(BusProvider.getBus(), new GoogleSignOutEvent());
			}
		} else {
			googleSignInAccount = null;
			BusProvider.postOnMainThread(BusProvider.getBus(), new GoogleSignOutEvent());
		}
	}

	private void attemptSilentSignIn() {
		OptionalPendingResult<GoogleSignInResult> pendingResult = Auth.GoogleSignInApi.silentSignIn(googleApiClient);

		if (pendingResult.isDone()) {
			// There's immediate result available.
			Log.i(TAG, "attemptSilentSignIn: pendingResult isDone");
			setGoogleSignInResult(pendingResult.get());
		} else {
			// we have to wait
			Log.i(TAG, "attemptSilentSignIn: pendingResult is NOT done, waiting...");

			pendingResult.setResultCallback(new ResultCallback<GoogleSignInResult>() {
				@Override
				public void onResult(@NonNull GoogleSignInResult result) {
					setGoogleSignInResult(result);
				}
			});
		}
	}

	public void signOut() {
		Auth.GoogleSignInApi.signOut(googleApiClient).setResultCallback(
				new ResultCallback<Status>() {
					@Override
					public void onResult(@NonNull Status status) {
						setGoogleSignInResult(null);
					}
				});
	}

	@Override
	public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
		Log.e(TAG, "onConnectionFailed: result:" + connectionResult);
	}

	@Override
	public void onConnected(@Nullable Bundle bundle) {
		Log.i(TAG, "onConnected: ");

		// if possible, sign in immediately
		connected = true;
		attemptSilentSignIn();
	}

	@Override
	public void onConnectionSuspended(int i) {
		connected = false;

		// TODO: Attempt reconnect, and broadcast that GoogleSignIn has been lost

		switch (i) {
			case CAUSE_NETWORK_LOST:
				Log.w(TAG, "onConnectionSuspended: CAUSE_NETWORK_LOST ");
				break;
			case CAUSE_SERVICE_DISCONNECTED:
				Log.w(TAG, "onConnectionSuspended: CAUSE_SERVICE_DISCONNECTED ");
				break;
		}
	}
}
