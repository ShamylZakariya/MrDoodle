package org.zakariya.doodle.view;

import android.content.Context;
import android.graphics.Canvas;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;

import org.zakariya.doodle.model.Doodle;

import java.util.ArrayList;
import java.util.List;

/**
 * Base view for doodling
 */
public class DoodleView extends View {

	public interface SizeListener {
		void onDoodleViewResized(DoodleView doodleView, int width, int height);
	}

	private ViewTreeObserver.OnGlobalLayoutListener layoutListener;
	private List<SizeListener> sizeListeners = new ArrayList<>();
	private DoodleCanvas doodleCanvas;

	public DoodleView(Context context) {
		super(context);
	}

	public DoodleView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Nullable
	public Doodle getDoodle() {
		return doodleCanvas != null ? doodleCanvas.getDoodle() : null;
	}

	/**
	 * Assigns a DoodleCanvas, which in turn manages a Doodle's viewport/scale, etc and
	 * dispatches draw and touch events.
	 * @param doodleCanvas a DoodleCanvas
	 */
	public void setDoodleCanvas(DoodleCanvas doodleCanvas) {
		this.doodleCanvas = doodleCanvas;
		this.doodleCanvas.setDoodleView(this);

		if (getWidth() > 0 && getHeight() > 0) {
			this.doodleCanvas.resize(getWidth(), getHeight());
		}
	}

	public DoodleCanvas getDoodleCanvas() {
		return doodleCanvas;
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();

		if (layoutListener == null) {

			layoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
				@Override
				public void onGlobalLayout() {
					if (doodleCanvas != null) {
						doodleCanvas.resize(getWidth(), getHeight());
					}
					for (SizeListener listener : sizeListeners) {
						listener.onDoodleViewResized(DoodleView.this, getWidth(), getHeight());
					}
				}
			};
		}

		getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
	}

	@Override
	protected void onDetachedFromWindow() {
		if (layoutListener != null) {
			getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
		}
		super.onDetachedFromWindow();
	}

	@Override
	public void draw(Canvas canvas) {
		super.draw(canvas);
		if (doodleCanvas != null) {
			doodleCanvas.draw(canvas);
		}
	}

	@Override
	public boolean onTouchEvent(@NonNull MotionEvent event) {
		DoodleCanvas canvas = doodleCanvas;
		if (canvas != null) {
			return canvas.onTouchEvent(event);
		} else {
			return super.onTouchEvent(event);
		}
	}

	/**
	 * Add a size listener, which will be notified when a doodle is resized
	 * @param listener a listener to be notified of doodle size changes
	 */
	public void addSizeListener(SizeListener listener) {
		sizeListeners.add(listener);
	}

	public void removeSizeListener(SizeListener listener) {
		sizeListeners.remove(listener);
	}

	public void clearSizeListeners() {
		sizeListeners.clear();
	}

}
