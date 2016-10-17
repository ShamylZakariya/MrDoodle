package org.zakariya.doodle.model;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.MotionEventCompat;
import android.util.Log;
import android.view.MotionEvent;

import org.zakariya.doodle.view.DoodleView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

import icepick.Icepick;
import icepick.State;

/**
 * Created by shamyl on 8/11/15.
 */
public abstract class Doodle {

	public interface EditListener {
		void onDoodleEdited(Doodle doodle);
	}

	private static final String TAG = "Doodle";

	protected static final float SCALE_MIN = 0.5f;
	protected static final float SCALE_MAX = 5.0f;
	protected static final float TRANSLATION_MAX = 20000f;

	protected Matrix screenToCanvasMatrix = new Matrix();
	protected Matrix canvasToScreenMatrix = new Matrix();
	protected RectF viewportScreenRect = new RectF();
	protected RectF viewportCanvasRect = new RectF();

	@State
	boolean transformRangeClampingEnabled = true;

	@State
	boolean dirty = false;

	@State
	float canvasScale = 1f;

	@State
	PointF canvasTranslation = new PointF(0, 0);

	@State
	boolean readOnly = false;

	private Brush brush;
	private int width, height;
	private int backgroundColor = 0xFFFFFFFF;
	private WeakReference<DoodleView> doodleViewWeakReference;
	private Set<EditListener> editListeners = new HashSet<>();

	public void setDoodleView(DoodleView doodleView) {
		doodleViewWeakReference = new WeakReference<>(doodleView);
	}

	public void addChangeListener(EditListener listener) {
		editListeners.add(listener);
	}

	public void removeChangeListener(EditListener listener) {
		editListeners.remove(listener);
	}

	@Nullable
	public DoodleView getDoodleView() {
		if (doodleViewWeakReference != null) {
			return doodleViewWeakReference.get();
		}

		return null;
	}

	public void invalidate() {
		DoodleView dv = getDoodleView();
		if (dv != null) {
			dv.invalidate();
		}
	}

	public void invalidate(RectF rect) {
		DoodleView dv = getDoodleView();
		if (dv != null) {
			dv.invalidate((int) rect.left, (int) rect.top, (int) rect.right, (int) rect.bottom);
		}
	}

	public void clear() {
		markDirty();
	}

	public void undo() {
		markDirty();
	}

	public abstract void draw(Canvas canvas);

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
			setCanvasScaleAndTranslation(s,tx,ty);
		}
	}


	public void resize(int newWidth, int newHeight) {
		width = newWidth;
		height = newHeight;

		viewportScreenRect.left = 0;
		viewportScreenRect.top = 0;
		viewportScreenRect.right = newWidth;
		viewportScreenRect.bottom = newHeight;

		updateMatrices();
		invalidate();
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public void setBrush(Brush brush) {
		this.brush = brush;
		this.brush.setScale(1/getCanvasScale());
	}

	public boolean isReadOnly() {
		return readOnly;
	}

	public void setReadOnly(boolean readOnly) {
		this.readOnly = readOnly;
	}

	public Brush getBrush() {
		return brush;
	}

	public void onCreate(Bundle savedInstanceState) {
		Icepick.restoreInstanceState(this, savedInstanceState);
		updateMatrices();
	}

	public void onSaveInstanceState(Bundle outState) {
		Icepick.saveInstanceState(this, outState);
	}

	/**
	 * Serialize the doodle drawing state
	 *
	 * @param out the output stream into which to stuff the doodle's drawing
	 */
	public abstract void serialize(OutputStream out);


	/**
	 * Serialize the doodle drawing state to a byte array
	 * @return byte array of doodle
	 */
	public byte[] serialize() {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		serialize(stream);
		return stream.toByteArray();
	}

	/**
	 * Inflate the doodle drawing state
	 *
	 * @param in the input stream from which to read the doodle
	 * @throws InvalidObjectException
	 */
	public abstract void inflate(InputStream in) throws InvalidObjectException;


	/**
	 * Inflate doodle drawing state from a byte array
	 * @param bytes byte data
	 * @throws InvalidObjectException
	 */
	public void inflate(byte []bytes) throws InvalidObjectException {
		ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
		inflate(stream);
	}

	public boolean onTouchEvent(@NonNull MotionEvent event) {
		int action = MotionEventCompat.getActionMasked(event);
		switch (action) {
			case MotionEvent.ACTION_DOWN:
				onTouchEventBegin(event);
				return true;
			case MotionEvent.ACTION_POINTER_DOWN:
				onTouchEventBegin(event);
				return true;
			case MotionEvent.ACTION_POINTER_UP:
				onTouchEventEnd(event);
				return true;
			case MotionEvent.ACTION_UP:
				onTouchEventEnd(event);
				return true;
			case MotionEvent.ACTION_MOVE:
				onTouchEventMove(event);
				return true;
		}

		return false;
	}

	protected abstract void onTouchEventBegin(@NonNull MotionEvent event);

	protected abstract void onTouchEventMove(@NonNull MotionEvent event);

	protected abstract void onTouchEventEnd(@NonNull MotionEvent event);

	public int getBackgroundColor() {
		return backgroundColor;
	}

	public void setBackgroundColor(int backgroundColor) {
		this.backgroundColor = backgroundColor;
	}

	/**
	 * Mark that this Doodle was modified in some way (drawing, etc)
	 */
	public void markDirty() {
		dirty = true;
		for (EditListener listener : editListeners) {
			listener.onDoodleEdited(this);
		}
	}

	/**
	 * Set whether this Doodle has been modified
	 *
	 * @param dirty if true, mark that this Doodle has been modified since it was loaded
	 */
	public void setDirty(boolean dirty) {
		this.dirty = dirty;
	}

	/**
	 * Check if this doodle was modified
	 *
	 * @return
	 */
	public boolean isDirty() {
		return dirty;
	}

	public boolean isTransformRangeClampingEnabled() {
		return transformRangeClampingEnabled;
	}

	public void setTransformRangeClampingEnabled(boolean transformRangeClampingEnabled) {
		this.transformRangeClampingEnabled = transformRangeClampingEnabled;
	}

	/**
	 * Get the current scale of the canvas
	 *
	 * @return
	 */
	public float getCanvasScale() {
		return canvasScale;
	}

	/**
	 * Set the scale of the canvas, where values less than 1 "shrink" the view of the canvas, and
	 * values > 1 "zoom in".
	 *
	 * @param canvasScale
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
			setCanvasScaleAndTranslation(1,0,0);
		}
	}

	/**
	 * @return the rect in the canvas's coordinate system which fits the canvas's content completely
	 */
	public abstract RectF getCanvasContentBoundingRect();

	/**
	 * Update the canvasToScreen and inverse screenToCanvas matrices and dependant
	 * element such as the viewportCanvasRect
	 * Note, translation is measured in screen coordinates.
	 */
	protected void updateMatrices() {

		canvasToScreenMatrix.reset();
		canvasToScreenMatrix.preTranslate(canvasTranslation.x, canvasTranslation.y);
		canvasToScreenMatrix.preScale(canvasScale, canvasScale);

		getBrush().setScale(1/canvasScale);

		screenToCanvasMatrix.reset();
		if (!canvasToScreenMatrix.invert(screenToCanvasMatrix)) {
			Log.e(TAG, "updateMatrices: Unable to invert canvasToScreenMatrix");
		}

		// update viewportCanvasRect
		screenToCanvasMatrix.mapRect(viewportCanvasRect, viewportScreenRect);
	}

}
