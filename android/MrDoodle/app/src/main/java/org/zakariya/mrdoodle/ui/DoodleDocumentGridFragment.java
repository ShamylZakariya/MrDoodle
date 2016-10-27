package org.zakariya.mrdoodle.ui;

import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.MenuRes;
import android.support.annotation.Nullable;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.github.rubensousa.bottomsheetbuilder.BottomSheetBuilder;
import com.github.rubensousa.bottomsheetbuilder.BottomSheetMenuDialog;
import com.github.rubensousa.bottomsheetbuilder.adapter.BottomSheetItemClickListener;
import com.squareup.otto.Subscribe;

import org.zakariya.mrdoodle.R;
import org.zakariya.mrdoodle.adapters.DoodleDocumentAdapter;
import org.zakariya.mrdoodle.events.DoodleDocumentCreatedEvent;
import org.zakariya.mrdoodle.events.DoodleDocumentEditedEvent;
import org.zakariya.mrdoodle.events.DoodleDocumentWasDeletedEvent;
import org.zakariya.mrdoodle.model.DoodleDocument;
import org.zakariya.mrdoodle.sync.LockState;
import org.zakariya.mrdoodle.sync.SyncManager;
import org.zakariya.mrdoodle.sync.events.LockStateChangedEvent;
import org.zakariya.mrdoodle.ui.itemdecorators.BorderItemDecoration;
import org.zakariya.mrdoodle.util.BusProvider;
import org.zakariya.mrdoodle.util.DoodleShareHelper;
import org.zakariya.mrdoodle.util.RecyclerItemClickListener;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import icepick.Icepick;
import icepick.State;
import io.realm.Realm;

import static android.app.Activity.RESULT_OK;
import static org.zakariya.mrdoodle.ui.DoodleActivity.RESULT_DOODLE_DOCUMENT_UUID;
import static org.zakariya.mrdoodle.ui.DoodleActivity.RESULT_SHOULD_DELETE_DOODLE;

/**
 * Shows a grid view of doodle documents
 */
public class DoodleDocumentGridFragment extends Fragment
		implements RecyclerItemClickListener.OnItemClickListener {

	private static final String TAG = DoodleDocumentGridFragment.class.getSimpleName();
	private static final int REQUEST_EDIT_DOODLE = 1;

	@Bind(R.id.coordinatorLayout)
	CoordinatorLayout coordinatorLayout;

	@Bind(R.id.recyclerView)
	RecyclerView recyclerView;

	@Bind(R.id.fab)
	FloatingActionButton newDoodleFab;

	@Bind(R.id.emptyView)
	View emptyView;

	AppBarLayout appBarLayout;

	Realm realm;
	RecyclerView.LayoutManager layoutManager;
	DoodleDocumentAdapter adapter;

	private BottomSheetMenuDialog bottomSheetDialog;
	private String documentQueuedToDelete;

	@State
	String selectedDocumentUuid;

	public DoodleDocumentGridFragment() {
		setHasOptionsMenu(true);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		realm = Realm.getDefaultInstance();
		Icepick.restoreInstanceState(this, savedInstanceState);
	}

	@Override
	public void onDestroy() {
		if (bottomSheetDialog != null) {
			bottomSheetDialog.dismiss();
		}

		adapter.onDestroy();

		// delete any documents which might have been queued to delete by user
		if (!TextUtils.isEmpty(documentQueuedToDelete)) {
			DoodleDocument doc = DoodleDocument.byUuid(realm, documentQueuedToDelete);
			if (doc != null) {
				DoodleDocument.delete(getContext(), realm, doc);
				BusProvider.getMainThreadBus().post(new DoodleDocumentWasDeletedEvent(documentQueuedToDelete));
			}
		}

		realm.close();
		realm = null;
		super.onDestroy();
	}

	@Override
	public void onResume() {
		super.onResume();
		BusProvider.getMainThreadBus().register(this);
		adapter.reload();
	}

	@Override
	public void onPause() {
		BusProvider.getMainThreadBus().unregister(this);
		super.onPause();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		Icepick.saveInstanceState(this, outState);
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
		super.onViewStateRestored(savedInstanceState);

		// we get the appBarLayout from our activity, so we delay the load until now
		appBarLayout = (AppBarLayout)getActivity().findViewById(R.id.appbar);

		// if user was viewing action sheet for a document when state was destroyed, show action sheet again
		if (!TextUtils.isEmpty(selectedDocumentUuid)) {
			DoodleDocument document = DoodleDocument.byUuid(realm, selectedDocumentUuid);
			selectedDocumentUuid = null;
			if (document != null) {
				queryDoodleDocumentAction(document);
			}
		}
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.menu_doodle_document_grid, menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menuItemSync:
				showSync();
				return true;

			case R.id.menuItemAbout:
				showAbout();
				return true;

			case R.id.menuItemShowModelOverview:
				showModelOverview();
				return true;
		}

		return false;
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.fragment_doodle_document_grid, container, false);
		ButterKnife.bind(this, v);

		// compute a good columns count such that items are no bigger than R.dimen.max_doodle_grid_item_size
		// use display width because recyclerView's width isn't available yet
		DisplayMetrics metrics = getResources().getDisplayMetrics();
		int displayWidth = metrics.widthPixels;
		float maxItemSize = getResources().getDimension(R.dimen.max_doodle_grid_item_size);
		int columns = (int) Math.ceil((float) displayWidth / maxItemSize);


		layoutManager = new GridLayoutManager(getContext(), columns);
		recyclerView.setLayoutManager(layoutManager);
		recyclerView.addOnItemTouchListener(new RecyclerItemClickListener(getContext(), recyclerView, this));

		recyclerView.addItemDecoration(new BorderItemDecoration(
				getResources().getDimension(R.dimen.doodle_grid_item_edge_width),
				ContextCompat.getColor(getContext(), R.color.doodleGridBackgroundColor)
		));

		adapter = new DoodleDocumentAdapter(recyclerView, getContext(), columns, getResources().getDimension(R.dimen.doodle_grid_item_thumbnail_padding), emptyView);
		recyclerView.setAdapter(adapter);

		return v;
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == RESULT_OK) {
			switch (requestCode) {
				case REQUEST_EDIT_DOODLE:
					if (data.getBooleanExtra(RESULT_SHOULD_DELETE_DOODLE, false)) {
						String documentUuid = data.getStringExtra(RESULT_DOODLE_DOCUMENT_UUID);
						deleteDoodleDocument(documentUuid);
					}

					break;
			}
		}

		super.onActivityResult(requestCode, resultCode, data);
	}


	@OnClick(R.id.fab)
	public void createNewPhotoDoodle() {
		DoodleDocument document = DoodleDocument.create(realm, getString(R.string.untitled_document));

		BusProvider.getMainThreadBus().post(new DoodleDocumentCreatedEvent(document.getUuid()));
		recyclerView.smoothScrollToPosition(0);

		editDoodleDocument(document);
	}

	@Override
	public void onItemClick(View view, int position) {
		DoodleDocument document = adapter.getDocumentAt(position);
		editDoodleDocument(document);
	}

	@Override
	public void onLongItemClick(View view, int position) {
		// we only show the sheet if a delete action isn't currently showing the undo snackbar
		if (TextUtils.isEmpty(documentQueuedToDelete)) {
			DoodleDocument document = adapter.getDocumentAt(position);
			queryDoodleDocumentAction(document);
		}
	}


	void queryDoodleDocumentAction(final DoodleDocument document) {

		if (bottomSheetDialog != null) {
			bottomSheetDialog.dismiss();
		}

		SyncManager syncManager = SyncManager.getInstance();
		LockState lockState = syncManager.getLockState();
		boolean isLocked = lockState.isLockedByAnotherDevice(document.getUuid());
		@MenuRes int menuRes = isLocked ? R.menu.menu_action_locked_doodle : R.menu.menu_action_unlocked_doodle;

		selectedDocumentUuid = document.getUuid();
		bottomSheetDialog = new BottomSheetBuilder(getActivity())
				.setMode(BottomSheetBuilder.MODE_LIST)
				.setAppBarLayout(appBarLayout)
				.setMenu(menuRes)
				.expandOnStart(true)
				.setIconTintColorResource(R.color.primaryDark)
				.setItemClickListener(new BottomSheetItemClickListener() {
					@Override
					public void onBottomSheetItemClick(MenuItem item) {
						switch(item.getItemId()) {
							case R.id.doodle_action_share:
								shareDoodleDocument(document);
								break;
							case R.id.doodle_action_delete:
								deleteDoodleDocument(document);
								break;
						}
						selectedDocumentUuid = null;
						newDoodleFab.show();
					}
				})
				.createDialog();

		bottomSheetDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				selectedDocumentUuid = null;
				newDoodleFab.show();
			}
		});

		bottomSheetDialog.show();
		newDoodleFab.hide();
	}

	void shareDoodleDocument(DoodleDocument doc) {
		DoodleShareHelper.share(getActivity(), doc);
	}

	void deleteDoodleDocument(String documentUuid) {
		DoodleDocument doc = DoodleDocument.byUuid(realm, documentUuid);
		if (doc != null) {
			deleteDoodleDocument(doc);
		}
	}

	void deleteDoodleDocument(DoodleDocument doc) {

		View rootView = getView();
		if (rootView == null) {
			throw new IllegalStateException("Called on unattached fragment");
		}

		// hide document from adapter. after snackbar times out we'll delete it, or if canceled, unhide it
		adapter.setDocumentHidden(doc, true);

		Snackbar snackbar = Snackbar.make(rootView, R.string.snackbar_document_deleted, Snackbar.LENGTH_LONG);
		documentQueuedToDelete = doc.getUuid();

		snackbar.setCallback(new Snackbar.Callback() {
			@Override
			public void onDismissed(Snackbar snackbar, int event) {
				super.onDismissed(snackbar, event);
				if (realm != null) {
					DoodleDocument doc = DoodleDocument.byUuid(realm, documentQueuedToDelete);
					if (doc != null && adapter.isDocumentHidden(doc)) {
						DoodleDocument.delete(getContext(), realm, doc);
						BusProvider.getMainThreadBus().post(new DoodleDocumentWasDeletedEvent(documentQueuedToDelete));
					}
				}
				documentQueuedToDelete = null;
			}
		});

		snackbar.setAction(R.string.snackbar_document_deleted_undo, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (realm != null) {
					DoodleDocument doc = DoodleDocument.byUuid(realm, documentQueuedToDelete);
					if (doc != null && adapter.isDocumentHidden(doc)) {
						adapter.setDocumentHidden(doc, false);
					}
				}
			}
		});

		// make text white
		View view = snackbar.getView();
		TextView tv = (TextView) view.findViewById(android.support.design.R.id.snackbar_text);
		tv.setTextColor(ContextCompat.getColor(getContext(), R.color.snackbarText));

		// set color of undo button
		snackbar.setActionTextColor(ContextCompat.getColor(getContext(), R.color.snackbarAction));

		snackbar.show();
	}

	void editDoodleDocument(DoodleDocument doc) {
		startActivityForResult(DoodleActivity.getIntent(getContext(), doc.getUuid()), REQUEST_EDIT_DOODLE);
	}

	void showAbout() {
		startActivity(new Intent(getContext(), AboutActivity.class));
	}

	void showSync() {
		startActivity(new Intent(getContext(), SyncSettingsActivity.class));
	}

	void showModelOverview() {
		startActivity(new Intent(getContext(), ModelOverviewActivity.class));
	}

	///////////////////////////////////////////////////////////////////

	@Subscribe
	public void onDoodleDocumentCreated(DoodleDocumentCreatedEvent event) {
		Log.i(TAG, "onDoodleDocumentCreated: uuid: " + event.getUuid());
		DoodleDocument document = DoodleDocument.byUuid(realm, event.getUuid());
		adapter.onItemAdded(document);
	}

	@Subscribe
	public void onDoodleDocumentWillBeDeletedEvent(DoodleDocumentWasDeletedEvent event) {
		Log.i(TAG, "onDoodleDocumentWillBeDeletedEvent: uuid: " + event.getUuid());
		adapter.onItemDeleted(event.getUuid());
	}

	@Subscribe
	public void onDoodleDocumentEditedEvent(DoodleDocumentEditedEvent event) {
		Log.i(TAG, "onDoodleDocumentEditedEvent: uuid: " + event.getUuid());
		DoodleDocument document = DoodleDocument.byUuid(realm, event.getUuid());
		adapter.onItemUpdated(document);
	}

	@Subscribe
	public void onLockStateChangedEvent(LockStateChangedEvent event) {
		Log.i(TAG, "onLockStateChangedEvent: event: " + event);

		// we need to notify the adapter of the new lock state so it can
		// update the right items

		adapter.setForeignLockState(event.getForeignLocks());
	}

	///////////////////////////////////////////////////////////////////

}
