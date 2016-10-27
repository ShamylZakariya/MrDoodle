package org.zakariya.mrdoodle.ui;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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
import org.zakariya.doodle.model.Doodle;
import org.zakariya.doodle.model.StrokeDoodle;
import org.zakariya.doodle.view.DoodleCanvas;
import org.zakariya.doodle.view.DoodleView;
import org.zakariya.flyoutmenu.FlyoutMenuView;
import org.zakariya.mrdoodle.R;
import org.zakariya.mrdoodle.events.DoodleDocumentEditedEvent;
import org.zakariya.mrdoodle.model.DoodleDocument;
import org.zakariya.mrdoodle.net.events.SyncServerConnectionStatusEvent;
import org.zakariya.mrdoodle.net.model.RemoteChangeReport;
import org.zakariya.mrdoodle.net.transport.RemoteLockStatus;
import org.zakariya.mrdoodle.sync.SyncManager;
import org.zakariya.mrdoodle.sync.events.LockStateChangedEvent;
import org.zakariya.mrdoodle.sync.events.RemoteChangeEvent;
import org.zakariya.mrdoodle.util.BusProvider;
import org.zakariya.mrdoodle.util.Debouncer;
import org.zakariya.mrdoodle.util.DoodleShareHelper;
import org.zakariya.mrdoodle.util.NavbarUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import butterknife.Bind;
import butterknife.ButterKnife;
import icepick.Icepick;
import icepick.State;
import io.realm.Realm;
import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

@SuppressWarnings("unused")
public class DoodleActivity extends BaseActivity implements DoodleView.SizeListener, DoodleView.TwoFingerTapListener, Doodle.EditListener {

	private static final String TAG = "DoodleActivity";

	public static final String EXTRA_DOODLE_DOCUMENT_UUID = "DoodleActivity.EXTRA_DOODLE_DOCUMENT_UUID";
	public static final String RESULT_DOODLE_DOCUMENT_UUID = "DoodleActivity.RESULT_DOODLE_DOCUMENT_UUID";

	// result code if doodle was edited while this activity was active
	public static final String RESULT_DID_EDIT_DOODLE = "DoodleActivity.RESULT_DID_EDIT_DOODLE";

	// result code if user requested to delete this doodle
	public static final String RESULT_SHOULD_DELETE_DOODLE = "DoodleActivity.RESULT_SHOULD_DELETE_DOODLE";

	private static final int ANIMATION_DURATION_MILLIS = 300;
	private static final int DOODLE_EDIT_SAVE_DEBOUNCE_MILLIS = 250;
	private static final float DEFAULT_ZOOM_LEVEL = 1;
	private static final boolean DEBUG_DRAW_DOODLE = false;
	private static final float TWO_FINGER_TAP_MIN_TRANSLATION_DP = 4;
	private static final float TWO_FINGER_TAP_MIN_SCALING = 0.2f;
	private static final float DOODLE_MIN_SCALE = 0.0125f;
	private static final float DOODLE_MAX_SCALE = 16f;

	private static final String PREFS_NAME = "DoodleActivity";
	private static final String PREF_KEY_PALETTE_MENU_SELECTION_ID = "PREF_KEY_PALETTE_MENU_SELECTION_ID";
	private static final String PREF_KEY_TOOL_MENU_SELECTION_ID = "PREF_KEY_TOOL_MENU_SELECTION_ID";

	private static final float DEFAULT_BRUSH_SIZE = 0;
	private static final boolean DEFAULT_BRUSH_IS_ERASER = false;
	private static final int DEFAULT_BRUSH_COLOR = 0xFF000000;

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
	int brushColor = DEFAULT_BRUSH_COLOR;

	@State
	float brushSize = DEFAULT_BRUSH_SIZE;

	@State
	boolean brushIsEraser = DEFAULT_BRUSH_IS_ERASER;

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

	@State
	DoodleCanvas doodleCanvas;

	@State
	StrokeDoodle doodle;

	private Realm realm;
	private Subscription lockSubscription;

	private DoodleDocument document;

	private MenuItem clearMenuItem;
	private MenuItem undoMenuItem;
	private MenuItem deleteMenuItem;

	// when true, the document is in a read-only state and can't be edited
	private boolean readOnly;

	// when true, it means the user wants write access
	private boolean wantDocumentWriteLock;

	private Subscription doodleSaveSubscription;
	private Debouncer<Void> doodleEditSaveDebouncer;

	/**
	 * Get an intent to view a given doodle document by its id
	 *
	 * @param context      the context that should launch the intent
	 * @param documentUuid the id of the doodle document to view/edit
	 * @return a suitable Intent
	 */
	public static Intent getIntent(Context context, String documentUuid) {
		Intent intent = new Intent(context, DoodleActivity.class);
		intent.putExtra(DoodleActivity.EXTRA_DOODLE_DOCUMENT_UUID, documentUuid);
		return intent;
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// set fullscreen flags
		goFullscreen();

		BusProvider.getMainThreadBus().register(this);

		setContentView(R.layout.activity_doodle);
		ButterKnife.bind(this);


		//
		// setup the toolbar - note: we are providing our own titleTextView so we'll hide the built-in title later
		//  Show the up button, and hide the default title. We're going to place
		//  an edittext over where the title would normally go
		//

		setSupportActionBar(toolbar);
		ActionBar actionBar = getSupportActionBar();
		assert actionBar != null;
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setDisplayShowTitleEnabled(false);


		doodleView.addSizeListener(this);
		doodleView.setTwoFingerTapListener(this);

		//
		//  Load the DoodleDocument
		//

		realm = Realm.getDefaultInstance();
		if (savedInstanceState == null) {
			documentUuid = getIntent().getStringExtra(EXTRA_DOODLE_DOCUMENT_UUID);
			document = DoodleDocument.byUuid(realm, documentUuid);
			if (document == null) {
				document = DoodleDocument.create(realm, getString(R.string.untitled_document));
				documentUuid = document.getUuid();
			}

			doodle = document.loadDoodle(this);
			doodleCanvas = new DoodleCanvas();
			doodleCanvas.setMinPinchTranslationForTap(dp2px(TWO_FINGER_TAP_MIN_TRANSLATION_DP));
			doodleCanvas.setMinPinchScalingForTap(TWO_FINGER_TAP_MIN_SCALING);
			doodleCanvas.setDisabledEdgeWidth(getResources().getDimension(R.dimen.doodle_canvas_disabled_touch_edge_width));

			// load the last set brush parameters
			SharedPreferences prefs = getSharedPreferences();
			toolFlyoutMenuSelectionId = prefs.getInt(PREF_KEY_TOOL_MENU_SELECTION_ID, toolFlyoutMenuSelectionId);
			paletteFlyoutMenuSelectionId = prefs.getInt(PREF_KEY_PALETTE_MENU_SELECTION_ID, paletteFlyoutMenuSelectionId);
		} else {
			Icepick.restoreInstanceState(this, savedInstanceState);
			document = DoodleDocument.byUuid(realm, documentUuid);
		}

		// set a touch dead zone on the edge where the navigation bar is hidden,
		// this prevents edge swipes which would reveal the navigation bar from
		// drawing a line or panning/zooming
		boolean navigationBarOnRight = isNavigationBarRightOfContent();
		doodleCanvas.setDisabledEdgeSwipeMask(navigationBarOnRight ? DoodleCanvas.EDGE_RIGHT : DoodleCanvas.EDGE_BOTTOM);


		doodleView.setDoodleCanvas(doodleCanvas);


		assert document != null;
		documentModificationTime = document.getModificationDate().getTime();

		assert doodle != null;
		setupDoodle(doodle);

		//
		// set up the document title edit text
		//

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


		updateBrush();

		// build our menus
		configureToolFlyoutMenu();
		configurePaletteFlyoutMenu();

		toolSelectorFlyoutMenu.setSelectedMenuItemById(toolFlyoutMenuSelectionId);
		paletteFlyoutMenu.setSelectedMenuItemById(paletteFlyoutMenuSelectionId);

		if (savedInstanceState == null) {
			doodleCanvas.setCanvasScale(DEFAULT_ZOOM_LEVEL);
		}


		// hide controls, they'll be shown or hidden appropriately after onResume
		// then the document is locked (or not)
		setViewVisibility(lockIconImageView, false, false);
		setViewVisibility(toolSelectorFlyoutMenu, false, false);
		setViewVisibility(paletteFlyoutMenu, false, false);

		lockIconImageView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showLockedDocumentExplanation();
			}
		});
	}

	@Override
	protected void onDestroy() {
		BusProvider.getMainThreadBus().unregister(this);

		if (doodleEditSaveDebouncer != null) {
			doodleEditSaveDebouncer.destroy();
		}

		if (doodleSaveSubscription != null && !doodleSaveSubscription.isUnsubscribed()) {
			doodleSaveSubscription.unsubscribe();
		}

		if (doodle != null) {
			doodle.removeChangeListener(this);
		}

		doodleView.removeSizeListener(this);

		if (lockSubscription != null && !lockSubscription.isUnsubscribed()) {
			lockSubscription.unsubscribe();
		}

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
		inflater.inflate(R.menu.menu_doodle, menu);

		clearMenuItem = menu.findItem(R.id.menuItemClear);
		undoMenuItem = menu.findItem(R.id.menuItemUndo);
		deleteMenuItem = menu.findItem(R.id.menuItemDelete);
		updateMenuItems();

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menuItemFitCanvasContent:
				fitDoodleCanvasContents();
				return true;

			case R.id.menuItemUndo:
				doodle.undo();
				return true;

			case R.id.menuItemClear:
				doodle.clear();
				return true;

			case R.id.menuItemDelete:
				queryDeleteDoodle();
				return true;

			case R.id.menuItemShare:
				DoodleShareHelper.share(this, document);
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
	protected void onStop() {

		SharedPreferences prefs = getSharedPreferences();
		prefs.edit()
				.putInt(PREF_KEY_PALETTE_MENU_SELECTION_ID, paletteFlyoutMenuSelectionId)
				.putInt(PREF_KEY_TOOL_MENU_SELECTION_ID, toolFlyoutMenuSelectionId)
				.apply();

		super.onStop();
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
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			goFullscreen();
		}
	}

	@Override
	public void onDoodleViewResized(DoodleView doodleView, int width, int height) {
		if (firstDisplayOfDoodle) {
			fitDoodleCanvasContents();
			firstDisplayOfDoodle = false;
		}
	}

	@Override
	public void onDoodleViewTwoFingerTap(DoodleView view, int tapCount) {
		if (tapCount > 1) {
			fitDoodleCanvasContents();
		}
	}

	@Override
	public void onDoodleEdited(Doodle doodle) {

		if (doodleEditSaveDebouncer == null) {
			doodleEditSaveDebouncer = new Debouncer<>(DOODLE_EDIT_SAVE_DEBOUNCE_MILLIS, new Action1<Void>() {
				@Override
				public void call(Void o) {
					saveDoodleIfEdited();
				}
			});
		}

		// debounce save
		doodleEditSaveDebouncer.send(null);
	}

	///////////////////////////////////////////////////////////////////

	@Subscribe
	public void onLockStateChanged(LockStateChangedEvent event) {
		if (wantDocumentWriteLock && event.isUnlocked(document.getUuid())) {
			Log.i(TAG, "onLockStateChanged: currently want write lock, and document is available, so requesting lock...");
			requestDocumentWriteLock();
		}
	}

	@Subscribe
	public void onRemoteChange(RemoteChangeEvent event) {

		// we could listen to DoodleDocumentWasEdited or DoodleDocumentWasDeleted,
		// but those aren't dispatched until after a sync completes. There's a potential
		// race condition where a document has been remotely deleted, and a viewing
		// device may get a lock on it, and make some changes while the document's realm
		// object has been deleted in a background thread. Boom! RemoteChangeEvent
		// is fired immediately (well, on the main thread)

		RemoteChangeReport report = event.getReport();
		Log.i(TAG, "onRemoteChange: report: " + report);

		if (report.getDocumentId().equals(documentUuid)) {
			switch (report.getAction()) {
				case UPDATE:
					if (readOnly) {
						Log.i(TAG, "onRemoteChange: UPDATE - updating the doodle");
						titleEditText.setText(document.getName());
						setupDoodle(document.loadDoodle(this));
					}
					break;
				case DELETE:
					// flag that the document is deleted by nulling it
					Log.i(TAG, "onRemoteChange: DELETE: nulling doodle and document");
					doodle = null;
					document = null;
					documentUuid = null;
					showDeletedDocumentExplanationAndExit();
					break;
			}
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

	///////////////////////////////////////////////////////////////////

	public void fitDoodleCanvasContents() {
		if (document == null) {
			return;
		}

		doodleCanvas.fitCanvasContent(getResources().getDimensionPixelSize(R.dimen.doodle_fit_canvas_contents_padding));
	}

	/**
	 * If the doodle is dirty (edits were made) saves it to its file, and returns true.
	 *
	 * @return true if the doodle had edits and needed to be saved
	 */
	public boolean saveDoodleIfEdited() {

		// if nothing to do, bail
		if (document == null || !doodle.isDirty()) {
			return false;
		}

		// create a copy of the doodle in its current state
		final StrokeDoodle doodleCopy = new StrokeDoodle(doodle);

		// serialize it to byte[] in an io thread, and then on main thread commit to store
		doodleSaveSubscription = Observable.fromCallable(
				new Callable<byte[]>() {
					@Override
					public byte[] call() throws Exception {
						return doodleCopy.serialize();
					}
				})
				.subscribeOn(Schedulers.io())
				.observeOn(AndroidSchedulers.mainThread())
				.subscribe(new Observer<byte[]>() {
					@Override
					public void onCompleted() {
					}

					@Override
					public void onError(Throwable e) {
						Log.e(TAG, "saveDoodleIfEdited - onError: e: ", e);
						e.printStackTrace();
					}

					@Override
					public void onNext(byte[] serializedCopy) {
						// persist
						realm.beginTransaction();
						document.setDoodleBytes(serializedCopy);
						realm.commitTransaction();
						markDocumentModified();
					}
				});

		return true;
	}

	boolean isReadOnly() {
		return readOnly;
	}

	void setReadOnly(boolean readOnly, boolean animate) {
		Log.i(TAG, "setReadOnly() called with: readOnly = [" + readOnly + "]");

		this.readOnly = readOnly;
		doodle.setReadOnly(this.readOnly);
		titleEditText.setEnabled(!this.readOnly);

		setViewVisibility(toolSelectorFlyoutMenu, !readOnly, animate);
		setViewVisibility(paletteFlyoutMenu, !readOnly, animate);
		setViewVisibility(lockIconImageView, readOnly, animate);
		updateMenuItems();
	}

	void setupDoodle(StrokeDoodle doodle) {
		this.doodle = doodle;
		this.doodle.setReadOnly(this.readOnly);

		this.doodle.setBackgroundColor(ContextCompat.getColor(this, R.color.doodleBackground));
		this.doodle.addChangeListener(this);

		doodleCanvas.setDoodle(this.doodle);
		doodleCanvas.setMinCanvasScale(DOODLE_MIN_SCALE);
		doodleCanvas.setMaxCanvasScale(DOODLE_MAX_SCALE);
		doodleCanvas.setCanvasScaleClamped(true);

		if (DEBUG_DRAW_DOODLE) {
			doodleCanvas.setCoordinateGridSize(dp2px(100));
			doodleCanvas.setDrawCoordinateGrid(true);
			doodleCanvas.setDrawInvalidationRect(true);
			doodleCanvas.setDrawViewport(true);
			doodleCanvas.setDrawCanvasContentBoundingRect(true);
		}
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

		int flags = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
				| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (getResources().getBoolean(R.bool.light_status_bar)) {
				flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
			}
		}

		getWindow().getDecorView().setSystemUiVisibility(flags);
	}

	SharedPreferences getSharedPreferences() {
		return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
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

	private void setViewVisibility(final View v, boolean visible, boolean animate) {
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
		if (document == null) {
			return;
		}

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
		if (document == null) {
			return;
		}

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

		// save doodle
		boolean edited = saveDoodleIfEdited();

		Intent resultData = new Intent();
		resultData.putExtra(RESULT_DID_EDIT_DOODLE, edited);
		resultData.putExtra(RESULT_DOODLE_DOCUMENT_UUID, document.getUuid());
		setResult(RESULT_OK, resultData);
	}

	private void updateMenuItems() {
		if (clearMenuItem != null && undoMenuItem != null) {
			clearMenuItem.setVisible(!readOnly);
			undoMenuItem.setVisible(!readOnly);
			deleteMenuItem.setVisible(!readOnly);
		}
	}

	private void showDeletedDocumentExplanationAndExit() {
		new AlertDialog.Builder(this)
				.setTitle(R.string.deleted_document_explanation_title)
				.setMessage(R.string.deleted_document_explanation_message)
				.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						NavUtils.navigateUpFromSameTask(DoodleActivity.this);
					}
				})
				.setCancelable(false)
				.show();
	}

	private void showLockedDocumentExplanation() {
		new AlertDialog.Builder(this)
				.setTitle(R.string.locked_document_explanation_dialog_title)
				.setMessage(R.string.locked_document_explanation_dialog_message)
				.setPositiveButton(android.R.string.ok, null)
				.show();
	}

	private void queryDeleteDoodle() {
		new AlertDialog.Builder(this)
				.setTitle(R.string.dialog_delete_doodle_title)
				.setMessage(R.string.dialog_delete_doodle_message)
				.setPositiveButton(R.string.dialog_delete_doodle_positive_button_title, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						deleteDoodleAndExit();
					}
				})
				.setNegativeButton(android.R.string.cancel, null)
				.show();
	}

	private void deleteDoodleAndExit() {
		Intent resultData = new Intent();
		resultData.putExtra(RESULT_DOODLE_DOCUMENT_UUID, document.getUuid());
		resultData.putExtra(RESULT_SHOULD_DELETE_DOODLE, true);
		setResult(RESULT_OK, resultData);

		NavUtils.navigateUpFromSameTask(this);
	}

	private boolean isNavigationBarRightOfContent() {
		int navbarHeight = NavbarUtils.getNavigationBarHeight(getResources());
		int navbarWidth = NavbarUtils.getNavigationBarWidth(getResources());
		return navbarWidth > navbarHeight;
	}
}
