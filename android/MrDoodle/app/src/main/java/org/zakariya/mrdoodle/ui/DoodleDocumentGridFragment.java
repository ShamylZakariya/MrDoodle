package org.zakariya.mrdoodle.ui;

import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
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
import org.zakariya.mrdoodle.sync.events.LockStateChangedEvent;
import org.zakariya.mrdoodle.util.BusProvider;
import org.zakariya.mrdoodle.util.DoodleShareHelper;
import org.zakariya.mrdoodle.util.RecyclerItemClickListener;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import icepick.Icepick;
import icepick.State;
import io.realm.Realm;

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
		realm.close();
		super.onDestroy();
	}

	@Override
	public void onResume() {
		super.onResume();
		BusProvider.getMainThreadBus().register(this);
		adapter.updateItems();
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
			DoodleDocument document = DoodleDocument.byUUID(realm, selectedDocumentUuid);
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

		recyclerView.addItemDecoration(new DividerItemDecoration(
				getResources().getDimension(R.dimen.doodle_grid_item_border_width),
				ContextCompat.getColor(getContext(), R.color.doodleGridThumbnailBorder)
		));

		adapter = new DoodleDocumentAdapter(recyclerView, getContext(), columns, getResources().getDimension(R.dimen.doodle_grid_item_thumbnail_padding), emptyView);

		recyclerView.setAdapter(adapter);

		return v;
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
			case REQUEST_EDIT_DOODLE:
				break;
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
		DoodleDocument document = adapter.getDocumentAt(position);
		queryDoodleDocumentAction(document);
	}


	void queryDoodleDocumentAction(final DoodleDocument document) {

		if (bottomSheetDialog != null) {
			bottomSheetDialog.dismiss();
		}

		selectedDocumentUuid = document.getUuid();
		bottomSheetDialog = new BottomSheetBuilder(getActivity())
				.setMode(BottomSheetBuilder.MODE_LIST)
				.setAppBarLayout(appBarLayout)
				.setMenu(R.menu.menu_doodle_action)
				.expandOnStart(true)
				.setIconTintColorResource(R.color.primary)
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

	void deleteDoodleDocument(DoodleDocument doc) {

		View rootView = getView();
		if (rootView == null) {
			throw new IllegalStateException("Called on unattached fragment");
		}

		// hide document from adapter. after snackbar times out we'll delete it, or if canceled, unhide it
		adapter.setDocumentHidden(doc, true);

		Snackbar snackbar = Snackbar.make(rootView, R.string.snackbar_document_deleted, Snackbar.LENGTH_LONG);

		// make text white
		View view = snackbar.getView();
		TextView tv = (TextView) view.findViewById(android.support.design.R.id.snackbar_text);
		tv.setTextColor(Color.WHITE);

		final String docUuid = doc.getUuid();

		snackbar.setCallback(new Snackbar.Callback() {
			@Override
			public void onDismissed(Snackbar snackbar, int event) {
				super.onDismissed(snackbar, event);
				DoodleDocument doc = DoodleDocument.byUUID(realm, docUuid);
				if (doc != null && adapter.isDocumentHidden(doc)) {
					DoodleDocument.delete(getContext(), realm, doc);
					BusProvider.getMainThreadBus().post(new DoodleDocumentWasDeletedEvent(docUuid));
				}
			}
		});

		snackbar.setAction(R.string.snackbar_document_deleted_undo, new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				DoodleDocument doc = DoodleDocument.byUUID(realm, docUuid);
				if (doc != null && adapter.isDocumentHidden(doc)) {
					adapter.setDocumentHidden(doc, false);
				}
			}
		});

		//noinspection deprecation
		snackbar.setActionTextColor(getResources().getColor(R.color.accent));

		snackbar.show();
	}

	void editDoodleDocument(DoodleDocument doc) {
		Intent intent = new Intent(getContext(), DoodleActivity.class);
		intent.putExtra(DoodleActivity.EXTRA_DOODLE_DOCUMENT_UUID, doc.getUuid());
		startActivityForResult(intent, REQUEST_EDIT_DOODLE);
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
		DoodleDocument document = DoodleDocument.byUUID(realm, event.getUuid());
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
		DoodleDocument document = DoodleDocument.byUUID(realm, event.getUuid());
		adapter.onItemUpdated(document);
	}

	@Subscribe
	public void onLockStateChangedEvent(LockStateChangedEvent event) {
		Log.i(TAG, "onLockStateChangedEvent: event: " + event);
	}

	///////////////////////////////////////////////////////////////////

	class DividerItemDecoration extends RecyclerView.ItemDecoration {

		float thickness;
		private Paint paint;

		public DividerItemDecoration(float thickness, int color) {
			this.thickness = thickness;

			paint = new Paint();
			paint.setAntiAlias(true);
			paint.setColor(color);
			paint.setStyle(Paint.Style.FILL);
		}

		@Override
		public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {
			super.onDrawOver(c, parent, state);

			GridLayoutManager glm = (GridLayoutManager) parent.getLayoutManager();
			DoodleDocumentAdapter adapter = (DoodleDocumentAdapter) parent.getAdapter();

			int columns = glm.getSpanCount();
			int rows = (int) Math.ceil((double) adapter.getItemCount() / (double) columns);

			int childCount = parent.getChildCount();
			for (int i = 0; i < childCount; i++) {
				View child = parent.getChildAt(i);
				int adapterPos = parent.getChildAdapterPosition(child);
				int row = (int) Math.floor((double) adapterPos / (double) columns);
				int col = adapterPos % columns;

				float leftBorderWidth = thickness;
				float topBorderWidth = thickness;
				float rightBorderWidth = thickness;
				float bottomBorderWidth = thickness;

				if (row > 0) {
					topBorderWidth *= 0.5;
				}

				if (row < rows - 1) {
					bottomBorderWidth *= 0.5;
				}

				if (col > 0) {
					leftBorderWidth *= 0.5;
				}

				if (col < columns - 1) {
					rightBorderWidth *= 0.5;
				}

				float left = child.getLeft();
				float top = child.getTop();
				float right = child.getRight();
				float bottom = child.getBottom();

				c.drawRect(left, top, right, top + topBorderWidth, paint);
				c.drawRect(left, bottom - bottomBorderWidth, right, bottom, paint);
				c.drawRect(left, top + topBorderWidth, left + leftBorderWidth, bottom - bottomBorderWidth, paint);
				c.drawRect(right - rightBorderWidth, top + topBorderWidth, right, bottom - bottomBorderWidth, paint);
			}
		}
	}

}
