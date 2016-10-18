package org.zakariya.doodle.model;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
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

@SuppressWarnings("WeakerAccess")
public class StrokeDoodle extends Doodle implements IncrementalInputStrokeTessellator.Listener {
	private static final String TAG = "StrokeDoodle";

	private static final int COOKIE = 0xD00D;

	private Paint backingStoreBitmapPaint;
	private RectF canvasContentBoundingRect = new RectF();
	private IncrementalInputStrokeTessellator incrementalInputStrokeTessellator;

	private Canvas bitmapCanvas;
	private Bitmap backingStoreBitmap;
	private boolean needsUpdateBackingStore = false;
	private InputStrokeTessellator tessellator = new InputStrokeTessellator();

	// one finger touch state
	private float[] strokeTouchPoint = {0f, 0f};

	@State
	ArrayList<IntermediateDrawingStep> drawingSteps = new ArrayList<>();

	public StrokeDoodle() {
		backingStoreBitmapPaint = new Paint();
		backingStoreBitmapPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));

		setBrush(new Brush(0xFF000000, 1, 1, 100, false));
	}

	public StrokeDoodle(InputStream serializedForm) throws InvalidObjectException {
		this();
		inflate(serializedForm);
	}

	public StrokeDoodle(StrokeDoodle src) {
		this();
		//noinspection unchecked
		this.drawingSteps = (ArrayList<IntermediateDrawingStep>) src.drawingSteps.clone();
	}

	@Override
	public void serialize(OutputStream out) {
		Output output = new Output(out);
		try {
			Kryo kryo = new Kryo();
			kryo.writeObject(output, COOKIE);
			kryo.writeObject(output, drawingSteps);
		} finally {
			output.close();
		}
	}

	@Override
	public void inflate(InputStream in) throws InvalidObjectException {
		Input input = new Input(in);
		Kryo kryo = new Kryo();

		try {
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
		} finally {
			input.close();
		}
	}

	@Override
	public void clear() {
		if (isReadOnly()) {
			return;
		}

		canvasContentBoundingRect = new RectF(); // mark empty
		incrementalInputStrokeTessellator = null;
		drawingSteps.clear();
		needsUpdateBackingStore = true;
		invalidate();

		super.clear();
	}

	@Override
	public void undo() {
		if (isReadOnly()) {
			return;
		}


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
				transformed.transform(getDoodleCanvas().getCanvasToScreenMatrix());
				canvas.drawPath(transformed, getBrush().getPaint());
			}
		}
	}

	protected void renderDrawingSteps(Canvas canvas) {
		for (IntermediateDrawingStep step : drawingSteps) {
			tessellator.setMinWidth(step.brush.getMinWidth());
			tessellator.setMaxWidth(step.brush.getMaxWidth());
			tessellator.setMaxVelDPps(step.brush.getMaxWidthDpPs());

			RectF viewportCanvasRect = getViewportCanvasRect();
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
			bitmapCanvas.concat(getDoodleCanvas().getCanvasToScreenMatrix());
			renderDrawingSteps(bitmapCanvas);
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
	}

	@Override
	public void resize(int newWidth, int newHeight) {
		// rebuild backing store bitmap, if necessary
		if (backingStoreBitmap == null || (newWidth != backingStoreBitmap.getWidth() || newHeight != backingStoreBitmap.getHeight())) {
			backingStoreBitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888);
			bitmapCanvas = new Canvas(backingStoreBitmap);
		}
	}

	public void canvasMatricesUpdated() {
		super.canvasMatricesUpdated();
		needsUpdateBackingStore = true;
	}

	@Override
	public void onLoadInstanceState(Bundle savedInstanceState) {
		Icepick.restoreInstanceState(this, savedInstanceState);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		Icepick.saveInstanceState(this, outState);
	}

	@Override
	public RectF getCanvasContentBoundingRect() {
		if (canvasContentBoundingRect.isEmpty()) {
			updateCanvasContentBoundingRect();
		}
		return canvasContentBoundingRect;
	}


	@Override
	public void onInputStrokeModified(InputStroke inputStroke, int startIndex, int endIndex, RectF rect) {
		getDoodleCanvas().getCanvasToScreenMatrix().mapRect(rect);
		canvasContentBoundingRect.union(rect);
		invalidate(rect);
	}

	@Override
	public void onLivePathModified(Path path, RectF rect) {
		if (getBrush().isEraser()) {
			onNewStaticPathAvailable(path, rect);
		} else {
			getDoodleCanvas().getCanvasToScreenMatrix().mapRect(rect);
			invalidate(rect);
		}
	}

	@Override
	public void onNewStaticPathAvailable(Path path, RectF rect) {
		Matrix canvasToScreenMatrix = getDoodleCanvas().getCanvasToScreenMatrix();

		// draw path into bitmapCanvas
		bitmapCanvas.save();
		bitmapCanvas.concat(canvasToScreenMatrix);
		bitmapCanvas.drawPath(path, getBrush().getPaint());
		bitmapCanvas.restore();

		canvasToScreenMatrix.mapRect(rect);
		invalidate(rect);
	}

	@Override
	public float getInputStrokeOptimizationThreshold() {
		return 1.5f * getBrush().getScale();
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

	@Override
	public boolean onTouchEventBegin(@NonNull MotionEvent event) {

		if (event.getPointerCount() == 1) {
			strokeTouchPoint[0] = event.getX(0);
			strokeTouchPoint[1] = event.getY(0);
			return true;
		} else if (event.getPointerCount() >= 2) {
			incrementalInputStrokeTessellator = null;
		}

		return false;
	}

	@Override
	public boolean onTouchEventMove(@NonNull MotionEvent event) {

		if (event.getPointerCount() == 1) {

			// single-touch is a draw event - discar iff readonly
			if (isReadOnly()) {
				return false;
			}

			Matrix screenToCanvasMatrix = getDoodleCanvas().getScreenToCanvasMatrix();
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

			markDirty();
			return true;
		}

		return false;
	}


	@Override
	public boolean onTouchEventEnd(@NonNull MotionEvent event) {

		if (!isReadOnly() && incrementalInputStrokeTessellator != null) {
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

			markDirty();
			return true;
		}

		return false;
	}

	private void updateCanvasContentBoundingRect() {
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

	public static final class IntermediateDrawingStep implements Cloneable, Parcelable, KryoSerializable {
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

		@Override
		protected Object clone() throws CloneNotSupportedException {
			IntermediateDrawingStep clone = (IntermediateDrawingStep) super.clone();
			clone.brush = this.brush.copy();
			clone.inputStrokes = (ArrayList<InputStroke>) this.inputStrokes.clone();
			return clone;
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
