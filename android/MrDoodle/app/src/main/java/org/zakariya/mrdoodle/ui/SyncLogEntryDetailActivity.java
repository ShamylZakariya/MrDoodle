package org.zakariya.mrdoodle.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import org.zakariya.mrdoodle.R;
import org.zakariya.mrdoodle.signin.SignInManager;
import org.zakariya.mrdoodle.signin.model.SignInAccount;
import org.zakariya.mrdoodle.sync.model.SyncLogEntry;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import butterknife.Bind;
import butterknife.ButterKnife;
import icepick.Icepick;
import icepick.State;
import io.realm.Realm;

/**
 * Created by shamyl on 9/14/16.
 */
public class SyncLogEntryDetailActivity extends AppCompatActivity {

	public static final String EXTRA_SYNC_LOG_ID = "SyncLogEntryDetailActivity.EXTRA_SYNC_LOG_ID";

	private static final String TAG = SyncLogEntryDetailActivity.class.getSimpleName();

	@Bind(R.id.toolbar)
	Toolbar toolbar;

	@Bind(R.id.dateTextView)
	TextView dateTextView;

	@Bind(R.id.statusTextView)
	TextView statusTextView;

	@Bind(R.id.failureTextView)
	TextView failureTextView;

	@Bind(R.id.logTextView)
	TextView logTextView;

	@State
	String syncLogEntryId;

	Realm realm;
	SyncLogEntry syncLogEntry;

	public static Intent getIntent(Context context, String syncLogEntryId) {
		Intent intent = new Intent(context, SyncLogEntryDetailActivity.class);
		intent.putExtra(EXTRA_SYNC_LOG_ID, syncLogEntryId);
		return intent;
	}


	@SuppressWarnings("TryFinallyCanBeTryWithResources")
	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_sync_log_entry_detail);
		ButterKnife.bind(this);

		setSupportActionBar(toolbar);

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
		}

		if (savedInstanceState == null) {
			syncLogEntryId = getIntent().getStringExtra(EXTRA_SYNC_LOG_ID);
		} else {
			Icepick.restoreInstanceState(this, savedInstanceState);
		}


		realm = Realm.getDefaultInstance();
		if (!TextUtils.isEmpty(syncLogEntryId)) {

			syncLogEntry = SyncLogEntry.get(realm, syncLogEntryId);
			if (syncLogEntry != null) {

				DateFormat dateFormat = DateFormat.getDateInstance();
				DateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
				Date syncDate = syncLogEntry.getDate();
				dateTextView.setText(getString(R.string.sync_log_entry_detail_date,
						dateFormat.format(syncDate),
						timeFormat.format(syncDate)));

				if (TextUtils.isEmpty(syncLogEntry.getFailure())) {
					statusTextView.setText(getString(R.string.sync_log_entry_detail_successful_sync));
					failureTextView.setVisibility(View.GONE);
				} else {
					statusTextView.setText(getString(R.string.sync_log_entry_detail_unsuccessful_sync));
					failureTextView.setText(syncLogEntry.getFailure());
					failureTextView.setVisibility(View.VISIBLE);
				}

				logTextView.setTypeface(Typeface.MONOSPACE);
				logTextView.setText(syncLogEntry.getLog());

			} else {
				throw new IllegalArgumentException("EXTRA_SYNC_LOG_ID must refer to a valid SyncLogEntry");
			}

		} else {
			throw new IllegalArgumentException("EXTRA_SYNC_LOG_ID must be provided");
		}
	}

	@Override
	protected void onDestroy() {
		realm.close();
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_sync_log_entry_detail, menu);
		MenuItem sendItem = menu.findItem(R.id.action_send);

//		if (TextUtils.isEmpty(syncLogEntry.getFailure())) {
//			sendItem.setVisible(false);
//		}

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				finish();
				return true;

			case R.id.action_send:
				sendSyncLog();
				return true;

		}
		return super.onOptionsItemSelected(item);
	}

	private void sendSyncLog() {
		// make an intent and send this
		Intent emailIntent = new Intent(Intent.ACTION_SEND);
		emailIntent.setType("plain/text");
		emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{getString(R.string.sync_log_entry_detail_send_email_address)});

		SignInAccount account = SignInManager.getInstance().getAccount();
		if (account != null) {
			emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.sync_log_entry_detail_send_email_subject_account, account.getEmail(), account.getId()));
		} else {
			emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.sync_log_entry_detail_send_email_subject));
		}


		String versionInfo = null;
		try {
			PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
			versionInfo = info.versionName + ":" + info.versionCode;
		} catch (Exception e) {
			Log.e(TAG, "Unable to extract package info: " + e);
			versionInfo = "Unable to determine MrDoodle version";
		}

		String message = getString(R.string.sync_log_entry_detail_send_email_message,
				versionInfo,
				syncLogEntry.getDate().toString(),
				syncLogEntry.getFailure(),
				syncLogEntry.getLog());

		emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, message);
		try {
			startActivity(emailIntent);
		} catch (Exception e) {
			// Unable to send email on this device...
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.sync_log_entry_detail_unable_to_send_email_title)
					.setMessage(R.string.sync_log_entry_detail_unable_to_send_email_message)
					.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
						}
					})
					.create();

			builder.show();
		}
	}
}
