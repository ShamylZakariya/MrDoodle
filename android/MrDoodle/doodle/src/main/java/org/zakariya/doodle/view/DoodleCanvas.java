package org.zakariya.doodle.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.MotionEventCompat;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;

import org.zakariya.doodle.model.Doodle;

import icepick.Icepick;
import icepick.State;

/**
 * DoodleCanvas
 */

@SuppressWarnings({"WeakerAccess", "unused"}) // Icepick needs non-private members
public class DoodleCanvas {

	/**
	 * Interface for listeners interested in being notified when user makes two-finger taps.
	 * A client could, for example, use a two-finger double-tap as a cue to zoom fit the canvas.
	 */
	public interface TwoFingerTapListener {
		void onTwoFingerTap(int tapCount);
	}


	private static final String TAG = "DoodleCanvas";

	private static final float SCALE_MIN = 0.125f;
	private static final float SCALE_MAX = 16.0f;
	private static final float TRANSLATION_MAX = 24000f;
	private static final long DOUBLE_TAP_DELAY_MILLIS = 350;
	private static final float DOUBLE_TAP_MIN_TRANSLATION_DP = 4;
	private static final float DOUBLE_TAP_MIN_SCALING = 0.2f;

	@State
	boolean drawInvalidationRect = false;

	@State
	boolean drawCoordinateGrid = false;

	@State
	boolean drawViewport = false;

	@State
	boolean drawCanvasContentBoundingRect = false;

	@State
	boolean transformRangeClampingEnabled = true;

	@State
	float canvasScale = 1f;

	@State
	PointF canvasTranslation = new PointF(0, 0);

	@State
	PointF canvasOriginRelativeToViewportCenter = null;

	@State
	boolean readOnly;

	@State
	float coordinateGridSize = 100;

	private Matrix screenToCanvasMatrix = new Matrix();
	private Matrix canvasToScreenMatrix = new Matrix();
	private RectF viewportScreenRect = new RectF();
	private RectF viewportCanvasRect = new RectF();
	private DoodleView doodleView;
	private Doodle doodle;
	private TwoFingerTapListener twoFingerTapListener;
	private int doubleTwoFingerTapCount;
	private boolean doubleTwoFingerTapCandidacyTimerStarted;
	private Handler doubleTwoFingerTapTimerHandler;
	private Paint overlayPaint;
	private RectF invalidationRect;

	// two finger touch state
	private boolean performingPinchOperations = false;
	private PointF pinchLastCenterScreen;
	private float pinchLastLengthScreen;
	private float totalPinchTranslation;
	private float totalPinchScaling;
	private float minPinchTranslationForTap;
	private float minPinchScalingForTap;


	/**
	 * Create a default DoodleCanvas. You will need to assign a doodle.
	 */
	public DoodleCanvas(Context context) {
		overlayPaint = new Paint();
		overlayPaint.setAntiAlias(true);
		overlayPaint.setStrokeWidth(1);
		overlayPaint.setStyle(Paint.Style.STROKE);

		minPinchTranslationForTap = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DOUBLE_TAP_MIN_TRANSLATION_DP, context.getResources().getDisplayMetrics());
		minPinchScalingForTap = DOUBLE_TAP_MIN_SCALING;

	}

	/**
	 * Create a DoodleCanvas with a Doodle
	 *
	 * @param doodle
	 */
	public DoodleCanvas(Context context, Doodle doodle) {
		this(context);
		setDoodle(doodle);
	}

	public void onLoadInstanceState(Bundle savedInstanceState) {
		Icepick.restoreInstanceState(this, savedInstanceState);
	}

	public void onSaveInstanceState(Bundle outState) {
		// we want to retain canvasOriginRelativeToViewportCenter so that we can
		// re-center content after a rotation/resize.
		canvasOriginRelativeToViewportCenter = new PointF();
		canvasOriginRelativeToViewportCenter.x = getCanvasTranslationX() - viewportScreenRect.centerX();
		canvasOriginRelativeToViewportCenter.y = getCanvasTranslationY() - viewportScreenRect.centerY();

		Icepick.saveInstanceState(this, outState);
	}

	/**
	 * @return get the Doodle being presented.
	 */
	public Doodle getDoodle() {
		return doodle;
	}

	/**
	 * Assign a doodle to present
	 *
	 * @param doodle the Doodle
	 */
	public void setDoodle(Doodle doodle) {
		this.doodle = doodle;
		this.doodle.setDoodleCanvas(this);
		this.doodle.setReadOnly(this.isReadOnly());

		DoodleView view = getDoodleView();
		if (view != null) {
			if (view.getWidth() > 0 && view.getHeight() > 0) {
				resize(view.getWidth(), view.getHeight());
			}
		}
	}

	void setDoodleView(DoodleView view) {
		doodleView = view;
	}

	@Nullable
	public DoodleView getDoodleView() {
		return doodleView;
	}

	public TwoFingerTapListener getTwoFingerTapListener() {
		return twoFingerTapListener;
	}

	public void setTwoFingerTapListener(TwoFingerTapListener twoFingerTapListener) {
		this.twoFingerTapListener = twoFingerTapListener;
	}

	public void invalidate() {
		DoodleView view = getDoodleView();
		if (view != null) {
			view.invalidate();
		}
	}

	public void invalidate(RectF rect) {
		invalidationRect = rect;
		DoodleView dv = getDoodleView();
		if (dv != null) {
			dv.invalidate((int) rect.left, (int) rect.top, (int) rect.right, (int) rect.bottom);
		}
	}

	public void draw(Canvas canvas) {

		if (shouldDrawCoordinateGrid() || shouldDrawViewport() || shouldDrawCanvasContentBoundingRect()) {
			canvas.save();
			canvas.concat(canvasToScreenMatrix);
			drawCoordinateGrid(canvas);
			drawViewport(canvas);
			drawCanvasContentBoundingRect(canvas);
			canvas.restore();
		}

		this.doodle.draw(canvas);

		drawInvalidationRect(canvas);
	}

	public void draw(Canvas canvas, int width, int height, boolean fitContents, float fitPadding) {
		int oldWidth = getWidth();
		int oldHeight = getHeight();
		if (oldWidth != width || oldHeight != height) {
			resize(width, height);
			draw(canvas, fitContents, fitPadding);
			if (oldWidth > 0 && oldHeight > 0) {
				resize(oldWidth, oldHeight);
			}
		} else {
			draw(canvas, fitContents, fitPadding);
		}
	}

	private void draw(Canvas canvas, boolean fitContents, float fitPadding) {
		float tx = 0, ty = 0, s = 0;
		if (fitContents) {
			tx = getCanvasTranslationX();
			ty = getCanvasTranslationY();
			s = getCanvasScale();
			fitCanvasContent(fitPadding);
		}

		draw(canvas);

		if (fitContents) {
			setCanvasScaleAndTranslation(s, tx, ty);
		}
	}

	public void resize(int newWidth, int newHeight) {
		if (this.doodle != null) {
			this.doodle.resize(newWidth, newHeight);
		}
		viewportScreenRect.left = 0;
		viewportScreenRect.top = 0;
		viewportScreenRect.right = newWidth;
		viewportScreenRect.bottom = newHeight;

		// if canvasOriginRelativeToViewportCenter is non-null, that means device rotated or screen resized
		// and we need to re-center content
		if (canvasOriginRelativeToViewportCenter != null) {
			setCanvasTranslation(
					viewportScreenRect.centerX() + canvasOriginRelativeToViewportCenter.x,
					viewportScreenRect.centerY() + canvasOriginRelativeToViewportCenter.y
			);
		}

		updateMatrices();
		invalidate();
	}

	public int getWidth() {
		return (int) viewportScreenRect.right;
	}

	public int getHeight() {
		return (int) viewportScreenRect.bottom;
	}

	public boolean isReadOnly() {
		return readOnly;
	}

	public void setReadOnly(boolean readOnly) {
		this.readOnly = readOnly;
		this.doodle.setReadOnly(readOnly);
	}

	public boolean shouldDrawInvalidationRect() {
		return drawInvalidationRect;
	}

	public void setDrawInvalidationRect(boolean drawInvalidationRect) {
		this.drawInvalidationRect = drawInvalidationRect;
		invalidate();
	}

	public boolean shouldDrawCoordinateGrid() {
		return drawCoordinateGrid;
	}

	public void setDrawCoordinateGrid(boolean drawCoordinateGrid) {
		this.drawCoordinateGrid = drawCoordinateGrid;
		invalidate();
	}

	public boolean shouldDrawViewport() {
		return drawViewport;
	}

	public void setDrawViewport(boolean drawViewport) {
		this.drawViewport = drawViewport;
		invalidate();
	}

	public boolean shouldDrawCanvasContentBoundingRect() {
		return drawCanvasContentBoundingRect;
	}

	public void setDrawCanvasContentBoundingRect(boolean drawCanvasContentBoundingRect) {
		this.drawCanvasContentBoundingRect = drawCanvasContentBoundingRect;
		invalidate();
	}

	public float getCoordinateGridSize() {
		return coordinateGridSize;
	}

	public void setCoordinateGridSize(float coordinateGridSize) {
		this.coordinateGridSize = coordinateGridSize;
		invalidate();
	}


	public void setTransformRangeClampingEnabled(boolean transformRangeClampingEnabled) {
		this.transformRangeClampingEnabled = transformRangeClampingEnabled;
	}

	public boolean isTransformRangeClampingEnabled() {
		return transformRangeClampingEnabled;
	}


	/**
	 * Get the current scale of the canvas
	 *
	 * @return the current scale of the canvas
	 */
	public float getCanvasScale() {
		return canvasScale;
	}

	/**
	 * Set the scale of the canvas, where values less than 1 "shrink" the view of the canvas, and
	 * values > 1 "zoom in".
	 *
	 * @param canvasScale new scale for the canvas
	 */
	public void setCanvasScale(float canvasScale) {
		this.canvasScale = clampCanvasScale(canvasScale);
		updateMatrices();
		invalidate();
	}

	protected float clampCanvasScale(float s) {
		return transformRangeClampingEnabled ? Math.min(Math.max(s, SCALE_MIN), SCALE_MAX) : s;
	}

	/**
	 * @return the canvas panning translation
	 */
	public PointF getCanvasTranslation() {
		return canvasTranslation;
	}

	/**
	 * @return canvas X translation in screen pixels (independent of canvas scale)
	 */
	public float getCanvasTranslationX() {
		return canvasTranslation.x;
	}

	/**
	 * @return canvas Y translation in screen pixels (independent of canvas scale)
	 */
	public float getCanvasTranslationY() {
		return canvasTranslation.y;
	}


	public Matrix getScreenToCanvasMatrix() {
		return screenToCanvasMatrix;
	}

	public Matrix getCanvasToScreenMatrix() {
		return canvasToScreenMatrix;
	}

	public RectF getViewportScreenRect() {
		return viewportScreenRect;
	}

	/**
	 * Set the canvas panning translation in screen pixels. Translation
	 * is independent of canvas scale
	 *
	 * @param x x translation in pixels
	 * @param y y translation in pixels
	 */
	public void setCanvasTranslation(float x, float y) {
		this.canvasTranslation.x = clampCanvasTranslation(x);
		this.canvasTranslation.y = clampCanvasTranslation(y);
		updateMatrices();
		invalidate();
	}

	protected float clampCanvasTranslation(float v) {
		return transformRangeClampingEnabled ? Math.min(Math.max(v, -TRANSLATION_MAX), TRANSLATION_MAX) : v;
	}

	/**
	 * Set canvas scale and translation in one pass. Note that translation is in screen pixels and is
	 * independent of canvas scale.
	 *
	 * @param s the new scale of the canvas where values > 1 are zooming in, and < 1 are zooming out to see more
	 * @param x x translation in pixels
	 * @param y y translation in pixels
	 */
	public void setCanvasScaleAndTranslation(float s, float x, float y) {
		this.canvasScale = clampCanvasScale(s);
		this.canvasTranslation.x = clampCanvasTranslation(x);
		this.canvasTranslation.y = clampCanvasTranslation(y);
		updateMatrices();
		invalidate();
	}

	/**
	 * Get the viewport rect on the canvas in canvas coordinates.
	 *
	 * @return the rect describing the view on the canvas in the canvas's coordinate system
	 */
	public RectF getViewportCanvasRect() {
		return viewportCanvasRect;
	}

	/**
	 * Center content of canvas in the viewport without changing zoom level
	 */
	public void centerCanvasContent() {

		RectF canvasBounds = getCanvasContentBoundingRect();
		if (!canvasBounds.isEmpty()) {
			// convert canvas bounds to screen
			RectF canvasContentBoundsScreenRect = new RectF();
			canvasToScreenMatrix.mapRect(canvasContentBoundsScreenRect, canvasBounds);

			float dx = viewportScreenRect.centerX() - canvasContentBoundsScreenRect.centerX();
			float dy = viewportScreenRect.centerY() - canvasContentBoundsScreenRect.centerY();
			setCanvasTranslation(getCanvasTranslationX() + dx, getCanvasTranslationY() + dy);
		} else {
			// we have no content, so go default
			setCanvasScaleAndTranslation(1, 0, 0);
		}
	}

	/**
	 * Center and fit canvas content in the viewport, zooming as needed to show
	 * all canvas content.
	 *
	 * @param padding amount of padding in pixels around the contents
	 */
	public void fitCanvasContent(float padding) {

		RectF canvasBounds = getCanvasContentBoundingRect();
		if (!canvasBounds.isEmpty()) {
			// convert canvas bounds to screen, and grow it by our padding amount
			RectF canvasContentBoundsScreenRect = new RectF();
			canvasToScreenMatrix.mapRect(canvasContentBoundsScreenRect, getCanvasContentBoundingRect());
			canvasContentBoundsScreenRect.inset(-padding, -padding);

			// now compute max scale that fits contents
			float scale = viewportScreenRect.width() / canvasContentBoundsScreenRect.width();
			if (canvasContentBoundsScreenRect.height() * scale > viewportScreenRect.height()) {
				scale = viewportScreenRect.height() / canvasContentBoundsScreenRect.height();
			}

			canvasScale = clampCanvasScale(getCanvasScale() * scale);

			updateMatrices();
			canvasToScreenMatrix.mapRect(canvasContentBoundsScreenRect, getCanvasContentBoundingRect());
			float dx = viewportScreenRect.centerX() - canvasContentBoundsScreenRect.centerX();
			float dy = viewportScreenRect.centerY() - canvasContentBoundsScreenRect.centerY();

			canvasTranslation.x = clampCanvasTranslation(canvasTranslation.x + dx);
			canvasTranslation.y = clampCanvasTranslation(canvasTranslation.y + dy);

			updateMatrices();
			invalidate();
		} else {
			// we have no content, so go default
			setCanvasScaleAndTranslation(1, 0, 0);
		}
	}

	/**
	 * @return the rect in the canvas's coordinate system which fits the canvas's content completely
	 */
	public RectF getCanvasContentBoundingRect() {
		return doodle.getCanvasContentBoundingRect();
	}

	/**
	 * Update the canvasToScreen and inverse screenToCanvas matrices and dependant
	 * element such as the viewportCanvasRect
	 * Note, translation is measured in screen coordinates.
	 */
	protected void updateMatrices() {

		canvasToScreenMatrix.reset();
		canvasToScreenMatrix.preTranslate(canvasTranslation.x, canvasTranslation.y);
		canvasToScreenMatrix.preScale(canvasScale, canvasScale);

		screenToCanvasMatrix.reset();
		if (!canvasToScreenMatrix.invert(screenToCanvasMatrix)) {
			Log.e(TAG, "updateMatrices: Unable to invert canvasToScreenMatrix");
		}

		// update viewportCanvasRect
		screenToCanvasMatrix.mapRect(viewportCanvasRect, viewportScreenRect);

		// and notify doodle
		doodle.canvasMatricesUpdated();
	}

	public boolean onTouchEvent(@NonNull MotionEvent event) {
		int action = MotionEventCompat.getActionMasked(event);
		switch (action) {
			case MotionEvent.ACTION_DOWN:
				return onTouchEventBegin(event);
			case MotionEvent.ACTION_POINTER_DOWN:
				return onTouchEventBegin(event);
			case MotionEvent.ACTION_POINTER_UP:
				return onTouchEventEnd(event);
			case MotionEvent.ACTION_UP:
				return onTouchEventEnd(event);
			case MotionEvent.ACTION_MOVE:
				return onTouchEventMove(event);
		}

		return false;
	}

	protected boolean onTouchEventBegin(@NonNull MotionEvent event) {

		if (event.getPointerCount() == 1) {
			return doodle.onTouchEventBegin(event);
		} else if (event.getPointerCount() == 2) {
			if (!doodle.onTouchEventBegin(event)) {
				// discard any drawing and prevent future drawing until gestures complete
				performingPinchOperations = true;

				float[] touch0 = {event.getX(0), event.getY(0)};
				float[] touch1 = {event.getX(1), event.getY(1)};
				pinchLastCenterScreen = new PointF((touch0[0] + touch1[0]) / 2, (touch0[1] + touch1[1]) / 2);

				float pinchDx = touch0[0] - touch1[0];
				float pinchDy = touch0[1] - touch1[1];
				pinchLastLengthScreen = (float) Math.sqrt((pinchDx * pinchDx) + (pinchDy * pinchDy));

				// reset; we'll measure total scaling and translation for tap candidacy
				totalPinchScaling = 0;
				totalPinchTranslation = 0;
				return true;
			}
		}

		return false;
	}

	protected boolean onTouchEventMove(@NonNull MotionEvent event) {

		if (event.getPointerCount() == 1 && !performingPinchOperations) {
			return doodle.onTouchEventMove(event);
		} else if (event.getPointerCount() >= 2) {
			if (!doodle.onTouchEventMove(event)) {
				// null this to flag that user has intentionally transformed canvas
				// and as such, re-centering in resize() should be ignored.
				canvasOriginRelativeToViewportCenter = null;

				float[] touch0 = {event.getX(0), event.getY(0)};
				float[] touch1 = {event.getX(1), event.getY(1)};
				PointF currentPinchCenterScreen = new PointF((touch0[0] + touch1[0]) / 2, (touch0[1] + touch1[1]) / 2);

				float pinchDx = touch0[0] - touch1[0];
				float pinchDy = touch0[1] - touch1[1];
				float currentPinchLengthScreen = (float) Math.sqrt((pinchDx * pinchDx) + (pinchDy * pinchDy));


				// translation of canvas is in screen coordinates (not canvas)
				// this means we are working in the coordinates of the touch event.
				// we translate the canvas by measuring dx/dy of the pinch center.
				// we scale the canvas by measuring the change in pinch length.
				// we take the vector from the pinch center to canvas's translation point
				// and rescale it by the change in zoom.


				// compute scale - first get change in scale, apply it to scale, clamp it
				// to allowed range, and then recompute change in scale. this avoids a weirdness
				// where scale gets clamped, but delta doesn't, and panning goes wonky at extremes
				// of allowed zoom levels
				float deltaScale = (currentPinchLengthScreen / pinchLastLengthScreen);
				float newScale = clampCanvasScale(canvasScale * deltaScale);
				deltaScale = newScale / canvasScale;

				float originRelativeToPinchCenterScreenX = getCanvasTranslationX() - currentPinchCenterScreen.x;
				float originRelativeToPinchCenterScreenY = getCanvasTranslationY() - currentPinchCenterScreen.y;

				float scaledOriginRelativeToPinchCenterScreenX = originRelativeToPinchCenterScreenX * deltaScale;
				float scaledOriginRelativeToPinchCenterScreenY = originRelativeToPinchCenterScreenY * deltaScale;

				float newOriginX = scaledOriginRelativeToPinchCenterScreenX + currentPinchCenterScreen.x;
				float newOriginY = scaledOriginRelativeToPinchCenterScreenY + currentPinchCenterScreen.y;

				// compute pan
				float screenDx = currentPinchCenterScreen.x - pinchLastCenterScreen.x;
				float screenDy = currentPinchCenterScreen.y - pinchLastCenterScreen.y;
				newOriginX += screenDx;
				newOriginY += screenDy;

				float tdx = newOriginX - getCanvasTranslationX();
				float tdy = newOriginY - getCanvasTranslationY();

				// apply new scale & translation
				setCanvasScaleAndTranslation(newScale, newOriginX, newOriginY);

				// tag current pinch state so we can act on deltas with next move event
				pinchLastCenterScreen.x = currentPinchCenterScreen.x;
				pinchLastCenterScreen.y = currentPinchCenterScreen.y;
				pinchLastLengthScreen = currentPinchLengthScreen;

				totalPinchScaling += Math.abs(1 - deltaScale);
				totalPinchTranslation += Math.sqrt((tdx * tdx) + (tdy * tdy));

				return true;
			}
		}

		return false;
	}

	protected boolean onTouchEventEnd(@NonNull MotionEvent event) {

		// a two-finger pinch gesture ended
		if (performingPinchOperations) {

			if (totalPinchScaling < minPinchScalingForTap && totalPinchTranslation < minPinchTranslationForTap) {
				dispatchTwoFingerTap();
			}

			if (event.getPointerCount() == 1) {
				// mark that we're done with multitouch pinch ops
				pinchLastCenterScreen = null;
				performingPinchOperations = false;
			}
		}

		return doodle.onTouchEventEnd(event);
	}


	protected void dispatchTwoFingerTap() {

		if (doubleTwoFingerTapTimerHandler == null) {
			doubleTwoFingerTapTimerHandler = new Handler();
		}

		doubleTwoFingerTapCount++;

		if (!doubleTwoFingerTapCandidacyTimerStarted) {
			doubleTwoFingerTapCandidacyTimerStarted = true;
			doubleTwoFingerTapTimerHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (twoFingerTapListener != null) {
						twoFingerTapListener.onTwoFingerTap(doubleTwoFingerTapCount);
					}

					doubleTwoFingerTapCount = 0;
					doubleTwoFingerTapCandidacyTimerStarted = false;
				}
			}, DOUBLE_TAP_DELAY_MILLIS);
		}
	}

	protected void drawInvalidationRect(Canvas canvas) {

		if (shouldDrawInvalidationRect()) {
			overlayPaint.setColor(0xFFFF0000);
			overlayPaint.setStrokeWidth(1);

			RectF r = invalidationRect != null ? invalidationRect : new RectF(0, 0, getWidth(), getHeight());
			canvas.drawRect(r, overlayPaint);
		}

		invalidationRect = null;
	}

	protected void drawViewport(Canvas canvas) {
		if (shouldDrawViewport()) {
			overlayPaint.setColor(0xFFFF66FF);
			overlayPaint.setStrokeWidth(0);

			float inset = 8 / canvasScale;
			float cornerRadius = 16 / canvasScale;

			RectF insetViewportOnCanvasRect = new RectF(viewportCanvasRect);
			insetViewportOnCanvasRect.inset(inset, inset);
			canvas.drawRoundRect(insetViewportOnCanvasRect, cornerRadius, cornerRadius, overlayPaint);
		}
	}

	protected void drawCanvasContentBoundingRect(Canvas canvas) {
		if (shouldDrawCanvasContentBoundingRect()) {
			RectF canvasContentBoundingRect = getCanvasContentBoundingRect();
			if (!canvasContentBoundingRect.isEmpty()) {
				overlayPaint.setColor(0xFF66FF66);
				overlayPaint.setStrokeWidth(0);
				canvas.drawRect(canvasContentBoundingRect, overlayPaint);
			}
		}
	}

	protected void drawCoordinateGrid(Canvas canvas) {
		if (shouldDrawCoordinateGrid()) {

			@ColorInt int gridColor = 0x33000000;
			@ColorInt int gridOriginColor = 0xFFFF3333;

			overlayPaint.setStrokeWidth(0);
			overlayPaint.setColor(0x33000000);

			float startX = viewportCanvasRect.left / coordinateGridSize;
			startX = viewportCanvasRect.left - ((startX - (float) Math.floor(startX)) * coordinateGridSize);

			float startY = viewportCanvasRect.top / coordinateGridSize;
			startY = viewportCanvasRect.top - ((startY - (float) Math.floor(startY)) * coordinateGridSize);

			float endX = startX + viewportCanvasRect.width() + coordinateGridSize;
			float endY = startY + viewportCanvasRect.height() + coordinateGridSize;

			for (float x = startX; x <= endX; x += coordinateGridSize) {
				overlayPaint.setColor(Math.abs(x) < coordinateGridSize / 2 ? gridOriginColor : gridColor);
				canvas.drawLine(x, viewportCanvasRect.top, x, viewportCanvasRect.bottom, overlayPaint);
			}

			for (float y = startY; y <= endY; y += coordinateGridSize) {
				overlayPaint.setColor(Math.abs(y) < coordinateGridSize / 2 ? gridOriginColor : gridColor);
				canvas.drawLine(viewportCanvasRect.left, y, viewportCanvasRect.right, y, overlayPaint);
			}
		}
	}

}
