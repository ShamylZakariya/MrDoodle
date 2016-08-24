package org.zakariya.mrdoodle.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.squareup.otto.Subscribe;
import com.squareup.picasso.Picasso;

import org.zakariya.mrdoodle.R;
import org.zakariya.mrdoodle.events.GoogleSignInEvent;
import org.zakariya.mrdoodle.events.GoogleSignOutEvent;
import org.zakariya.mrdoodle.events.SyncServerConnectionStatusEvent;
import org.zakariya.mrdoodle.net.SyncEngine;
import org.zakariya.mrdoodle.net.SyncServerConnection;
import org.zakariya.mrdoodle.net.api.SyncService;
import org.zakariya.mrdoodle.net.transport.Status;
import org.zakariya.mrdoodle.sync.SyncManager;
import org.zakariya.mrdoodle.util.AsyncExecutor;
import org.zakariya.mrdoodle.util.BusProvider;
import org.zakariya.mrdoodle.util.GoogleSignInManager;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import retrofit2.Response;

/**
 * Created by shamyl on 1/2/16.
 */
public class SyncSettingsActivity extends BaseActivity {

	private static final String TAG = "SyncSettingsActivity";
	private static final int RC_GET_SIGN_IN = 1;

	@Bind(R.id.toolbar)
	Toolbar toolbar;

	@Bind(R.id.signedIn)
	ViewGroup signedInView;

	@Bind(R.id.signedOut)
	ViewGroup signedOutView;

	@Bind(R.id.userEmailTextView)
	TextView userEmailTextView;

	@Bind(R.id.userIdTokenTextView)
	TextView userIdTokenTextView;

	@Bind(R.id.userNameTextView)
	TextView userNameTextView;

	@Bind(R.id.avatarImageView)
	ImageView avatarImageView;

	@Bind(R.id.syncHistoryRecyclerView)
	RecyclerView syncHistoryRecyclerView;

	@Bind(R.id.connectedTextView)
	TextView connectedTextView;

	MenuItem signOutMenuItem;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_sync_settings);
		ButterKnife.bind(this);
		BusProvider.getBus().register(this);

		setSupportActionBar(toolbar);
		syncToCurrentSignedInState();
		syncToCurrentServerConnectionState();

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
		}
	}

	@Override
	protected void onDestroy() {
		BusProvider.getBus().unregister(this);
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu_sync, menu);
		signOutMenuItem = menu.findItem(R.id.menuItemSignOut);
		syncToCurrentSignedInState();
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menuItemSignOut:
				signOut();
				return true;

			case android.R.id.home:
				NavUtils.navigateUpFromSameTask(this);
				return true;

			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == RC_GET_SIGN_IN) {
			GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
			GoogleSignInManager.getInstance().setGoogleSignInResult(result);
		}
	}

	@OnClick(R.id.signInButton)
	void signIn() {
		Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(GoogleSignInManager.getInstance().getGoogleApiClient());
		startActivityForResult(signInIntent, RC_GET_SIGN_IN);
	}

	@OnClick(R.id.connectButton)
	void connect() {
		Log.i(TAG, "connect: connecting to sync server");
		SyncManager.getInstance().getSyncServerConnection().connect();
	}

	@OnClick(R.id.disconnectButton)
	void disconnect() {
		Log.i(TAG, "disconnect: disconnecting from sync server");
		SyncManager.getInstance().getSyncServerConnection().disconnect();
	}

	@OnClick(R.id.statusButton)
	void status() {
		Log.i(TAG, "status: getting status from sync server");

		SyncManager syncManager = SyncManager.getInstance();
		SyncEngine syncEngine = syncManager.getSyncEngine();
		final SyncService service = syncEngine.getSyncService();
		AsyncExecutor executor = syncEngine.getExecutor();
		final GoogleSignInAccount account = GoogleSignInManager.getInstance().getGoogleSignInAccount();

		if (account != null) {
			executor.execute("test", new AsyncExecutor.Job<Response<Status>>() {
				@Override
				public Response<Status> execute() throws Exception {
					return service.getStatus(account.getId()).execute();
				}
			}, new AsyncExecutor.JobListener<Response<Status>>() {
				@Override
				public void onComplete(Response<Status> response) {
					if (response.isSuccessful()) {
						Log.i(TAG, "AsyncExecutor::onResponse: successful : status: " + response.body());
					} else {
						Log.e(TAG, "AsyncExecutor::onResponse: not successful, code; " + response.code() + " message: " + response.message());
					}
				}

				@Override
				public void onError(Throwable error) {
					Log.e(TAG, "AsyncExecutor::onError: ", error);
				}
			});
		}

//		Call<Status> response = service.getStatus(account.getId());
//		response.enqueue(new Callback<Status>() {
//			@Override
//			public void onResponse(Call<Status> call, Response<Status> response) {
//				if (response.isSuccessful()) {
//					Log.i(TAG, "onResponse: successful : status: " + response.body());
//				} else {
//					Log.e(TAG, "onResponse: not successful, code; " + response.code() + " message: " + response.message());
//				}
//			}
//
//			@Override
//			public void onFailure(Call<Status> call, Throwable t) {
//				Log.e(TAG, "onFailure: error: ", t);
//			}
//		});
	}

	@OnClick(R.id.syncNowButton)
	void syncNow() {
	}

	@OnClick(R.id.resetAndSyncButton)
	void resetAndSync() {
	}

	void signOut() {
		GoogleSignInManager.getInstance().signOut();
	}

	@Subscribe
	public void onSignedIn(GoogleSignInEvent event) {
		showSignedInState(event.getGoogleSignInAccount());
	}

	@Subscribe
	public void onSignedOut(GoogleSignOutEvent event) {
		showSignedOutState();
	}

	@Subscribe
	public void onSyncServerConnectionStatusChanged(SyncServerConnectionStatusEvent event) {

		@StringRes int textId = 0;

		switch (event.getStatus()) {
			case DISCONNECTED:
				textId = R.string.sync_server_connection_status_disconnected;
				break;
			case CONNECTING:
				textId = R.string.sync_server_connection_status_connecting;
				break;
			case AUTHORIZING:
				textId = R.string.sync_server_connection_status_authorizing;
				break;
			case CONNECTED:
				textId = R.string.sync_server_connection_status_connected;
				break;
		}

		Log.d(TAG, "onSyncServerConnectionStatusChanged: " + getString(textId));
		connectedTextView.setText(textId);
	}


	private void syncToCurrentSignedInState() {
		GoogleSignInAccount account = GoogleSignInManager.getInstance().getGoogleSignInAccount();
		if (account != null) {
			showSignedInState(account);
		} else {
			showSignedOutState();
		}
	}

	private void syncToCurrentServerConnectionState() {
		SyncServerConnection connection = SyncManager.getInstance().getSyncServerConnection();

		@StringRes int textId = 0;
		if (connection.isConnecting()) {
			textId = R.string.sync_server_connection_status_connecting;
		} else if (connection.isConnected()) {
			if (connection.isAuthenticating()) {
				textId = R.string.sync_server_connection_status_authorizing;
			} else if (connection.isAuthenticated()) {
				textId = R.string.sync_server_connection_status_connected;
			}
		} else {
			textId = R.string.sync_server_connection_status_disconnected;
		}

		if (textId != 0) {
			connectedTextView.setText(textId);
		}
	}

	private void showSignedOutState() {
		signedInView.setVisibility(View.GONE);
		signedOutView.setVisibility(View.VISIBLE);

		if (signOutMenuItem != null) {
			signOutMenuItem.setVisible(false);
		}
	}

	private void showSignedInState(GoogleSignInAccount account) {
		signedInView.setVisibility(View.VISIBLE);
		signedOutView.setVisibility(View.GONE);

		if (signOutMenuItem != null) {
			signOutMenuItem.setVisible(true);
		}

		Picasso.with(this).load(account.getPhotoUrl()).into(avatarImageView);
		userEmailTextView.setText(account.getEmail());
		userNameTextView.setText(account.getDisplayName());
		userIdTokenTextView.setText(account.getIdToken());
	}
}
