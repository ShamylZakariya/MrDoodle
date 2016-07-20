package org.zakariya.mrdoodle.activities;

import android.annotation.TargetApi;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.v4.app.NavUtils;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.ColorUtils;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import org.zakariya.doodle.model.Brush;
import org.zakariya.doodle.model.StrokeDoodle;
import org.zakariya.doodle.view.DoodleView;
import org.zakariya.flyoutmenu.FlyoutMenuView;
import org.zakariya.mrdoodle.R;
import org.zakariya.mrdoodle.model.DoodleDocument;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import icepick.Icepick;
import icepick.State;
import io.realm.Realm;

public class DoodleActivity extends BaseActivity {

	public static final String EXTRA_DOODLE_DOCUMENT_UUID = "DoodleActivity.EXTRA_DOODLE_DOCUMENT_UUID";

	public static final String RESULT_DID_EDIT_DOODLE = "DoodleActivity.RESULT_DID_EDIT_DOODLE";
	public static final String RESULT_DOODLE_DOCUMENT_UUID = "DoodleActivity.RESULT_DOODLE_DOCUMENT_UUID";

	private static final String STATE_DOODLE = "DoodleActivity.STATE_DOODLE";

	private static final float DEFAULT_ZOOM_LEVEL = 1;
	private static final boolean DEBUG_DRAW_DOODLE = false;

	private static final String TAG = "DoodleActivity";

	@ColorInt
	private static final int TOOL_MENU_FILL_COLOR = 0xFF303030;

	private static final float ALPHA_CHECKER_SIZE_DP = 8;

	@ColorInt
	private static final int ALPHA_CHECKER_COLOR = 0xFFD6D6D6;

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


	Realm realm;
	DoodleDocument document;
	StrokeDoodle doodle;

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

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
	}

	@Override
	protected void onDestroy() {
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
	protected void onPause() {
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
			document.saveDoodle(this, doodle);
			doodle.setDirty(false);

			// mark that the document was modified
			realm.beginTransaction();
			document.markModified();
			realm.commitTransaction();

			return true;
		} else {
			return false;
		}
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
			document.setModificationDate(new Date());
			realm.commitTransaction();
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
		doodle.setBrush(new Brush(getBrushColor(), getBrushSize(), getBrushSize(), 600, isBrushEraser()));
	}

	float dp2px(float dp) {
		return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
	}


	private void configureToolFlyoutMenu() {

		float alphaCheckerSize = dp2px(ALPHA_CHECKER_SIZE_DP);


		final int count = 6;
		List<ToolFlyoutMenuItem> items = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			float size = (float) (i + 1) / (float) count;
			items.add(new ToolFlyoutMenuItem(items.size(), size, false, alphaCheckerSize, TOOL_MENU_FILL_COLOR));
		}

		for (int i = 0; i < count; i++) {
			float size = (float) (i + 1) / (float) count;
			items.add(new ToolFlyoutMenuItem(items.size(), size, true, alphaCheckerSize, TOOL_MENU_FILL_COLOR));
		}

		toolSelectorFlyoutMenu.setLayout(new FlyoutMenuView.GridLayout(count, FlyoutMenuView.GridLayout.UNSPECIFIED));
		toolSelectorFlyoutMenu.setAdapter(new FlyoutMenuView.ArrayAdapter<>(items));

		float toolInset = dp2px(4);
		final ToolFlyoutButtonRenderer toolButtonRenderer = new ToolFlyoutButtonRenderer(toolInset, 1, false, alphaCheckerSize, TOOL_MENU_FILL_COLOR);
		toolSelectorFlyoutMenu.setButtonRenderer(toolButtonRenderer);

		toolSelectorFlyoutMenu.setSelectionListener(new FlyoutMenuView.SelectionListener() {
			@Override
			public void onItemSelected(FlyoutMenuView flyoutMenuView, FlyoutMenuView.MenuItem item) {
				toolFlyoutMenuSelectionId = item.getId();
				ToolFlyoutMenuItem toolMenuItem = (ToolFlyoutMenuItem) item;

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

		List<PaletteFlyoutMenuItem> items = new ArrayList<>();
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
				items.add(new PaletteFlyoutMenuItem(items.size(), ColorUtils.HSLToColor(hsl), cornerRadius));
			}
		}

		paletteFlyoutMenu.setAdapter(new FlyoutMenuView.ArrayAdapter<>(items));

		float insetDp = 8;
		float insetPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, insetDp, getResources().getDisplayMetrics());
		final PaletteFlyoutButtonRenderer renderer = new PaletteFlyoutButtonRenderer(insetPx);
		paletteFlyoutMenu.setButtonRenderer(renderer);

		paletteFlyoutMenu.setSelectionListener(new FlyoutMenuView.SelectionListener() {
			@Override
			public void onItemSelected(FlyoutMenuView flyoutMenuView, FlyoutMenuView.MenuItem item) {
				paletteFlyoutMenuSelectionId = item.getId();
				int color = ((PaletteFlyoutMenuItem) item).color;
				renderer.setCurrentColor(color);
				setBrushColor(color);
			}

			@Override
			public void onDismissWithoutSelection(FlyoutMenuView flyoutMenuView) {
			}
		});
	}



	private static final class PaletteFlyoutButtonRenderer extends FlyoutMenuView.ButtonRenderer {

		Paint paint;
		RectF insetButtonBounds = new RectF();
		float inset;
		@ColorInt
		int currentColor;
		double currentColorLuminance;

		public PaletteFlyoutButtonRenderer(float inset) {
			paint = new Paint();
			paint.setAntiAlias(true);
			this.inset = inset;
		}

		@SuppressWarnings("unused")
		public int getCurrentColor() {
			return currentColor;
		}

		public void setCurrentColor(int currentColor) {
			this.currentColor = currentColor;
			currentColorLuminance = ColorUtils.calculateLuminance(this.currentColor);
		}



		@Override
		public void onDrawButtonContent(Canvas canvas, RectF buttonBounds, @ColorInt int buttonColor, float alpha) {
			insetButtonBounds.left = buttonBounds.left + inset;
			insetButtonBounds.top = buttonBounds.top + inset;
			insetButtonBounds.right = buttonBounds.right - inset;
			insetButtonBounds.bottom = buttonBounds.bottom - inset;

			paint.setAlpha((int) (alpha * 255f));
			paint.setStyle(Paint.Style.FILL);
			paint.setColor(currentColor);
			canvas.drawOval(insetButtonBounds, paint);

			if (currentColorLuminance > 0.7) {
				paint.setStyle(Paint.Style.STROKE);
				paint.setColor(0x33000000);
				canvas.drawOval(insetButtonBounds, paint);
			}
		}
	}

	private static final class ToolFlyoutButtonRenderer extends FlyoutMenuView.ButtonRenderer {

		ToolRenderer toolRenderer;
		Paint paint;
		RectF insetButtonBounds = new RectF();
		float inset;

		public ToolFlyoutButtonRenderer(float inset, float size, boolean isEraser, float alphaCheckerSize, @ColorInt int fillColor) {
			this.inset = inset;
			toolRenderer = new ToolRenderer(size, isEraser, alphaCheckerSize, fillColor);
			paint = new Paint();
			paint.setAntiAlias(true);
		}

		@SuppressWarnings("unused")
		@ColorInt
		public int getFillColor() {
			return toolRenderer.getFillColor();
		}

		@SuppressWarnings("unused")
		public void setFillColor(@ColorInt int fillColor) {
			toolRenderer.setFillColor(fillColor);
		}

		public float getSize() {
			return toolRenderer.getSize();
		}

		public void setSize(float size) {
			toolRenderer.setSize(size);
		}

		@SuppressWarnings("unused")
		public boolean isEraser() {
			return toolRenderer.isEraser();
		}

		public void setIsEraser(boolean isEraser) {
			toolRenderer.setIsEraser(isEraser);
		}

		@Override
		public void onDrawButtonContent(Canvas canvas, RectF buttonBounds, @ColorInt int buttonColor, float alpha) {
			insetButtonBounds.left = buttonBounds.left + inset;
			insetButtonBounds.top = buttonBounds.top + inset;
			insetButtonBounds.right = buttonBounds.right - inset;
			insetButtonBounds.bottom = buttonBounds.bottom - inset;

			paint.setAlpha((int) (alpha * 255f));
			paint.setColor(buttonColor);
			paint.setStyle(Paint.Style.FILL);
			toolRenderer.draw(canvas, insetButtonBounds);
		}
	}


	private static final class ToolFlyoutMenuItem extends FlyoutMenuView.MenuItem {

		ToolRenderer toolRenderer;

		public ToolFlyoutMenuItem(int id, float size, boolean isEraser, float alphaCheckerSize, @ColorInt int fillColor) {
			super(id);
			toolRenderer = new ToolRenderer(size, isEraser, alphaCheckerSize, fillColor);
		}

		public float getSize() {
			return toolRenderer.getSize();
		}

		public boolean isEraser() {
			return toolRenderer.isEraser();
		}

		@Override
		public void onDraw(Canvas canvas, RectF bounds, float degreeSelected) {
			toolRenderer.draw(canvas, bounds);
		}
	}

	private static final class PaletteFlyoutMenuItem extends FlyoutMenuView.MenuItem {

		@ColorInt
		int color;

		Paint paint;
		float cornerRadius;

		public PaletteFlyoutMenuItem(int id, @ColorInt int color, float cornerRadius) {
			super(id);
			this.color = ColorUtils.setAlphaComponent(color, 255);
			this.cornerRadius = cornerRadius;
			paint = new Paint();
			paint.setAntiAlias(true);
			paint.setStyle(Paint.Style.FILL);
			paint.setColor(color);
		}

		@Override
		public void onDraw(Canvas canvas, RectF bounds, float degreeSelected) {
			if (cornerRadius > 0) {
				canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, paint);
			} else {
				canvas.drawRect(bounds, paint);
			}
		}
	}

	/**
	 * Convenience class for rendering the tool state in the ToolFlyoutMenuItem and ToolFlyoutButtonRenderer
	 */
	private static final class ToolRenderer {

		float size;
		float radius;
		float alphaCheckerSize;
		boolean isEraser;
		Path clipPath;
		Paint paint;
		RectF previousBounds;
		@ColorInt
		int fillColor;

		public ToolRenderer(float size, boolean isEraser, float alphaCheckerSize, @ColorInt int fillColor) {
			this.alphaCheckerSize = alphaCheckerSize;
			paint = new Paint();
			paint.setAntiAlias(true);

			setSize(size);
			setIsEraser(isEraser);
			setFillColor(fillColor);
		}

		public float getSize() {
			return size;
		}

		public void setSize(float size) {
			this.size = Math.min(Math.max(size, 0), 1);
			clipPath = null;
		}

		public void setIsEraser(boolean isEraser) {
			this.isEraser = isEraser;
		}

		public boolean isEraser() {
			return isEraser;
		}

		public int getFillColor() {
			return fillColor;
		}

		public void setFillColor(int fillColor) {
			this.fillColor = fillColor;
		}

		public void draw(Canvas canvas, RectF bounds) {
			float maxRadius = Math.min(bounds.width(), bounds.height()) / 2;
			radius = size * maxRadius;

			if (isEraser) {
				buildEraserFillClipPath(bounds);

				canvas.save();
				canvas.clipPath(clipPath);

				int left = (int) bounds.left;
				int top = (int) bounds.top;
				int right = (int) bounds.right;
				int bottom = (int) bounds.bottom;

				paint.setStyle(Paint.Style.FILL);
				paint.setColor(0xFFFFFFFF);
				canvas.drawRect(left, top, right, bottom, paint);

				paint.setColor(ALPHA_CHECKER_COLOR);
				for (int y = top, j = 0; y < bottom; y += (int) alphaCheckerSize, j++) {
					for (int x = left, k = 0; x < right; x += alphaCheckerSize, k++) {
						if ((j + k) % 2 == 0) {
							canvas.drawRect(x, y, x + alphaCheckerSize, y + alphaCheckerSize, paint);
						}
					}
				}

				canvas.restore();

				paint.setStyle(Paint.Style.STROKE);
				canvas.drawCircle(bounds.centerX(), bounds.centerY(), radius, paint);

			} else {
				paint.setStyle(Paint.Style.FILL);
				paint.setColor(fillColor);
				canvas.drawCircle(bounds.centerX(), bounds.centerY(), radius, paint);
			}
		}

		void buildEraserFillClipPath(RectF bounds) {
			if (previousBounds == null || clipPath == null || !bounds.equals(previousBounds)) {
				previousBounds = new RectF(bounds);

				clipPath = new Path();
				clipPath.addCircle(bounds.centerX(), bounds.centerY(), radius, Path.Direction.CW);
			}
		}

	}

}
