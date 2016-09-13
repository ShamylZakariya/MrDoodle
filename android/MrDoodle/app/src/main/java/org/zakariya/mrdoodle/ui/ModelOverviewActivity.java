package org.zakariya.mrdoodle.ui;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.zakariya.mrdoodle.R;
import org.zakariya.mrdoodle.model.DoodleDocument;
import org.zakariya.mrdoodle.sync.ChangeJournal;
import org.zakariya.mrdoodle.sync.model.ChangeJournalItem;
import org.zakariya.mrdoodle.sync.SyncManager;
import org.zakariya.mrdoodle.sync.TimestampRecorder;
import org.zakariya.mrdoodle.sync.model.ChangeType;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import io.realm.Realm;
import io.realm.Sort;

/**
 * Shows an overview - for debugging the sync system - of DoodleDocuments, ChangeJournal, and Timestamp Recorder
 */
public class ModelOverviewActivity extends AppCompatActivity {

	@Bind(R.id.viewPager)
	ViewPager viewPager;

	@Bind(R.id.tabs)
	TabLayout tabs;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_model_overview);
		ButterKnife.bind(this);

		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		if (getSupportActionBar() != null) {
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
			getSupportActionBar().setHomeButtonEnabled(true);
		}

		viewPager.setAdapter(new ModelPagerAdapter(getSupportFragmentManager()));
		tabs.setupWithViewPager(viewPager);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_model_overview, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	private static class ModelPagerAdapter extends FragmentPagerAdapter {
		public ModelPagerAdapter(FragmentManager fm) {
			super(fm);
		}

		@Override
		public Fragment getItem(int position) {
			switch (position) {
				case 0:
					return new DoodleDocumentListFragment();
				case 1:
					return new JournalItemListFragment();
				case 2:
					return new TimestampListFragment();
			}
			throw new IllegalArgumentException("position out of bounds");
		}

		@Override
		public int getCount() {
			return 3;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			switch (position) {
				case 0:
					return "Doodles";
				case 1:
					return "Change Journal";
				case 2:
					return "Timestamps";
			}
			throw new IllegalArgumentException("position out of bounds");
		}
	}

	public static class ListFragment extends Fragment {

		Realm realm;

		@Bind(R.id.listView)
		ListView listView;

		public ListFragment() {
		}

		@Override
		public void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			realm = Realm.getDefaultInstance();
		}

		@Override
		public void onDestroy() {
			realm.close();
			super.onDestroy();
		}

		@Nullable
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
			View view = inflater.inflate(R.layout.fragment_model_overview_page, container, false);
			ButterKnife.bind(this,view);

			listView.setAdapter(new ListItemModelArrayAdapter(getActivity(), getListItems()));

			return view;
		}

		List<ListItemModel> getListItems() {
			return null;
		}
	}

	public static class DoodleDocumentListFragment extends ListFragment {
		@Override
		List<ListItemModel> getListItems() {

			DateFormat format = SimpleDateFormat.getDateTimeInstance();
			ArrayList<ListItemModel> itemModels = new ArrayList<>();

			for (DoodleDocument doc : DoodleDocument.all(realm).sort("creationDate", Sort.ASCENDING)) {
				ListItemModel item = new ListItemModel();
				item.primaryText = doc.getName();
				item.secondaryText = doc.getUuid()
						+ " created: " + format.format(doc.getCreationDate())
						+ " modified: " + format.format(doc.getModificationDate());
				item.orphaned = false;
				itemModels.add(item);
			}

			return itemModels;
		}
	}

	public static class JournalItemListFragment extends ListFragment {
		@Override
		List<ListItemModel> getListItems() {
			SyncManager syncManager = SyncManager.getInstance();
			ChangeJournal journal = syncManager.getChangeJournal();

			ArrayList<ListItemModel> itemModels = new ArrayList<>();

			for (ChangeJournalItem item : journal.getChangeJournalItems(realm)) {
				ListItemModel listItemModel = new ListItemModel();
				listItemModel.primaryText = item.getModelObjectClass() + " : " + item.getModelObjectId();
				listItemModel.secondaryText = "Change type: " + ChangeType.values()[item.getChangeType()].toString();
				itemModels.add(listItemModel);
			}

			Collections.sort(itemModels, new Comparator<ListItemModel>() {
				@Override
				public int compare(ListItemModel a, ListItemModel b) {
					return a.primaryText.compareToIgnoreCase(b.primaryText);
				}
			});

			return itemModels;
		}
	}

	public static class TimestampListFragment extends ListFragment {
		@Override
		List<ListItemModel> getListItems() {
			ArrayList<ListItemModel> itemModels = new ArrayList<>();
			TimestampRecorder timestampRecorder = SyncManager.getInstance().getTimestampRecorder();

			if (timestampRecorder != null ) {
				for (String id : timestampRecorder.getTimestamps().keySet()) {
					ListItemModel listItemModel = new ListItemModel();
					String referencedItemClass = classOfItemWithId(id);
					long timestamp = timestampRecorder.getTimestamp(id);

					if (referencedItemClass != null) {
						listItemModel.primaryText = referencedItemClass + " : " + id;
					} else {
						listItemModel.primaryText = "<NULL> : " + id;
					}

					Date date = new Date();
					date.setTime(timestamp * 1000);
					listItemModel.secondaryText = date.toString() + "   (" + timestamp + ")";
					listItemModel.orphaned = referencedItemClass == null;

					itemModels.add(listItemModel);
				}
			}

			Collections.sort(itemModels, new Comparator<ListItemModel>() {
				@Override
				public int compare(ListItemModel lhs, ListItemModel rhs) {
					return lhs.primaryText.compareTo(rhs.primaryText);
				}
			});

			return itemModels;
		}

		private String classOfItemWithId(String id) {
			if (realm.where(DoodleDocument.class).equalTo("uuid",id).findFirst() != null) {
				return DoodleDocument.class.getSimpleName();
			}
			return null;
		}

	}


	private static class ListItemModel {
		String primaryText;
		String secondaryText;
		boolean orphaned;
	}

	private static class ListItemModelArrayAdapter extends ArrayAdapter<ListItemModel> {

		private class ViewHolder {
			TextView primaryText;
			TextView secondaryText;
			View orphanedMarker;
		}

		private LayoutInflater inflater;

		public ListItemModelArrayAdapter(Context context, List<ListItemModel> objects) {
			super(context, 0, objects);
			this.inflater = LayoutInflater.from(context);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder viewHolder;
			if (convertView == null) {
				convertView = inflater.inflate(R.layout.list_item_model_overview, parent, false);
				viewHolder = new ViewHolder();
				viewHolder.primaryText = (TextView) convertView.findViewById(R.id.primaryText);
				viewHolder.secondaryText = (TextView) convertView.findViewById(R.id.secondaryText);
				viewHolder.orphanedMarker = convertView.findViewById(R.id.orphanedMarker);
				convertView.setTag(viewHolder);
			} else {
				viewHolder = (ViewHolder) convertView.getTag();
			}

			ListItemModel item = getItem(position);
			viewHolder.primaryText.setText(item.primaryText);
			viewHolder.secondaryText.setText(item.secondaryText);
			viewHolder.orphanedMarker.setVisibility(item.orphaned ? View.VISIBLE : View.GONE);

			return convertView;
		}
	}

}
