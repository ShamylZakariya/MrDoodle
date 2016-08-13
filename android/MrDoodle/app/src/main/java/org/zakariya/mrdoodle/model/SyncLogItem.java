package org.zakariya.mrdoodle.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.Sort;

/**
 * SyncLogItem
 * Represents a single entry in the sync log
 */
public class SyncLogItem extends RealmObject {

	private String log;
	private Date date;
	private String failure;

	/**
	 * Create a new SyncLogItem representing a sync transcript for a given account
	 *
	 * @param realm the realm to contain the SyncLogItem
	 * @return a new SyncLogItem, in the realm
	 */
	public static SyncLogItem create(Realm realm) {
		SyncLogItem item = realm.createObject(SyncLogItem.class);
		item.setDate(new Date());
		return item;
	}

	/**
	 * Get all SyncLogItems, sorted by date, ascending
	 *
	 * @param realm the Realm instance
	 * @return all SyncLogItems
	 */
	public static RealmResults<SyncLogItem> all(Realm realm) {
		RealmResults<SyncLogItem> items = realm.where(SyncLogItem.class).findAll();
		items.sort("date", Sort.ASCENDING);
		return items;
	}

	/**
	 * Trims the set of SyncLogItems to the newest subset, keeping the set size to a set max length
	 *
	 * @param realm the Realm instance
	 * @param max   the max number of SyncLogItems we're interested in keeping
	 */
	public static void prune(Realm realm, int max) {
		RealmResults<SyncLogItem> allSyncLogItems = all(realm);
		if (allSyncLogItems.size() > max) {
			List<SyncLogItem> remainder = new ArrayList<SyncLogItem>();
			for (int i = max; i < allSyncLogItems.size(); i++) {
				SyncLogItem item = allSyncLogItems.get(i);
				remainder.add(item);
			}

			realm.beginTransaction();
			for (SyncLogItem item : remainder) {
				item.deleteFromRealm();
			}
			realm.commitTransaction();
		}
	}

	public String getLog() {
		return log;
	}

	public void setLog(String log) {
		this.log = log;
	}

	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public String getFailure() {
		return failure;
	}

	public void setFailure(String failure) {
		this.failure = failure;
	}

}
