package org.zakariya.doodle.geom;

import android.graphics.PointF;
import android.support.annotation.Nullable;
import android.util.Pair;

/**
 * Created by shamyl on 10/15/15.
 */
public class LineSegment {
	private PointF a;
	private PointF b;
	private PointF dir;
	float length;

	public LineSegment(PointF a, PointF b) {
		this.a = a;
		this.b = b;
		Pair<PointF, Float> dir = PointFUtil.dir(a, b);
		this.dir = dir.first;
		this.length = dir.second;
	}

	public PointF getA() {
		return a;
	}

	public PointF getB() {
		return b;
	}

	public PointF getDir() {
		return dir;
	}

	public float getLength() {
		return length;
	}

	public float distance(PointF point, boolean bounded) {

		PointF toPoint = PointFUtil.subtract(point, a);
		float projectedLength = PointFUtil.dot(toPoint, dir);

		if (bounded) {
			// early exit condition, we'll have to get distance from endpoints
			if (projectedLength < 0) {
				return PointFUtil.distance(point, a);
			} else if (projectedLength > length) {
				return PointFUtil.distance(point, b);
			}
		}

		// compute distance from point to the closest projection point on the line segment
		PointF projectedOnSegment = PointFUtil.add(a, PointFUtil.scale(dir, projectedLength));
		return PointFUtil.distance(point, projectedOnSegment);
	}

	public float distanceSquared(PointF point, boolean bounded) {

		PointF toPoint = PointFUtil.subtract(point, a);
		float projectedLength = PointFUtil.dot(toPoint, dir);

		if (bounded) {
			// early exit condition, we'll have to get distance from endpoints
			if (projectedLength < 0) {
				return PointFUtil.distance2(point, a);
			} else if (projectedLength > length) {
				return PointFUtil.distance2(point, b);
			}
		}

		// compute distance from point to the closest projection point on the line segment
		PointF projectedOnSegment = PointFUtil.add(a, PointFUtil.scale(dir, projectedLength));
		return PointFUtil.distance2(point, projectedOnSegment);
	}

	/**
	 * Find the intersection point of this LineSegment and another, if any.
	 *
	 * @param other   the line segment to intersect
	 * @param bounded if false, the line segments are treated as infinite-length lines
	 * @return the intersection point, if any. null if the lines are parallel, or if the test is bounded and the segments do not intersect
	 */
	@Nullable
	public PointF intersection(LineSegment other, boolean bounded) {
		// cribbed from: http://wiki.processing.org/w/Line-Line_intersection
		float adx = other.a.x - a.x;
		float ady = other.a.y - a.y;
		float bdx = other.b.x - b.x;
		float bdy = other.b.y - b.y;
		float ad_dot_bd = adx * bdy - ady * bdx;

		if (Math.abs(ad_dot_bd) < 1e-4) {
			return null;
		}

		float abx = b.x - a.x;
		float aby = b.y - a.y;
		float t = (abx * bdy - aby * bdx) / ad_dot_bd;

		if (bounded) {
			if (t < 1e-4 || t > 1 - 1e-4) {
				return null;
			}

			float u = (abx * ady - aby * adx) / ad_dot_bd;
			if (u < 1e-4 || u > 1 - 1e-4) {
				return null;
			}
		}

		return new PointF(a.x + t * adx, a.y + t * ady);
	}
}
