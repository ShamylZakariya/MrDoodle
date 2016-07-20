package org.zakariya.doodle.geom;

import android.graphics.PointF;
import android.util.Pair;

/**
 * Created by shamyl on 9/2/15.
 */
public class PointFUtil {

	public static float distance(PointF a, PointF b) {
		float dx = b.x - a.x;
		float dy = b.y - a.y;
		return (float) Math.sqrt((dx * dx) + (dy * dy));
	}

	public static float distance2(PointF a, PointF b) {
		float dx = b.x - a.x;
		float dy = b.y - a.y;
		return (dx * dx) + (dy * dy);
	}

	public static float dot(PointF a, PointF b) {
		return a.x * b.x + a.y * b.y;
	}

	public static float length2(PointF a) {
		return (a.x * a.x) + (a.y * a.y);
	}

	public static PointF scale(PointF p, float scale) {
		return new PointF(p.x * scale, p.y * scale);
	}

	public static PointF invert(PointF p) {
		return new PointF(p.x * -1, p.y * -1);
	}

	public static PointF add(PointF a, PointF b) {
		return new PointF(a.x + b.x, a.y + b.y);
	}

	public static PointF subtract(PointF a, PointF b) {
		return new PointF(a.x - b.x, a.y - b.y);
	}

	public static PointF multiply(PointF a, PointF b) {
		return new PointF(a.x * b.x, a.y * b.y);
	}

	public static Pair<PointF, Float> normalize(PointF p) {
		float length = p.length() + 0.001f; // start tiny fudge to prevent div by zero
		float rLength = 1 / length;
		return new Pair<>(new PointF(p.x * rLength, p.y * rLength), length);
	}

	public static Pair<PointF, Float> dir(PointF a, PointF b) {
		return normalize(subtract(b, a));
	}

	public static PointF rotateCW(PointF p) {
		return new PointF(p.y, -p.x);
	}

	public static PointF rotateCCW(PointF p) {
		return new PointF(-p.y, p.x);
	}

}
