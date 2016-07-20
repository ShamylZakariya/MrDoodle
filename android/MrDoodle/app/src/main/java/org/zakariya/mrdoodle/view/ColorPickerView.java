package org.zakariya.mrdoodle.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.v4.graphics.ColorUtils;
import android.support.v4.view.ViewCompat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnticipateOvershootInterpolator;
import android.view.animation.Interpolator;

import org.zakariya.doodle.geom.PointFUtil;
import org.zakariya.mrdoodle.R;

/**
 * ColorPickerView
 */
@SuppressWarnings("unused")
public class ColorPickerView extends View {

	public interface OnColorChangeListener {
		void onColorChange(ColorPickerView view, int color);
	}

	private static final String TAG = "ColorPickerView";

	private enum DragState {
		NONE,
		DRAGGING_HUE_HANDLE,
		DRAGGING_TONE_HANDLE
	}

	private enum Gravity {
		START,
		CENTER,
		END
	}

	private static final float SQRT_2 = (float) Math.sqrt(2);
	private static final float SEPARATOR_WIDTH_DP = 8f;
	private static final float MAX_TONE_SWATCH_RADIUS_DP = 22;
	private static final long ANIMATION_DURATION = 200;
	private static final int WHITE = 0xFFFFFFFF;

	private int backgroundColor = 0xFFFFFFFF;
	private int precision = 8;
	private int color = 0xFF000000;
	private int snappedColor, snappedPureHueColor;
	private float currentDragHue, snappedHue, snappedSaturation, snappedLightness;
	private float requestedHueRingDiameter = 0;
	private Gravity hGravity = Gravity.CENTER;
	private Gravity vGravity = Gravity.CENTER;

	private Paint paint;
	private LayoutInfo layoutInfo = new LayoutInfo();
	private DragState dragState;
	private OnColorChangeListener onColorChangeListener;

	// animation state
	private long dragStartTime, dragEndTime;
	private Interpolator interpolator;
	float hueKnobEngagement;
	float toneKnobEngagement;

	public ColorPickerView(Context context) {
		super(context);
		init(null, 0);
	}

	public ColorPickerView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(attrs, 0);
	}

	public ColorPickerView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(attrs, defStyle);
	}

	private void init(AttributeSet attrs, int defStyle) {
		// Load attributes
		final TypedArray a = getContext().obtainStyledAttributes(
				attrs, R.styleable.ColorPickerView, defStyle, 0);

		color = a.getColor(R.styleable.ColorPickerView_android_color, color);
		precision = a.getInt(R.styleable.ColorPickerView_precision, precision);
		computeSnappedHSLFromColor(color);

		requestedHueRingDiameter = a.getDimension(R.styleable.ColorPickerView_hueRingDiameter, requestedHueRingDiameter);

		parseAndApplyGravities(a.getString(R.styleable.ColorPickerView_gravity));

		a.recycle();

		// determine the background color of the current theme
		TypedValue ba = new TypedValue();
		getContext().getTheme().resolveAttribute(android.R.attr.windowBackground, ba, true);
		if (ba.type >= TypedValue.TYPE_FIRST_COLOR_INT && ba.type <= TypedValue.TYPE_LAST_COLOR_INT) {
			backgroundColor = ba.data;
		}

		Drawable backgroundDrawable = getBackground();
		if (backgroundDrawable instanceof ColorDrawable) {
			backgroundColor = ((ColorDrawable) backgroundDrawable).getColor();
		}

		paint = new Paint();
		paint.setAntiAlias(true);
		setLayerType(LAYER_TYPE_SOFTWARE, paint);

		interpolator = new AnticipateOvershootInterpolator();
		setDragState(DragState.NONE);
	}

	public OnColorChangeListener getOnColorChangeListener() {
		return onColorChangeListener;
	}

	public void setOnColorChangeListener(OnColorChangeListener onColorChangeListener) {
		this.onColorChangeListener = onColorChangeListener;
	}

	public int getPrecision() {
		return precision;
	}

	public void setPrecision(int precision) {
		this.precision = Math.min(Math.max(precision, 4), 32);
		computeSnappedHSLFromColor(color);
		invalidate();
	}


	public int getInitialColor() {
		return color;
	}

	/**
	 * Set the initial color for this color picker to attempt to represent, given the
	 * color space resolution constraints of its current precision. After setting an
	 * initial color, getCurrentColor() will return the closest color representable.
	 *
	 * @param color an initial color for the color picker to represent.
	 */
	public void setInitialColor(int color) {
		this.color = color;
		computeSnappedHSLFromColor(color);
		invalidate();
	}

	public float getHueRingDiameter() {
		return requestedHueRingDiameter;
	}

	public void setHueRingDiameter(float requestedHueRingDiameter) {
		this.requestedHueRingDiameter = requestedHueRingDiameter;
		updateLayoutInfo();
		invalidate();
	}

	/**
	 * Get the current color represented by this color picker. Note, if this is called
	 * immediately after calling setInitialColor(), the current color may not be the same as the color
	 * provided to setInitialColor, since the precision of the color picker view narrows the color space.
	 *
	 * @return the color selected by the user
	 */
	public int getCurrentColor() {
		return snappedColor;
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		if (getParent() != null) {
			ViewGroup v = (ViewGroup) getParent();
			v.setClipChildren(false);
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		updateLayoutInfo();
		invalidate();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		if (requestedHueRingDiameter > 0) {
			final int estimatedIntrinsicSize = (int) (requestedHueRingDiameter + 3 * MAX_TONE_SWATCH_RADIUS_DP);

			int widthMode = MeasureSpec.getMode(widthMeasureSpec);
			int widthSize = MeasureSpec.getSize(widthMeasureSpec);
			int heightMode = MeasureSpec.getMode(heightMeasureSpec);
			int heightSize = MeasureSpec.getSize(heightMeasureSpec);

			int width;
			int height;

			if (widthMode == MeasureSpec.EXACTLY) {
				width = widthSize;
			} else if (widthMode == MeasureSpec.AT_MOST) {
				width = Math.min(estimatedIntrinsicSize, widthSize);
			} else {
				width = estimatedIntrinsicSize;
			}

			width += getPaddingLeft() + getPaddingRight();

			if (heightMode == MeasureSpec.EXACTLY) {
				height = heightSize;
			} else if (heightMode == MeasureSpec.AT_MOST) {
				height = Math.min(estimatedIntrinsicSize, heightSize);
			} else {
				height = estimatedIntrinsicSize;
			}

			height += getPaddingTop() + getPaddingBottom();

			setMeasuredDimension(width, height);
		} else {
			// no requested hue ring diameter means we can't make a meaningful size measurement
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		//
		// first draw the hue ring - a set of colored arcs around the center
		//

		canvas.save();

		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(layoutInfo.hueRingThickness);
		paint.setStrokeCap(Paint.Cap.BUTT);

		int precision = this.precision;
		float[] hsl = {0, 1, 0.5f};
		final float hueAngleIncrement = layoutInfo.hueAngleIncrement;
		float hueAngle = 0;
		float angleInset = (float) (Math.atan((SEPARATOR_WIDTH_DP / 2) / layoutInfo.hueRingRadius) * 180 / Math.PI);

		for (int hueStep = 0; hueStep < precision; hueStep++, hueAngle += hueAngleIncrement) {
			hsl[0] = hueAngle * 180f / (float) Math.PI;
			int color = ColorUtils.HSLToColor(hsl);
			paint.setColor(color);

			float angle = (float) (hueStep * hueAngleIncrement * 180 / Math.PI);
			float startAngle = -90f + angle - layoutInfo.hueAngleIncrementDegrees / 2;
			float sweep = layoutInfo.hueAngleIncrementDegrees;

			canvas.drawArc(layoutInfo.hueRingRect, startAngle + angleInset, sweep - 2 * angleInset, false, paint);
		}

		canvas.restore();


		//
		// now render the tone square
		//

		float swatchSize = layoutInfo.toneSquareSwatchSize;
		float swatchRadius = swatchSize / 2 - SEPARATOR_WIDTH_DP / 2;
		float swatchY = 0f;
		float swatchX;
		float swatchIncrement = 1f / (float) (precision - 1);

		for (int row = 0; row < precision; row++, swatchY += swatchIncrement) {
			paint.setStyle(Paint.Style.FILL);

			float swatchLeft = layoutInfo.toneSquareLeft;
			float swatchTop = layoutInfo.toneSquareTop + row * swatchSize;
			swatchX = 0f;

			// first row is solid white - so draw with a hairline grey circle to clarify
			if (row == 0 && backgroundColor == WHITE) {
				paint.setStyle(Paint.Style.FILL);
				paint.setColor(0xFFFFFFFF);
				for (int col = 0; col < precision; col++, swatchLeft += swatchSize) {
					canvas.drawCircle(swatchLeft, swatchTop, swatchRadius, paint);
				}
				paint.setStyle(Paint.Style.STROKE);
				paint.setColor(0xFFdddddd);
				paint.setStrokeWidth(1);
				swatchLeft = layoutInfo.toneSquareLeft;
				for (int col = 0; col < precision; col++, swatchLeft += swatchSize) {
					canvas.drawCircle(swatchLeft, swatchTop, swatchRadius, paint);
				}
			} else {
				for (int col = 0; col < precision; col++, swatchLeft += swatchSize, swatchX += swatchIncrement) {
					paint.setColor(plotToneSquareSwatchColor(snappedHue, swatchX, swatchY));
					canvas.drawCircle(swatchLeft, swatchTop, swatchRadius, paint);
				}
			}

			paint.setStyle(Paint.Style.FILL);
		}

		final long now = System.currentTimeMillis();

		//
		// Draw the knob handles.
		// First, get the "engagement" factor. The knob grows when it's grabbed, and
		// shrinks when let go. The engagement grows from 0->1, then 1->0 to show this.
		//

		switch (dragState) {
			case DRAGGING_HUE_HANDLE:
				hueKnobEngagement = Math.min((float) (now - dragStartTime) / (float) ANIMATION_DURATION, 1f);
				toneKnobEngagement = 0;
				break;
			case DRAGGING_TONE_HANDLE:
				toneKnobEngagement = Math.min((float) (now - dragStartTime) / (float) ANIMATION_DURATION, 1f);
				hueKnobEngagement = 0;
				break;
			case NONE:
				if (toneKnobEngagement > 0) {
					toneKnobEngagement = 1f - Math.min(((float) (now - dragEndTime) / (float) ANIMATION_DURATION), 1f);
				}
				if (hueKnobEngagement > 0) {
					hueKnobEngagement = 1f - Math.min(((float) (now - dragEndTime) / (float) ANIMATION_DURATION), 1f);
				}
				break;
		}

		float hueKnobAngle = lrpDegrees(snappedHue, currentDragHue, interpolator.getInterpolation(hueKnobEngagement));
		PointF hueKnobPosition = getHueKnobPosition(hueKnobAngle);
		PointF toneKnobPosition = getToneKnobPosition(snappedSaturation, snappedLightness);

		// draw whichever knob is currently engaged on top
		if (hueKnobEngagement > toneKnobEngagement) {
			drawKnob(canvas, snappedColor, toneKnobPosition.x, toneKnobPosition.y, toneKnobEngagement);
			drawKnob(canvas, snappedPureHueColor, hueKnobPosition.x, hueKnobPosition.y, hueKnobEngagement);
		} else {
			drawKnob(canvas, snappedPureHueColor, hueKnobPosition.x, hueKnobPosition.y, hueKnobEngagement);
			drawKnob(canvas, snappedColor, toneKnobPosition.x, toneKnobPosition.y, toneKnobEngagement);
		}

		//
		// now determine if we must continue animation
		//

		boolean animating = false;
		if (dragState == DragState.NONE) {
			if (now - dragEndTime < ANIMATION_DURATION) {
				animating = true;
			}
		} else {
			animating = true;
		}

		if (animating) {
			ViewCompat.postInvalidateOnAnimation(this);
		}
	}

	private void drawKnob(Canvas canvas, int color, float x, float y, float engagement) {
		paint.setStyle(Paint.Style.FILL);
		paint.setColor(color);

		int shadowColor = 0x55000000;
		float minShadowRadius = 4;
		float maxShadowRadius = 16;
		float minShadowOffset = 2;
		float maxShadowOffset = 16;

		if (engagement > 0) {
			final float rInactive = layoutInfo.knobRadius;
			final float rActive = layoutInfo.knobRadius * 1.5f;
			final float extensionInactive = 1f;
			final float extensionActive = 3f;
			final float t = interpolator.getInterpolation(engagement);
			final float r = lrp(rInactive, rActive, t);
			final float extension = lrp(extensionInactive, extensionActive, t);

			Path popupKnobPath = new Path();
			popupKnobPath.addRoundRect(new RectF(x - r, y - extension * r, x + r, y + r), r, r, Path.Direction.CW);

			float sr = lrp(minShadowRadius, maxShadowRadius, t);
			float so = lrp(minShadowOffset, maxShadowOffset, t);
			paint.setShadowLayer(sr, 0, so, shadowColor);
			canvas.drawPath(popupKnobPath, paint);
		} else {
			paint.setShadowLayer(minShadowRadius, 0, minShadowOffset, shadowColor);
			canvas.drawCircle(x, y, layoutInfo.knobRadius, paint);
		}

		paint.setShadowLayer(0, 0, 0, 0);
	}

	private PointF getHueKnobPosition(float hue) {
		float selectedHueAngle = (float) ((hue * Math.PI / 180) - Math.PI / 2);
		float knobPositionRadius = layoutInfo.hueRingRadius;
		float knobX = layoutInfo.centerX + (float) (Math.cos(selectedHueAngle) * knobPositionRadius);
		float knobY = layoutInfo.centerY + (float) (Math.sin(selectedHueAngle) * knobPositionRadius);
		return new PointF(knobX, knobY);
	}

	private PointF getToneKnobPosition(float snappedSaturation, float snappedLightness) {
		float x = layoutInfo.toneSquareLeft + (1 - snappedSaturation) * layoutInfo.toneSquareSize;
		float y = layoutInfo.toneSquareTop + (1 - snappedLightness) * layoutInfo.toneSquareSize;
		return new PointF(x, y);
	}

	private int plotToneSquareSwatchColor(float hue, float x, float y) {
		float[] hsl = {
				hue,
				(float) snapSaturationOrLightnessValue(1 - x),
				(float) snapSaturationOrLightnessValue(1 - y)
		};

		return ColorUtils.HSLToColor(hsl);
	}

	private static float lrp(float a, float b, float v) {
		return a + v * (b - a);
	}

	private static float lrpDegrees(float a, float b, float v) {
		// normalize to [0,360]
		while (a < 360f) {
			a += 360f;
		}

		while (a > 360f) {
			a -= 360f;
		}

		while (b < 360f) {
			b += 360f;
		}

		while (b > 360f) {
			b -= 360f;
		}

		float ap = a - 360f;
		float bp = b - 360f;

		// find shortest distance to interpolate
		float abd = Math.abs(a - b);
		float apbd = Math.abs(ap - b);
		float abpd = Math.abs(a - bp);

		if (abd < apbd && abd < abpd) {
			return a + v * (b - a);
		} else if (apbd < abd && apbd < abpd) {
			return ap + v * (b - ap);
		} else {
			return a + v * (bp - a);
		}
	}

	private void updateLayoutInfo() {

		int knobPadding = (int) (MAX_TONE_SWATCH_RADIUS_DP + 4 * SEPARATOR_WIDTH_DP);
		int paddingLeft = getPaddingLeft() + knobPadding;
		int paddingTop = getPaddingTop() + knobPadding;
		int paddingRight = getPaddingRight() + knobPadding;
		int paddingBottom = getPaddingBottom() + knobPadding;

		int contentWidth = getWidth() - paddingLeft - paddingRight;
		int contentHeight = getHeight() - paddingTop - paddingBottom;
		int contentSize = Math.min(contentWidth, contentHeight);
		float maxHueRingRadius = contentSize / 2f;

		layoutInfo.contentSize = contentSize;

		if (requestedHueRingDiameter > 0) {
			float minHueRingRadius = (precision * MAX_TONE_SWATCH_RADIUS_DP) * SQRT_2;
			layoutInfo.hueRingRadius = Math.max(Math.min(requestedHueRingDiameter / 2, maxHueRingRadius), minHueRingRadius);
		} else {
			layoutInfo.hueRingRadius = maxHueRingRadius;
		}

		// apply gravity to centerX, centerY
		switch (hGravity) {
			case START:
				layoutInfo.centerX = paddingLeft + layoutInfo.hueRingRadius;
				break;
			case END:
				layoutInfo.centerX = paddingLeft + contentWidth - layoutInfo.hueRingRadius;
				break;
			case CENTER:
				layoutInfo.centerX = (float) Math.floor(paddingLeft + contentWidth / 2f);
				break;
		}

		switch (vGravity) {
			case START:
				layoutInfo.centerY = paddingTop + layoutInfo.hueRingRadius;
				break;
			case END:
				layoutInfo.centerY = paddingTop + contentHeight - layoutInfo.hueRingRadius;
				break;
			case CENTER:
				layoutInfo.centerY = (float) Math.floor(paddingTop + contentHeight / 2f);
				break;
		}


		float arcLeft = layoutInfo.centerX - layoutInfo.hueRingRadius;
		float arcRight = layoutInfo.centerX + layoutInfo.hueRingRadius;
		float arcTop = layoutInfo.centerY - layoutInfo.hueRingRadius;
		float arcBottom = layoutInfo.centerY + layoutInfo.hueRingRadius;
		layoutInfo.hueRingRect = new RectF(arcLeft, arcTop, arcRight, arcBottom);


		float toneSquareScale = 0.875f;
		float estimatedToneSquareSize = 2 * ((layoutInfo.hueRingRadius - MAX_TONE_SWATCH_RADIUS_DP) * toneSquareScale / SQRT_2);
		float estimatedToneSwatchSize = estimatedToneSquareSize / precision;

		layoutInfo.toneSquareSize = 2 * (((layoutInfo.hueRingRadius - estimatedToneSwatchSize - MAX_TONE_SWATCH_RADIUS_DP) * toneSquareScale) / SQRT_2);
		layoutInfo.toneSquareSwatchSize = layoutInfo.toneSquareSize / (float) (precision - 1);
		layoutInfo.toneSquareLeft = layoutInfo.centerX - layoutInfo.toneSquareSize / 2;
		layoutInfo.toneSquareTop = layoutInfo.centerY - layoutInfo.toneSquareSize / 2;

		layoutInfo.hueRingThickness = (layoutInfo.toneSquareSwatchSize - 2 * SEPARATOR_WIDTH_DP) * 0.75f / 2;
		layoutInfo.hueAngleIncrement = (float) (2 * Math.PI) / (float) precision;
		layoutInfo.hueAngleIncrementDegrees = 360f / (float) precision;

		layoutInfo.knobRadius = (layoutInfo.toneSquareSwatchSize / 2) + (4 * SEPARATOR_WIDTH_DP);
	}

	private void computeSnappedHSLFromColor(int color) {
		float[] hsl = {0, 0, 0};
		ColorUtils.RGBToHSL(Color.red(color), Color.green(color), Color.blue(color), hsl);

		currentDragHue = snappedHue = (float) snapHueValue(hsl[0]);
		snappedSaturation = (float) snapSaturationOrLightnessValue(hsl[1]);
		snappedLightness = (float) snapSaturationOrLightnessValue(hsl[2]);

		updateSnappedColor();
	}

	private void updateSnappedColor() {
		float[] hsl = {snappedHue, snappedSaturation, snappedLightness};
		snappedColor = ColorUtils.HSLToColor(hsl);

		hsl[1] = 1;
		hsl[2] = 0.5f;
		snappedPureHueColor = ColorUtils.HSLToColor(hsl);

		if (onColorChangeListener != null) {
			onColorChangeListener.onColorChange(this, snappedColor);
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				return onTouchStart(event);
			case MotionEvent.ACTION_MOVE:
				return onTouchMove(event);
			case MotionEvent.ACTION_UP:
				return onTouchEnd();
		}

		return super.onTouchEvent(event);
	}

	private boolean onTouchStart(MotionEvent event) {
		PointF pos = new PointF(event.getX(), event.getY());
		PointF hueKnobPosition = getHueKnobPosition(snappedHue);
		PointF toneKnobPosition = getToneKnobPosition(snappedSaturation, snappedLightness);
		float dist2 = layoutInfo.knobRadius * layoutInfo.knobRadius;
		float hueKnobDist2 = PointFUtil.distance2(hueKnobPosition, pos);
		float toneKnobDist2 = PointFUtil.distance2(toneKnobPosition, pos);

		// check if user tapped hue or tone knobs
		if (hueKnobDist2 < dist2 && hueKnobDist2 < toneKnobDist2) {
			updateSnappedHueForTouchPosition(event.getX(), event.getY());
			setDragState(DragState.DRAGGING_HUE_HANDLE);
			return true;
		} else if (toneKnobDist2 < dist2 && toneKnobDist2 < hueKnobDist2) {
			setDragState(DragState.DRAGGING_TONE_HANDLE);
			return true;
		} else {

			// user did not tap a knob. so check if user tapped on the ring, in which case
			// set the hue directly and switch drag state to DRAGGING_HUE_HANDLE, otherwise,
			// see if user tapped on the tone swatch. if so, set sat/light directly and switch
			// drag state to DRAGGING_TONE_HANDLE. Otherwise, do nothing.

			// check if user tapped on hue ring
			float touchRadiusFromCenter = PointFUtil.distance(
					new PointF(layoutInfo.centerX, layoutInfo.centerY),
					new PointF(event.getX(), event.getY()));

			if (touchRadiusFromCenter > layoutInfo.hueRingRadius - layoutInfo.knobRadius &&
					touchRadiusFromCenter < layoutInfo.hueRingRadius + layoutInfo.knobRadius) {
				updateSnappedHueForTouchPosition(event.getX(), event.getY());
				setDragState(DragState.DRAGGING_HUE_HANDLE);
				return true;
			}

			// check if user tapped on tone square
			float toneX = (event.getX() - layoutInfo.toneSquareLeft) / layoutInfo.toneSquareSize;
			float toneY = (event.getY() - layoutInfo.toneSquareTop) / layoutInfo.toneSquareSize;
			float fudge = layoutInfo.knobRadius / layoutInfo.toneSquareSize;

			if (toneX >= -fudge && toneX <= 1 + fudge && toneY >= -fudge && toneY <= 1 + fudge) {
				updateSnappedSaturationAndLightnessForTouchPosition(event.getX(), event.getY());
				setDragState(DragState.DRAGGING_TONE_HANDLE);
				return true;
			}
		}

		setDragState(DragState.NONE);
		return false;
	}

	private boolean onTouchMove(MotionEvent event) {
		switch (dragState) {
			case DRAGGING_HUE_HANDLE:
				updateSnappedHueForTouchPosition(event.getX(), event.getY());
				return true;

			case DRAGGING_TONE_HANDLE:
				updateSnappedSaturationAndLightnessForTouchPosition(event.getX(), event.getY());
				return true;

			case NONE:
				break;
		}
		return false;
	}

	private boolean onTouchEnd() {
		setDragState(DragState.NONE);
		return false;
	}

	private void setDragState(DragState dragState) {
		this.dragState = dragState;
		if (dragState == DragState.NONE) {
			dragStartTime = 0;
			dragEndTime = System.currentTimeMillis();
		} else {
			dragStartTime = System.currentTimeMillis();
			dragEndTime = 0;

			// start animation
			ViewCompat.postInvalidateOnAnimation(this);
		}
	}

	private void updateSnappedHueForTouchPosition(float x, float y) {
		PointF dir = PointFUtil.dir(new PointF(0, 0), new PointF(layoutInfo.centerX - x, layoutInfo.centerY - y)).first;
		currentDragHue = (float) (Math.atan2(dir.y, dir.x) * 180 / Math.PI) - 90f; // hue zero is pointing up, so rotate CCW 90deg
		while (currentDragHue < 0) {
			currentDragHue += 360.0f;
		}

		float snapped = (float) snapHueValue(currentDragHue);
		if (Math.abs(snapped - snappedHue) > 1e-3) {
			snappedHue = snapped;
			updateSnappedColor();
		}
	}

	private void updateSnappedSaturationAndLightnessForTouchPosition(float x, float y) {
		float toneX = (x - layoutInfo.toneSquareLeft) / layoutInfo.toneSquareSize;
		float toneY = (y - layoutInfo.toneSquareTop) / layoutInfo.toneSquareSize;

		toneX = Math.min(Math.max(toneX, 0), 1);
		toneY = Math.min(Math.max(toneY, 0), 1);

		float sat = (float) snapSaturationOrLightnessValue(1 - toneX);
		float light = (float) snapSaturationOrLightnessValue(1 - toneY);

		if (Math.abs(sat - snappedSaturation) > 1e-3 || Math.abs(light - snappedLightness) > 1e-3) {
			snappedSaturation = sat;
			snappedLightness = light;
			updateSnappedColor();
		}
	}

	private double snapHueValue(double hue) {
		// notice, hue uses precision, whereas sat/light uses (precision-1)
		// this is because the hue slider ring has precision+1 entries, because value 360 == value 0,
		// it's like a spiral where the last entry overlaps the first.

		hue /= 360;
		hue = Math.round(hue * precision);
		hue /= precision;
		hue *= 360;
		return hue;
	}

	private double snapSaturationOrLightnessValue(double v) {
		v = Math.round(v * (precision - 1));
		v = v / (double) (precision - 1);
		return v;
	}

	private void parseAndApplyGravities(String spec) {
		if (!TextUtils.isEmpty(spec)) {

			spec = spec.toLowerCase().trim();
			String[] tokens = spec.split("\\|", 2);
			Log.i(TAG, "parseAndApplyGravities spec: \"" + spec + "\" tokens: " + TextUtils.join(",", tokens));

			if (tokens.length == 1) {
				// horizontal and vertical gravities are same
				hGravity = vGravity = parseGravitySpec(tokens[0]);
			} else if (tokens.length == 2) {
				hGravity = parseGravitySpec(tokens[0]);
				vGravity = parseGravitySpec(tokens[1]);
			}
		}
	}

	private Gravity parseGravitySpec(String specToken) {
		switch (specToken) {
			case "start":
				return Gravity.START;
			case "end":
				return Gravity.END;
			case "center":
				return Gravity.CENTER;
		}

		// default fallthrough to center
		return Gravity.CENTER;
	}

	private static final class LayoutInfo {
		float contentSize;
		float centerX, centerY;
		float hueRingRadius;
		float hueRingThickness;
		RectF hueRingRect;
		float toneSquareSize, toneSquareLeft, toneSquareTop, toneSquareSwatchSize;
		float hueAngleIncrement;
		float hueAngleIncrementDegrees;
		float knobRadius;

		LayoutInfo() {
		}
	}
}
