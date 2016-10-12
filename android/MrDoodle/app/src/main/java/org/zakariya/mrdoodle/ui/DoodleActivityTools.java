package org.zakariya.mrdoodle.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.support.annotation.ColorInt;
import android.support.v4.graphics.ColorUtils;

import org.zakariya.flyoutmenu.FlyoutMenuView;

/**
 * These inner classes are used to draw the trigger buttons and menu items of DoodleActivity flyout menus.
 */
@SuppressWarnings("unused WeakerAccess")
public class DoodleActivityTools {

	@ColorInt
	private static final int ALPHA_CHECKER_COLOR = 0xFFD6D6D6;


	static final class PaletteFlyoutButtonRenderer extends FlyoutMenuView.ButtonRenderer {

		Paint paint;
		RectF insetButtonBounds = new RectF();
		float inset;
		@ColorInt
		int currentColor;
		double currentColorLuminance;

		PaletteFlyoutButtonRenderer(float inset) {
			paint = new Paint();
			paint.setAntiAlias(true);
			this.inset = inset;
		}

		public int getCurrentColor() {
			return currentColor;
		}

		void setCurrentColor(int currentColor) {
			this.currentColor = currentColor;
			currentColorLuminance = ColorUtils.calculateLuminance(this.currentColor);
		}


		@Override
		public void onDrawButtonContent(Canvas canvas, RectF buttonBounds, @ColorInt int buttonColor, float alpha) {
			insetButtonBounds.left = buttonBounds.left + inset;
			insetButtonBounds.top = buttonBounds.top + inset;
			insetButtonBounds.right = buttonBounds.right - inset;
			insetButtonBounds.bottom = buttonBounds.bottom - inset;

			paint.setAlpha((int) (alpha * 255f));
			paint.setStyle(Paint.Style.FILL);
			paint.setColor(currentColor);
			canvas.drawOval(insetButtonBounds, paint);

			if (inset > 0 && currentColorLuminance > 0.7) {
				paint.setStyle(Paint.Style.STROKE);
				paint.setColor(0x33000000);
				canvas.drawOval(insetButtonBounds, paint);
			}
		}
	}

	static final class ToolFlyoutButtonRenderer extends FlyoutMenuView.ButtonRenderer {

		ToolRenderer toolRenderer;
		Paint paint;
		RectF insetButtonBounds = new RectF();
		float inset;

		ToolFlyoutButtonRenderer(float inset, float size, boolean isEraser, float alphaCheckerSize, @ColorInt int fillColor) {
			this.inset = inset;
			toolRenderer = new ToolRenderer(size, isEraser, alphaCheckerSize, fillColor);
			paint = new Paint();
			paint.setAntiAlias(true);
		}

		@ColorInt
		public int getFillColor() {
			return toolRenderer.getFillColor();
		}

		public void setFillColor(@ColorInt int fillColor) {
			toolRenderer.setFillColor(fillColor);
		}

		public float getSize() {
			return toolRenderer.getSize();
		}

		public void setSize(float size) {
			toolRenderer.setSize(size);
		}

		public boolean isEraser() {
			return toolRenderer.isEraser();
		}

		public void setIsEraser(boolean isEraser) {
			toolRenderer.setIsEraser(isEraser);
		}

		@Override
		public void onDrawButtonContent(Canvas canvas, RectF buttonBounds, @ColorInt int buttonColor, float alpha) {
			insetButtonBounds.left = buttonBounds.left + inset;
			insetButtonBounds.top = buttonBounds.top + inset;
			insetButtonBounds.right = buttonBounds.right - inset;
			insetButtonBounds.bottom = buttonBounds.bottom - inset;

			paint.setAlpha((int) (alpha * 255f));
			paint.setColor(buttonColor);
			paint.setStyle(Paint.Style.FILL);
			toolRenderer.draw(canvas, insetButtonBounds);
		}
	}

	static final class ToolFlyoutMenuItem extends FlyoutMenuView.MenuItem {

		ToolRenderer toolRenderer;

		ToolFlyoutMenuItem(int id, float size, boolean isEraser, float alphaCheckerSize, @ColorInt int fillColor) {
			super(id);
			toolRenderer = new ToolRenderer(size, isEraser, alphaCheckerSize, fillColor);
		}

		public float getSize() {
			return toolRenderer.getSize();
		}

		public boolean isEraser() {
			return toolRenderer.isEraser();
		}

		@Override
		public void onDraw(Canvas canvas, RectF bounds, float degreeSelected) {
			toolRenderer.draw(canvas, bounds);
		}
	}

	static final class PaletteFlyoutMenuItem extends FlyoutMenuView.MenuItem {

		@ColorInt
		int color;

		Paint paint;
		float cornerRadius;

		PaletteFlyoutMenuItem(int id, @ColorInt int color, float cornerRadius) {
			super(id);
			this.color = ColorUtils.setAlphaComponent(color, 255);
			this.cornerRadius = cornerRadius;
			paint = new Paint();
			paint.setAntiAlias(true);
			paint.setStyle(Paint.Style.FILL);
			paint.setColor(color);
		}

		@Override
		public void onDraw(Canvas canvas, RectF bounds, float degreeSelected) {
			if (cornerRadius > 0) {
				canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, paint);
			} else {
				canvas.drawRect(bounds, paint);
			}
		}
	}

	/**
	 * Convenience class for rendering the tool state in the ToolFlyoutMenuItem and ToolFlyoutButtonRenderer
	 */
	private static final class ToolRenderer {

		float size;
		float radius;
		float alphaCheckerSize;
		boolean isEraser;
		Path clipPath;
		Paint paint;
		RectF previousBounds;
		@ColorInt
		int fillColor;

		ToolRenderer(float size, boolean isEraser, float alphaCheckerSize, @ColorInt int fillColor) {
			this.alphaCheckerSize = alphaCheckerSize;
			paint = new Paint();
			paint.setAntiAlias(true);

			setSize(size);
			setIsEraser(isEraser);
			setFillColor(fillColor);
		}

		public float getSize() {
			return size;
		}

		public void setSize(float size) {
			this.size = Math.min(Math.max(size, 0), 1);
			clipPath = null;
		}

		public void setIsEraser(boolean isEraser) {
			this.isEraser = isEraser;
		}

		public boolean isEraser() {
			return isEraser;
		}

		public int getFillColor() {
			return fillColor;
		}

		public void setFillColor(int fillColor) {
			this.fillColor = fillColor;
		}

		void draw(Canvas canvas, RectF bounds) {
			float maxRadius = Math.min(bounds.width(), bounds.height()) / 2;
			radius = size * maxRadius;

			if (isEraser) {
				buildEraserFillClipPath(bounds);

				canvas.save();
				canvas.clipPath(clipPath);

				int left = (int) bounds.left;
				int top = (int) bounds.top;
				int right = (int) bounds.right;
				int bottom = (int) bounds.bottom;

				paint.setStyle(Paint.Style.FILL);
				paint.setColor(0xFFFFFFFF);
				canvas.drawRect(left, top, right, bottom, paint);

				paint.setColor(ALPHA_CHECKER_COLOR);
				for (int y = top, j = 0; y < bottom; y += (int) alphaCheckerSize, j++) {
					for (int x = left, k = 0; x < right; x += alphaCheckerSize, k++) {
						if ((j + k) % 2 == 0) {
							canvas.drawRect(x, y, x + alphaCheckerSize, y + alphaCheckerSize, paint);
						}
					}
				}

				canvas.restore();

				paint.setStyle(Paint.Style.STROKE);
				canvas.drawCircle(bounds.centerX(), bounds.centerY(), radius, paint);

			} else {
				paint.setStyle(Paint.Style.FILL);
				paint.setColor(fillColor);
				canvas.drawCircle(bounds.centerX(), bounds.centerY(), radius, paint);
			}
		}

		void buildEraserFillClipPath(RectF bounds) {
			if (previousBounds == null || clipPath == null || !bounds.equals(previousBounds)) {
				previousBounds = new RectF(bounds);

				clipPath = new Path();
				clipPath.addCircle(bounds.centerX(), bounds.centerY(), radius, Path.Direction.CW);
			}
		}

	}
}
