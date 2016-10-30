package org.zakariya.doodle.view;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.MotionEventCompat;
import android.util.Log;
import android.view.MotionEvent;

import org.zakariya.doodle.model.Doodle;

/**
 * DoodleCanvas is a "presenter" for Doodle instances. The DoodleCanvas
 * represents a viewport, translation, and scaling. The DoodleCanvas also
 * dispatches touch events, handling two-finger touches which aren't consumed
 * by the doodle.
 * <p>
 * Generally, one will create a DoodleView, assign a DoodleCanvas to it, and finally
 * assign a doodle to the DoodleCanvas.
 * <p>
 * DoodleCanvas is parcelable, and intended to be stateful during screen rotations.
 * It will maintain the center of the viewport through rotations.
 */

@SuppressWarnings({"WeakerAccess", "unused"})
public class DoodleCanvas implements Parcelable {

	public static final int EDGE_TOP = 1;
	public static final int EDGE_RIGHT = 1 << 1;
	public static final int EDGE_BOTTOM = 1 << 2;
	public static final int EDGE_LEFT = 1 << 3;


	private static final String TAG = "DoodleCanvas";

	private static final float TRANSLATION_MAX = 24000f;

	// this is state that must persist
	private boolean drawInvalidationRect = false;
	private boolean drawCoordinateGrid = false;
	private boolean drawViewport = false;
	private boolean drawCanvasContentBoundingRect = false;
	private boolean transformRangeClampingEnabled = true;
	private float canvasScale = 1f;
	private PointF canvasTranslation = new PointF(0, 0);
	private PointF canvasOriginRelativeToViewportCenter = null;
	private float coordinateGridSize = 100;
	private float minPinchTranslationForTap = 12f;
	private float minPinchScalingForTap = 0.2f;
	private float minCanvasScale = 0.125f;
	private float maxCanvasScale = 16.0f;
	private int disabledEdgeSwipeMask = 0;
	private float disabledEdgeWidth = 0;


	private Matrix screenToCanvasMatrix = new Matrix();
	private Matrix canvasToScreenMatrix = new Matrix();
	private RectF viewportScreenRect = new RectF();
	private RectF viewportCanvasRect = new RectF();
	private DoodleView doodleView;
	private Doodle doodle;
	private Paint overlayPaint;
	private RectF invalidationRect;

	// touch state
	private boolean touchDiscarded = false;
	private boolean performingPinchOperations = false;
	private PointF pinchLastCenterScreen;
	private float pinchLastLengthScreen;
	private float totalPinchTranslation;
	private float totalPinchScaling;


	/**
	 * Create a default DoodleCanvas. You will need to assign a doodle.
	 */
	public DoodleCanvas() {
		overlayPaint = new Paint();
		overlayPaint.setAntiAlias(true);
		overlayPaint.setStrokeWidth(1);
		overlayPaint.setStyle(Paint.Style.STROKE);
	}

	/**
	 * Create a DoodleCanvas with a Doodle
	 *
	 * @param doodle the presented doodle
	 */
	public DoodleCanvas(Doodle doodle) {
		this();
		setDoodle(doodle);
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

		DoodleView view = getDoodleView();
		if (view != null) {
			if (view.getWidth() > 0 && view.getHeight() > 0) {
				resize(view.getWidth(), view.getHeight());
			}
		}
	}

	/**
	 * Assign the doodle view enclosing this doodle.
	 *
	 * @param view the wrapping DoodleView
	 */
	public void setDoodleView(DoodleView view) {
		doodleView = view;
	}

	/**
	 * @return the enclosing DoodleView, if there is one
	 */
	@Nullable
	public DoodleView getDoodleView() {
		return doodleView;
	}

	/**
	 * Notify the enclosing DoodleView to repaint the entire view.
	 */
	public void invalidate() {
		DoodleView view = getDoodleView();
		if (view != null) {
			view.invalidate();
		}
	}

	/**
	 * Notify the enclosing DoodleView to repaint a sub rect of the view
	 *
	 * @param rect a dirty rect to update
	 */
	public void invalidate(RectF rect) {
		invalidationRect = rect;
		DoodleView dv = getDoodleView();
		if (dv != null) {
			dv.invalidate((int) rect.left, (int) rect.top, (int) rect.right, (int) rect.bottom);
		}
	}

	/**
	 * Draw the enclosed Doodle into the provided canvas with the current transform
	 *
	 * @param canvas a canvas to draw into
	 */
	public void draw(Canvas canvas) {

		this.doodle.draw(canvas);

		if (shouldDrawCoordinateGrid() || shouldDrawViewport() || shouldDrawCanvasContentBoundingRect()) {
			canvas.save();
			canvas.concat(canvasToScreenMatrix);
			drawCoordinateGrid(canvas);
			drawViewport(canvas);
			drawCanvasContentBoundingRect(canvas);
			canvas.restore();
		}

		drawInvalidationRect(canvas);
	}

	/**
	 * Draw the enclosed doodle with constraints. This functionality exists mostly for thumbnail rendering. While this method resizes and assigns a new transform, the previous size and transform will be re-applied afterwards.
	 *
	 * @param canvas      a canvas to draw into
	 * @param width       width of canvas
	 * @param height      height of canvas
	 * @param fitContents if true, a transform will be set which guarantees contents will be center-fit to the width/height
	 * @param fitPadding  amount of padding in pixels around the doodle (if fitContents is true)
	 */
	public void draw(Canvas canvas, int width, int height, boolean fitContents, float fitPadding) {
		boolean clamped = isCanvasScaleClamped();
		setCanvasScaleClamped(false);
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
		setCanvasScaleClamped(clamped);
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

	/**
	 * Resize the canvas and internal viewport. Will be forwarded to doodle, which may use width/height
	 * info to generate bitmap backing stores, etc.
	 *
	 * @param newWidth  new width of canvas
	 * @param newHeight new height of canvas
	 */
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

	/**
	 * @return the width of the canvas
	 */
	public int getWidth() {
		return (int) viewportScreenRect.right;
	}

	/**
	 * @return the height of the canvas
	 */
	public int getHeight() {
		return (int) viewportScreenRect.bottom;
	}

	/**
	 * @return true if the canvas is drawing invalidation rect overlays
	 */
	public boolean shouldDrawInvalidationRect() {
		return drawInvalidationRect;
	}

	/**
	 * For debugging purposes, you may want to see the rect passed to DoodleCanvas::invalidate(Rect)
	 *
	 * @param drawInvalidationRect if true, draw invalidation rect when updating
	 */
	public void setDrawInvalidationRect(boolean drawInvalidationRect) {
		this.drawInvalidationRect = drawInvalidationRect;
		invalidate();
	}

	/**
	 * @return true if canvas is set to draw the coordinate grid
	 */
	public boolean shouldDrawCoordinateGrid() {
		return drawCoordinateGrid;
	}

	/**
	 * Turn on or off the drawing of a coordinate grid
	 *
	 * @param drawCoordinateGrid if true, draw the coordinate grid
	 */
	public void setDrawCoordinateGrid(boolean drawCoordinateGrid) {
		this.drawCoordinateGrid = drawCoordinateGrid;
		invalidate();
	}

	/**
	 * @return true if drawing of the viewport is enabled (generally for debugging purposes)
	 */
	public boolean shouldDrawViewport() {
		return drawViewport;
	}

	/**
	 * Turn on or off the drawing of the viewport (generally for debugging purposes)
	 *
	 * @param drawViewport if true, draw the viewport
	 */
	public void setDrawViewport(boolean drawViewport) {
		this.drawViewport = drawViewport;
		invalidate();
	}

	/**
	 * @return true if canvas is set to draw the bounding rectangle of canvas contents
	 */
	public boolean shouldDrawCanvasContentBoundingRect() {
		return drawCanvasContentBoundingRect;
	}

	/**
	 * Turn on or off the drawing of a bounding rect around the canvas contents (generally for debugging purposes)
	 *
	 * @param drawCanvasContentBoundingRect if true, draw content bounding rect
	 */
	public void setDrawCanvasContentBoundingRect(boolean drawCanvasContentBoundingRect) {
		this.drawCanvasContentBoundingRect = drawCanvasContentBoundingRect;
		invalidate();
	}

	/**
	 * @return the size of the coordinate grid
	 */
	public float getCoordinateGridSize() {
		return coordinateGridSize;
	}

	/**
	 * If drawing the coordinate grid (shouldDrawCoordinateGrid()), set its size.
	 *
	 * @param coordinateGridSize the size of the coordinate grid
	 */
	public void setCoordinateGridSize(float coordinateGridSize) {
		this.coordinateGridSize = coordinateGridSize;
		invalidate();
	}

	public float getMinPinchTranslationForTap() {
		return minPinchTranslationForTap;
	}

	/**
	 * Since fingers are big and fat, this is the scaling fudge factor for two-finger touch
	 * wiggle to be considered a tap. I.e, since touching with two fingers initiates a translation operation,
	 * this is the minimum amount of translation allowed to trigger a tap when the two-fingers are lifted.
	 *
	 * @param minPinchTranslationForTap
	 */
	public void setMinPinchTranslationForTap(float minPinchTranslationForTap) {
		this.minPinchTranslationForTap = minPinchTranslationForTap;
	}

	public float getMinPinchScalingForTap() {
		return minPinchScalingForTap;
	}

	/**
	 * Since fingers are big and fat, this is the scaling fudge factor for two-finger touch
	 * wiggle to be considered a tap. I.e, since touching with two fingers initiates a scaling operation,
	 * this is the minimum amount of scale allowed to trigger a tap when the two-fingers are lifted.
	 *
	 * @param minPinchScalingForTap
	 */
	public void setMinPinchScalingForTap(float minPinchScalingForTap) {
		this.minPinchScalingForTap = minPinchScalingForTap;
	}

	/**
	 * @return Minimum scale allowed for the canvas, if canvas scale is clamped.
	 */
	public float getMinCanvasScale() {
		return minCanvasScale;
	}

	/**
	 * Set the minimum allowable scale for the canvas. This only applies if scale is clamped.
	 *
	 * @param minCanvasScale the minimum allowable scale for the canvas
	 */
	public void setMinCanvasScale(float minCanvasScale) {
		this.minCanvasScale = minCanvasScale;
		setCanvasScale(getCanvasScale());
	}

	/**
	 * @return Maximum scale allowed for the canvas, if canvas scale is clamped.
	 */
	public float getMaxCanvasScale() {
		return maxCanvasScale;
	}

	/**
	 * Set the maximum allowable scale for the canvas. This only applies if scale is clamped.
	 *
	 * @param maxScale the minimum allowable scale for the canvas
	 */
	public void setMaxCanvasScale(float maxScale) {
		this.maxCanvasScale = maxScale;
		setCanvasScale(getCanvasScale());
	}

	/**
	 * Turn on or off the clamping of canvas scale the the min/max set via setMinCanvasScale and setMaxCanvasScale.
	 *
	 * @param transformRangeClampingEnabled if true, transforms will clamp scale to the current min/max
	 */
	public void setCanvasScaleClamped(boolean transformRangeClampingEnabled) {
		this.transformRangeClampingEnabled = transformRangeClampingEnabled;
	}

	public boolean isCanvasScaleClamped() {
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
		return transformRangeClampingEnabled ? Math.min(Math.max(s, minCanvasScale), maxCanvasScale) : s;
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

	/**
	 * @return the matrix that transforms screen coordinates to the canvas/doodle coordinates
	 */
	public Matrix getScreenToCanvasMatrix() {
		return screenToCanvasMatrix;
	}

	/**
	 * @return the matrix that transforms canvas/doodle coordinates to screen coordinates
	 */
	public Matrix getCanvasToScreenMatrix() {
		return canvasToScreenMatrix;
	}

	/**
	 * @return the current viewport rect (generally, (0,0,getWidth(),getHeight()))
	 */
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
	 * Disable edge swipes from edges specified in the mask
	 *
	 * @param mask a logical or of EDGE_TOP, EDGE_RIGHT, EDGE_BOTTOM and EDGE_RIGHT
	 */
	public void setDisabledEdgeSwipeMask(int mask) {
		this.disabledEdgeSwipeMask = mask;
	}

	/**
	 * @return the disabled edge swipe mask
	 */
	public int getDisabledEdgeSwipeMask() {
		return this.disabledEdgeSwipeMask;
	}

	/**
	 * Get the width, in pixels, of the disabled edge region around the canvas
	 * which will prevent new touch events from being consumed.
	 *
	 * @return the width of the disabled region that  prevents touches from starting
	 */
	public float getDisabledEdgeWidth() {
		return disabledEdgeWidth;
	}

	/**
	 * @param disabledEdgeWidth the width of the disabled region around the edge that prevents touches from starting.
	 */
	public void setDisabledEdgeWidth(float disabledEdgeWidth) {
		this.disabledEdgeWidth = disabledEdgeWidth;
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

		// this is required to maintain viewport center location during rotations
		canvasOriginRelativeToViewportCenter = new PointF();
		canvasOriginRelativeToViewportCenter.x = getCanvasTranslationX() - viewportScreenRect.centerX();
		canvasOriginRelativeToViewportCenter.y = getCanvasTranslationY() - viewportScreenRect.centerY();

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
		touchDiscarded = false;
		if (disabledEdgeSwipeMask != 0) {
			if ((disabledEdgeSwipeMask & EDGE_TOP) == EDGE_TOP) {
				if (event.getY() < viewportScreenRect.top + disabledEdgeWidth) {
					touchDiscarded = true;
					return false;
				}
			}

			if ((disabledEdgeSwipeMask & EDGE_RIGHT) == EDGE_RIGHT) {
				if (event.getX() > viewportScreenRect.right - disabledEdgeWidth) {
					touchDiscarded = true;
					return false;
				}
			}

			if ((disabledEdgeSwipeMask & EDGE_BOTTOM) == EDGE_BOTTOM) {
				if (event.getY() > viewportScreenRect.bottom - disabledEdgeWidth) {
					touchDiscarded = true;
					return false;
				}
			}

			if ((disabledEdgeSwipeMask & EDGE_LEFT) == EDGE_LEFT) {
				if (event.getX() < viewportScreenRect.left + disabledEdgeWidth) {
					touchDiscarded = true;
					return false;
				}
			}
		}


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

		if (touchDiscarded) {
			return false;
		}

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

		// reset touchDiscardedFlag
		if (touchDiscarded) {
			touchDiscarded = false;
			return false;
		}

		// a two-finger pinch gesture ended
		if (performingPinchOperations) {

			if (event.getPointerCount() == 2 && totalPinchScaling < minPinchScalingForTap && totalPinchTranslation < minPinchTranslationForTap) {
				DoodleView doodleView = getDoodleView();
				if (doodleView != null) {
					doodleView.dispatchTwoFingerTap();
				}
			}

			if (event.getPointerCount() == 1) {
				// mark that we're done with multitouch pinch ops
				pinchLastCenterScreen = null;
				performingPinchOperations = false;
			}
		}

		return doodle.onTouchEventEnd(event);
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

	// Parcelable

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeByte(this.drawInvalidationRect ? (byte) 1 : (byte) 0);
		dest.writeByte(this.drawCoordinateGrid ? (byte) 1 : (byte) 0);
		dest.writeByte(this.drawViewport ? (byte) 1 : (byte) 0);
		dest.writeByte(this.drawCanvasContentBoundingRect ? (byte) 1 : (byte) 0);
		dest.writeByte(this.transformRangeClampingEnabled ? (byte) 1 : (byte) 0);
		dest.writeFloat(this.canvasScale);
		dest.writeParcelable(this.canvasTranslation, flags);
		dest.writeParcelable(this.canvasOriginRelativeToViewportCenter, flags);
		dest.writeFloat(this.coordinateGridSize);
		dest.writeFloat(this.minPinchTranslationForTap);
		dest.writeFloat(this.minPinchScalingForTap);
		dest.writeFloat(this.minCanvasScale);
		dest.writeFloat(this.maxCanvasScale);
		dest.writeInt(this.disabledEdgeSwipeMask);
		dest.writeFloat(this.disabledEdgeWidth);
	}

	protected DoodleCanvas(Parcel in) {
		this.drawInvalidationRect = in.readByte() != 0;
		this.drawCoordinateGrid = in.readByte() != 0;
		this.drawViewport = in.readByte() != 0;
		this.drawCanvasContentBoundingRect = in.readByte() != 0;
		this.transformRangeClampingEnabled = in.readByte() != 0;
		this.canvasScale = in.readFloat();
		this.canvasTranslation = in.readParcelable(PointF.class.getClassLoader());
		this.canvasOriginRelativeToViewportCenter = in.readParcelable(PointF.class.getClassLoader());
		this.coordinateGridSize = in.readFloat();
		this.minPinchTranslationForTap = in.readFloat();
		this.minPinchScalingForTap = in.readFloat();
		this.minCanvasScale = in.readFloat();
		this.maxCanvasScale = in.readFloat();
		this.disabledEdgeSwipeMask = in.readInt();
		this.disabledEdgeWidth = in.readFloat();
	}

	public static final Creator<DoodleCanvas> CREATOR = new Creator<DoodleCanvas>() {
		@Override
		public DoodleCanvas createFromParcel(Parcel source) {
			return new DoodleCanvas(source);
		}

		@Override
		public DoodleCanvas[] newArray(int size) {
			return new DoodleCanvas[size];
		}
	};
}
