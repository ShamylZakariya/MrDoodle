package org.zakariya.mrdoodle.util;

import android.view.View;
import android.view.animation.ScaleAnimation;
import android.view.animation.Transformation;
import android.widget.LinearLayout;

/**
 * Created by shamyl on 11/22/16.
 */

public class CollapseAnimation extends ScaleAnimation {

	private View view;
	private LinearLayout.LayoutParams layoutParams;
	private int marginBottomFromY, marginBottomToY;
	private boolean vanishAfter = false;

	public CollapseAnimation(float fromY, float toY, int duration, View view, boolean vanishAfter) {
		super(1, 1, fromY, toY);
		setDuration(duration);
		this.view = view;
		this.vanishAfter = vanishAfter;
		this.layoutParams = (LinearLayout.LayoutParams) view.getLayoutParams();

		int height = this.view.getHeight();
		marginBottomFromY = (int) (height * fromY) + layoutParams.bottomMargin - height;
		marginBottomToY = (int) (0 - ((height * toY) + layoutParams.bottomMargin)) - height;
	}

	@Override
	protected void applyTransformation(float interpolatedTime, Transformation t) {
		super.applyTransformation(interpolatedTime, t);
		if (interpolatedTime < 1.0f) {
			int newMarginBottom = marginBottomFromY + (int) ((marginBottomToY - marginBottomFromY) * interpolatedTime);
			layoutParams.setMargins(layoutParams.leftMargin, layoutParams.topMargin, layoutParams.rightMargin, newMarginBottom);
			view.getParent().requestLayout();
		} else if (vanishAfter) {
			view.setVisibility(View.GONE);
		}
	}

}
