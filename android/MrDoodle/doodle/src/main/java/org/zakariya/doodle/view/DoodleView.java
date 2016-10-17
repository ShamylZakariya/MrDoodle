package org.zakariya.doodle.view;

import android.content.Context;
import android.graphics.Canvas;
import android.support.annotation.NonNull;
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

	private static final String TAG = "DoodleView";
	private Doodle doodle;
	private ViewTreeObserver.OnGlobalLayoutListener layoutListener;
	private List<SizeListener> sizeListeners = new ArrayList<>();
	private boolean readOnly;

	public DoodleView(Context context) {
		super(context);
	}

	public DoodleView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public Doodle getDoodle() {
		return doodle;
	}

	public void setDoodle(Doodle doodle) {
		this.doodle = doodle;
		doodle.setReadOnly(this.isReadOnly());
		doodle.setDoodleView(this);

		if (getWidth() > 0 && getHeight() > 0) {
			doodle.resize(getWidth(), getHeight());
		}

		invalidate();
	}

	public boolean isReadOnly() {
		return readOnly;
	}

	public void setReadOnly(boolean readOnly) {
		this.readOnly = readOnly;
		if (doodle != null) {
			doodle.setReadOnly(readOnly);
		}
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();

		if (layoutListener == null) {

			layoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
				@Override
				public void onGlobalLayout() {
					if (getWidth() != doodle.getWidth() || getHeight() != doodle.getHeight()) {
						doodle.resize(getWidth(), getHeight());
						for (SizeListener listener : sizeListeners) {
							listener.onDoodleViewResized(DoodleView.this, getWidth(), getHeight());
						}
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
		if (doodle != null) {
			doodle.draw(canvas);
		}
	}

	@Override
	public boolean onTouchEvent(@NonNull MotionEvent event) {
		if (doodle != null) {
			return doodle.onTouchEvent(event);
		} else {
			return super.onTouchEvent(event);
		}
	}

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
