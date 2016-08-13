package org.zakariya.mrdoodle.ui.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.support.annotation.ColorInt;
import android.util.AttributeSet;
import android.widget.ImageView;

import org.zakariya.mrdoodle.R;

/**
 * Created by shamyl on 1/9/16.
 */
public class BorderedImageView extends ImageView {

	private Paint paint;
	float leftBorderWidth;
	float topBorderWidth;
	float rightBorderWidth;
	float bottomBorderWidth;

	@ColorInt
	int borderColor = 0xFF000000;

	public BorderedImageView(final Context context) {
		super(context, null);
		init(context, null, 0);
	}

	public BorderedImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context, attrs, 0);
	}

	public BorderedImageView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(context, attrs, defStyleAttr);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public BorderedImageView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		init(context, attrs, defStyleAttr);
	}

	private void init(Context context, AttributeSet attrs, int defStyle) {
		paint = new Paint();
		paint.setAntiAlias(true);
		paint.setStyle(Paint.Style.FILL);

		final TypedArray a = getContext().obtainStyledAttributes(
				attrs, R.styleable.BorderedImageView, defStyle, 0);

		setBorderColor(a.getColor(R.styleable.BorderedImageView_borderColor, borderColor));
		leftBorderWidth = a.getDimension(R.styleable.BorderedImageView_leftBorderWidth, 0);
		rightBorderWidth = a.getDimension(R.styleable.BorderedImageView_rightBorderWidth, 0);
		topBorderWidth = a.getDimension(R.styleable.BorderedImageView_topBorderWidth, 0);
		bottomBorderWidth = a.getDimension(R.styleable.BorderedImageView_bottomBorderWidth, 0);

		a.recycle();
	}

	@Override
	public void draw(Canvas canvas) {
		super.draw(canvas);

		if (Color.alpha(borderColor) > 0) {
			int w = getWidth();
			int h = getHeight();
			if (leftBorderWidth > 0) {
				canvas.drawRect(0, 0, leftBorderWidth, h, paint);
			}

			if (rightBorderWidth > 0) {
				canvas.drawRect(w - rightBorderWidth, 0, w, h, paint);
			}

			if (topBorderWidth > 0) {
				canvas.drawRect(leftBorderWidth, 0, w - rightBorderWidth, topBorderWidth, paint);
			}

			if (bottomBorderWidth > 0) {
				canvas.drawRect(leftBorderWidth, h - bottomBorderWidth, w - rightBorderWidth, h, paint);
			}
		}
	}

	public void setBorderWidth(float w) {
		leftBorderWidth = w;
		topBorderWidth = w;
		rightBorderWidth = w;
		bottomBorderWidth = w;
		invalidate();
	}

	public float getLeftBorderWidth() {
		return leftBorderWidth;
	}

	public void setLeftBorderWidth(float leftBorderWidth) {
		if (leftBorderWidth != this.leftBorderWidth) {
			this.leftBorderWidth = leftBorderWidth;
			invalidate();
		}
	}

	public float getTopBorderWidth() {
		return topBorderWidth;
	}

	public void setTopBorderWidth(float topBorderWidth) {
		if (topBorderWidth != this.topBorderWidth) {
			this.topBorderWidth = topBorderWidth;
			invalidate();
		}
	}

	public float getRightBorderWidth() {
		return rightBorderWidth;
	}

	public void setRightBorderWidth(float rightBorderWidth) {
		if (rightBorderWidth != this.rightBorderWidth) {
			this.rightBorderWidth = rightBorderWidth;
			invalidate();
		}
	}

	public float getBottomBorderWidth() {
		return bottomBorderWidth;
	}

	public void setBottomBorderWidth(float bottomBorderWidth) {
		if (bottomBorderWidth != this.bottomBorderWidth) {
			this.bottomBorderWidth = bottomBorderWidth;
			invalidate();
		}
	}

	public int getBorderColor() {
		return borderColor;
	}

	public void setBorderColor(int borderColor) {
		if (borderColor != this.borderColor) {
			this.borderColor = borderColor;
			paint.setColor(this.borderColor);
			invalidate();
		}
	}
}
