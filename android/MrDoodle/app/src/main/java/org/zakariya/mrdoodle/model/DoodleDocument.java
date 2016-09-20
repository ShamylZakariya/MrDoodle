package org.zakariya.mrdoodle.model;

import android.content.Context;
import android.support.annotation.Nullable;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import org.zakariya.doodle.model.Doodle;
import org.zakariya.doodle.model.StrokeDoodle;
import org.zakariya.mrdoodle.events.DoodleDocumentCreatedEvent;
import org.zakariya.mrdoodle.events.DoodleDocumentWillBeDeletedEvent;
import org.zakariya.mrdoodle.events.DoodleDocumentEditedEvent;
import org.zakariya.mrdoodle.util.BusProvider;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.util.ArrayList;
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
	private static final int COOKIE = 0xD0D1;

	public static final String BLOB_TYPE = "DoodleDocument";


	@PrimaryKey
	private String uuid;

	@Required
	private String name;

	@Required
	private Date creationDate;

	@Required
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

	/**
	 * Delete a DoodleDocument and its associated files on disc
	 * @param context a context, needed to get access to files dir
	 * @param realm a realm
	 * @param doc the doodle document
	 */
	public static void delete(Context context, Realm realm, DoodleDocument doc) {

		// notify that the document is about to be deleted
		String uuid = doc.getUuid();
		BusProvider.postOnMainThread(new DoodleDocumentWillBeDeletedEvent(uuid));

		doc.deleteSaveFile(context);

		realm.beginTransaction();
		doc.deleteFromRealm();
		realm.commitTransaction();
	}

	/**
	 * Delete all DoodleDocuments and associated files on disc
	 * @param context a context, needed to get access to files dir
	 * @param realm a realm
	 */
	public static void deleteAll(Context context, Realm realm) {
		ArrayList<DoodleDocument> all = new ArrayList<>(DoodleDocument.all(realm));
		for (DoodleDocument doc : all) {
			delete(context, realm, doc);
		}
	}

	/**
	 * Get all DoodleDocuments
	 * @param realm a realm
	 * @return all DoodleDocuments
	 */
	public static RealmResults<DoodleDocument> all(Realm realm) {
		return realm.where(DoodleDocument.class).findAll();
	}

	/**
	 * Delete the document's save file
	 *
	 * @param context  the context
	 */
	void deleteSaveFile(Context context) {
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

	public byte[] serialize(Context context) {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		Output output = new Output(stream);
		Kryo kryo = new Kryo();

		kryo.writeObject(output, COOKIE);
		kryo.writeObject(output, getUuid());
		kryo.writeObject(output, getName());
		kryo.writeObject(output, getCreationDate());
		kryo.writeObject(output, getModificationDate());

		Doodle doodle = loadDoodle(context);
		byte [] doodleBytes = doodle.serialize();
		kryo.writeObject(output, doodleBytes);
		output.close();

		return stream.toByteArray();
	}

	public static void createOrUpdate(Context context, Realm realm, byte [] serializedBytes) throws Exception {
		ByteArrayInputStream inputStream = new ByteArrayInputStream(serializedBytes);
		Input input = new Input(inputStream);
		Kryo kryo = new Kryo();

		int cookie = kryo.readObject(input, Integer.class);
		if (cookie == COOKIE) {

			String uuid = kryo.readObject(input, String.class);
			String name = kryo.readObject(input, String.class);
			Date creationDate = kryo.readObject(input, Date.class);
			Date modificationDate = kryo.readObject(input, Date.class);
			byte [] doodleBytes = kryo.readObject(input, byte[].class);

			// create the doodle
			StrokeDoodle doodle = new StrokeDoodle(context, new ByteArrayInputStream(doodleBytes));

			boolean wasModified = false;

			// now if we already have a DoodleDocument with this modelId, update it. otherwise, make a new one
			DoodleDocument document = byUUID(realm, uuid);
			if (document != null) {
				wasModified = true;
				realm.beginTransaction();
				document.setName(name);
				document.setCreationDate(creationDate);
				document.setModificationDate(modificationDate);
				realm.commitTransaction();
			} else {
				realm.beginTransaction();
				document = realm.createObject(DoodleDocument.class);
				document.setUuid(uuid);
				document.setCreationDate(creationDate);
				document.setModificationDate(modificationDate);
				document.setName(name);
				realm.commitTransaction();
			}

			// doodle has to be saved separately
			document.saveDoodle(context, doodle);

			if (wasModified) {
				BusProvider.postOnMainThread(new DoodleDocumentEditedEvent(document.getUuid()));
			} else {
				BusProvider.postOnMainThread(new DoodleDocumentCreatedEvent(document.getUuid()));
			}

		} else {
			throw new InvalidObjectException("Missing COOKIE header (0x" + Integer.toString(COOKIE, 16) + ")");
		}
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
			doodle.serialize(bufferedOutputStream);
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
				doodle.inflate(bufferedInputStream);
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
