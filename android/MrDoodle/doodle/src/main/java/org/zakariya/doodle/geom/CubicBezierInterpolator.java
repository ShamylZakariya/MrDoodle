package org.zakariya.doodle.geom;

import android.graphics.PointF;
import android.support.annotation.NonNull;

import static org.zakariya.doodle.geom.PointFUtil.distance;

/**
 * Created by shamyl on 9/2/15.
 */
public class CubicBezierInterpolator {
	PointF start, startControl, endControl, end;

	public CubicBezierInterpolator() {
	}

	public CubicBezierInterpolator(PointF start, PointF startControl, PointF endControl, PointF end) {
		this.start = start;
		this.startControl = startControl;
		this.endControl = endControl;
		this.end = end;
	}

	public void set(PointF start, PointF startControl, PointF endControl, PointF end) {
		this.start = start;
		this.startControl = startControl;
		this.endControl = endControl;
		this.end = end;
	}

	public PointF getStart() {
		return start;
	}

	public void setStart(PointF start) {
		this.start = start;
	}

	public PointF getStartControl() {
		return startControl;
	}

	public void setStartControl(PointF startControl) {
		this.startControl = startControl;
	}

	public PointF getEndControl() {
		return endControl;
	}

	public void setEndControl(PointF endControl) {
		this.endControl = endControl;
	}

	public PointF getEnd() {
		return end;
	}

	public void setEnd(PointF end) {
		this.end = end;
	}

	/**
	 * Get the point on the bezier line defined by start->end distance `t along that line, where `t varies from 0->1
	 *
	 * @param t distance along the bezier line, varying from 0->1
	 * @return the bezier point value of the curve defined by start->end
	 */
	public PointF getBezierPoint(float t) {
		return getBezierPoint(t, new PointF());
	}

	/**
	 * Get the point on the bezier line defined by start->end distance `t along that line, where `t varies from 0->1
	 *
	 * @param t    distance along the bezier line, varying from 0->1
	 * @param into a PointF instance into which to write the bezier point value
	 * @return the bezier point value of the curve defined by start->end stored in `into
	 */
	public PointF getBezierPoint(float t, @NonNull PointF into) {
		// from http://ciechanowski.me/blog/2014/02/18/drawing-bezier-curves/

		final float nt = 1 - t;
		final float A = nt * nt * nt;
		final float B = 3 * nt * nt * t;
		final float C = 3 * nt * t * t;
		final float D = t * t * t;

		float px = start.x * A;
		float py = start.y * A;

		px += startControl.x * B;
		py += startControl.y * B;

		px += endControl.x * C;
		py += endControl.y * C;

		px += end.x * D;
		py += end.y * D;

		into.x = px;
		into.y = py;

		return into;
	}

	/**
	 * @param scale the scale at which the line is being rendered
	 * @return An estimated number of subdivisions to divide this bezier curve into to represent start visually appendAndSmooth curve.
	 */
	public int getRecommendedSubdivisions(float scale) {
		// from http://ciechanowski.me/blog/2014/02/18/drawing-bezier-curves/

		final float l0 = distance(start, startControl);
		final float l1 = distance(startControl, endControl);
		final float l2 = distance(endControl, end);
		final float approximateLength = l0 + l1 + l2;
		final float segments = approximateLength / 3;
		final float slope = 1.0f;

		return (int) Math.ceil(Math.sqrt(segments * segments) * slope * scale);
	}
}
