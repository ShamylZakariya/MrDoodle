package org.zakariya.mrdoodle.sync.model;

import android.support.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.Sort;

/**
 * SyncLogEntry
 * Represents a single entry in the sync log
 */
public class SyncLogEntry extends RealmObject {

	private String uuid;
	private String log;
	private Date date;
	private String failure;

	/**
	 * Create a new SyncLogEntry representing a sync transcript for a given account
	 *
	 * @param realm the realm to contain the SyncLogEntry
	 * @return a new SyncLogEntry, in the realm
	 */
	public static SyncLogEntry create(Realm realm) {
		SyncLogEntry item = realm.createObject(SyncLogEntry.class);
		item.setUuid(UUID.randomUUID().toString());
		item.setDate(new Date());
		return item;
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

	public void appendLog(String message) {
		if (log != null) {
			log += message + "\n";
		} else {
			log = message + "\n";
		}
	}

	public void appendError(String message, Throwable t) {
		message = "ERROR:\t" + message + "\n" + t.getMessage() + "\n" + throwableStacktraceToString(t) + "\n";
		if (log != null) {
			log += message;
		} else {
			log = message;
		}
	}

	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
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

	private static String throwableStacktraceToString(Throwable t)
	{
		ByteArrayOutputStream s = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(s);
		t.printStackTrace(ps);
		ps.close();
		return s.toString();
	}

}
