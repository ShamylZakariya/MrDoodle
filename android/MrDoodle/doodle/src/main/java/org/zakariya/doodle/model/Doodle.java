package org.zakariya.doodle.model;

import android.graphics.Canvas;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.MotionEvent;

import org.zakariya.doodle.view.DoodleCanvas;
import org.zakariya.doodle.view.DoodleView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by shamyl on 8/11/15.
 */
public abstract class Doodle {

	public interface EditListener {
		void onDoodleEdited(Doodle doodle);
	}

	private static final String TAG = "Doodle";

	private boolean dirty = false;
	private boolean readOnly = false;
	private Brush brush;
	private int backgroundColor = 0xFFFFFFFF;
	private Set<EditListener> editListeners = new HashSet<>();
	private DoodleCanvas doodleCanvas;


	public void addChangeListener(EditListener listener) {
		editListeners.add(listener);
	}

	public void removeChangeListener(EditListener listener) {
		editListeners.remove(listener);
	}

	public void setDoodleCanvas(DoodleCanvas doodleCanvas) {
		this.doodleCanvas = doodleCanvas;
	}

	protected DoodleCanvas getDoodleCanvas() {
		return doodleCanvas;
	}

	@Nullable
	private DoodleView getDoodleView() {
		return doodleCanvas.getDoodleView();
	}

	void invalidate() {
		DoodleCanvas canvas = getDoodleCanvas();
		if (canvas != null) {
			canvas.invalidate();
		}
	}

	void invalidate(RectF rect) {
		DoodleCanvas canvas = getDoodleCanvas();
		if (canvas != null) {
			canvas.invalidate(rect);
		}
	}

	public void clear() {
		markDirty();
	}

	public void undo() {
		markDirty();
	}

	public abstract void draw(Canvas canvas);

	public abstract void resize(int newWidth, int newHeight);

	public void canvasMatricesUpdated() {
		this.brush.setScale(1 / getCanvasScale());
	}

	public abstract void onLoadInstanceState(Bundle savedInstanceState);
	public abstract void onSaveInstanceState(Bundle outState);

	public void setBrush(Brush brush) {
		this.brush = brush;
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

	public abstract boolean onTouchEventBegin(@NonNull MotionEvent event);

	public abstract boolean onTouchEventMove(@NonNull MotionEvent event);

	public abstract boolean onTouchEventEnd(@NonNull MotionEvent event);


	public int getBackgroundColor() {
		return backgroundColor;
	}

	public void setBackgroundColor(int backgroundColor) {
		this.backgroundColor = backgroundColor;
		invalidate();
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

	/**
	 * @return the current scale of the canvas presenting this Doodle
	 */
	public float getCanvasScale() {
		return doodleCanvas.getCanvasScale();
	}

	/**
	 * @return the canvas panning translation
	 */
	public PointF getCanvasTranslation() {
		return doodleCanvas.getCanvasTranslation();
	}

	/**
	 * @return canvas X translation in screen pixels (independent of canvas scale)
	 */
	public float getCanvasTranslationX() {
		return doodleCanvas.getCanvasTranslation().x;
	}

	/**
	 * @return canvas Y translation in screen pixels (independent of canvas scale)
	 */
	public float getCanvasTranslationY() {
		return doodleCanvas.getCanvasTranslation().y;
	}

	/**
	 * Get the viewport rect on the canvas in canvas coordinates.
	 *
	 * @return the rect describing the view on the canvas in the canvas's coordinate system
	 */
	public RectF getViewportCanvasRect() {
		return getDoodleCanvas().getViewportCanvasRect();
	}

	/**
	 * @return the rect in the canvas's coordinate system which fits the canvas's content completely
	 */
	public abstract RectF getCanvasContentBoundingRect();

}
