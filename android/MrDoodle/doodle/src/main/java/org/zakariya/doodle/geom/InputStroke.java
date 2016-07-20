package org.zakariya.doodle.geom;

import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Pair;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoSerializable;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.ArrayList;

/**
 * Created by shamyl on 9/28/15.
 */
public class InputStroke implements Parcelable, KryoSerializable {

	private static final String TAG = "InputStroke";

	// as vertices are added, when a new segment represents a corner greater than this size,
	// and autoOptimizationThreshold is > 0, the line will be optimized in-place.
	private static final float AUTO_OPTIMIZE_CORNER_THRESHOLD = (float) Math.cos(45);

	static final int MIN_OPTIMIZATION_SIZE = 8;
	static final int POINT_VELOCITY_SMOOTHING_KERNEL_SIZE = 7; // MUST BE ODD
	static float POINT_VELOCITY_SMOOTHING_KERNEL[];
	static int POINT_VELOCITY_SMOOTHING_KERNEL_BACKTRACK = 0;

	private ArrayList<Point> points = new ArrayList<>();
	private RectF boundingRect = new RectF();
	private float autoOptimizationThreshold = 0;

	static {
		// generate a kernel with linear fade out from center
		final int kernelSize = POINT_VELOCITY_SMOOTHING_KERNEL_SIZE;
		final int halfKernelSize = kernelSize / 2;
		POINT_VELOCITY_SMOOTHING_KERNEL = new float[kernelSize];
		POINT_VELOCITY_SMOOTHING_KERNEL_BACKTRACK = halfKernelSize;
		for (int i = 0; i <= halfKernelSize; i++) {
			float v = (float) i / (float) (halfKernelSize + 1);
			float kv = 1f - v;
			POINT_VELOCITY_SMOOTHING_KERNEL[halfKernelSize + i] = kv;
			POINT_VELOCITY_SMOOTHING_KERNEL[halfKernelSize - i] = kv;
		}

		// now sum
		float sum = 0;
		for (int i = 0; i < kernelSize; i++) {
			sum += POINT_VELOCITY_SMOOTHING_KERNEL[i];
		}

		// now normalize
		for (int i = 0; i < kernelSize; i++) {
			POINT_VELOCITY_SMOOTHING_KERNEL[i] /= sum;
		}
	}

	public InputStroke() {
	}

	public InputStroke(float autoOptimizationThreshold) {
		this.autoOptimizationThreshold = autoOptimizationThreshold;
	}

	public ArrayList<Point> getPoints() {
		return points;
	}

	public int size() {
		return points.size();
	}

	public boolean isEmpty() {
		return points.isEmpty();
	}

	public Point get(int i) {
		if (i < 0) {
			return get(points.size() + i);
		} else {
			return points.get(i);
		}
	}

	public float getAutoOptimizationThreshold() {
		return autoOptimizationThreshold;
	}

	public void setAutoOptimizationThreshold(float autoOptimizationThreshold) {
		this.autoOptimizationThreshold = Math.max(autoOptimizationThreshold, 0);
	}

	/**
	 * Get the tangent of the line at a given point
	 *
	 * @param i the index of the point of interest
	 * @return the tangent vector, computed as the bisecting unit vector between the preceeding line segment and the outgoing line segment, pointing "forward"
	 */
	public PointF getTangent(int i) {
		final int count = points.size();
		if (count < 2) {
			return new PointF(0, 0);
		}

		if (i == 0) {
			return PointFUtil.dir(get(0).position, get(1).position).first;
		} else if (i == count - 1) {
			return PointFUtil.dir(get(i - 1).position, get(i).position).first;
		} else {
			PointF a = get(i - 1).position;
			PointF b = get(i).position;
			PointF c = get(i + 1).position;

			Pair<PointF, Float> abDir = PointFUtil.dir(a, b);
			PointF abPrime = PointFUtil.rotateCCW(abDir.first);

			Pair<PointF, Float> bcDir = PointFUtil.dir(b, c);
			PointF bcPrime = PointFUtil.rotateCCW(bcDir.first);

			PointF half = new PointF(abPrime.x + bcPrime.x, abPrime.y + bcPrime.y);
			if (PointFUtil.length2(half) > 1e-4) {
				return PointFUtil.normalize(PointFUtil.rotateCW(half)).first;
			} else {
				return bcPrime;
			}
		}
	}

	public PointF getSegmentDirection(int i) {
		if (i < size() - 1) {
			PointF a = points.get(i).position;
			PointF b = points.get(i + 1).position;
			return PointFUtil.dir(a, b).first;
		} else {
			return null;
		}
	}

	@Nullable
	public Point firstPoint() {
		return points.isEmpty() ? null : points.get(0);
	}

	@Nullable
	public Point lastPoint() {
		return points.isEmpty() ? null : points.get(points.size() - 1);
	}

	public boolean add(float x, float y, long timestamp) {
		Point p = new Point(x, y, timestamp);
		points.add(p);

		final int size = points.size();

		// since we use a smoothing kernel, each time we add a point, we need to recalculate
		// the velocities of some number of points leading up to the newly added point
		for (int i = Math.max(size - POINT_VELOCITY_SMOOTHING_KERNEL_BACKTRACK, 0); i < size; i++) {
			updateVelocityOfPoint(i);
		}

		if (points.size() == 1) {
			// give it a little space since a point has no area
			boundingRect.set(x - 0.5f, y - 0.5f, x + 0.5f, y + 0.5f);
		} else {
			boundingRect.union(x, y);
		}

		boolean didOptimize = false;
		if (autoOptimizationThreshold > 0 && size > MIN_OPTIMIZATION_SIZE) {

			// look to see if the newly added segment represents a tight corner to the previous segment.
			int newSegmentIndex = size - 2;
			int previousSegmentIndex = size - 3;
			PointF previousSegmentDir = getSegmentDirection(previousSegmentIndex);
			PointF newSegmentDir = getSegmentDirection(newSegmentIndex);
			float dot = PointFUtil.dot(previousSegmentDir, newSegmentDir);

			if (dot < AUTO_OPTIMIZE_CORNER_THRESHOLD) {
				didOptimize = optimize(autoOptimizationThreshold) > 0;
			}
		}

		return didOptimize;
	}

	public boolean add(float x, float y) {
		return add(x, y, System.currentTimeMillis());
	}

	public void finish() {
		if (autoOptimizationThreshold > 0 && points.size() > 2) {
			optimize(autoOptimizationThreshold);
		}
	}

	public void invalidate() {
		computeBoundingRect();
	}

	public void clear() {
		points.clear();
		boundingRect = new RectF();
	}

	public RectF getBoundingRect() {
		if (boundingRect.isEmpty()){
			computeBoundingRect();
		}
		return boundingRect;
	}

	public RectF computeBoundingRect() {
		if (!isEmpty()) {
			Point p = get(0);
			boundingRect = new RectF(p.position.x - 0.5f, p.position.y - 0.5f, p.position.x + 0.5f, p.position.y + 0.5f);
			for (int i = 1, N = size(); i < N; i++) {
				p = get(i);
				boundingRect.union(p.position.x, p.position.y);
			}
		} else {
			boundingRect = new RectF();
		}

		return boundingRect;
	}

	@Override
	public String toString() {
		return TextUtils.join(",", points);
	}

	@Override
	protected Object clone() throws CloneNotSupportedException {
		return copy();
	}

	public InputStroke copy() {
		InputStroke c = new InputStroke();
		int size = points.size();
		c.points.ensureCapacity(size);
		for (int i = 0; i < size; i++) {
			c.points.add(points.get(i).copy());
		}

		return c;
	}

	/**
	 * Optimizes this InputStroke to use fewer points
	 *
	 * @param threshold minimum linear deviation for a vertex to be included in optimized stroke
	 * @return number of points removed in optimization pass
	 */
	public int optimize(float threshold) {
		final int initialSize = points.size();
		if (threshold > 0 && initialSize > 2) {
			InputStroke optimized = this.optimized(threshold);
			this.points = optimized.points;
			this.boundingRect = optimized.boundingRect;

			return initialSize - this.points.size();
		}

		return 0;
	}

	/**
	 * Return an optimized InputStroke where vertices which deviate less than `threshold from the line defined by their neighbors are excised
	 *
	 * @param threshold the minimum linear deviation for a vertex to be included in final stroke
	 * @return optimized InputStroke with fewer points
	 */
	public InputStroke optimized(float threshold) {
		InputStroke optimized = _optimize(this, threshold * threshold, 0);
		optimized.computeBoundingRect();
		return optimized;
	}

	private static InputStroke _optimize(InputStroke in, float thresholdSquared, int depth) {

		if (in.size() <= 2) {
			return in;
		}

		//
		//	Find the vertex farthest from the line defined by the start and and of the path
		//

		float maxDistSquared = 0;
		int maxDistSquaredIndex = 0;
		final int size = in.size();
		final Point first = in.get(0);
		final Point last = in.get(size - 1);
		final LineSegment line = new LineSegment(first.position, last.position);

		for (int i = 1; i < size - 1; i++) {
			float dist = line.distanceSquared(in.get(i).position, true);
			if (dist > maxDistSquared) {
				maxDistSquared = dist;
				maxDistSquaredIndex = i;
			}
		}

		//
		//	If the farthest vertex is greater than our thresholdSquared, we need to
		//	partition and optimize left and right separately
		//

		if (maxDistSquared > thresholdSquared) {

			//
			//	Partition 'in' into left and right sub vectors, optimize them and join
			//

			InputStroke left = _slice(in, 0, maxDistSquaredIndex + 1);
			InputStroke right = _slice(in, maxDistSquaredIndex, size);
			InputStroke leftSimplified = _optimize(left, thresholdSquared, depth + 1);
			InputStroke rightSimplified = _optimize(right, thresholdSquared, depth + 1);

			InputStroke joined = new InputStroke();
			joined.points.ensureCapacity(leftSimplified.size() + rightSimplified.size() - 1);

			for (int i = 0; i < leftSimplified.size() - 1; i++) {
				joined.points.add(leftSimplified.points.get(i));
			}

			// skip first point of right since it's same as last of left
			for (int i = 0; i < rightSimplified.size(); i++) {
				joined.points.add(rightSimplified.points.get(i));
			}

			return joined;
		} else {

			//
			//  The line's straight enough that we don't need to keep the middle points
			//

			InputStroke optimized = new InputStroke();
			optimized.points.ensureCapacity(2);
			optimized.points.add(first);
			optimized.points.add(last);

			return optimized;
		}
	}

	/**
	 * Cut out a slice of an InputStroke starting at index start, up to but not including element at end index
	 *
	 * @param stroke the stroke to slice
	 * @param start  index of first element to copy
	 * @param end    end of slice, not included in output
	 * @return sub slice of this InputStroke
	 * NOTE: Does not update bounds of slice, you must call computeBoundingRect if you need the bounds updated.
	 */
	private static InputStroke _slice(InputStroke stroke, int start, int end) {
		InputStroke s = new InputStroke();
		s.points.ensureCapacity(end - start);

		for (int i = start; i < end; i++) {
			s.points.add(stroke.points.get(i));
		}

		return s;
	}

	/**
	 * Compute and update the stored velocity of the point at index i
	 *
	 * @param i index of the point whose velocity needs to be recalculated
	 */
	private void updateVelocityOfPoint(int i) {
		Point p = get(i);
		if (!p.freezeVelocity) {
			float sum = 0;
			for (int j = i - POINT_VELOCITY_SMOOTHING_KERNEL_BACKTRACK, k = 0, end = (i + POINT_VELOCITY_SMOOTHING_KERNEL_BACKTRACK + 1); j < end; j++, k++) {
				sum += POINT_VELOCITY_SMOOTHING_KERNEL[k] * computeVelocityOfPoint(j);
			}

			p.velocity = sum;
		}
	}

	/**
	 * Get the velocity of the point at `i as the average of the velocity of the preceeding and following line segments
	 *
	 * @param i the index of the point to query velocity
	 * @return velocity in Dp-per-second
	 */
	private float computeVelocityOfPoint(int i) {
		// velocity is computed as average of velocity draiwng preceding and
		// following segments joined by the point in question.
		// first and last points are assume to have zero velocity for preceding and following
		// segments, respectively.

		int size = points.size();
		if (size < 3) {
			return 0;
		}

		if (i < 0) {
			return 0;
		} else if (i == 0) {
			final Point a = points.get(i);
			final Point b = points.get(i + 1);
			final float length = PointFUtil.distance(a.position, b.position);
			final float seconds = (b.timestamp - a.timestamp) / 1000f;
			return (length / seconds) * 0.5f;
		} else if (i < size - 2) {
			final Point a = points.get(i - 1);
			final Point b = points.get(i);
			final Point c = points.get(i + 1);

			// get velocity of the preceding segment
			final float abLength = PointFUtil.distance(a.position, b.position);
			final float abSeconds = (b.timestamp - a.timestamp) / 1000f;
			final float abVel = abLength / abSeconds;

			// get velocity of the following segment
			final float cbLength = PointFUtil.distance(b.position, c.position);
			final float cbSeconds = (c.timestamp - b.timestamp) / 1000f;
			final float cbVel = cbLength / cbSeconds;

			return (abVel + cbVel) * 0.5f;

		} else if (i == points.size() - 1) {
			final Point a = points.get(i - 1);
			final Point b = points.get(i);
			final float length = PointFUtil.distance(a.position, b.position);
			final float seconds = (b.timestamp - a.timestamp) / 1000f;
			return (length / seconds) * 0.5f;
		} else {
			return 0;
		}
	}

	// Parcelable

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(size());
		for (Point point : points) {
			dest.writeParcelable(point, 0);
		}
	}

	public static final Parcelable.Creator<InputStroke> CREATOR = new Parcelable.Creator<InputStroke>() {
		public InputStroke createFromParcel(Parcel in) {
			return new InputStroke(in);
		}

		public InputStroke[] newArray(int size) {
			return new InputStroke[size];
		}
	};

	private InputStroke(Parcel in) {
		points = new ArrayList<>();
		int count = in.readInt();
		for (int i = 0; i < count; i++) {
			Point p = in.readParcelable(null);
			points.add(p);
		}

		invalidate();
	}

	// KryoSerializable
	static final int SERIALIZATION_VERSION = 0;

	@Override
	public void write(Kryo kryo, Output output) {
		output.writeInt(SERIALIZATION_VERSION);
		output.writeInt(size());
		for (Point point : points) {
			kryo.writeObject(output, point);
		}
	}

	@Override
	public void read(Kryo kryo, Input input) {
		int serializationVersion = input.readInt();
		switch (serializationVersion) {
			case 0:
				points = new ArrayList<>();
				int count = input.readInt();
				for (int i = 0; i < count; i++) {
					points.add(kryo.readObject(input, Point.class));
				}
				break;
			default:
				throw new IllegalArgumentException("Unsupported " + this.getClass().getName() + " serialization version: " + serializationVersion);
		}
	}

	/**
	 * Represents user input. As user drags across screen, each location is recorded along with its timestamp.
	 * The timestamps can be compared across an array of Point to determine the velocity of the touch,
	 * which will be used to determine line thickness.
	 */
	public static class Point implements Parcelable, KryoSerializable {
		public PointF position = new PointF();
		public long timestamp;
		public float velocity = 0;
		public boolean freezeVelocity = false;

		Point() {
		}

		public Point(float x, float y) {
			position.x = x;
			position.y = y;
			timestamp = System.currentTimeMillis();
		}

		public Point(float x, float y, long timestamp) {
			position.x = x;
			position.y = y;
			this.timestamp = timestamp;
		}

		@Override
		public String toString() {
			return "(" + position.x + "," + position.y + ")";
		}

		@Override
		protected Object clone() throws CloneNotSupportedException {
			return copy();
		}

		public Point copy() {
			Point p = new Point(position.x, position.y, timestamp);
			p.velocity = velocity;
			p.freezeVelocity = freezeVelocity;
			return p;
		}

		// Parcelable

		@Override
		public int describeContents() {
			return 0;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeFloat(position.x);
			dest.writeFloat(position.y);
			dest.writeLong(timestamp);
			dest.writeFloat(velocity);
			dest.writeInt(freezeVelocity ? 1 : 0);
		}

		public static final Creator<Point> CREATOR = new Creator<Point>() {
			public Point createFromParcel(Parcel in) {
				return new Point(in);
			}

			public Point[] newArray(int size) {
				return new Point[size];
			}
		};

		private Point(Parcel in) {
			position = new PointF(in.readFloat(), in.readFloat());
			timestamp = in.readLong();
			velocity = in.readFloat();
			freezeVelocity = in.readInt() == 1;
		}

		// KryoSerializable

		static final int SERIALIZATION_VERSION = 0;

		@Override
		public void write(Kryo kryo, Output output) {
			output.writeInt(SERIALIZATION_VERSION);
			output.writeFloat(position.x);
			output.writeFloat(position.y);
			output.writeLong(timestamp);
			output.writeFloat(velocity);
			output.writeBoolean(freezeVelocity);
		}

		@Override
		public void read(Kryo kryo, Input input) {
			int serializationVersion = input.readInt();
			switch (serializationVersion) {
				case 0:
					position = new PointF(input.readFloat(), input.readFloat());
					timestamp = input.readLong();
					velocity = input.readFloat();
					freezeVelocity = input.readBoolean();
					break;
				default:
					throw new IllegalArgumentException("Unsupported " + this.getClass().getName() + " serialization version: " + serializationVersion);
			}
		}
	}
}
