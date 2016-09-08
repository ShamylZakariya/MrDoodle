package org.zakariya.mrdoodle.sync.model;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;

/**
 * Represents a single recorded change to the local model. These changes, collected in the Journal,
 * will be used at sync-time to push local changes upstream to the server.
 */
public class JournalItem extends RealmObject {

	private String modelObjectId;
	private String modelObjectClass;
	private int changeType;

	/**
	 * Create a new JournalItem representing a change to a model object
	 *
	 * @param realm            the realm
	 * @param modelObjectId    the id of the model object that was modified
	 * @param modelObjectClass the class of the model object that was modified
	 * @param type             the type of change
	 * @return new JournalItem inserted into the Realm
	 */
	public static JournalItem create(Realm realm, String modelObjectId, String modelObjectClass, Type type) {
		JournalItem change = realm.createObject(JournalItem.class);
		change.setModelObjectId(modelObjectId);
		change.setModelObjectClass(modelObjectClass);
		change.setChangeType(type.ordinal());
		return change;
	}

	public static RealmResults<JournalItem> all(Realm realm) {
		return realm.where(JournalItem.class).findAll();
	}


	public static String toString(JournalItem c) {
		if (c != null) {
			return "<JournalItem modelObjectId: " + c.getModelObjectId() + " modelObjectClass: " + c.getModelObjectClass() + " type: " + Type.values()[c.getChangeType()].name() + ">";
		}

		return "<JournalItem null>";
	}

	public String getModelObjectId() {
		return modelObjectId;
	}

	public void setModelObjectId(String modelObjectId) {
		this.modelObjectId = modelObjectId;
	}

	public String getModelObjectClass() {
		return modelObjectClass;
	}

	public void setModelObjectClass(String modelObjectClass) {
		this.modelObjectClass = modelObjectClass;
	}

	public int getChangeType() {
		return changeType;
	}

	public void setChangeType(int changeType) {
		this.changeType = changeType;
	}

	public static enum Type {
		MODIFY, DELETE
	}
}
