package org.zakariya.mrdoodle.ui;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.NavUtils;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
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
import org.zakariya.mrdoodle.sync.model.SyncLogEntry;
import org.zakariya.mrdoodle.util.BusProvider;
import org.zakariya.mrdoodle.util.RecyclerItemClickListener;

import java.text.DateFormat;
import java.util.concurrent.Callable;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmResults;
import retrofit2.Response;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

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

	Realm realm;
	SyncLogAdapter syncLogAdapter;
	Subscription syncSubscription;

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

		realm = Realm.getDefaultInstance();
		syncLogAdapter = new SyncLogAdapter(realm);
		syncHistoryRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
		syncHistoryRecyclerView.setAdapter(syncLogAdapter);
		syncHistoryRecyclerView.addOnItemTouchListener(new RecyclerItemClickListener(this, syncHistoryRecyclerView, new RecyclerItemClickListener.OnItemClickListener() {
			@Override
			public void onItemClick(View view, int position) {
				SyncLogEntry entry = syncLogAdapter.getItemAtPosition(position);
				showSyncLogEntryDetail(entry);
			}

			@Override
			public void onLongItemClick(View view, int position) {
			}
		}));
	}

	@Override
	protected void onDestroy() {
		if (syncSubscription != null && !syncSubscription.isUnsubscribed()) {
			syncSubscription.unsubscribe();
		}

		BusProvider.getBus().unregister(this);
		syncLogAdapter.onDestroy();
		realm.close();
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
		final SignInAccount account = SignInManager.getInstance().getAccount();

		if (account != null) {
			Callable<Response<Status>> statusCall = new Callable<Response<Status>>() {
				@Override
				public Response<Status> call() throws Exception {
					return service.getStatus(account.getId()).execute();
				}
			};

			syncSubscription = Observable.fromCallable(statusCall)
					.subscribeOn(Schedulers.io())
					.observeOn(AndroidSchedulers.mainThread())
					.subscribe(new Observer<Response<Status>>() {
						@Override
						public void onCompleted() {
							Log.i(TAG, "onCompleted: ");
						}

						@Override
						public void onError(Throwable e) {
							Log.e(TAG, "status error: ", e);
						}

						@Override
						public void onNext(Response<Status> statusResponse) {
							if (statusResponse.isSuccessful()) {
								Log.i(TAG, "status() - status: " + statusResponse.body());
							} else {
								Log.e(TAG, "status() - failed, code; " + statusResponse.code() + " message: " + statusResponse.message());
							}
						}
					});
		}
	}

	@OnClick(R.id.modelOverviewButton)
	void showModelOverview() {
		Log.i(TAG, "showModelOverview:");
		startActivity(new Intent(this, ModelOverviewActivity.class));
	}

	@OnClick(R.id.syncNowButton)
	void syncNow() {

		SyncManager syncManager = SyncManager.getInstance();
		if (syncManager.isSyncing()) {
			Log.i(TAG, "syncNow: currently syncing, never mind...");
			return;
		}

		syncSubscription = syncManager.sync()
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(new Observer<Status>() {
					@Override
					public void onCompleted() {
					}

					@Override
					public void onError(Throwable e) {
						Log.e(TAG, "sync - onError: ", e);
					}

					@Override
					public void onNext(Status status) {
						Log.i(TAG, "sync - onNext: SUCCESS, I GUESS. Status: " + status);
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

		SyncManager.LocalStoreDeleter deleter = new SyncManager.LocalStoreDeleter() {
			@Override
			public void deleteLocalStore() {
				Realm realm = Realm.getDefaultInstance();
				DoodleDocument.deleteAll(SyncSettingsActivity.this, realm);
				realm.close();
			}
		};

		syncSubscription = syncManager.resetAndSync(deleter)
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(new Observer<Status>() {
					@Override
					public void onCompleted() {
					}

					@Override
					public void onError(Throwable e) {
						Log.e(TAG, "resetAndSync - onError: ", e);
					}

					@Override
					public void onNext(Status status) {
						Log.i(TAG, "resetAndSync - onNext: SUCCESS, I GUESS. Status: " + status);
					}
				});
	}

	@OnClick(R.id.clearSyncHistoryButton)
	void clearSyncHistory() {
		realm.beginTransaction();
		realm.delete(SyncLogEntry.class);
		realm.commitTransaction();
		syncLogAdapter.notifyDataSetChanged();
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

	void showSignedOutState() {
		signedInView.setVisibility(View.GONE);
		signedOutView.setVisibility(View.VISIBLE);

		if (signOutMenuItem != null) {
			signOutMenuItem.setVisible(false);
		}
	}

	void showSignedInState(SignInAccount account) {
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

	void showSyncLogEntryDetail(SyncLogEntry entry) {
		startActivity(SyncLogEntryDetailActivity.getIntent(this, entry.getUuid()));
	}

	static class SyncLogAdapter extends RecyclerView.Adapter<SyncLogAdapter.ViewHolder> implements RealmChangeListener<Realm> {

		Realm realm;
		RealmResults<SyncLogEntry> syncLogEntries;
		DateFormat dateFormatter;

		public static class ViewHolder extends RecyclerView.ViewHolder {
			@Bind(R.id.syncDateTextView)
			TextView syncDateTextView;

			@Bind(R.id.syncSuccessTextView)
			TextView syncSuccessTextView;

			@Bind(R.id.syncSuccessIndicatorImageView)
			ImageView syncSuccessIndicatorImageView;

			public ViewHolder(View itemView) {
				super(itemView);
				ButterKnife.bind(this, itemView);
			}
		}

		public SyncLogAdapter(Realm realm) {
			this.realm = realm;
			syncLogEntries = SyncLogEntry.all(realm);
			dateFormatter = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG);
			realm.addChangeListener(this);
		}

		void onDestroy() {
			realm.removeChangeListener(this);
		}

		@Override
		public void onChange(Realm element) {
			syncLogEntries = SyncLogEntry.all(realm);
			notifyDataSetChanged();
		}

		@Override
		public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
			View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_sync_log_entry, parent, false);
			return new ViewHolder(v);
		}

		@Override
		public void onBindViewHolder(ViewHolder holder, int position) {
			SyncLogEntry entry = getItemAtPosition(position);
			holder.syncDateTextView.setText(dateFormatter.format(entry.getDate()));
			if (entry.getFailure() == null) {
				holder.syncSuccessTextView.setText(R.string.sync_log_entry_success);
				holder.syncSuccessIndicatorImageView.setImageResource(R.drawable.icon_sync_success_black_24dp);
			} else {
				holder.syncSuccessTextView.setText(R.string.sync_log_entry_failure);
				holder.syncSuccessIndicatorImageView.setImageResource(R.drawable.icon_sync_failure_black_24dp);
			}
		}

		SyncLogEntry getItemAtPosition(int position) {
			return syncLogEntries.get(position);
		}

		@Override
		public int getItemCount() {
			return syncLogEntries.size();
		}
	}
}
