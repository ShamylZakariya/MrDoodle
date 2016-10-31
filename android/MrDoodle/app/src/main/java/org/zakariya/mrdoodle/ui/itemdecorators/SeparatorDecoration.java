package org.zakariya.mrdoodle.ui.itemdecorators;

import android.graphics.Rect;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;

/**
 * Insets item edges "smartly" to give an even spacing between items
 */

public class SeparatorDecoration extends RecyclerView.ItemDecoration {
	private int separationThickness;
	private int hSeparationThickness;
	private boolean addSeparatorToEdges;

	/**
	 * Inset items in a grid layout by an amount with optional edge insets
	 * @param separationThickness the amount to inset by
	 * @param addSeparatorToEdges if true, the items at the edges (leftmost, rightmost, topmost, bottommost) will have padding against the recyclerview's edge
	 */
	public SeparatorDecoration(int separationThickness, boolean addSeparatorToEdges) {
		this.separationThickness = separationThickness;
		this.addSeparatorToEdges = addSeparatorToEdges;

		hSeparationThickness = separationThickness/2;
	}

	@Override
	public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
		GridLayoutManager glm = (GridLayoutManager) parent.getLayoutManager();
		RecyclerView.Adapter adapter = parent.getAdapter();

		int columns = glm.getSpanCount();
		int rows = (int) Math.ceil((double) adapter.getItemCount() / (double) columns);
		int adapterPosition = parent.getChildAdapterPosition(view);

		int row = (int) Math.floor((double) adapterPosition / (double) columns);
		int col = adapterPosition % columns;

		if (col == 0) {
			if (addSeparatorToEdges) {
				outRect.left = separationThickness;
			}
			outRect.right = hSeparationThickness;
		} else if (col == columns - 1) {
			if (addSeparatorToEdges) {
				outRect.right = separationThickness;
			}
			outRect.left = hSeparationThickness;
		} else {
			outRect.left = hSeparationThickness;
			outRect.right = hSeparationThickness;
		}

		if (row == 0) {
			if (addSeparatorToEdges) {
				outRect.top = separationThickness;
			}
			outRect.bottom = hSeparationThickness;
		} else if (row == rows - 1) {
			if (addSeparatorToEdges) {
				outRect.bottom = separationThickness;
			}
			outRect.top = hSeparationThickness;
		} else {
			outRect.top = hSeparationThickness;
			outRect.bottom = hSeparationThickness;
		}
	}
}
