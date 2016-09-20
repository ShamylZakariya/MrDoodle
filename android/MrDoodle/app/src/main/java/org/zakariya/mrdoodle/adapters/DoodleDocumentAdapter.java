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
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
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
import java.util.HashMap;
import java.util.Map;

import io.realm.Realm;

/**
 * RecyclerView.Adapter which prevents a live & updating model of all DoodleDocument instances
 */
public class DoodleDocumentAdapter extends RecyclerView.Adapter<DoodleDocumentAdapter.ViewHolder> {

	private static final String TAG = DoodleDocumentAdapter.class.getSimpleName();

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

	private static class Item {
		String uuid;
		DoodleDocument document;

		public Item(DoodleDocument document) {
			this.document = document;
			this.uuid = document.getUuid();
		}
	}

	WeakReference<RecyclerView> weakRecyclerView;
	Context context;
	Realm realm;
	View emptyView;
	ArrayList<Item> items = new ArrayList<>();
	Map<String,Item> hiddenItems = new HashMap<>();

	DateFormat dateFormatter;
	int columns;
	int crossFadeDuration;
	int itemWidth;
	float thumbnailPadding;

	Comparator<Item> sortComparator = new Comparator<Item>() {
		@Override
		public int compare(Item lhs, Item rhs) {
			long leftCreationDate = lhs.document.getCreationDate().getTime();
			long rightCreationDate = rhs.document.getCreationDate().getTime();
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

	public DoodleDocumentAdapter(RecyclerView recyclerView, Context context, int columns, View emptyView) {
		this.context = context;
		this.emptyView = emptyView;
		weakRecyclerView = new WeakReference<>(recyclerView);
		this.columns = columns;
		this.realm = Realm.getDefaultInstance();
		dateFormatter = DateFormat.getDateTimeInstance();
		crossFadeDuration = context.getResources().getInteger(android.R.integer.config_shortAnimTime);
		thumbnailPadding = 4 * context.getResources().getDimensionPixelSize(R.dimen.doodle_grid_item_border_width);

		updateItems();
	}

	public void onDestroy() {
		realm.close();
	}

	public DoodleDocument getDocumentAt(int position) {
		return this.items.get(position).document;
	}

	public void setDocumentHidden(DoodleDocument document, boolean hidden) {

		if (hidden == isDocumentHidden(document)) {
			return;
		}

		if (hidden) {
			int position = getIndexOfDocument(document);
			if (position < 0) {
				throw new IllegalArgumentException("Document: " + document.getUuid() + " is not in the list of visible documents");
			}

			Item item = items.get(position);
			items.remove(position);
			hiddenItems.put(item.uuid, item);
			notifyItemRemoved(position);
		} else {
			Item item = hiddenItems.remove(document.getUuid());
			if (item == null) {
				throw new IllegalArgumentException("Document: " + document.getUuid() + " is not in the hidden set");
			}

			items.add(item);
			sortDocuments();
			int position = getIndexOfDocument(item.uuid);
			notifyItemInserted(position);
		}

		updateEmptyView();
	}

	public boolean isDocumentHidden(String documentUuid) {
		return hiddenItems.containsKey(documentUuid);
	}

	public boolean isDocumentHidden(DoodleDocument document) {
		return isDocumentHidden(document.getUuid());
	}

	public int getIndexOfDocument(String uuid) {
		// we need to compare UUIDs because object === checks might not work with Realm objects
		for (int i = 0; i < items.size(); i++) {
			Item item = items.get(i);
			if (item.uuid.equals(uuid)) {
				return i;
			}
		}

		return -1;
	}

	public int getIndexOfDocument(DoodleDocument document) {
		return getIndexOfDocument(document.getUuid());
	}

	public void onItemAdded(DoodleDocument doc) {
		Log.d(TAG, "onItemAdded() called with: " + "doc = [" + doc + "]");

		items.add(new Item(doc));
		sortDocuments();

		int index = getIndexOfDocument(doc);
		if (index >= 0) {
			notifyItemInserted(index);
		} else {
			notifyDataSetChanged();
		}

		updateEmptyView();
	}

	public void onItemDeleted(String documentUuid) {
		Log.d(TAG, "onItemDeleted() called with: " + "documentUuid = [" + documentUuid + "]");

		hiddenItems.remove(documentUuid);
		int index = getIndexOfDocument(documentUuid);
		if (index >= 0) {
			items.remove(index);
			notifyItemRemoved(index);
		}

		updateEmptyView();
	}

	public void onItemUpdated(DoodleDocument doc) {
		Log.d(TAG, "onItemUpdated() called with: " + "doc = [" + doc + "]");

		int previousIndex = getIndexOfDocument(doc);
		if (previousIndex >= 0) {
			sortDocuments();
			int newIndex = getIndexOfDocument(doc);
			if (newIndex != previousIndex) {
				notifyItemMoved(previousIndex, newIndex);
			}
			notifyItemChanged(newIndex);
		}

		updateEmptyView();
	}

	@Override
	public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		LayoutInflater inflater = LayoutInflater.from(parent.getContext());
		View v = inflater.inflate(R.layout.doodle_document_grid_item, parent, false);
		return new ViewHolder(v);
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

		Item item = items.get(position);

		holder.doodleDocument = item.document;
		holder.infoTextView.setText(context.getString(R.string.doodle_document_grid_info_text,
				item.document.getName(),
				dateFormatter.format(item.document.getCreationDate()),
				dateFormatter.format(item.document.getModificationDate()),
				item.document.getUuid()));

		// note: Our icons are square, so knowing item width is sufficient
		int thumbnailWidth = itemWidth;
		//noinspection SuspiciousNameCombination
		int thumbnailHeight = itemWidth;

		// store the id used to represent this thumbnail for quick future lookup
		holder.thumbnailId = DoodleThumbnailRenderer.getThumbnailId(item.document, thumbnailWidth, thumbnailHeight);

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
					item.document, thumbnailWidth, thumbnailHeight, thumbnailPadding,
					new DoodleThumbnailRenderer.Callbacks() {
						@Override
						public void onThumbnailReady(Bitmap thumbnail) {

							holder.setThumbnailImage(thumbnail);
							holder.loadingImageView.animate()
									.alpha(0)
									.setDuration(crossFadeDuration)
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
		return items.size();
	}

	void updateItems() {
		this.items.clear();
		for (DoodleDocument doc : DoodleDocument.all(realm)) {
			this.items.add(new Item(doc));
		}

		sortDocuments();
		updateEmptyView();
		notifyDataSetChanged();
	}

	void sortDocuments() {
		Collections.sort(items, sortComparator);
	}

	void updateEmptyView() {
		emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
	}

}
