package org.zakariya.mrdoodle.sync.model;

import android.support.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.Sort;

/**
 * SyncLogEntry
 * Represents a single entry in the sync log
 */
public class SyncLogEntry extends RealmObject {

	public static enum Phase {
		START,
		PUSH_START,
		PUSH_ITEM,
		PUSH_COMPLETE,
		PULL_START,
		PULL_ITEM,
		PULL_COMPLETE,
		COMPLETE
	}

	private String uuid;
	private Date date;
	private RealmList<SyncLogEntryLineItem> lineItems;
	private String failure;

	public SyncLogEntry() {
		this.uuid = UUID.randomUUID().toString();
		this.date = new Date();
		this.lineItems = new RealmList<>();
		this.failure = null;
	}

	/**
	 * Get all SyncLogEntries, sorted by date, newest first
	 *
	 * @param realm the Realm instance
	 * @return all SyncLogItems
	 */
	public static RealmResults<SyncLogEntry> all(Realm realm) {
		return realm.where(SyncLogEntry.class).findAll().sort("date", Sort.DESCENDING);
	}

	@Nullable
	public static SyncLogEntry get(Realm realm, String uuid) {
		return realm.where(SyncLogEntry.class).equalTo("uuid", uuid).findFirst();
	}

	/**
	 * Trims the set of SyncLogItems to the newest subset, keeping the set size to a set max length
	 *
	 * @param realm the Realm instance
	 * @param max   the max number of SyncLogItems we're interested in keeping
	 */
	public static void prune(Realm realm, int max) {
		RealmResults<SyncLogEntry> allSyncLogEntries = all(realm);
		if (allSyncLogEntries.size() > max) {
			List<SyncLogEntry> remainder = new ArrayList<SyncLogEntry>();
			for (int i = max; i < allSyncLogEntries.size(); i++) {
				SyncLogEntry item = allSyncLogEntries.get(i);
				remainder.add(item);
			}

			realm.beginTransaction();
			for (SyncLogEntry item : remainder) {
				item.deleteFromRealm();
			}
			realm.commitTransaction();
		}
	}

	public void appendLog(Phase phase, String message) {
		lineItems.add(new SyncLogEntryLineItem(phase.ordinal(), new Date(), message));
	}

	public void appendError(Phase phase, String message, Throwable t) {
		failure = t.getMessage();
		lineItems.add(new SyncLogEntryLineItem(phase.ordinal(), new Date(), message, failure));
	}

	public String getLog() {
		StringBuilder builder = new StringBuilder();
		for (SyncLogEntryLineItem item : lineItems) {
			builder.append(item.toString());
			builder.append("\n");
		}
		return builder.toString();
	}

	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
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

	public RealmList<SyncLogEntryLineItem> getLineItems() {
		return lineItems;
	}

	public void setLineItems(RealmList<SyncLogEntryLineItem> lineItems) {
		this.lineItems = lineItems;
	}

	private static String throwableStacktraceToString(Throwable t) {
		ByteArrayOutputStream s = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(s);
		t.printStackTrace(ps);
		ps.close();
		return s.toString();
	}

}
