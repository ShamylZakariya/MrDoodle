package org.zakariya.mrdoodle.ui;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.v4.app.NavUtils;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.ColorUtils;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPropertyAnimatorCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.otto.Subscribe;

import org.zakariya.doodle.model.Brush;
import org.zakariya.doodle.model.StrokeDoodle;
import org.zakariya.doodle.view.DoodleView;
import org.zakariya.flyoutmenu.FlyoutMenuView;
import org.zakariya.mrdoodle.R;
import org.zakariya.mrdoodle.events.DoodleDocumentEditedEvent;
import org.zakariya.mrdoodle.model.DoodleDocument;
import org.zakariya.mrdoodle.net.events.SyncServerConnectionStatusEvent;
import org.zakariya.mrdoodle.net.transport.RemoteLockStatus;
import org.zakariya.mrdoodle.sync.SyncManager;
import org.zakariya.mrdoodle.sync.events.LockStateChangedEvent;
import org.zakariya.mrdoodle.util.BusProvider;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import icepick.Icepick;
import icepick.State;
import io.realm.Realm;
import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class DoodleActivity extends BaseActivity implements DoodleView.SizeListener {

	private static final String TAG = "DoodleActivity";

	public static final String EXTRA_DOODLE_DOCUMENT_UUID = "DoodleActivity.EXTRA_DOODLE_DOCUMENT_UUID";
	public static final String RESULT_DID_EDIT_DOODLE = "DoodleActivity.RESULT_DID_EDIT_DOODLE";
	public static final String RESULT_DOODLE_DOCUMENT_UUID = "DoodleActivity.RESULT_DOODLE_DOCUMENT_UUID";

	private static final String STATE_DOODLE = "DoodleActivity.STATE_DOODLE";

	private static final int ANIMATION_DURATION_MILLIS = 300;
	private static final float DEFAULT_ZOOM_LEVEL = 1;
	private static final boolean DEBUG_DRAW_DOODLE = false;


	@ColorInt
	private static final int TOOL_MENU_FILL_COLOR = 0xFF303030;

	private static final float ALPHA_CHECKER_SIZE_DP = 8;

	private static final float BRUSH_SCALE = 32f;

	@Bind(R.id.titleEditText)
	EditText titleEditText;

	@Bind(R.id.toolbar)
	Toolbar toolbar;

	@Bind(R.id.doodleView)
	DoodleView doodleView;

	@Bind(R.id.toolSelectorFlyoutMenu)
	FlyoutMenuView toolSelectorFlyoutMenu;

	@Bind(R.id.paletteFlyoutMenu)
	FlyoutMenuView paletteFlyoutMenu;

	@Bind(R.id.lockIconImageView)
	ImageView lockIconImageView;

	@State
	int brushColor = 0xFF000000;

	@State
	float brushSize = 0;

	@State
	boolean brushIsEraser;

	@State
	String documentUuid;

	@State
	long documentModificationTime = 0;

	@State
	int paletteFlyoutMenuSelectionId;

	@State
	int toolFlyoutMenuSelectionId;

	@State
	boolean firstDisplayOfDoodle = true;

	Realm realm;
	Subscription lockSubscription;

	DoodleDocument document;
	StrokeDoodle doodle;

	// when true, the document is in a read-only state and can't be edited
	boolean readOnly;

	// when true, it means the user wants write access
	boolean wantDocumentWriteLock;


	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		BusProvider.getMainThreadBus().register(this);

		setContentView(R.layout.activity_doodle);
		ButterKnife.bind(this);

		//
		// setup the toolbar - note: we are providing our own titleTextView so we'll hide the built-in title later
		//

		setSupportActionBar(toolbar);
		ActionBar actionBar = getSupportActionBar();

		//
		//  Load the DoodleDocument
		//

		realm = Realm.getDefaultInstance();
		if (savedInstanceState == null) {
			documentUuid = getIntent().getStringExtra(EXTRA_DOODLE_DOCUMENT_UUID);

			if (!TextUtils.isEmpty(documentUuid)) {

				document = DoodleDocument.byUUID(realm, documentUuid);
				if (document == null) {
					throw new IllegalArgumentException("Document UUID didn't refer to an existing DoodleDocument");
				}

				documentModificationTime = document.getModificationDate().getTime();
			}
		} else {
			Icepick.restoreInstanceState(this, savedInstanceState);
			if (!TextUtils.isEmpty(documentUuid)) {
				document = DoodleDocument.byUUID(realm, documentUuid);
				if (document == null) {
					throw new IllegalArgumentException("Document UUID didn't refer to an existing DoodleDocument");
				}
			}
		}

		if (document != null) {

			// show the up button
			if (actionBar != null) {
				actionBar.setDisplayHomeAsUpEnabled(true);
			}

			// hide default title
			if (actionBar != null) {
				actionBar.setDisplayShowTitleEnabled(false);
			}

			// update title
			titleEditText.setText(document.getName());
			titleEditText.addTextChangedListener(new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {
				}

				@Override
				public void afterTextChanged(Editable s) {
					setDocumentName(s.toString());
				}
			});

			titleEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
				@Override
				public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
					if (actionId == EditorInfo.IME_ACTION_DONE) {
						v.clearFocus();
						goFullscreen();
						return true;
					} else {
						return false;
					}
				}
			});

			titleEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
				@Override
				public void onFocusChange(View v, boolean hasFocus) {
					if (!hasFocus) {
						hideKeyboard(v);
						goFullscreen();
					}
				}
			});
		} else {

			// we don't have a document, this means we're running in a test harness where
			// DoodleActivity was launched directly.
			titleEditText.setVisibility(View.GONE);
		}

		//
		//  Create the PhotoDoodle. If this is result of a state restoration
		//  load from the saved instance state, otherwise, load from the saved document.
		//

		if (savedInstanceState != null) {
			doodle = new StrokeDoodle(this);

			Bundle doodleState = savedInstanceState.getBundle(STATE_DOODLE);
			if (doodleState != null) {
				doodle.onCreate(doodleState);
			}
		} else {
			if (document != null) {
				doodle = document.loadDoodle(this);
			} else {
				// DoodleActivity was launched directly for testing, create a blank document
				doodle = new StrokeDoodle(this);
			}
		}

		doodle.setBackgroundColor(ContextCompat.getColor(this, R.color.doodleBackground));

		doodleView.setDoodle(doodle);
		doodleView.addSizeListener(this);

		updateBrush();

		// build our menus
		configureToolFlyoutMenu();
		configurePaletteFlyoutMenu();

		toolSelectorFlyoutMenu.setSelectedMenuItemById(toolFlyoutMenuSelectionId);
		paletteFlyoutMenu.setSelectedMenuItemById(paletteFlyoutMenuSelectionId);

		if (savedInstanceState == null) {
			doodle.setCanvasScale(DEFAULT_ZOOM_LEVEL);
		}

		doodle.setCoordinateGridSize(dp2px(100));
		if (DEBUG_DRAW_DOODLE) {
			doodle.setDrawCoordinateGrid(true);
			doodle.setDrawInvalidationRect(true);
			doodle.setDrawViewport(true);
			doodle.setDrawCanvasContentBoundingRect(true);
		}

		doodle.setTwoFingerTapListener(new StrokeDoodle.TwoFingerTapListener() {
			@Override
			public void onTwoFingerTap(int tapCount) {
				if (tapCount > 1) {
					fitDoodleCanvasContents();
				}
			}
		});

		// hide controls, they'll be shown or hidden appropriately after onResume
		// then the document is locked (or not)
		animateVisibility(lockIconImageView, false, false);
		animateVisibility(toolSelectorFlyoutMenu, false, false);
		animateVisibility(paletteFlyoutMenu, false, false);

		lockIconImageView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showLockedDocumentExplanation();
			}
		});
	}

	@Override
	protected void onDestroy() {
		doodleView.removeSizeListener(this);
		BusProvider.getMainThreadBus().unregister(this);

		if (lockSubscription != null && !lockSubscription.isUnsubscribed()) {
			lockSubscription.unsubscribe();
		}

		realm.close();
		super.onDestroy();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		Icepick.saveInstanceState(this, outState);

		Bundle doodleState = new Bundle();
		doodle.onSaveInstanceState(doodleState);
		outState.putBundle(STATE_DOODLE, doodleState);

		super.onSaveInstanceState(outState);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu_doodle, menu);
		return true;
	}

	@Override
	protected void onResume() {
		requestDocumentWriteLock();
		super.onResume();
	}

	@Override
	protected void onPause() {
		releaseDocumentWriteLock();
		saveDoodleIfEdited();
		super.onPause();
	}

	@Override
	public void onBackPressed() {

		if (titleEditText.hasFocus()) {
			titleEditText.clearFocus();
			return;
		}

		saveAndSetActivityResult();
		super.onBackPressed();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menuItemCenterCanvasContent:
				doodle.centerCanvasContent();
				return true;

			case R.id.menuItemFitCanvasContent:
				fitDoodleCanvasContents();
				return true;

			case R.id.menuItemUndo:
				doodle.undo();
				return true;

			case R.id.menuItemClear:
				doodle.clear();
				return true;

			case android.R.id.home:
				saveAndSetActivityResult();
				NavUtils.navigateUpFromSameTask(this);
				return true;

			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			goFullscreen();
		}
	}

	public void fitDoodleCanvasContents() {
		doodle.fitCanvasContent(getResources().getDimensionPixelSize(R.dimen.doodle_fit_canvas_contents_padding));
	}

	/**
	 * If the doodle is dirty (edits were made) saves it to its file, and returns true.
	 *
	 * @return true if the doodle had edits and needed to be saved
	 */
	public boolean saveDoodleIfEdited() {
		if (document == null) {
			return false;
		}

		if (doodle.isDirty()) {
			realm.beginTransaction();
			document.saveDoodle(this, doodle);
			realm.commitTransaction();
			doodle.setDirty(false);

			// mark that the document was modified
			markDocumentModified();

			return true;
		} else {
			return false;
		}
	}

	boolean isReadOnly() {
		return readOnly;
	}

	void setReadOnly(boolean readOnly, boolean animate) {
		Log.i(TAG, "setReadOnly() called with: readOnly = [" + readOnly + "]");

		this.readOnly = readOnly;
		doodleView.setReadOnly(this.readOnly);

		animateVisibility(toolSelectorFlyoutMenu, !readOnly, animate);
		animateVisibility(paletteFlyoutMenu, !readOnly, animate);
		animateVisibility(lockIconImageView, readOnly, animate);
	}

	void animateVisibility(final View v, boolean visible, boolean animate) {
		if (animate) {
			if (visible) {
				v.setVisibility(View.VISIBLE);
			}

			float scale = visible ? 1 : 0;
			float alpha = visible ? 1 : 0;

			ViewPropertyAnimatorCompat animator = ViewCompat.animate(v)
					.scaleX(scale)
					.scaleY(scale)
					.alpha(alpha)
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
			if (visible) {
				v.setScaleX(1);
				v.setScaleY(1);
				v.setAlpha(1);
				v.setVisibility(View.VISIBLE);
			} else {
				v.setScaleX(0);
				v.setScaleY(0);
				v.setAlpha(0);
				v.setVisibility(View.GONE);
			}
		}
	}


	private void requestDocumentWriteLock() {

		wantDocumentWriteLock = true;
		SyncManager syncManager = SyncManager.getInstance();

		if (syncManager.isConnected()) {
			Log.i(TAG, "requestDocumentWriteLock: ");

			lockSubscription = SyncManager.getInstance().requestLock(document.getUuid())
					.subscribeOn(Schedulers.io())
					.observeOn(AndroidSchedulers.mainThread())
					.subscribe(new Observer<RemoteLockStatus>() {
						@Override
						public void onCompleted() {
						}

						@Override
						public void onError(Throwable e) {
							Log.e(TAG, "requestDocumentWriteLock - onError: ", e);
						}

						@Override
						public void onNext(RemoteLockStatus remoteLockStatus) {
							setReadOnly(!remoteLockStatus.lockHeldByRequestingDevice, true);
						}
					});
		} else {
			setReadOnly(false, true);
		}
	}

	private void releaseDocumentWriteLock() {

		wantDocumentWriteLock = false;
		SyncManager syncManager = SyncManager.getInstance();
		if (syncManager.isConnected()) {
			Log.i(TAG, "releaseDocumentWriteLock: ");
			lockSubscription = SyncManager.getInstance().releaseLock(document.getUuid())
					.subscribeOn(Schedulers.io())
					.observeOn(AndroidSchedulers.mainThread())
					.subscribe(new Observer<RemoteLockStatus>() {
						@Override
						public void onCompleted() {
						}

						@Override
						public void onError(Throwable e) {
							Log.e(TAG, "releaseDocumentWriteLock - onError: ", e);
						}

						@Override
						public void onNext(RemoteLockStatus remoteLockStatus) {
							setReadOnly(true, true);
						}
					});
		} else {
			setReadOnly(true, true);
		}

	}

	private void markDocumentModified() {
		realm.beginTransaction();
		document.markModified();
		realm.commitTransaction();
		BusProvider.getMainThreadBus().post(new DoodleDocumentEditedEvent(document.getUuid()));
	}

	private void saveAndSetActivityResult() {
		if (document == null) {
			return;
		}

		saveDoodleIfEdited();

		boolean edited = document.getModificationDate().getTime() > documentModificationTime;

		Intent resultData = new Intent();
		resultData.putExtra(RESULT_DID_EDIT_DOODLE, edited);
		resultData.putExtra(RESULT_DOODLE_DOCUMENT_UUID, document.getUuid());
		setResult(RESULT_OK, resultData);
	}

	public void setDocumentName(String documentName) {
		if (!documentName.equals(document.getName())) {
			realm.beginTransaction();
			document.setName(documentName);
			realm.commitTransaction();
			markDocumentModified();
		}
	}


	@SuppressWarnings("unused")
	public String getDocumentName() {
		return document.getName();
	}

	public int getBrushColor() {
		return brushColor;
	}

	public void setBrushColor(int color) {
		this.brushColor = color;
		updateBrush();
	}

	public void setBrushSize(float brushSize) {
		this.brushSize = brushSize;
		updateBrush();
	}

	public float getBrushSize() {
		return this.brushSize;
	}

	public void setBrushIsEraser(boolean isEraser) {
		this.brushIsEraser = isEraser;
		updateBrush();
	}

	public boolean isBrushEraser() {
		return brushIsEraser;
	}

	void hideKeyboard(View view) {
		InputMethodManager manager = (InputMethodManager) view.getContext().getSystemService(INPUT_METHOD_SERVICE);
		if (manager != null) {
			manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
		}
	}

	void goFullscreen() {

		int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
				| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
				| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
				| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

		getWindow().getDecorView().setSystemUiVisibility(flags);
	}

	void updateBrush() {
		doodle.setBrush(new Brush(getBrushColor(), getBrushSize() / 2, getBrushSize(), 600, isBrushEraser()));
	}

	float dp2px(float dp) {
		return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
	}


	private void configureToolFlyoutMenu() {

		float alphaCheckerSize = dp2px(ALPHA_CHECKER_SIZE_DP);


		final int count = 6;
		List<DoodleActivityTools.ToolFlyoutMenuItem> items = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			float size = (float) (i + 1) / (float) count;
			items.add(new DoodleActivityTools.ToolFlyoutMenuItem(items.size(), size, false, alphaCheckerSize, TOOL_MENU_FILL_COLOR));
		}

		for (int i = 0; i < count; i++) {
			float size = (float) (i + 1) / (float) count;
			items.add(new DoodleActivityTools.ToolFlyoutMenuItem(items.size(), size, true, alphaCheckerSize, TOOL_MENU_FILL_COLOR));
		}

		toolSelectorFlyoutMenu.setLayout(new FlyoutMenuView.GridLayout(count, FlyoutMenuView.GridLayout.UNSPECIFIED));
		toolSelectorFlyoutMenu.setAdapter(new FlyoutMenuView.ArrayAdapter<>(items));

		float toolInsetPx = dp2px(0);
		final DoodleActivityTools.ToolFlyoutButtonRenderer toolButtonRenderer = new DoodleActivityTools.ToolFlyoutButtonRenderer(toolInsetPx, 1, false, alphaCheckerSize, TOOL_MENU_FILL_COLOR);
		toolSelectorFlyoutMenu.setButtonRenderer(toolButtonRenderer);

		toolSelectorFlyoutMenu.setSelectionListener(new FlyoutMenuView.SelectionListener() {
			@Override
			public void onItemSelected(FlyoutMenuView flyoutMenuView, FlyoutMenuView.MenuItem item) {
				toolFlyoutMenuSelectionId = item.getId();
				DoodleActivityTools.ToolFlyoutMenuItem toolMenuItem = (DoodleActivityTools.ToolFlyoutMenuItem) item;

				toolButtonRenderer.setSize(toolMenuItem.getSize());
				toolButtonRenderer.setIsEraser(toolMenuItem.isEraser());
				setBrushSize(toolMenuItem.getSize() * BRUSH_SCALE);
				setBrushIsEraser(toolMenuItem.isEraser());
			}

			@Override
			public void onDismissWithoutSelection(FlyoutMenuView flyoutMenuView) {
			}
		});
	}


	private void configurePaletteFlyoutMenu() {

		int cols = 8;
		int rows = 8;
		float hsl[] = {0, 0, 0};
		float cornerRadius = 0;
		if (paletteFlyoutMenu.getItemMargin() > 0) {
			cornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, getResources().getDisplayMetrics());
		}

		paletteFlyoutMenu.setLayout(new FlyoutMenuView.GridLayout(cols, FlyoutMenuView.GridLayout.UNSPECIFIED));

		List<DoodleActivityTools.PaletteFlyoutMenuItem> items = new ArrayList<>();
		for (int r = 0; r < rows; r++) {
			float hue = 360f * ((float) r / (float) rows);
			hsl[0] = hue;
			for (int c = 0; c < cols; c++) {
				if (c == 0) {
					float lightness = (float) r / (float) (rows - 1);
					hsl[1] = 0;
					hsl[2] = lightness;
				} else {
					float lightness = (float) c / (float) cols;
					hsl[1] = 1;
					hsl[2] = lightness;
				}
				items.add(new DoodleActivityTools.PaletteFlyoutMenuItem(items.size(), ColorUtils.HSLToColor(hsl), cornerRadius));
			}
		}

		paletteFlyoutMenu.setAdapter(new FlyoutMenuView.ArrayAdapter<>(items));

		float insetPx = dp2px(0);
		final DoodleActivityTools.PaletteFlyoutButtonRenderer renderer = new DoodleActivityTools.PaletteFlyoutButtonRenderer(insetPx);
		paletteFlyoutMenu.setButtonRenderer(renderer);

		paletteFlyoutMenu.setSelectionListener(new FlyoutMenuView.SelectionListener() {
			@Override
			public void onItemSelected(FlyoutMenuView flyoutMenuView, FlyoutMenuView.MenuItem item) {
				paletteFlyoutMenuSelectionId = item.getId();
				int color = ((DoodleActivityTools.PaletteFlyoutMenuItem) item).color;
				renderer.setCurrentColor(color);
				setBrushColor(color);
			}

			@Override
			public void onDismissWithoutSelection(FlyoutMenuView flyoutMenuView) {
			}
		});
	}

	@Subscribe
	public void onLockStateChanged(LockStateChangedEvent event) {
		if (wantDocumentWriteLock && event.isUnlocked(document.getUuid())) {
			Log.i(TAG, "onLockStateChanged: currently want write lock, and document is available, so requesting lock...");
			requestDocumentWriteLock();
		}
	}

	@Subscribe
	public void onSyncServerConnectionStatusChanged(SyncServerConnectionStatusEvent event) {

		// if user connects, we now have to play by the rules: request a write lock.

		if (event.isConnected()) {
			Log.i(TAG, "onSyncServerConnectionStatusChanged: CONNECTED.");
			if (wantDocumentWriteLock) {
				requestDocumentWriteLock();
			} else {
				releaseDocumentWriteLock();
			}
		}

		// I have considered the case of disconnecting. I think we should simply leave
		// the lock state as-is. A document being edited will still be edited, and its
		// changes recorded in the journal. A document being viewed read-only will stay
		// that way. User can back out and in to get a write lock if they want.

	}

	@Override
	public void onDoodleViewResized(DoodleView doodleView, int width, int height) {
		if (firstDisplayOfDoodle) {
			fitDoodleCanvasContents();
			firstDisplayOfDoodle = false;
		}
	}

	private void showLockedDocumentExplanation() {
		new AlertDialog.Builder(this)
				.setTitle(R.string.locked_document_explanation_dialog_title)
				.setMessage(R.string.locked_document_explanation_dialog_message)
				.setPositiveButton(android.R.string.ok, null)
				.show();
	}
}
