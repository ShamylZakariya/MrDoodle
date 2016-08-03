package org.zakariya.mrdoodle.util;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Base64;
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
import com.google.gson.Gson;

import org.zakariya.mrdoodle.R;
import org.zakariya.mrdoodle.events.GoogleSignInEvent;
import org.zakariya.mrdoodle.events.GoogleSignOutEvent;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by shamyl on 1/2/16.
 */
public class GoogleSignInManager implements GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks {

	public interface GoogleIdTokenReceiver {
		void onIdTokenAvailable(String idToken);

		void onIdTokenError();
	}


	private static final String TAG = "GoogleSignInManager";
	private static GoogleSignInManager instance;

	private Context context;
	private GoogleApiClient googleApiClient;
	private GoogleSignInAccount googleSignInAccount;
	private boolean connected;
	private List<GoogleIdTokenReceiver> idTokenReceivers = new ArrayList<>();
	private Gson gson = new Gson();
	boolean isRenewingConnection;
	int connectionSuspendedCount = 0;
	Handler reconnectHandler = new Handler();

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

	public void requestIdToken(GoogleIdTokenReceiver receiver) {
		if (getGoogleSignInAccount() != null && isIdTokenValid(getGoogleSignInAccount().getIdToken())) {
			receiver.onIdTokenAvailable(getGoogleSignInAccount().getIdToken());
		} else {
			idTokenReceivers.add(receiver);
			renew();
		}
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
				} else {
					Log.w(TAG, "setGoogleSignInResult: SIGNED IN, but no account data????");
				}
			} else {
				googleSignInAccount = null;
			}
		} else {
			googleSignInAccount = null;
		}

		// post no notifications if we're renewing out connection status (to get an updated IdToken)
		if (!isRenewingConnection) {
			if (googleSignInAccount != null) {
				BusProvider.postOnMainThread(BusProvider.getBus(), new GoogleSignInEvent(googleSignInAccount));
			} else {
				BusProvider.postOnMainThread(BusProvider.getBus(), new GoogleSignOutEvent());
			}
		}

		if (googleSignInResult != null && isRenewingConnection) {

			for (GoogleIdTokenReceiver receiver : idTokenReceivers) {
				if (googleSignInAccount != null) {
					receiver.onIdTokenAvailable(googleSignInAccount.getIdToken());
				} else {
					receiver.onIdTokenError();
				}
			}
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

		connected = true;

		// reset the connection suspended count to reset exponential reconnect time backoff
		connectionSuspendedCount = 0;

		// if possible, sign in immediately
		attemptSilentSignIn();
	}

	@Override
	public void onConnectionSuspended(int i) {

		// our connection to the google sign in service has failed.
		// I'm doing exponential back off for reconnect

		connected = false;
		connectionSuspendedCount++;
		long reconnectDelayMillis = 1000 * (long)Math.pow(1.4, connectionSuspendedCount);

		switch (i) {
			case CAUSE_NETWORK_LOST:
				Log.w(TAG, "onConnectionSuspended: CAUSE_NETWORK_LOST - connectionSuspendedCount: " + connectionSuspendedCount + " will attempt reconnect in : " + reconnectDelayMillis/1000 + " seconds...");
				break;
			case CAUSE_SERVICE_DISCONNECTED:
				Log.w(TAG, "onConnectionSuspended: CAUSE_SERVICE_DISCONNECTED - connectionSuspendedCount: " + connectionSuspendedCount + " will attempt reconnect in : " + reconnectDelayMillis/1000 + " seconds...");
				break;
		}

		reconnectHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				connect();
			}
		}, reconnectDelayMillis);
	}

	protected static class JWTSecondComponent {
		String sub;
		long iat;
		long exp;
	}

	/**
	 * Get the expiration time of the specified google id token
	 *
	 * @param idToken the id token (base64 JWT token) provided by google sign in service
	 * @return the time in seconds GMT of the token's expiration, or -1 if the token couldn't be parsed
	 */
	protected long getIdTokenExpirationSeconds(String idToken) {
		if (TextUtils.isEmpty(idToken)) {
			return -1;
		}

		// split token and use the second segment
		String[] components = idToken.split("\\.");
		if (components.length != 3) {
			return -1;
		}

		byte[] componentBytes = Base64.decode(components[1], Base64.DEFAULT);
		String componentString = new String(componentBytes);

		JWTSecondComponent component = gson.fromJson(componentString, JWTSecondComponent.class);
		if (component == null) {
			return -1;
		}

		return component.exp;
	}

	/**
	 * Check if the token is usable
	 *
	 * @param idToken the id token (base64 JWT token) provided by google sign in service
	 * @return true iff the token could be parsed, and its expiration date is greater than TOKEN_EXPIRATION_WINDOW_SECONDS in the future
	 */
	protected boolean isIdTokenValid(String idToken) {
		long nowSeconds = (new Date()).getTime() / 1000;
		long expirationSeconds = getIdTokenExpirationSeconds(idToken);

		if (expirationSeconds < 0) {
			Log.d(TAG, "isIdTokenValid: token was invalid");
			return false;
		}

		long remainingSeconds = expirationSeconds - nowSeconds;
		Log.d(TAG, "isIdTokenValid: token will expire in: " + remainingSeconds + " seconds.");

		return (remainingSeconds > 0);
	}

	protected void renew() {
		isRenewingConnection = true;

		// disconnect and reconnect. When reconnect completes, queued GoogleIdTokenReceiver instances
		// will be provided the updated token they requested
		disconnect();
		connect();
	}

}
