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

import com.squareup.otto.Subscribe;
import com.squareup.picasso.Picasso;

import org.zakariya.mrdoodle.R;
import org.zakariya.mrdoodle.events.SyncServerConnectionStatusEvent;
import org.zakariya.mrdoodle.model.DoodleDocument;
import org.zakariya.mrdoodle.net.SyncEngine;
import org.zakariya.mrdoodle.net.SyncServerConnection;
import org.zakariya.mrdoodle.net.api.SyncService;
import org.zakariya.mrdoodle.net.transport.Status;
import org.zakariya.mrdoodle.signin.SignInManager;
import org.zakariya.mrdoodle.signin.SignInTechnique;
import org.zakariya.mrdoodle.signin.events.SignInEvent;
import org.zakariya.mrdoodle.signin.events.SignOutEvent;
import org.zakariya.mrdoodle.signin.model.SignInAccount;
import org.zakariya.mrdoodle.sync.SyncManager;
import org.zakariya.mrdoodle.util.AsyncExecutor;
import org.zakariya.mrdoodle.util.BusProvider;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.realm.Realm;
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

	@Bind(R.id.userIdTextView)
	TextView userIdTextView;

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
			SignInManager signInManager = SignInManager.getInstance();
			SignInTechnique technique = signInManager.getSignInTechnique();
			technique.handleSignInIntentResult(data);
		}
	}

	@OnClick(R.id.signInButton)
	void signIn() {
		SignInManager signInManager = SignInManager.getInstance();
		SignInTechnique technique = signInManager.getSignInTechnique();
		if (technique.requiresSignInIntent()) {
			Intent signInIntent = SignInManager.getInstance().getSignInTechnique().getSignInIntent();
			startActivityForResult(signInIntent, RC_GET_SIGN_IN);
		} else {
			technique.signIn();
		}
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
		AsyncExecutor executor = syncManager.getExecutor();
		final SignInAccount account = SignInManager.getInstance().getAccount();

		if (account != null) {
			executor.execute("getStatus", new AsyncExecutor.Job<Response<Status>>() {
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
	}

	@OnClick(R.id.syncNowButton)
	void syncNow() {

		SyncManager syncManager = SyncManager.getInstance();
		if (syncManager.isSyncing()) {
			Log.i(TAG, "syncNow: currently syncing, never mind...");
			return;
		}


		syncManager.getExecutor().execute("sync", new AsyncExecutor.Job<Void>() {
			@Override
			public Void execute() throws Exception {
				SyncManager.getInstance().sync();
				return null;
			}
		}, new AsyncExecutor.JobListener<Void>() {
			@Override
			public void onComplete(Void result) {
				Log.i(TAG, "syncNow - onComplete: SUCCESS, I GUESS");
			}

			@Override
			public void onError(Throwable error) {
				Log.e(TAG, "syncNow - onError: ", error);
			}
		});
	}

	@OnClick(R.id.resetAndSyncButton)
	void resetAndSync() {
		SyncManager syncManager = SyncManager.getInstance();
		if (syncManager.isSyncing()) {
			Log.i(TAG, "syncNow: currently syncing, never mind...");
			return;
		}


		syncManager.getExecutor().execute("resetAndSync", new AsyncExecutor.Job<Void>() {
			@Override
			public Void execute() throws Exception {

				SyncManager.getInstance().resetAndSync(new SyncManager.LocalStoreDeleter() {
					@Override
					public void deleteLocalStore() {
						Realm realm = Realm.getDefaultInstance();
						realm.delete(DoodleDocument.class);
						realm.close();
					}
				});

				return null;
			}
		}, new AsyncExecutor.JobListener<Void>() {
			@Override
			public void onComplete(Void result) {
				Log.i(TAG, "syncNow - onComplete: SUCCESS, I GUESS");
			}

			@Override
			public void onError(Throwable error) {
				Log.e(TAG, "syncNow - onError: ", error);
			}
		});

	}

	void signOut() {
		SignInManager.getInstance().signOut();
	}

	@Subscribe
	public void onSignedIn(SignInEvent event) {
		showSignedInState(event.getAccount());
	}

	@Subscribe
	public void onSignedOut(SignOutEvent event) {
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
		SignInAccount account = SignInManager.getInstance().getAccount();
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

	private void showSignedInState(SignInAccount account) {
		signedInView.setVisibility(View.VISIBLE);
		signedOutView.setVisibility(View.GONE);

		if (signOutMenuItem != null) {
			signOutMenuItem.setVisible(true);
		}

		Picasso.with(this).load(account.getPhotoUrl()).into(avatarImageView);
		userEmailTextView.setText(account.getEmail());
		userNameTextView.setText(account.getDisplayName());
		userIdTextView.setText(account.getId());
	}
}
