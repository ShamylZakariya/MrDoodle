package org.zakariya.doodle.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.util.TypedValue;
import android.view.MotionEvent;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoSerializable;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import org.zakariya.doodle.geom.IncrementalInputStrokeTessellator;
import org.zakariya.doodle.geom.InputStroke;
import org.zakariya.doodle.geom.InputStrokeTessellator;

import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.OutputStream;
import java.util.ArrayList;

import icepick.Icepick;
import icepick.State;

public class StrokeDoodle extends Doodle implements IncrementalInputStrokeTessellator.Listener {
	private static final String TAG = "StrokeDoodle";

	private static final int COOKIE = 0xD00D;
	private static final float DEFAULT_GRID_SIZE_DP = 128;

	private static final long DOUBLE_TAP_DELAY_MILLIS = 350;
	private static final float DOUBLE_TAP_MIN_TRANSLATION_DP = 4;
	private static final float DOUBLE_TAP_MIN_SCALING = 0.2f;

	/**
	 * Interface for listeners interested in being notified when user makes two-finger taps.
	 * A client could, for example, use a two-finger double-tap as a cue to zoom fit the canvas.
	 */
	public interface TwoFingerTapListener {
		void onTwoFingerTap(int tapCount);
	}

	private Paint overlayPaint, backingStoreBitmapPaint;
	private RectF invalidationRect;
	private RectF canvasContentBoundingRect = new RectF();
	private IncrementalInputStrokeTessellator incrementalInputStrokeTessellator;
	private Context context;
	private Canvas bitmapCanvas;
	private Bitmap backingStoreBitmap;
	private boolean needsUpdateBackingStore = false;
	private float coordinateGridSize = 100;
	private InputStrokeTessellator tessellator = new InputStrokeTessellator();

	// one finger touch state
	private float[] strokeTouchPoint = {0f, 0f};

	// two finger touch state
	private boolean performingPinchOperations = false;
	private PointF pinchLastCenterScreen;
	private float pinchLastLengthScreen;
	private float totalPinchTranslation;
	private float totalPinchScaling;
	private float minPinchTranslationForTap;
	private float minPinchScalingForTap;
	private int doubleTwoFingerTapCount;
	private boolean doubleTwoFingerTapCandidacyTimerStarted;
	private Handler doubleTwoFingerTapTimerHandler;
	private TwoFingerTapListener twoFingerTapListener;

	@State
	boolean drawInvalidationRect = false;

	@State
	boolean drawCoordinateGrid = false;

	@State
	boolean drawViewport = false;

	@State
	boolean drawCanvasContentBoundingRect = false;

	@State
	ArrayList<IntermediateDrawingStep> drawingSteps = new ArrayList<>();

	@State
	PointF canvasOriginRelativeToViewportCenter = null;

	public StrokeDoodle(Context context) {
		this.context = context;

		overlayPaint = new Paint();
		overlayPaint.setAntiAlias(true);
		overlayPaint.setStrokeWidth(1);
		overlayPaint.setStyle(Paint.Style.STROKE);

		backingStoreBitmapPaint = new Paint();
		backingStoreBitmapPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));

		setBrush(new Brush(0xFF000000, 1, 1, 100, false));
		setCoordinateGridSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DEFAULT_GRID_SIZE_DP, context.getResources().getDisplayMetrics()));

		minPinchTranslationForTap = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DOUBLE_TAP_MIN_TRANSLATION_DP, context.getResources().getDisplayMetrics());
		minPinchScalingForTap = DOUBLE_TAP_MIN_SCALING;
	}

	public StrokeDoodle(Context context, InputStream serializedForm) throws InvalidObjectException {
		this(context);
		inflate(serializedForm);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Icepick.restoreInstanceState(this, savedInstanceState);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {

		// we want to retain canvasOriginRelativeToViewportCenter so that we can
		// re-center content after a rotation/resize.
		canvasOriginRelativeToViewportCenter = new PointF();
		canvasOriginRelativeToViewportCenter.x = getCanvasTranslationX() - viewportScreenRect.centerX();
		canvasOriginRelativeToViewportCenter.y = getCanvasTranslationY() - viewportScreenRect.centerY();

		Icepick.saveInstanceState(this, outState);
		super.onSaveInstanceState(outState);
	}

	@Override
	public void serialize(OutputStream out) {
		Output output = new Output(out);

		Kryo kryo = new Kryo();
		kryo.writeObject(output, COOKIE);
		kryo.writeObject(output, drawingSteps);

		output.close();
	}

	@Override
	public void inflate(InputStream in) throws InvalidObjectException {
		Input input = new Input(in);
		Kryo kryo = new Kryo();

		int cookie = kryo.readObject(input, Integer.class);
		if (cookie == COOKIE) {
			//noinspection unchecked
			drawingSteps = kryo.readObject(input, ArrayList.class);
			updateBackingStore();
			setDirty(false);
			invalidate();

		} else {
			throw new InvalidObjectException("Missing COOKIE header (0x" + Integer.toString(COOKIE, 16) + ")");
		}
	}

	@Override
	public void clear() {
		canvasContentBoundingRect = new RectF(); // mark empty
		incrementalInputStrokeTessellator = null;
		drawingSteps.clear();
		needsUpdateBackingStore = true;
		invalidate();

		super.clear();
	}

	@Override
	public void undo() {
		if (!drawingSteps.isEmpty()) {
			drawingSteps.remove(drawingSteps.size() - 1);
		}

		// invalidation
		updateCanvasContentBoundingRect();
		needsUpdateBackingStore = true;
		invalidate();

		super.undo();
	}

	protected void drawActiveStroke(Canvas canvas) {
		// draw the active end of the current stroke
		// note: Android's hardware renderer triggers a bug in path rendering when
		// the canvas has a matrix applied to it. Paths end up pixelated
		// when scale is > 1. So, we need to transform the individual path
		// and not apply a scale to the canvas. Fortunately, there's only one
		// active stroke, and it's usually not long...

		if (incrementalInputStrokeTessellator != null && !getBrush().isEraser()) {
			Path path = incrementalInputStrokeTessellator.getLivePath();
			if (path != null && !path.isEmpty()) {
				Path transformed = new Path(path);
				transformed.transform(canvasToScreenMatrix);
				canvas.drawPath(transformed, getBrush().getPaint());
			}
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

	protected void renderDrawingSteps(Canvas canvas) {
		for (IntermediateDrawingStep step : drawingSteps) {
			tessellator.setMinWidth(step.brush.getMinWidth());
			tessellator.setMaxWidth(step.brush.getMaxWidth());
			tessellator.setMaxVelDPps(step.brush.getMaxWidthDpPs());

			for (InputStroke stroke : step.inputStrokes) {
				if (RectF.intersects(viewportCanvasRect, stroke.computeBoundingRect())) {
					tessellator.setInputStroke(stroke);
					Path path = tessellator.tessellate(false, true, true);
					canvas.drawPath(path, step.brush.getPaint());
				}
			}
		}
	}

	protected void updateBackingStore() {
		if (backingStoreBitmap != null) {
			backingStoreBitmap.eraseColor(0x0);

			bitmapCanvas.save();
			bitmapCanvas.concat(canvasToScreenMatrix);

			renderDrawingSteps(bitmapCanvas);
			drawCoordinateGrid(bitmapCanvas);
			drawViewport(bitmapCanvas);
			drawCanvasContentBoundingRect(bitmapCanvas);

			bitmapCanvas.restore();
		}
	}

	@Override
	public void draw(Canvas canvas) {

		// draw background fill
		if (Color.alpha(getBackgroundColor()) > 0) {
			canvas.drawColor(getBackgroundColor());
		}

		// update the backing store bitmap (if needed) and then render it
		if (needsUpdateBackingStore) {
			updateBackingStore();
			needsUpdateBackingStore = false;
		}

		canvas.drawBitmap(backingStoreBitmap, 0, 0, backingStoreBitmapPaint);

		drawActiveStroke(canvas);
		drawInvalidationRect(canvas);
	}

	@Override
	public void resize(int newWidth, int newHeight) {
		super.resize(newWidth, newHeight);

		// rebuild backing store bitmap, if necessary
		if (backingStoreBitmap == null || (newWidth != backingStoreBitmap.getWidth() || newHeight != backingStoreBitmap.getHeight())) {
			backingStoreBitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);
			bitmapCanvas = new Canvas(backingStoreBitmap);
		}

		// if canvasOriginRelativeToViewportCenter is non-null, that means device rotated or screen resized
		// and we need to re-center content
		if (canvasOriginRelativeToViewportCenter != null) {
			setCanvasTranslation(
					viewportScreenRect.centerX() + canvasOriginRelativeToViewportCenter.x,
					viewportScreenRect.centerY() + canvasOriginRelativeToViewportCenter.y
			);
		}
	}

	@Override
	public void centerCanvasContent() {
		updateCanvasContentBoundingRect();
		super.centerCanvasContent();
	}

	@Override
	public void fitCanvasContent(float padding) {
		updateCanvasContentBoundingRect();
		super.fitCanvasContent(padding);
	}

	@Override
	public RectF getCanvasContentBoundingRect() {
		if (canvasContentBoundingRect.isEmpty()) {
			updateCanvasContentBoundingRect();
		}
		return canvasContentBoundingRect;
	}

	@Override
	protected void updateMatrices() {
		needsUpdateBackingStore = true;
		super.updateMatrices();
	}

	@Override
	public void onInputStrokeModified(InputStroke inputStroke, int startIndex, int endIndex, RectF rect) {
		canvasToScreenMatrix.mapRect(rect);
		canvasContentBoundingRect.union(rect);
		invalidationRect = rect;
		invalidate(rect);
	}

	@Override
	public void onLivePathModified(Path path, RectF rect) {
		if (getBrush().isEraser()) {
			onNewStaticPathAvailable(path, rect);
		} else {
			canvasToScreenMatrix.mapRect(rect);
			invalidationRect = rect;
			invalidate(rect);
		}
	}

	@Override
	public void onNewStaticPathAvailable(Path path, RectF rect) {
		// draw path into bitmapCanvas
		bitmapCanvas.save();
		bitmapCanvas.concat(canvasToScreenMatrix);
		bitmapCanvas.drawPath(path, getBrush().getPaint());
		bitmapCanvas.restore();

		canvasToScreenMatrix.mapRect(rect);
		invalidationRect = rect;
		invalidate(rect);
	}

	@Override
	public float getInputStrokeOptimizationThreshold() {
		return 1.5f;
	}

	@Override
	public float getStrokeMinWidth() {
		return getBrush().getMinWidth();
	}

	@Override
	public float getStrokeMaxWidth() {
		return getBrush().getMaxWidth();
	}

	@Override
	public float getStrokeMaxVelDPps() {
		return getBrush().getMaxWidthDpPs();
	}

	public boolean shouldDrawCoordinateGrid() {
		return drawCoordinateGrid;
	}

	public void setDrawCoordinateGrid(boolean drawCoordinateGrid) {
		this.drawCoordinateGrid = drawCoordinateGrid;
		invalidate();
	}

	public boolean shouldDrawInvalidationRect() {
		return drawInvalidationRect;
	}

	public void setDrawInvalidationRect(boolean drawInvalidationRect) {
		this.drawInvalidationRect = drawInvalidationRect;
		invalidate();
	}

	public boolean shouldDrawViewport() {
		return drawViewport;
	}

	public void setDrawViewport(boolean drawViewport) {
		this.drawViewport = drawViewport;
		needsUpdateBackingStore = true;
		invalidate();
	}

	public boolean shouldDrawCanvasContentBoundingRect() {
		return drawCanvasContentBoundingRect;
	}

	public void setDrawCanvasContentBoundingRect(boolean drawCanvasContentBoundingRect) {
		this.drawCanvasContentBoundingRect = drawCanvasContentBoundingRect;
		needsUpdateBackingStore = true;
		invalidate();
	}

	public float getCoordinateGridSize() {
		return coordinateGridSize;
	}

	public void setCoordinateGridSize(float coordinateGridSize) {
		this.coordinateGridSize = coordinateGridSize;
	}

	public TwoFingerTapListener getTwoFingerTapListener() {
		return twoFingerTapListener;
	}

	public void setTwoFingerTapListener(TwoFingerTapListener twoFingerTapListener) {
		this.twoFingerTapListener = twoFingerTapListener;
	}

	public Context getContext() {
		return context;
	}

	@Override
	protected void onTouchEventBegin(@NonNull MotionEvent event) {

		// collect the touch location and timestamp. if user uses only one finger,
		// in onTouchEventMove we'll start the line

		if (event.getPointerCount() == 1) {
			strokeTouchPoint[0] = event.getX(0);
			strokeTouchPoint[1] = event.getY(0);
		} else if (event.getPointerCount() == 2) {

			// discard any drawing and prevent future drawing until gestures complete
			performingPinchOperations = true;
			incrementalInputStrokeTessellator = null;

			float[] touch0 = {event.getX(0), event.getY(0)};
			float[] touch1 = {event.getX(1), event.getY(1)};
			pinchLastCenterScreen = new PointF((touch0[0] + touch1[0]) / 2, (touch0[1] + touch1[1]) / 2);

			float pinchDx = touch0[0] - touch1[0];
			float pinchDy = touch0[1] - touch1[1];
			pinchLastLengthScreen = (float) Math.sqrt((pinchDx * pinchDx) + (pinchDy * pinchDy));

			// reset; we'll measure total scaling and translation for tap candidacy
			totalPinchScaling = 0;
			totalPinchTranslation = 0;
		}

	}

	protected void onTouchEventMove(@NonNull MotionEvent event) {

		if (event.getPointerCount() == 1 && !performingPinchOperations) {

			if (incrementalInputStrokeTessellator == null) {

				screenToCanvasMatrix.mapPoints(strokeTouchPoint);

				incrementalInputStrokeTessellator = new IncrementalInputStrokeTessellator(this);
				incrementalInputStrokeTessellator.add(strokeTouchPoint[0], strokeTouchPoint[1]);
			} else {

				// NOTE: We don't care about event.getHistorical{X,Y} - it prevents line smoothing
				strokeTouchPoint[0] = event.getX(0);
				strokeTouchPoint[1] = event.getY(0);

				screenToCanvasMatrix.mapPoints(strokeTouchPoint);
				incrementalInputStrokeTessellator.add(strokeTouchPoint[0], strokeTouchPoint[1]);
			}

		} else if (event.getPointerCount() >= 2) {
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
			totalPinchTranslation += Math.sqrt((tdx*tdx) + (tdy*tdy));
		}
	}


	protected void onTouchEventEnd(@NonNull MotionEvent event) {

		// a two-finger pinch gesture ended
		if (performingPinchOperations) {

			if (totalPinchScaling < minPinchScalingForTap && totalPinchTranslation < minPinchTranslationForTap) {
				handleTwoFingerTap();
			}

			if (event.getPointerCount() == 1){
				// mark that we're done with multitouch pinch ops
				pinchLastCenterScreen = null;
				performingPinchOperations = false;
				needsUpdateBackingStore = true;
			}
		}


		if (incrementalInputStrokeTessellator != null) {
			incrementalInputStrokeTessellator.finish();
			if (!incrementalInputStrokeTessellator.getStaticPaths().isEmpty()) {
				IntermediateDrawingStep step = new IntermediateDrawingStep(getBrush().copy(), incrementalInputStrokeTessellator.getInputStrokes());
				drawingSteps.add(step);

				canvasContentBoundingRect.union(step.computeBounds(tessellator));
			}

			// clean up
			incrementalInputStrokeTessellator = null;

			// mark redraw needed
			needsUpdateBackingStore = true;
		}
	}

	protected void handleTwoFingerTap() {

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

	protected void updateCanvasContentBoundingRect() {
		canvasContentBoundingRect = new RectF();
		RectF pathBounds = new RectF();

		for (IntermediateDrawingStep step : drawingSteps) {
			tessellator.setMinWidth(step.brush.getMinWidth());
			tessellator.setMaxWidth(step.brush.getMaxWidth());
			tessellator.setMaxVelDPps(step.brush.getMaxWidthDpPs());

			for (InputStroke stroke : step.inputStrokes) {
				tessellator.setInputStroke(stroke);
				Path path = tessellator.tessellate(false, true, true);
				path.computeBounds(pathBounds, true);
				canvasContentBoundingRect.union(pathBounds);
			}
		}
	}

	public static final class IntermediateDrawingStep implements Parcelable, KryoSerializable {
		Brush brush;
		ArrayList<InputStroke> inputStrokes;

		@SuppressWarnings("unused")
		public IntermediateDrawingStep() {
			// needed for Kryo
		}

		public IntermediateDrawingStep(Brush brush, ArrayList<InputStroke> inputStrokes) {
			this.brush = brush;
			this.inputStrokes = inputStrokes;
		}

		RectF computeBounds(InputStrokeTessellator tess) {
			RectF bounds = new RectF();
			RectF pathBounds = new RectF();

			tess.setMinWidth(brush.getMinWidth());
			tess.setMaxWidth(brush.getMaxWidth());
			tess.setMaxVelDPps(brush.getMaxWidthDpPs());

			for (InputStroke stroke : inputStrokes) {
				tess.setInputStroke(stroke);
				Path path = tess.tessellate(false, true, true);
				path.computeBounds(pathBounds, true);
				bounds.union(pathBounds);
			}

			return bounds;
		}

		// Parcelable

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeParcelable(brush, 0);
			dest.writeInt(inputStrokes.size());
			for (InputStroke stroke : inputStrokes) {
				dest.writeParcelable(stroke, 0);
			}
		}

		public static final Parcelable.Creator<IntermediateDrawingStep> CREATOR = new Parcelable.Creator<IntermediateDrawingStep>() {
			public IntermediateDrawingStep createFromParcel(Parcel in) {
				return new IntermediateDrawingStep(in);
			}

			public IntermediateDrawingStep[] newArray(int size) {
				return new IntermediateDrawingStep[size];
			}
		};

		private IntermediateDrawingStep(Parcel in) {
			brush = in.readParcelable(Brush.class.getClassLoader());
			int count = in.readInt();
			inputStrokes = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				InputStroke s = in.readParcelable(InputStroke.class.getClassLoader());
				inputStrokes.add(s);
			}
		}

		// KryoSerializable

		static final int SERIALIZATION_VERSION = 0;

		@Override
		public void write(Kryo kryo, Output output) {
			output.writeInt(SERIALIZATION_VERSION);
			kryo.writeObject(output, brush);
			kryo.writeObject(output, inputStrokes);
		}

		@Override
		@SuppressWarnings("unchecked")
		public void read(Kryo kryo, Input input) {
			int serializationVersion = input.readInt();
			switch (serializationVersion) {
				case 0:
					brush = kryo.readObject(input, Brush.class);
					inputStrokes = kryo.readObject(input, ArrayList.class);
					break;

				default:
					throw new IllegalArgumentException("Unsupported " + this.getClass().getName() + " serialization version: " + serializationVersion);
			}
		}
	}
}
