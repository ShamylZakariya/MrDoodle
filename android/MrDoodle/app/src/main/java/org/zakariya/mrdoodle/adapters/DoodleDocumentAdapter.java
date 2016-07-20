package org.zakariya.mrdoodle.adapters;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.zakariya.mrdoodle.R;
import org.zakariya.mrdoodle.model.DoodleDocument;
import org.zakariya.mrdoodle.util.DoodleThumbnailRenderer;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import io.realm.Realm;
import io.realm.RealmResults;

/**
 * Created by shamyl on 12/18/15.
 */
public class DoodleDocumentAdapter extends RecyclerView.Adapter<DoodleDocumentAdapter.ViewHolder> {

	private static final String TAG = DoodleDocumentAdapter.class.getSimpleName();

	public interface OnClickListener {
		/**
		 * Called when an item is clicked
		 *
		 * @param document the document represented by this item
		 */
		void onDoodleDocumentClick(DoodleDocument document, View tappedItem);
	}

	public interface OnLongClickListener {
		/**
		 * Called when an item is long clicked
		 *
		 * @param document the document represented by this item
		 * @return true if the long click is handled, false otherwise
		 */
		boolean onDoodleDocumentLongClick(DoodleDocument document, View tappedItem);
	}

	public class ViewHolder extends RecyclerView.ViewHolder {
		public DoodleDocument doodleDocument;
		public String thumbnailId;
		public View rootView;
		public ImageView imageView;
		public ImageView loadingImageView;
		public TextView infoTextView;

		DoodleThumbnailRenderer.RenderTask thumbnailRenderTask;

		public ViewHolder(View v) {
			super(v);
			rootView = v;
			imageView = (ImageView) v.findViewById(R.id.imageView);
			loadingImageView = (ImageView) v.findViewById(R.id.loadingImageView);
			infoTextView = (TextView) v.findViewById(R.id.infoTextView);
		}

		@TargetApi(Build.VERSION_CODES.LOLLIPOP)
		public void setThumbnailImage(Bitmap thumbnail) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
				Context context = rootView.getContext();
				int rippleColor = ContextCompat.getColor(context, R.color.primary);
				BitmapDrawable drawable = new BitmapDrawable(context.getResources(), thumbnail);
				RippleDrawable ripple = new RippleDrawable(ColorStateList.valueOf(rippleColor), drawable, null);
				imageView.setImageDrawable(ripple);
			} else {
				imageView.setImageBitmap(thumbnail);
			}
		}
	}

	WeakReference<RecyclerView> weakRecyclerView;
	Context context;
	Realm realm;
	View emptyView;
	ArrayList<DoodleDocument> doodleDocuments;
	DateFormat dateFormatter;
	WeakReference<OnClickListener> weakOnClickListener;
	WeakReference<OnLongClickListener> weakOnLongClickListener;
	int columns;
	int crossfadeDuration;
	int itemWidth;
	float thumbnailPadding;

	Comparator<DoodleDocument> sortComparator = new Comparator<DoodleDocument>() {
		@Override
		public int compare(DoodleDocument lhs, DoodleDocument rhs) {
			long leftCreationDate = lhs.getCreationDate().getTime();
			long rightCreationDate = rhs.getCreationDate().getTime();
			long delta = rightCreationDate - leftCreationDate;

			// this dance is to avoid long->int precision loss
			if (delta < 0) {
				return -1;
			} else if (delta > 0) {
				return 1;
			}
			return 0;
		}
	};

	public DoodleDocumentAdapter(Context context, RecyclerView recyclerView, int columns, RealmResults<DoodleDocument> items, View emptyView) {
		this.context = context;
		this.emptyView = emptyView;
		weakRecyclerView = new WeakReference<>(recyclerView);
		this.columns = columns;
		realm = Realm.getDefaultInstance();
		dateFormatter = DateFormat.getDateTimeInstance();
		crossfadeDuration = context.getResources().getInteger(android.R.integer.config_shortAnimTime);
		doodleDocuments = new ArrayList<>();

		thumbnailPadding = 4 * context.getResources().getDimensionPixelSize(R.dimen.doodle_grid_item_border_width);

		setItems(items);
	}

	public void setOnClickListener(@Nullable OnClickListener listener) {
		if (listener != null) {
			weakOnClickListener = new WeakReference<>(listener);
		} else {
			weakOnClickListener = null;
		}
	}

	@Nullable
	public OnClickListener getOnClickListener() {
		return weakOnClickListener != null ? weakOnClickListener.get() : null;
	}

	public void setOnLongClickListener(@Nullable OnLongClickListener listener) {
		if (listener != null) {
			weakOnLongClickListener = new WeakReference<>(listener);
		} else {
			weakOnLongClickListener = null;
		}
	}

	@Nullable
	public OnLongClickListener getOnLongClickListener() {
		return weakOnLongClickListener != null ? weakOnLongClickListener.get() : null;
	}

	/**
	 * Your activity/fragment needs to call this to clean up the internal Realm instance
	 */
	public void onDestroy() {
		realm.close();
	}

	void updateEmptyView() {
		emptyView.setVisibility(doodleDocuments.isEmpty() ? View.VISIBLE : View.GONE);
	}

	@Override
	public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		LayoutInflater inflater = LayoutInflater.from(parent.getContext());
		View v = inflater.inflate(R.layout.doodle_document_grid_item, parent, false);

		final ViewHolder holder = new ViewHolder(v);
		holder.imageView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				DoodleDocument doc = holder.doodleDocument;
				OnClickListener listener = getOnClickListener();
				if (doc != null && listener != null) {
					listener.onDoodleDocumentClick(doc, holder.imageView);
				}
			}
		});

		holder.imageView.setLongClickable(true);
		holder.imageView.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				DoodleDocument doc = holder.doodleDocument;
				OnLongClickListener listener = getOnLongClickListener();
				return doc != null && listener != null && listener.onDoodleDocumentLongClick(doc, holder.imageView);
			}
		});

		return holder;
	}

	@Override
	public void onBindViewHolder(final ViewHolder holder, int position) {

		if (itemWidth == 0) {
			RecyclerView recyclerView = weakRecyclerView.get();
			if (recyclerView != null) {
				int totalWidth = recyclerView.getWidth();
				itemWidth = Math.round((float) totalWidth / (float) columns);
			}
		}

		if (holder.thumbnailRenderTask != null) {
			holder.thumbnailRenderTask.cancel();
			holder.thumbnailRenderTask = null;
		}

		DoodleDocument doc = doodleDocuments.get(position);

		holder.doodleDocument = doc;
		holder.infoTextView.setText(context.getString(R.string.doodle_document_grid_info_text,
				doc.getName(),
				dateFormatter.format(doc.getCreationDate()),
				dateFormatter.format(doc.getModificationDate()),
				doc.getUuid()));

		// note: Our icons are square, so knowing item width is sufficient
		int thumbnailWidth = itemWidth;
		//noinspection SuspiciousNameCombination
		int thumbnailHeight = itemWidth;

		// store the id used to represent this thumbnail for quick future lookup
		holder.thumbnailId = DoodleThumbnailRenderer.getThumbnailId(doc, thumbnailWidth, thumbnailHeight);

		DoodleThumbnailRenderer thumbnailer = DoodleThumbnailRenderer.getInstance();
		Bitmap thumbnail = thumbnailer.getThumbnailById(holder.thumbnailId);

		if (thumbnail != null) {

			//
			//  The thumbnail is available, run with it
			//

			holder.thumbnailRenderTask = null;
			holder.loadingImageView.setVisibility(View.GONE);
			holder.setThumbnailImage(thumbnail);

		} else {

			//
			//  Thumbnail has to be rendered. Show the loading image view and fire off a request
			//

			holder.loadingImageView.setAlpha(1f);
			holder.loadingImageView.setVisibility(View.VISIBLE);

			holder.thumbnailRenderTask = DoodleThumbnailRenderer.getInstance().renderThumbnail(
					doc, thumbnailWidth, thumbnailHeight, thumbnailPadding,
					new DoodleThumbnailRenderer.Callbacks() {
						@Override
						public void onThumbnailReady(Bitmap thumbnail) {

							holder.setThumbnailImage(thumbnail);
							holder.loadingImageView.animate()
									.alpha(0)
									.setDuration(crossfadeDuration)
									.setListener(new AnimatorListenerAdapter() {
										@Override
										public void onAnimationEnd(Animator animation) {
											holder.loadingImageView.setVisibility(View.GONE);
											super.onAnimationEnd(animation);
										}
									});
						}
					});
		}
	}

	@Override
	public int getItemCount() {
		return doodleDocuments.size();
	}

	public void setItems(RealmResults<DoodleDocument> items) {
		doodleDocuments.clear();
		for (DoodleDocument doc : items) {
			this.doodleDocuments.add(doc);
		}

		sortDocuments();
		updateEmptyView();
		notifyDataSetChanged();
	}

	/**
	 * Add a document to the list
	 *
	 * @param doc the document
	 */
	public void addItem(DoodleDocument doc) {
		doodleDocuments.add(0, doc);
		sortDocuments();
		int index = getIndexOfItem(doc);
		if (index >= 0) {
			notifyItemInserted(index);
		} else {
			notifyDataSetChanged();
		}

		updateEmptyView();
	}

	/**
	 * Remove a document from the list.
	 *
	 * @param doc the document to remove
	 */
	public void removeItem(DoodleDocument doc) {
		int index = getIndexOfItem(doc);
		if (index >= 0) {
			doodleDocuments.remove(index);
			notifyItemRemoved(index);
		}

		updateEmptyView();
	}

	/**
	 * When a document is edited, it goes to the top of the list. Call this to re-sort storage and move the item.
	 *
	 * @param doc the edited document
	 */
	public void itemWasUpdated(DoodleDocument doc) {
		int previousIndex = getIndexOfItem(doc);
		if (previousIndex >= 0) {
			sortDocuments();
			int newIndex = getIndexOfItem(doc);
			if (newIndex != previousIndex) {
				notifyItemMoved(previousIndex, newIndex);
			}
			notifyItemChanged(newIndex);
		}

		updateEmptyView();
	}

	public boolean contains(DoodleDocument document) {
		return getIndexOfItem(document.getUuid()) >= 0;
	}

	private void sortDocuments() {
		Collections.sort(doodleDocuments, sortComparator);
	}

	private int getIndexOfItem(String uuid) {
		// we need to compare UUIDs because object === checks might not work with Realm objects
		for (int i = 0; i < doodleDocuments.size(); i++) {
			DoodleDocument doc = doodleDocuments.get(i);
			if (doc.getUuid().equals(uuid)) {
				return i;
			}
		}

		return -1;
	}

	private int getIndexOfItem(DoodleDocument document) {
		return getIndexOfItem(document.getUuid());
	}
}
