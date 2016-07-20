package org.zakariya.doodle.geom;

import android.graphics.PointF;

import static org.zakariya.doodle.geom.PointFUtil.distance;

/**
 * Created by shamyl on 10/4/15.
 */
public class QuadraticBezierInterpolator {

	PointF start, control, end;

	public QuadraticBezierInterpolator(PointF start, PointF control, PointF end) {
		this.start = start;
		this.control = control;
		this.end = end;
	}

	public void set(PointF start, PointF control, PointF end) {
		this.start = start;
		this.control = control;
		this.end = end;
	}

	public PointF getStart() {
		return start;
	}

	public void setStart(PointF start) {
		this.start = start;
	}

	public PointF getControl() {
		return control;
	}

	public void setControl(PointF control) {
		this.control = control;
	}

	public PointF getEnd() {
		return end;
	}

	public void setEnd(PointF end) {
		this.end = end;
	}

	/**
	 * Get the point on the quadratic bezier line at distance t along line, where `t varies from 0->1
	 *
	 * @param t distance along the bezier line, varying from 0->1
	 * @return the bezier point value of the curve defined by start->end
	 */
	public PointF getInterpolatedPoint(float t) {
		float seg0X = start.x + (t * (control.x - start.x));
		float seg0Y = start.y + (t * (control.y - start.y));
		float seg1X = control.x + (t * (end.x - control.x));
		float seg1Y = control.y + (t * (end.y - control.y));
		float bX = seg0X + (t * (seg1X - seg0X));
		float bY = seg0Y + (t * (seg1Y - seg0Y));
		return new PointF(bX, bY);
	}

	/**
	 * @param scale the scale at which the line is being rendered
	 * @return An estimated number of subdivisions to divide this bezier curve into to represent start visually appendAndSmooth curve.
	 */
	public int getRecommendedSubdivisions(float scale) {
		// from http://ciechanowski.me/blog/2014/02/18/drawing-bezier-curves/

		float l0 = distance(start, control);
		float l1 = distance(control, end);
		float approximateLength = l0 + l1;
		float min = 10;
		float segments = approximateLength / 30;
		float slope = 0.6f;

		return (int) Math.ceil(Math.sqrt(segments * segments) * slope + (min * min) * scale);
	}

}
