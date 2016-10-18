package org.zakariya.mrdoodle.model;

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.Log;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import org.zakariya.doodle.model.Doodle;
import org.zakariya.doodle.model.StrokeDoodle;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
	private static final int MAX_REALM_BYTE_ARRAY_SIZE = 1024 * 1024 * 8; // 8 megs. Realm can do 16 but let's be safe.

	public static final String DOCUMENT_TYPE = "DoodleDocument";


	@PrimaryKey
	private String uuid;

	@Required
	private String name;

	@Required
	private Date creationDate;

	@Required
	private Date modificationDate;

	private byte[] doodleBytes;

	/**
	 * Create a new DoodleDocument with UUID, name and creationDate set.
	 *
	 * @param realm the realm into which to assign the new document
	 * @param name  the name of the document, e.g., "Untitled Document"
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

		return doc;
	}

	/**
	 * Delete a DoodleDocument and its associated files on disc
	 *
	 * @param context a context, needed to get access to files dir
	 * @param realm   a realm
	 * @param doc     the doodle document
	 */
	public static void delete(Context context, Realm realm, DoodleDocument doc) {
		doc.deleteSaveFile(context);

		realm.beginTransaction();
		doc.deleteFromRealm();
		realm.commitTransaction();
	}

	/**
	 * Delete all DoodleDocuments and associated files on disc
	 *
	 * @param context a context, needed to get access to files dir
	 * @param realm   a realm
	 */
	public static void deleteAll(Context context, Realm realm) {
		ArrayList<DoodleDocument> all = new ArrayList<>(DoodleDocument.all(realm));
		for (DoodleDocument doc : all) {
			delete(context, realm, doc);
		}
	}

	/**
	 * Get all DoodleDocuments
	 *
	 * @param realm a realm
	 * @return all DoodleDocuments
	 */
	public static RealmResults<DoodleDocument> all(Realm realm) {
		return realm.where(DoodleDocument.class).findAll();
	}

	/**
	 * Delete the document's save file
	 *
	 * @param context the context
	 */
	private void deleteSaveFile(Context context) {
		File file = getSaveFile(context);
		if (file.exists()) {
			//noinspection ResultOfMethodCallIgnored
			file.delete();
		}
	}

	@Nullable
	public static DoodleDocument byUuid(Realm realm, String uuid) {
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
		byte[] doodleBytes = doodle.serialize();
		kryo.writeObject(output, doodleBytes);
		output.close();

		return stream.toByteArray();
	}

	/**
	 * given serialized DoodleDocument data, create a new document or update an existing one in the realm
	 *
	 * @param context         an android context
	 * @param realm           a realm where doodle documents should live
	 * @param serializedBytes the serialized form of a doodle document
	 * @return true iff an existing document was modified, false if a new document was created
	 * @throws IOException
	 */
	public static boolean createOrUpdate(Context context, Realm realm, byte[] serializedBytes) throws IOException {
		ByteArrayInputStream inputStream = new ByteArrayInputStream(serializedBytes);
		Input input = new Input(inputStream);
		Kryo kryo = new Kryo();

		int cookie = kryo.readObject(input, Integer.class);
		if (cookie == COOKIE) {

			String uuid = kryo.readObject(input, String.class);
			String name = kryo.readObject(input, String.class);
			Date creationDate = kryo.readObject(input, Date.class);
			Date modificationDate = kryo.readObject(input, Date.class);
			byte[] doodleBytes = kryo.readObject(input, byte[].class);

			boolean wasModified = false;

			// now if we already have a DoodleDocument with this documentId, update it. otherwise, make a new one
			DoodleDocument document = byUuid(realm, uuid);
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
			realm.beginTransaction();
			document.saveDoodle(context, doodleBytes);
			realm.commitTransaction();

			return wasModified;
		} else {
			throw new MalformedDoodleDocumentException("Missing COOKIE header (0x" + Integer.toString(COOKIE, 16) + ")");
		}
	}

	/**
	 * Get the file used by a DoodleDocument to save its doodle.
	 * Note, the doodle is not saved in the realm because realm doesn't like big binary blobs.
	 *
	 * @param context the context
	 * @return a File object referring to the document's doodle's save data. May not exist if nothing has been saved
	 */
	private File getSaveFile(Context context) {
		return new File(context.getFilesDir(), getUuid() + ".doodle");
	}

	private void saveDoodle(Context context, byte[] doodleBytes) {
		// if byte stream isn't too big, save directly to realm. Otherwise, save to a file
		if (doodleBytes.length < MAX_REALM_BYTE_ARRAY_SIZE) {
			setDoodleBytes(doodleBytes);
		} else {
			try {
				File outputFile = getSaveFile(context);
				ByteArrayOutputStream byteStream = new ByteArrayOutputStream(doodleBytes.length);
				byteStream.write(doodleBytes);
				FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
				byteStream.writeTo(fileOutputStream);
				fileOutputStream.close();
			} catch (IOException e) {
				Log.e(TAG, "saveDoodle: unable to save doodle byte[] to file", e);
			}
		}
	}

	public void saveDoodle(Context context, StrokeDoodle doodle) {
		// serialize doodle to byte stream and hand off byte[]
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		doodle.serialize(byteStream);
		saveDoodle(context, byteStream.toByteArray());
	}

	public StrokeDoodle loadDoodle(Context context) {
		StrokeDoodle doodle = new StrokeDoodle();

		byte[] doodleBytes = getDoodleBytes();
		if (doodleBytes != null && doodleBytes.length > 0) {
			ByteArrayInputStream inputStream = new ByteArrayInputStream(doodleBytes);

			try {
				doodle.inflate(inputStream);
				inputStream.close();
			} catch (IOException e) {
				Log.e(TAG, "loadDoodle: unable to inflate doodle from Realm doodleBytes[]", e);
				e.printStackTrace();
			}
		} else {
			// this doodle may have been too big to save to Realm, so try to load a file
			File inputFile = getSaveFile(context);
			if (inputFile.exists()) {
				try {
					FileInputStream inputStream = new FileInputStream(inputFile);
					BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
					doodle.inflate(bufferedInputStream);
					inputStream.close();
				} catch (IOException e) {
					Log.e(TAG, "loadDoodle: unable to inflate doodle from file: " + inputFile, e);
					e.printStackTrace();
				}
			}
		}


		return doodle;
	}


	/**
	 * Set the document's modification date to now and notify that the document has been edited
	 */
	public void markModified() {
		setModificationDate(new Date());
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

	public byte[] getDoodleBytes() {
		return doodleBytes;
	}

	public void setDoodleBytes(byte[] doodleBytes) {
		this.doodleBytes = doodleBytes;
	}
}
