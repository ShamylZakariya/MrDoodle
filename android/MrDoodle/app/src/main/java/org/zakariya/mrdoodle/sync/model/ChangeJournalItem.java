package org.zakariya.mrdoodle.sync.model;

import io.realm.Realm;
import io.realm.RealmObject;

/**
 * Represents a single recorded change to the local model. These changes, collected in the Journal,
 * will be used at sync-time to push local changes upstream to the server.
 */
public class ChangeJournalItem extends RealmObject {

	private String prefix;
	private String modelObjectId;
	private String modelObjectClass;
	private int changeType;

	public ChangeJournalItem() {
	}

	public ChangeJournalItem(String prefix, String modelObjectId, String modelObjectClass, int changeType) {
		this.prefix = prefix;
		this.modelObjectId = modelObjectId;
		this.modelObjectClass = modelObjectClass;
		this.changeType = changeType;
	}

	public ChangeJournalItem(String prefix, String modelObjectId, String modelObjectClass, ChangeType changeType) {
		this(prefix, modelObjectId, modelObjectClass, changeType.ordinal());
	}

	public ChangeJournalItem(String modelObjectId, String modelObjectClass, ChangeType changeType) {
		this(null, modelObjectId, modelObjectClass, changeType.ordinal());
	}

	/**
	 * Create a new ChangeJournalItem representing a change to a model object
	 *
	 * @param realm            the realm
	 * @param modelObjectId    the id of the model object that was modified
	 * @param modelObjectClass the class of the model object that was modified
	 * @param type             the type of change
	 * @return new ChangeJournalItem inserted into the Realm
	 */
	public static ChangeJournalItem create(Realm realm, String prefix, String modelObjectId, String modelObjectClass, ChangeType type) {
		ChangeJournalItem change = realm.createObject(ChangeJournalItem.class);
		change.setPrefix(prefix);
		change.setModelObjectId(modelObjectId);
		change.setModelObjectClass(modelObjectClass);
		change.setChangeType(type.ordinal());
		return change;
	}

	public static String toString(ChangeJournalItem c) {
		if (c != null) {
			return "<ChangeJournalItem modelObjectId: " + c.getModelObjectId() + " modelObjectClass: " + c.getModelObjectClass() + " type: " + ChangeType.values()[c.getChangeType()].name() + ">";
		}

		return "<ChangeJournalItem null>";
	}

	public String getPrefix() {
		return prefix;
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
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

}
