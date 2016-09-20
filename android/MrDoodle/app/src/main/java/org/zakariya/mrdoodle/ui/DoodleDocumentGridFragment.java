package org.zakariya.mrdoodle.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.zakariya.mrdoodle.R;
import org.zakariya.mrdoodle.adapters.DoodleDocumentAdapter;
import org.zakariya.mrdoodle.events.DoodleDocumentCreatedEvent;
import org.zakariya.mrdoodle.events.DoodleDocumentEditedEvent;
import org.zakariya.mrdoodle.events.DoodleDocumentWillBeDeletedEvent;
import org.zakariya.mrdoodle.model.DoodleDocument;
import org.zakariya.mrdoodle.util.BusProvider;
import org.zakariya.mrdoodle.util.RecyclerItemClickListener;

import java.lang.ref.WeakReference;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import icepick.Icepick;
import io.realm.Realm;

/**
 * Shows a grid view of doodle documents
 */
public class DoodleDocumentGridFragment extends Fragment implements RecyclerItemClickListener.OnItemClickListener {

	private static final String TAG = DoodleDocumentGridFragment.class.getSimpleName();
	private static final int REQUEST_EDIT_DOODLE = 1;

	@Bind(R.id.recyclerView)
	RecyclerView recyclerView;

	@Bind(R.id.fab)
	FloatingActionButton newDoodleFab;

	@Bind(R.id.emptyView)
	View emptyView;

	Realm realm;
	RecyclerView.LayoutManager layoutManager;
	DoodleDocumentAdapter adapter;

	public DoodleDocumentGridFragment() {
		setHasOptionsMenu(true);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		Log.i(TAG, "onCreate: ");
		super.onCreate(savedInstanceState);
		realm = Realm.getDefaultInstance();
		Icepick.restoreInstanceState(this, savedInstanceState);
		BusProvider.getBus().register(this);
	}

	@Override
	public void onDestroy() {
		Log.i(TAG, "onDestroy: ");
		BusProvider.getBus().unregister(this);
		adapter.onDestroy();
		realm.close();
		super.onDestroy();
	}

	@Override
	public void onResume() {
		Log.i(TAG, "onResume: ");
		super.onResume();
	}

	@Override
	public void onStop() {
		Log.i(TAG, "onStop: ");
		super.onStop();
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		Icepick.saveInstanceState(this, outState);
		super.onSaveInstanceState(outState);
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

		adapter = new DoodleDocumentAdapter(recyclerView, getContext(), columns, emptyView);
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
		DoodleDocument newDoc = DoodleDocument.create(realm, getString(R.string.untitled_document));
		recyclerView.smoothScrollToPosition(0);
		editDoodleDocument(newDoc);
	}

	@Override
	public void onItemClick(View view, int position) {
		DoodleDocument document = adapter.getDocumentAt(position);
		editDoodleDocument(document);
	}

	@Override
	public void onLongItemClick(View view, int position) {
		DoodleDocument document = adapter.getDocumentAt(position);
		queryDeleteDoodleDocument(document);
	}


	void queryDeleteDoodleDocument(final DoodleDocument document) {
		final WeakReference<DoodleDocumentGridFragment> weakThis = new WeakReference<>(this);

		// TODO: Figure out why I get "The Activity's LayoutInflater already has a Factory installed so we can not install AppCompat's" warning here.
		// Using getThemedContext() doesn't fix the problem.
		// Context context = ((AppCompatActivity)getActivity()).getSupportActionBar().getThemedContext();
		Context context = getActivity();

		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setMessage(R.string.dialog_delete_document_message)
				.setNegativeButton(android.R.string.cancel, null)
				.setPositiveButton(R.string.dialog_delete_document_destructive_button_title, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						DoodleDocumentGridFragment strongThis = weakThis.get();
						if (strongThis != null) {
							strongThis.deletePhotoDoodle(document);
						}
					}
				})
				.show();
	}

	void deletePhotoDoodle(DoodleDocument doc) {

		View rootView = getView();
		if (rootView == null) {
			throw new IllegalStateException("Called on unattached fragment");
		}

		// hide document from adapter
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
		Log.i(TAG, "editDoodleDocument: UUID: " + doc.getUuid());
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
	public void onDoodleDocumentWillBeDeletedEvent(DoodleDocumentWillBeDeletedEvent event) {
		Log.i(TAG, "onDoodleDocumentWillBeDeletedEvent: uuid: " + event.getUuid());
		adapter.onItemDeleted(event.getUuid());
	}

	@Subscribe
	public void onDoodleDocumentEditedEvent(DoodleDocumentEditedEvent event) {
		Log.i(TAG, "onDoodleDocumentEditedEvent: uuid: " + event.getUuid());
		DoodleDocument document = DoodleDocument.byUUID(realm, event.getUuid());
		adapter.onItemUpdated(document);
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
