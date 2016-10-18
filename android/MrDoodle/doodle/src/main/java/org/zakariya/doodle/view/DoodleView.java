package org.zakariya.doodle.view;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;

import org.zakariya.doodle.model.Doodle;

import java.util.ArrayList;
import java.util.List;

/**
 * DoodleView is an Android View which is suitable for embedding a DoodleCanvas, which in turns
 * embeds a Doodle. DoodleView is responsible for dispatching draw, resize and touch events to
 * the embedded DoodleCanvas.
 */
@SuppressWarnings("unused")
public class DoodleView extends View {

	private static final String TAG = "DoodleView";
	private static final long DOUBLE_TAP_DELAY_MILLIS = 350;

	public interface SizeListener {
		void onDoodleViewResized(DoodleView doodleView, int width, int height);
	}

	/**
	 * Interface for listeners interested in being notified when user makes two-finger taps.
	 * A client could, for example, use a two-finger double-tap as a cue to zoom fit the canvas.
	 */
	public interface TwoFingerTapListener {
		void onDoodleViewTwoFingerTap(DoodleView doodleView, int tapCount);
	}


	private ViewTreeObserver.OnGlobalLayoutListener layoutListener;
	private List<SizeListener> sizeListeners = new ArrayList<>();
	private DoodleCanvas doodleCanvas;
	private DoodleView.TwoFingerTapListener twoFingerTapListener;
	private int twoFingerTapCount;
	private boolean twoFingerTapCandidacyTimerStarted;
	private Handler twoFingerTapTimerHandler;


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
	 *
	 * @param doodleCanvas a DoodleCanvas
	 */
	public void setDoodleCanvas(DoodleCanvas doodleCanvas) {
		this.doodleCanvas = doodleCanvas;
		this.doodleCanvas.setDoodleView(this);

		if (getWidth() > 0 && getHeight() > 0) {
			this.doodleCanvas.resize(getWidth(), getHeight());
		}
	}

	/**
	 * @return the enclosed DoodleCanvas
	 */
	public DoodleCanvas getDoodleCanvas() {
		return doodleCanvas;
	}

	/**
	 * @return the assigned two-finger tap listener
	 */
	public DoodleView.TwoFingerTapListener getTwoFingerTapListener() {
		return twoFingerTapListener;
	}

	/**
	 * Assign a two-finger tap lister.
	 *
	 * @param twoFingerTapListener a listener to be notified when user performs a two-finger-tap gesture
	 */
	public void setTwoFingerTapListener(DoodleView.TwoFingerTapListener twoFingerTapListener) {
		this.twoFingerTapListener = twoFingerTapListener;
	}

	/**
	 * Add a size listener, which will be notified when a doodle is resized
	 *
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

	void dispatchTwoFingerTap() {

		if (twoFingerTapTimerHandler == null) {
			twoFingerTapTimerHandler = new Handler(Looper.getMainLooper());
		}

		Log.i(TAG, "dispatchTwoFingerTap: twoFingerTapCount: " + twoFingerTapCount);
		twoFingerTapCount++;

		if (!twoFingerTapCandidacyTimerStarted) {
			twoFingerTapCandidacyTimerStarted = true;
			twoFingerTapTimerHandler.postDelayed(new Runnable() {
				@Override
				public void run() {

				TwoFingerTapListener listener = getTwoFingerTapListener();
				if (listener != null) {
					Log.i(TAG, "dispatchTwoFingerTap - DISPATCHING twoFingerTapCount: " + twoFingerTapCount);
					listener.onDoodleViewTwoFingerTap(DoodleView.this, twoFingerTapCount);
				}

				twoFingerTapCount = 0;
				twoFingerTapCandidacyTimerStarted = false;

				}
			}, DOUBLE_TAP_DELAY_MILLIS);
		}
	}
}
