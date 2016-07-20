package org.zakariya.mrdoodle.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import org.zakariya.mrdoodle.R;

/**
 * Draws a circular color swatch, a circle in the assigned color.
 */
public class ColorSwatchView extends View {

	private static final String TAG = "ColorSwatchView";
	static final int ALPHA_CHECKER_COLOR = 0xFFD6D6D6;

	private int color = Color.BLACK;
	private int selectionColor = Color.WHITE;
	private boolean selected = false;
	private float selectionThickness = 4;
	private float alphaCheckerSize = 8;
	private Paint swatchPaint;
	private Paint selectionPaint;
	private Paint alphaCheckerPaint;
	private Path alphaClipPath;


	public ColorSwatchView(Context context) {
		super(context);
		init(null, 0);
	}

	public ColorSwatchView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(attrs, 0);
	}

	public ColorSwatchView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(attrs, defStyle);
	}

	private void init(AttributeSet attrs, int defStyle) {
		final TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.ColorSwatchView, defStyle, 0);

		color = a.getColor(R.styleable.ColorSwatchView_android_color, color);
		selectionColor = a.getColor(R.styleable.ColorSwatchView_selectionColor, selectionColor);
		selectionThickness = a.getDimension(R.styleable.ColorSwatchView_selectionThickness, selectionThickness);
		selected = a.getBoolean(R.styleable.ColorSwatchView_isSelected, selected);
		alphaCheckerSize = a.getDimension(R.styleable.ColorSwatchView_alphaCheckerSize, alphaCheckerSize);

		a.recycle();

		int shadowColor = 0x55000000;
		float shadowRadius = 4;
		float shadowOffset = 2;
		swatchPaint = new Paint();
		swatchPaint.setAntiAlias(true);
		swatchPaint.setStyle(Paint.Style.FILL);
		swatchPaint.setShadowLayer(shadowRadius, 0, shadowOffset, shadowColor);
		setLayerType(LAYER_TYPE_SOFTWARE, swatchPaint);

		selectionPaint = new Paint();
		selectionPaint.setAntiAlias(true);
		selectionPaint.setStyle(Paint.Style.STROKE);

		configurePaintsAndInvalidate();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		if (Color.alpha(color) < 255) {
			// draw alpha checkerboard. First set a circular clip path

			canvas.save();
			canvas.clipPath(alphaClipPath);

			final float width = getWidth();
			final float height = getHeight();
			alphaCheckerPaint.setColor(0xFFFFFFFF);
			canvas.drawRect(0, 0, width, height, alphaCheckerPaint);

			alphaCheckerPaint.setColor(ALPHA_CHECKER_COLOR);
			for (int y = 0, j = 0; y < height; y += alphaCheckerSize, j++) {
				for (int x = 0, k = 0; x < width; x += alphaCheckerSize, k++) {
					if ((j + k) % 2 == 0) {
						canvas.drawRect(x, y, x + alphaCheckerSize, y + alphaCheckerSize, alphaCheckerPaint);
					}
				}
			}

			canvas.restore();
		}

		final float cx = getSwatchCenterX();
		final float cy = getSwatchCenterY();

		canvas.drawCircle(cx, cy, getSwatchRadius(), swatchPaint);

		if (selected) {
			canvas.drawCircle(cx, cy, getSwatchHighlightRadius(), selectionPaint);
		}
	}

	@Override
	public void layout(int l, int t, int r, int b) {
		super.layout(l, t, r, b);
		alphaClipPath = null;
		configurePaintsAndInvalidate();
	}

	private float getSwatchCenterX() {
		return getWidth() / 2 + getPaddingLeft() / 2 - getPaddingRight() / 2;
	}

	private float getSwatchCenterY() {
		return getHeight() / 2 + getPaddingTop() / 2 - getPaddingBottom() / 2;
	}

	private float getSwatchRadius() {
		final float width = getWidth() - (getPaddingLeft() + getPaddingRight());
		final float height = getHeight() - (getPaddingTop() + getPaddingBottom());
		return Math.min(width, height) / 2 - selectionThickness;
	}

	private float getSwatchHighlightRadius() {
		return getSwatchRadius() + selectionThickness / 2;
	}

	private void configurePaintsAndInvalidate() {
		swatchPaint.setColor(color);
		selectionPaint.setStrokeWidth(selectionThickness);
		selectionPaint.setColor(selectionColor);


		if (Color.alpha(color) < 255) {
			if (alphaCheckerPaint == null) {
				alphaCheckerPaint = new Paint();
				alphaCheckerPaint.setStyle(Paint.Style.FILL);
				alphaCheckerPaint.setColor(ALPHA_CHECKER_COLOR);
			}

			if (alphaClipPath == null) {
				alphaClipPath = new Path();
				alphaClipPath.addCircle(getSwatchCenterX(), getSwatchCenterY(), getSwatchRadius(), Path.Direction.CW);
			}
		}

		invalidate();
	}

	public int getColor() {
		return color;
	}

	public void setColor(int color) {
		this.color = color;
		configurePaintsAndInvalidate();
	}

	public int getSelectionColor() {
		return selectionColor;
	}

	public void setSelectionColor(int selectionColor) {
		this.selectionColor = selectionColor;
		configurePaintsAndInvalidate();
	}

	@Override
	public boolean isSelected() {
		return selected;
	}

	@Override
	public void setSelected(boolean selected) {
		this.selected = selected;
		configurePaintsAndInvalidate();
	}

	public float getSelectionThickness() {
		return selectionThickness;
	}

	public void setSelectionThickness(float selectionThickness) {
		this.selectionThickness = selectionThickness;
		configurePaintsAndInvalidate();
	}
}
