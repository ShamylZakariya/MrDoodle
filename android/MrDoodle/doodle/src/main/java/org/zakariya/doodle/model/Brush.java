package org.zakariya.doodle.model;

import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.Parcel;
import android.os.Parcelable;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoSerializable;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

/**
 * Created by shamyl on 10/28/15.
 */
public class Brush implements Parcelable, KryoSerializable {

	private int color;
	private float minWidth;
	private float maxWidth;
	private float maxWidthDpPs;
	private boolean eraser;
	private Paint paint;

	public Brush() {
	}

	public Brush(int color, float minWidth, float maxWidth, float maxWidthDpPs, boolean eraser) {
		this.color = color;
		this.minWidth = minWidth;
		this.maxWidth = maxWidth;
		this.maxWidthDpPs = maxWidthDpPs;
		this.eraser = eraser;
	}

	public int getColor() {
		return color;
	}

	public float getMinWidth() {
		return minWidth;
	}

	public float getMaxWidth() {
		return maxWidth;
	}

	public float getMaxWidthDpPs() {
		return maxWidthDpPs;
	}

	public boolean isEraser() {
		return eraser;
	}

	public Paint getPaint() {
		if (paint == null) {
			paint = new Paint();
			paint.setAntiAlias(true);
			paint.setColor(color);
			paint.setStyle(Paint.Style.FILL);
			paint.setXfermode(new PorterDuffXfermode(eraser ? PorterDuff.Mode.CLEAR : PorterDuff.Mode.SRC_OVER));
		}
		return paint;
	}

	public Brush copy() {
		return new Brush(color, minWidth, maxWidth, maxWidthDpPs, eraser);
	}

	// Parcelable

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(color);
		dest.writeFloat(minWidth);
		dest.writeFloat(maxWidth);
		dest.writeFloat(maxWidthDpPs);
		dest.writeInt(eraser ? 1 : 0);
	}

	public static final Parcelable.Creator<Brush> CREATOR = new Parcelable.Creator<Brush>() {
		public Brush createFromParcel(Parcel in) {
			return new Brush(in);
		}

		public Brush[] newArray(int size) {
			return new Brush[size];
		}
	};

	private Brush(Parcel in) {
		color = in.readInt();
		minWidth = in.readFloat();
		maxWidth = in.readFloat();
		maxWidthDpPs = in.readFloat();
		eraser = in.readInt() == 1;
	}

	// KryoSerializable

	static final int SERIALIZATION_VERSION = 0;

	@Override
	public void write(Kryo kryo, Output output) {
		output.writeInt(SERIALIZATION_VERSION);
		output.writeInt(color);
		output.writeFloat(minWidth);
		output.writeFloat(maxWidth);
		output.writeFloat(maxWidthDpPs);
		output.writeBoolean(eraser);
	}

	@Override
	public void read(Kryo kryo, Input input) {
		int serializationVersion = input.readInt();
		switch (serializationVersion) {
			case 0:
				color = input.readInt();
				minWidth = input.readFloat();
				maxWidth = input.readFloat();
				maxWidthDpPs = input.readFloat();
				eraser = input.readBoolean();
				break;
			default:
				throw new IllegalArgumentException("Unsupported " + this.getClass().getName() + " serialization version: " + serializationVersion);
		}
	}
}
