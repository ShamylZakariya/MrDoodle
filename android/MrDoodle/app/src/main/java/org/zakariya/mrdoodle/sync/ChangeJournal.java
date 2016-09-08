package org.zakariya.mrdoodle.sync;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.squareup.otto.Subscribe;

import org.zakariya.mrdoodle.events.ApplicationDidBackgroundEvent;
import org.zakariya.mrdoodle.events.ChangeJournalUpdatedEvent;
import org.zakariya.mrdoodle.events.DoodleDocumentCreatedEvent;
import org.zakariya.mrdoodle.events.DoodleDocumentDeletedEvent;
import org.zakariya.mrdoodle.events.DoodleDocumentEditedEvent;
import org.zakariya.mrdoodle.model.DoodleDocument;
import org.zakariya.mrdoodle.sync.model.JournalItem;
import org.zakariya.mrdoodle.util.BusProvider;

import java.util.ArrayList;
import java.util.HashMap;

import io.realm.Realm;

/**
 * Listens for various model create/modify/delete events, collecting them and creating an
 * up-to-date list of JournalItems representing changes since last time journal was cleared
 */
public class ChangeJournal {

	private final static String TAG = "ChangeJournal";
	private final static int COMMIT_DEBOUNCE_DELAY_MILLIS = 2 * 1000; // 2 seconds

	private Context context;
	private HashMap<String, Change> localChanges = new HashMap<>();
	private Handler delayedCommitHandler;
	private Runnable delayedCommit;
	private boolean dirty;

	public ChangeJournal(Context context) {
		this.context = context;
		this.dirty = false;

		delayedCommitHandler = new Handler(Looper.getMainLooper());
		delayedCommit = new Runnable() {
			@Override
			public void run() {
				ChangeJournal.this.commit();
			}
		};
		BusProvider.getBus().register(this);
	}

	/**
	 * Deletes recorded local changes
	 */
	public void clear(boolean notify) {
		localChanges.clear();

		// gather and remove JournalItems for this account
		Realm realm = Realm.getDefaultInstance();

		ArrayList<JournalItem> existingJournalItems = new ArrayList<>();
		for (JournalItem item : JournalItem.all(realm)) {
			existingJournalItems.add(item);
		}

		realm.beginTransaction();

		for (JournalItem journalItem : existingJournalItems) {
			journalItem.deleteFromRealm();
		}

		realm.commitTransaction();
		realm.close();

		// cancel any pending serializations
		dirty = false;
		delayedCommitHandler.removeCallbacks(delayedCommit);

		if (notify) {
			BusProvider.postOnMainThread(new ChangeJournalUpdatedEvent());
		}
	}

	private void commit() {
		if (!dirty) {
			Log.i(TAG, "commit - not dirty, nothing to commit");
			return;
		}

		Realm realm = Realm.getDefaultInstance();

		// Merge our changes with those in the existing journal - with newer changes overwriting older ones
		// NOTE: We have to copy JournalItems to an ArrayList since removing them later
		// would mutate RealmResults, causing an internal consistency exception

		HashMap<String, Change> mergedChanges = new HashMap<>();
		ArrayList<JournalItem> existingJournalItems = new ArrayList<>();
		for (JournalItem item : JournalItem.all(realm)) {
			existingJournalItems.add(item);
			mergedChanges.put(item.getModelObjectId(), new Change(item.getModelObjectId(), item.getModelObjectClass(), item.getChangeType()));
		}

		for (String id : localChanges.keySet()) {
			mergedChanges.put(id, localChanges.get(id));
		}

		localChanges.clear();

		realm.beginTransaction();

		// Clear all existing journal items for this account, and replace with the merged set

		for (JournalItem journalItem : existingJournalItems) {
			journalItem.deleteFromRealm();
		}

		for (Change change : mergedChanges.values()) {
			JournalItem.create(
					realm,
					change.modelObjectId,
					change.modelObjectClass,
					JournalItem.Type.values()[change.changeType]);
		}

		realm.commitTransaction();
		realm.close();

		dirty = false;

		// notify
		BusProvider.postOnMainThread(new ChangeJournalUpdatedEvent());
	}

	///////////////////////////////////////////////////////////////////

	private void scheduleCommit() {
		delayedCommitHandler.removeCallbacks(delayedCommit);
		delayedCommitHandler.postDelayed(delayedCommit, COMMIT_DEBOUNCE_DELAY_MILLIS);
	}

	private void markModified(String id, String className) {
		Log.d(TAG, "markModified class: " + className + " id: " + id);
		localChanges.put(id, new Change(id, className, JournalItem.Type.MODIFY.ordinal()));

		dirty = true;
		scheduleCommit();
	}

	private void markDeleted(String id, String className) {
		Log.d(TAG, "markDeleted class: " + className + " id: " + id);
		localChanges.put(id, new Change(id, className, JournalItem.Type.DELETE.ordinal()));

		dirty = true;
		scheduleCommit();
	}

	///////////////////////////////////////////////////////////////////

	@Subscribe
	public void onApplicationDidBackground(ApplicationDidBackgroundEvent event) {
		commit();
	}

	///////////////////////////////////////////////////////////////////

	@Subscribe
	public void onDoodleDocumentCreated(DoodleDocumentCreatedEvent event) {
		markModified(event.getUuid(), DoodleDocument.class.getSimpleName());
	}

	@Subscribe
	public void onDoodleDocumentDeleted(DoodleDocumentDeletedEvent event) {
		markDeleted(event.getUuid(), DoodleDocument.class.getSimpleName());
	}

	@Subscribe
	public void onDoodleDocumentModified(DoodleDocumentEditedEvent event) {
		markModified(event.getUuid(), DoodleDocument.class.getSimpleName());
	}

	///////////////////////////////////////////////////////////////////

	// Change shadows model.JournalItem
	private static class Change {
		private String modelObjectId;
		private String modelObjectClass;
		private int changeType;

		private Change(String modelObjectId, String modelObjectClass, int changeType) {
			this.modelObjectId = modelObjectId;
			this.modelObjectClass = modelObjectClass;
			this.changeType = changeType;
		}

		@Override
		public String toString() {
			return "<Change id: " + modelObjectId + " class: " + modelObjectClass + " type: " + changeType + ">";
		}
	}
}
