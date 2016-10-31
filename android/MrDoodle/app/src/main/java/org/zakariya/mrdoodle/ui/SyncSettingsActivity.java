package org.zakariya.mrdoodle.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.DrawableRes;
import android.support.annotation.MenuRes;
import android.support.annotation.Nullable;
import android.support.v4.app.NavUtils;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPropertyAnimatorCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.otto.Subscribe;
import com.squareup.picasso.Picasso;

import org.zakariya.mrdoodle.BuildConfig;
import org.zakariya.mrdoodle.R;
import org.zakariya.mrdoodle.events.DoodleDocumentStoreWillBeClearedEvent;
import org.zakariya.mrdoodle.model.DoodleDocument;
import org.zakariya.mrdoodle.net.SyncApi;
import org.zakariya.mrdoodle.net.SyncServerConnection;
import org.zakariya.mrdoodle.net.api.SyncApiService;
import org.zakariya.mrdoodle.net.events.SyncServerConnectionStatusEvent;
import org.zakariya.mrdoodle.net.model.SyncReport;
import org.zakariya.mrdoodle.net.transport.RemoteStatus;
import org.zakariya.mrdoodle.signin.SignInManager;
import org.zakariya.mrdoodle.signin.SignInTechnique;
import org.zakariya.mrdoodle.signin.events.SignInEvent;
import org.zakariya.mrdoodle.signin.events.SignOutEvent;
import org.zakariya.mrdoodle.signin.model.SignInAccount;
import org.zakariya.mrdoodle.sync.SyncManager;
import org.zakariya.mrdoodle.sync.model.SyncLogEntry;
import org.zakariya.mrdoodle.util.BusProvider;
import org.zakariya.mrdoodle.util.Debouncer;
import org.zakariya.mrdoodle.util.RecyclerItemClickListener;

import java.text.DateFormat;
import java.util.concurrent.Callable;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import icepick.Icepick;
import icepick.State;
import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmResults;
import retrofit2.Response;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

import static org.zakariya.mrdoodle.R.string.sync_server_connection_status_connected;

/**
 * Created by shamyl on 1/2/16.
 */
public class SyncSettingsActivity extends BaseActivity {

	private static final String TAG = "SyncSettingsActivity";
	private static final int RC_GET_SIGN_IN = 1;
	private static final int RC_AUTH_UNAVAILABLE = 2;
	private static final long ANIMATION_DURATION_MILLIS = 250;


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

	@Bind(R.id.disconnectedMessageBox)
	View disconnectedMessageBox;

	@Bind(R.id.disconnectedTextView)
	TextView disconnectedTextView;

	@Bind(R.id.disconnectedExplanationTextView)
	TextView disconnectedExplanationTextView;

	@State
	boolean connected;

	private MenuItem toggleServerConnectionMenuItem;
	private MenuItem getRemoteStatusMenuItem;
	private MenuItem syncNowMenuItem;
	private MenuItem resetAndSyncMenuItem;
	private MenuItem signOutMenuItem;
	private MenuItem connectionStatusMenuItem;

	private Realm realm;
	private SyncLogAdapter syncLogAdapter;
	private Subscription syncSubscription;
	private Debouncer<Pair<Boolean, String>> disconnectionMessageDebouncer;

	public static Intent getIntent(Context context) {
		return new Intent(context, SyncSettingsActivity.class);
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (savedInstanceState != null) {
			Icepick.restoreInstanceState(this, savedInstanceState);
		}

		setContentView(R.layout.activity_sync_settings);
		ButterKnife.bind(this);
		BusProvider.getMainThreadBus().register(this);

		setSupportActionBar(toolbar);
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

		// only show userId in debug builds - users don't need to see this :)
		userIdTextView.setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);

		// hide disconnection message for now
		disconnectedMessageBox.setVisibility(View.GONE);

		// to minimize flicker during reconnect attempts, we will use a debouncer. the first
		// value, the boolean, is whether to show the message, the second is the error message
		disconnectionMessageDebouncer = new Debouncer<>(500, new Action1<Pair<Boolean, String>>() {
			@Override
			public void call(Pair<Boolean, String> payload) {
				disconnectedMessageBox.setVisibility(payload.first ? View.VISIBLE : View.GONE);
				if (!TextUtils.isEmpty(payload.second)) {
					disconnectedExplanationTextView.setVisibility(View.VISIBLE);
					disconnectedExplanationTextView.setText(payload.second);
				} else {
					disconnectedExplanationTextView.setVisibility(View.GONE);
				}
			}
		});

	}

	@Override
	protected void onDestroy() {
		if (syncSubscription != null && !syncSubscription.isUnsubscribed()) {
			syncSubscription.unsubscribe();
		}

		if (disconnectionMessageDebouncer != null) {
			disconnectionMessageDebouncer.destroy();
		}

		BusProvider.getMainThreadBus().unregister(this);
		syncLogAdapter.onDestroy();
		realm.close();
		super.onDestroy();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		Icepick.saveInstanceState(this, outState);
		super.onSaveInstanceState(outState);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();

		@MenuRes int menuId = BuildConfig.DEBUG ? R.menu.menu_sync_debug : R.menu.menu_sync;
		inflater.inflate(menuId, menu);

		signOutMenuItem = menu.findItem(R.id.menuItemSignOut);
		connectionStatusMenuItem = menu.findItem(R.id.menuItemConnectionStatus);
		toggleServerConnectionMenuItem = menu.findItem(R.id.menuItemToggleServerConnection);
		getRemoteStatusMenuItem = menu.findItem(R.id.menuItemGetSyncServerStatus);
		syncNowMenuItem = menu.findItem(R.id.menuItemSyncNow);
		resetAndSyncMenuItem = menu.findItem(R.id.menuItemResetAndSync);

		showCurrentSignedInState(false);
		showCurrentServerConnectionState(false);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

			case R.id.menuItemConnectionStatus:
				showCurrentServerConnectionState(true);
				return true;

			case R.id.menuItemSignOut:
				querySignOut();
				return true;

			case R.id.menuItemToggleServerConnection:
				if (isConnectedToServer()) {
					disconnect();
				} else {
					connect();
				}
				return true;

			case R.id.menuItemGetSyncServerStatus:
				remoteStatus();
				return true;

			case R.id.menuItemShowModelOverview:
				showModelOverview();
				return true;

			case R.id.menuItemSyncNow:
				syncNow();
				return true;

			case R.id.menuItemResetAndSync:
				queryResetAndSync();
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

		switch (requestCode) {
			case RC_GET_SIGN_IN: {
				if (resultCode == RESULT_OK) {
					SignInManager signInManager = SignInManager.getInstance();
					SignInTechnique technique = signInManager.getSignInTechnique();
					technique.handleSignInIntentResult(data);
				}
				break;
			}
			case RC_AUTH_UNAVAILABLE: {
				Log.d(TAG, "onActivityResult() called with: requestCode = [" + requestCode + "], resultCode = [" + resultCode + "], data = [" + data + "]");
				break;
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		showCurrentSignedInState(false);
		showCurrentServerConnectionState(false);
	}

	@OnClick(R.id.signInButton)
	void signIn() {
		SignInManager signInManager = SignInManager.getInstance();
		SignInTechnique technique = signInManager.getSignInTechnique();
		if (technique.checkAvailability(this, RC_AUTH_UNAVAILABLE)) {
			if (technique.requiresSignInIntent()) {
				Intent signInIntent = SignInManager.getInstance().getSignInTechnique().getSignInIntent();
				startActivityForResult(signInIntent, RC_GET_SIGN_IN);
			} else {
				technique.signIn();
			}
		}
	}

	void connect() {
		Log.d(TAG, "connect: connecting to sync server");
		SyncManager.getInstance().getSyncServerConnection().connect();
	}

	void disconnect() {
		Log.d(TAG, "disconnect: disconnecting from sync server");
		SyncManager.getInstance().getSyncServerConnection().disconnect();
	}

	void remoteStatus() {
		Log.d(TAG, "remoteStatus: getting remoteStatus from sync server");

		SyncManager syncManager = SyncManager.getInstance();
		SyncApi syncApi = syncManager.getSyncApi();
		final SyncApiService service = syncApi.getSyncApiService();
		final SignInAccount account = SignInManager.getInstance().getAccount();

		if (account != null) {
			Callable<Response<RemoteStatus>> statusCall = new Callable<Response<RemoteStatus>>() {
				@Override
				public Response<RemoteStatus> call() throws Exception {
					return service.getRemoteStatus(account.getId()).execute();
				}
			};

			syncSubscription = Observable.fromCallable(statusCall)
					.subscribeOn(Schedulers.io())
					.observeOn(AndroidSchedulers.mainThread())
					.subscribe(new Observer<Response<RemoteStatus>>() {
						@Override
						public void onCompleted() {
						}

						@Override
						public void onError(Throwable e) {
							Log.e(TAG, "remoteStatus error: ", e);
						}

						@Override
						public void onNext(Response<RemoteStatus> statusResponse) {
							if (statusResponse.isSuccessful()) {
								Log.d(TAG, "remoteStatus() - remoteStatus: " + statusResponse.body());
							} else {
								Log.e(TAG, "remoteStatus() - failed, code; " + statusResponse.code() + " message: " + statusResponse.message());
							}
						}
					});
		}
	}

	void showModelOverview() {
		startActivity(new Intent(this, ModelOverviewActivity.class));
	}

	void syncNow() {

		SyncManager syncManager = SyncManager.getInstance();
		if (syncManager.isSyncing()) {
			Log.d(TAG, "syncNow: currently syncing, never mind...");
			return;
		}

		if (!syncManager.isConnected()) {
			Log.d(TAG, "syncNow: not connected to server...");
			return;
		}

		syncSubscription = syncManager.sync()
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(new Observer<SyncReport>() {
					@Override
					public void onCompleted() {
					}

					@Override
					public void onError(Throwable e) {
						Log.e(TAG, "sync - onError: ", e);
					}

					@Override
					public void onNext(SyncReport syncReport) {
						Log.d(TAG, "sync - onNext: SUCCESS - syncReport: " + syncReport);
					}
				});
	}

	void queryResetAndSync() {
		new AlertDialog.Builder(this)
				.setTitle(R.string.reset_and_sync_dialog_title)
				.setMessage(R.string.reset_and_sync_dialog_message)
				.setPositiveButton(R.string.reset_and_sync_dialog_positive_action_title, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						resetAndSync();
					}
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	void resetAndSync() {

		SyncManager syncManager = SyncManager.getInstance();
		if (syncManager.isSyncing()) {
			Log.d(TAG, "syncNow: currently syncing, never mind...");
			return;
		}

		if (!syncManager.isConnected()) {
			Log.d(TAG, "resetAndSync: not connected to server...");
			return;
		}

		BusProvider.getMainThreadBus().post(new DoodleDocumentStoreWillBeClearedEvent());

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
				.subscribe(new Observer<SyncReport>() {
					@Override
					public void onCompleted() {
					}

					@Override
					public void onError(Throwable e) {
						Log.e(TAG, "resetAndSync - onError: ", e);
					}

					@Override
					public void onNext(SyncReport syncReport) {
						Log.d(TAG, "resetAndSync - onNext: SUCCESS - syncReport: " + syncReport);
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

	void querySignOut() {
		new AlertDialog.Builder(this)
				.setTitle(R.string.sign_out_dialog_title)
				.setMessage(R.string.sign_out_dialog_message)
				.setPositiveButton(R.string.sign_out_dialog_positive_action_title, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						signOut();
					}
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	void signOut() {
		SignInManager.getInstance().signOut();
	}

	@Subscribe
	public void onSignedIn(SignInEvent event) {
		showCurrentSignedInState(true);
	}

	@Subscribe
	public void onSignedOut(SignOutEvent event) {
		showCurrentSignedInState(true);
		Toast.makeText(this, R.string.signed_out, Toast.LENGTH_SHORT).show();
	}

	@Subscribe
	public void onSyncServerConnectionStatusChanged(SyncServerConnectionStatusEvent event) {

		@DrawableRes int iconRes = 0;

		switch (event.getStatus()) {
			case DISCONNECTED: {
				connected = false;
				iconRes = R.drawable.ic_sync_disconnected;
				showDisconnectionMessage(formatDisconnectionError(event.getError()));
				break;
			}

			case CONNECTED: {
				boolean wasConnected = this.connected;
				connected = true;
				iconRes = R.drawable.ic_sync_connected;
				hideDisconnectionMessage();
				if (!wasConnected) {
					Toast.makeText(SyncSettingsActivity.this, sync_server_connection_status_connected, Toast.LENGTH_SHORT).show();
				}
				break;
			}
		}

		if (connectionStatusMenuItem != null && iconRes != 0) {
			connectionStatusMenuItem.setIcon(iconRes);
		}

		if (toggleServerConnectionMenuItem != null) {
			toggleServerConnectionMenuItem.setTitle(connected ? R.string.sync_menu_disconnect : R.string.sync_menu_connect);
		}

	}

	private boolean isSignedIn() {
		return SignInManager.getInstance().getAccount() != null;
	}

	private void showCurrentSignedInState(boolean animate) {

		SignInAccount account = SignInManager.getInstance().getAccount();
		boolean signedIn = account != null;

		showView(signedInView, signedIn, animate);
		showView(signedOutView, !signedIn, animate);

		if (signedIn) {
			Picasso.with(this).load(account.getPhotoUrl()).into(avatarImageView);
			userEmailTextView.setText(account.getEmail());
			userNameTextView.setText(account.getDisplayName());
			userIdTextView.setText(getString(R.string.user_id_text_view, account.getId()));
		}

		// this can be called from onCreate - and menu isn't available yet
		if (signOutMenuItem != null) {
			signOutMenuItem.setVisible(signedIn);
		}

		// these items are only defined in the debug menu
		if (toggleServerConnectionMenuItem != null) {
			toggleServerConnectionMenuItem.setVisible(signedIn);
			getRemoteStatusMenuItem.setVisible(signedIn);
			syncNowMenuItem.setVisible(signedIn);
			resetAndSyncMenuItem.setVisible(signedIn);
			connectionStatusMenuItem.setVisible(signedIn);
		}
	}

	private void showView(final View v, boolean visible, boolean animate) {
		if (animate) {

			if (visible) {
				v.setVisibility(View.VISIBLE);
			}

			ViewPropertyAnimatorCompat animator = ViewCompat.animate(v)
					.alpha(visible ? 1 : 0)
					.setDuration(ANIMATION_DURATION_MILLIS);

			if (!visible) {
				animator.withEndAction(new Runnable() {
					@Override
					public void run() {
						v.setVisibility(View.GONE);
					}
				});
			}

		} else {
			v.setVisibility(visible ? View.VISIBLE : View.GONE);
		}
	}


	private boolean isConnectedToServer() {
		return connected;
	}

	private void showCurrentServerConnectionState(boolean toastStatus) {
		SyncServerConnection connection = SyncManager.getInstance().getSyncServerConnection();
		onSyncServerConnectionStatusChanged(new SyncServerConnectionStatusEvent(connection));

		if (toastStatus) {
			Toast.makeText(this,
					this.connected
							? sync_server_connection_status_connected
							: R.string.sync_server_connection_status_disconnected,
					Toast.LENGTH_SHORT).show();
		}

	}

	void showSyncLogEntryDetail(SyncLogEntry entry) {
		startActivity(SyncLogEntryDetailActivity.getIntent(this, entry.getUuid()));
	}

	String formatDisconnectionError(@Nullable Exception e) {
		return e != null ? e.toString() : null;
	}

	void showDisconnectionMessage(String message) {
		disconnectionMessageDebouncer.send(new Pair<>(true, message));
	}

	void hideDisconnectionMessage() {
		disconnectionMessageDebouncer.send(new Pair<Boolean, String>(false, null));
	}

	///////////////////////////////////////////////////////////////////


	static class SyncLogAdapter extends RecyclerView.Adapter<SyncLogAdapter.ViewHolder> implements RealmChangeListener<Realm> {

		Realm realm;
		RealmResults<SyncLogEntry> syncLogEntries;
		DateFormat dateFormatter;

		static class ViewHolder extends RecyclerView.ViewHolder {
			@Bind(R.id.syncDateTextView)
			TextView syncDateTextView;

			@Bind(R.id.syncSuccessTextView)
			TextView syncSuccessTextView;

			@Bind(R.id.syncSuccessIndicatorImageView)
			ImageView syncSuccessIndicatorImageView;

			ViewHolder(View itemView) {
				super(itemView);
				ButterKnife.bind(this, itemView);
			}
		}

		SyncLogAdapter(Realm realm) {
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
				holder.syncSuccessIndicatorImageView.setImageResource(R.drawable.ic_sync_success);
			} else {
				holder.syncSuccessTextView.setText(R.string.sync_log_entry_failure);
				holder.syncSuccessIndicatorImageView.setImageResource(R.drawable.ic_sync_failure);
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
