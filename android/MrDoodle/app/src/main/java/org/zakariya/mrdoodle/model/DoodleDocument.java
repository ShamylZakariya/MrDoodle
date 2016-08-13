package org.zakariya.mrdoodle.model;

import android.content.Context;
import android.support.annotation.Nullable;

import org.zakariya.doodle.model.StrokeDoodle;
import org.zakariya.mrdoodle.events.DoodleDocumentCreatedEvent;
import org.zakariya.mrdoodle.events.DoodleDocumentDeletedEvent;
import org.zakariya.mrdoodle.events.DoodleDocumentEditedEvent;
import org.zakariya.mrdoodle.util.BusProvider;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmObject;
import io.realm.RealmResults;
import io.realm.annotations.PrimaryKey;
import io.realm.annotations.Required;

/**
 * Created by shamyl on 12/16/15.
 */
public class DoodleDocument extends RealmObject {

	private static final String TAG = DoodleDocument.class.getSimpleName();

	@PrimaryKey
	private String uuid;

	@Required
	private String name;

	@Required
	private Date creationDate;

	private Date modificationDate;

	/**
	 * Create a new DoodleDocument with UUID, name and creationDate set.
	 *
	 * @param realm the realm into which to assign the new document
	 * @param name  the name of the document, e.g., "Untitle Document"
	 * @return a new DoodleDocument with unique UUID, in the realm and ready to use
	 */
	public static DoodleDocument create(Realm realm, String name) {

		realm.beginTransaction();
		DoodleDocument doc = realm.createObject(DoodleDocument.class);
		doc.setUuid(UUID.randomUUID().toString());
		doc.setCreationDate(new Date());
		doc.setModificationDate(new Date());
		doc.setName(name);
		realm.commitTransaction();

		BusProvider.postOnMainThread(new DoodleDocumentCreatedEvent(doc.getUuid()));

		return doc;
	}

	public static void delete(Context context, Realm realm, DoodleDocument doc) {
		String uuid = doc.getUuid();

		doc.deleteSaveFile(context);

		realm.beginTransaction();
		doc.deleteFromRealm();
		realm.commitTransaction();

		BusProvider.postOnMainThread(new DoodleDocumentDeletedEvent(uuid));
	}

	public static RealmResults<DoodleDocument> all(Realm realm) {
		return realm.where(DoodleDocument.class).findAll();
	}

	/**
	 * Delete the document's save file
	 *
	 * @param context  the context
	 */
	public void deleteSaveFile(Context context) {
		File file = getSaveFile(context);
		if (file.exists()) {
			//noinspection ResultOfMethodCallIgnored
			file.delete();
		}
	}

	@Nullable
	public static DoodleDocument byUUID(Realm realm, String uuid) {
		return realm.where(DoodleDocument.class).equalTo("uuid", uuid).findFirst();
	}

	/**
	 * Get the file used by a DoodleDocument to save its doodle.
	 * Note, the doodle is not saved in the realm because realm doesn't like big binary blobs.
	 *
	 * @param context  the context
	 * @return a File object referring to the document's doodle's save data. May not exist if nothing has been saved
	 */
	public File getSaveFile(Context context) {
		return new File(context.getFilesDir(), getUuid() + ".doodle");
	}

	public void saveDoodle(Context context, StrokeDoodle doodle) {
		File outputFile = getSaveFile(context);
		try {
			FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
			BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
			doodle.serializeDoodle(bufferedOutputStream);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public StrokeDoodle loadDoodle(Context context) {
		StrokeDoodle doodle = new StrokeDoodle(context);
		File inputFile = getSaveFile(context);
		if (inputFile.exists()) {
			try {
				FileInputStream inputStream = new FileInputStream(inputFile);
				BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
				doodle.inflateDoodle(bufferedInputStream);
				inputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return doodle;
	}


	/**
	 * Set the document's modification date to now and notify that the document has been edited
	 */
	public void markModified() {
		setModificationDate(new Date());
		BusProvider.postOnMainThread(new DoodleDocumentEditedEvent(getUuid()));
	}

	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Date getCreationDate() {
		return creationDate;
	}

	public void setCreationDate(Date creationDate) {
		this.creationDate = creationDate;
	}

	public Date getModificationDate() {
		return modificationDate;
	}

	public void setModificationDate(Date modificationDate) {
		this.modificationDate = modificationDate;
	}
}
