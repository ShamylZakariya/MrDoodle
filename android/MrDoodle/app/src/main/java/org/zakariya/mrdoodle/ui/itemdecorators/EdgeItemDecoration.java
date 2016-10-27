package org.zakariya.mrdoodle.ui.itemdecorators;

import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.SparseIntArray;
import android.view.View;

import org.zakariya.mrdoodle.adapters.DoodleDocumentAdapter;

/**
 * Paints an edge separating items
 */

public class EdgeItemDecoration extends RecyclerView.ItemDecoration {
	private Paint paint;
	private SparseIntArray columnWidths = new SparseIntArray();
	private SparseIntArray rowHeights = new SparseIntArray();
	private Path verticalLinePath = new Path();
	private Path horizontalLinePath = new Path();

	public EdgeItemDecoration(float strokeWidth, float dashLength, int color) {
		paint = new Paint();
		paint.setAntiAlias(true);
		paint.setColor(color);
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(strokeWidth);

		if (dashLength > 0) {
			float[] intervals = {dashLength, dashLength};
			DashPathEffect dashPathEffect = new DashPathEffect(intervals, 0);
			paint.setPathEffect(dashPathEffect);
		}
	}

	@Override
	public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {
		super.onDrawOver(c, parent, state);

		GridLayoutManager glm = (GridLayoutManager) parent.getLayoutManager();
		DoodleDocumentAdapter adapter = (DoodleDocumentAdapter) parent.getAdapter();

		int columns = glm.getSpanCount();
		int rows = (int) Math.ceil((double) adapter.getItemCount() / (double) columns);

		int top = 0;
		int left = 0;
		int right = parent.getWidth();
		int bottom = parent.getHeight();
		int firstVisibleRow = Integer.MAX_VALUE;
		int firstVisibleCol = Integer.MAX_VALUE;

		columnWidths.clear();
		rowHeights.clear();

		int childCount = parent.getChildCount();
		for (int i = 0; i < childCount; i++) {

			View child = parent.getChildAt(i);
			int adapterPos = parent.getChildAdapterPosition(child);

			int row = (int) Math.floor((double) adapterPos / (double) columns);
			int col = adapterPos % columns;

			if (row < firstVisibleRow) {
				firstVisibleRow = row;
			}

			if (col < firstVisibleCol) {
				firstVisibleCol = col;
			}

			float childLeft = child.getLeft();
			float childTop = child.getTop();
			float childWidth = child.getWidth();
			float childHeight = child.getHeight();

			if (childTop < top) {
				top = (int) childTop;
			}

			if (childLeft < left) {
				left = (int) childLeft;
			}

			if (columnWidths.get(col) == 0) {
				columnWidths.put(col, (int) childWidth);
			}

			if (rowHeights.get(row) == 0) {
				rowHeights.put(row, (int) childHeight);
			}
		}

		// now we know the widths of children in the first visible row,
		// and the heights of children in the first visible column

		// determine minimum line length to guarantee line crosses visible area
		int lineHeight = rowHeights.get(firstVisibleRow) + parent.getHeight();
		int lineWidth = columnWidths.get(firstVisibleCol) + parent.getWidth();

		verticalLinePath.reset();
		horizontalLinePath.reset();

		float columnLeft = left;
		float columnWidth = 0;
		for (int i = 0, N = columnWidths.size(); i < N; i++) {
			int columnIndex = columnWidths.keyAt(i);

			columnWidth = columnWidths.get(columnIndex);
			columnLeft += columnWidth;

			if (columnIndex >= columns - 1) {
				break;
			}

			float x0 = columnLeft;
			float y0 = top;
			float x1 = columnLeft;
			float y1 = y0 + lineHeight;

			verticalLinePath.moveTo(x0, y0);
			verticalLinePath.lineTo(x1, y1);
		}

		// fill out
		if (columnWidth > 0) {
			while (columnLeft < right) {
				float x0 = columnLeft;
				float y0 = top;
				float x1 = columnLeft;
				float y1 = y0 + lineHeight;

				verticalLinePath.moveTo(x0, y0);
				verticalLinePath.lineTo(x1, y1);

				columnLeft += columnWidth;
			}
		}


		float rowTop = top;
		float rowHeight = 0;
		for (int i = 0, N = rowHeights.size(); i < N; i++) {
			int rowIndex = rowHeights.keyAt(i);

			rowHeight = rowHeights.get(rowIndex);
			rowTop += rowHeight;

			if (rowIndex >= rows - 1) {
				break;
			}


			float x0 = left;
			float y0 = rowTop;
			float x1 = x0 + lineWidth;
			float y1 = rowTop;

			horizontalLinePath.moveTo(x0, y0);
			horizontalLinePath.lineTo(x1, y1);
		}

		if (rowHeight > 0) {
			while (rowTop < bottom) {
				float x0 = left;
				float y0 = rowTop;
				float x1 = x0 + lineWidth;
				float y1 = rowTop;

				horizontalLinePath.moveTo(x0, y0);
				horizontalLinePath.lineTo(x1, y1);

				rowTop += rowHeight;
			}
		}

		c.drawPath(verticalLinePath, paint);
		c.drawPath(horizontalLinePath, paint);
	}
}
