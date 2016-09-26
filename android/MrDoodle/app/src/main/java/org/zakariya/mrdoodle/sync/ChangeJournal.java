package org.zakariya.mrdoodle.sync;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import org.zakariya.mrdoodle.events.ChangeJournalUpdatedEvent;
import org.zakariya.mrdoodle.sync.model.ChangeJournalItem;
import org.zakariya.mrdoodle.sync.model.ChangeType;
import org.zakariya.mrdoodle.util.BusProvider;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.realm.Realm;

/**
 * Listens for various model create/modify/delete events, collecting them and creating an
 * up-to-date list of JournalItems representing changes since last time journal was cleared
 */
public class ChangeJournal {

	private final static String TAG = "ChangeJournal";
	private final static int NOTIFICATION_DEBOUNCE_MILLIS = 2 * 1000; // 2 seconds

	private Handler delayedNotificationHandler;
	private Runnable delayedNotification;
	private String persistPrefix;
	private boolean notifies;
	private Map<String, ChangeJournalItem> changeJournalItemsByObjectId = new HashMap<>();

	/**
	 * Create a ChangeJournal. If a persistPrefix is provided, the ChangeJournal will
	 * be persistent, saving to a realm. Otherwise, it will be in-memory only.
	 *
	 * @param persistPrefix a "namespace" under which this journal reads/writes ChangeJournalItems. If null, ChangeJournal will not persist and will be in-memory only
	 * @param notifies      if true, changes to the journal will post ChangeJournalUpdatedEvent, after a short debounce period
	 */
	public ChangeJournal(@Nullable String persistPrefix, boolean notifies) {
		this.persistPrefix = persistPrefix;
		this.notifies = notifies;

		if (this.notifies) {
			delayedNotificationHandler = new Handler(Looper.getMainLooper());
			delayedNotification = new Runnable() {
				@Override
				public void run() {
					BusProvider.postOnMainThread(new ChangeJournalUpdatedEvent(ChangeJournal.this));
				}
			};
		}
	}

	/**
	 * Creates a copy of the provided source journal, but the copy will be a non-persisting in-memory journal which will not post change notifications.
	 *
	 * @param source the journal to copy
	 * @return a non-persisting, non-event-emitting ChangeJournal with the same entries as the source
	 */
	static public ChangeJournal createSilentInMemoryCopy(ChangeJournal source) {

		// create a non-persisting and non-notifying ChangeJournal, and copy our changes over
		ChangeJournal copy = new ChangeJournal(null, false);

		Realm realm = Realm.getDefaultInstance();
		for (ChangeJournalItem item : source.getChangeJournalItems(realm)) {
			copy.changeJournalItemsByObjectId.put(item.getModelObjectId(),
					new ChangeJournalItem(null,
							item.getModelObjectId(),
							item.getModelObjectClass(),
							item.getChangeType()));
		}

		realm.close();

		return copy;
	}

	/**
	 * Get this ChangeJournal's items
	 *
	 * @param realm if this ChangeJournal persists, it needs a realm instance to query for its items
	 * @return this ChangeJournal's items
	 */
	public Set<ChangeJournalItem> getChangeJournalItems(Realm realm) {
		if (shouldPersist()) {
			return new HashSet<>(realm.where(ChangeJournalItem.class).findAll());
		} else {
			throw new UnsupportedOperationException("ChangeJournal::getChangeJournalItems(Realm) not supported for in-memory journals, only persisting ones");
		}
	}

	public Set<ChangeJournalItem> getChangeJournalItems() {
		if (!shouldPersist()) {
			return new HashSet<>(changeJournalItemsByObjectId.values());
		} else {
			throw new UnsupportedOperationException("ChangeJournal::getChangeJournalItems() not supported for persisting ChangeJournals, only in-memory");
		}
	}

	public boolean shouldPersist() {
		return !TextUtils.isEmpty(persistPrefix);
	}

	public boolean shouldNotify() {
		return notifies;
	}

	/**
	 * Merge the items in src to this journal.
	 *
	 * @param src      a ChangeJournal to copy items from
	 * @param notifies if true, this journal will post ChangeJournalUpdatedEvent
	 */
	public void merge(ChangeJournal src, boolean notifies) {
		Realm realm = Realm.getDefaultInstance();

		Set<ChangeJournalItem> srcItems = src.shouldPersist()
				? src.getChangeJournalItems(realm)
				: src.getChangeJournalItems();

		if (shouldPersist()) {

			realm.beginTransaction();
			for (ChangeJournalItem item : srcItems) {

				ChangeJournalItem change = realm.where(ChangeJournalItem.class)
						.equalTo("prefix", persistPrefix)
						.equalTo("modelObjectId", item.getModelObjectId())
						.findFirst();

				if (change != null) {
					change.setChangeType(item.getChangeType());
				} else {
					ChangeJournalItem.create(realm,
							persistPrefix,
							item.getModelObjectId(),
							item.getModelObjectClass(),
							ChangeType.values()[item.getChangeType()]);
				}
			}
			realm.commitTransaction();

		} else {

			for (ChangeJournalItem item : srcItems) {
				changeJournalItemsByObjectId.put(item.getModelObjectId(),
						new ChangeJournalItem(null,
								item.getModelObjectId(),
								item.getModelObjectClass(),
								item.getChangeType()));
			}

		}

		realm.close();

		if (notifies) {
			notifyChangeJournalUpdated();
		}
	}

	/**
	 * Deletes recorded local changes
	 */
	public void clear(boolean notifies) {

		if (shouldPersist()) {
			Realm realm = Realm.getDefaultInstance();
			realm.beginTransaction();

			realm.where(ChangeJournalItem.class)
					.equalTo("prefix", persistPrefix)
					.findAll()
					.deleteAllFromRealm();

			realm.delete(ChangeJournalItem.class);
			realm.commitTransaction();
			realm.close();
		} else {
			changeJournalItemsByObjectId.clear();
		}

		if (notifies) {
			notifyChangeJournalUpdated();
		}
	}

	/**
	 * Clears changes for any object with a given id
	 *
	 * @param id id of the object with changes to remove
	 */
	public void clear(String id) {
		if (shouldPersist()) {
			Realm realm = Realm.getDefaultInstance();
			realm.beginTransaction();

			realm.where(ChangeJournalItem.class)
					.equalTo("prefix", persistPrefix)
					.equalTo("modelObjectId", id)
					.findAll()
					.deleteAllFromRealm();

			realm.commitTransaction();
			realm.close();
		} else {
			changeJournalItemsByObjectId.remove(id);
		}

		notifyChangeJournalUpdated();
	}

	public void markModified(String id, String className) {
		Log.d(TAG, "markModified class: " + className + " id: " + id);
		mark(id, className, ChangeType.MODIFY);
		notifyChangeJournalUpdated();
	}

	public void markDeleted(String id, String className) {
		Log.d(TAG, "markDeleted class: " + className + " id: " + id);
		mark(id, className, ChangeType.DELETE);
		notifyChangeJournalUpdated();
	}

	void notifyChangeJournalUpdated() {
		if (shouldNotify()) {
			delayedNotificationHandler.removeCallbacks(delayedNotification);
			delayedNotificationHandler.postDelayed(delayedNotification, NOTIFICATION_DEBOUNCE_MILLIS);
		}
	}

	void mark(String id, String className, ChangeType type) {

		if (shouldPersist()) {
			// update an existing item, or create a new one
			Realm realm = Realm.getDefaultInstance();
			ChangeJournalItem change = realm.where(ChangeJournalItem.class)
					.equalTo("prefix", persistPrefix)
					.equalTo("modelObjectId", id)
					.findFirst();

			if (change != null) {
				realm.beginTransaction();
				change.setChangeType(type.ordinal());
				realm.commitTransaction();
			} else {
				realm.beginTransaction();
				ChangeJournalItem.create(realm, persistPrefix, id, className, type);
				realm.commitTransaction();
			}
			realm.close();
		} else {
			changeJournalItemsByObjectId.put(id, new ChangeJournalItem(id, className, type));
		}
	}

}
