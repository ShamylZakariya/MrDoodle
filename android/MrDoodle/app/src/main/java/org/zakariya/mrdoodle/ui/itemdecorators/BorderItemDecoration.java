package org.zakariya.mrdoodle.ui.itemdecorators;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import org.zakariya.mrdoodle.adapters.DoodleDocumentAdapter;

/**
 * Paints a border around grid items with even thickness sidestepping the issue
 * where right/bottom-edge borders double up with the following item's left/top-edge border.
 */
public class BorderItemDecoration extends RecyclerView.ItemDecoration {

	private float thickness;
	private Paint paint;

	public BorderItemDecoration(float thickness, int color) {
		this.thickness = thickness;

		paint = new Paint();
		paint.setAntiAlias(true);
		paint.setColor(color);
		paint.setStyle(Paint.Style.FILL);
	}

	@Override
	public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {
		super.onDrawOver(c, parent, state);

		GridLayoutManager glm = (GridLayoutManager) parent.getLayoutManager();
		DoodleDocumentAdapter adapter = (DoodleDocumentAdapter) parent.getAdapter();

		int columns = glm.getSpanCount();
		int rows = (int) Math.ceil((double) adapter.getItemCount() / (double) columns);

		int childCount = parent.getChildCount();
		for (int i = 0; i < childCount; i++) {
			View child = parent.getChildAt(i);
			int adapterPos = parent.getChildAdapterPosition(child);
			int row = (int) Math.floor((double) adapterPos / (double) columns);
			int col = adapterPos % columns;

			float leftBorderWidth = thickness;
			float topBorderWidth = thickness;
			float rightBorderWidth = thickness;
			float bottomBorderWidth = thickness;

			if (row > 0) {
				topBorderWidth *= 0.5;
			}

			if (row < rows - 1) {
				bottomBorderWidth *= 0.5;
			}

			if (col > 0) {
				leftBorderWidth *= 0.5;
			}

			if (col < columns - 1) {
				rightBorderWidth *= 0.5;
			}

			float left = child.getLeft();
			float top = child.getTop();
			float right = child.getRight();
			float bottom = child.getBottom();

			c.drawRect(left, top, right, top + topBorderWidth, paint);
			c.drawRect(left, bottom - bottomBorderWidth, right, bottom, paint);
			c.drawRect(left, top + topBorderWidth, left + leftBorderWidth, bottom - bottomBorderWidth, paint);
			c.drawRect(right - rightBorderWidth, top + topBorderWidth, right, bottom - bottomBorderWidth, paint);
		}
	}
}
