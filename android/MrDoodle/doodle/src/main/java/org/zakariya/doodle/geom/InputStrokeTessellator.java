package org.zakariya.doodle.geom;

import android.graphics.Path;
import android.graphics.PointF;

import org.zakariya.doodle.util.FloatBuffer;

import java.util.ArrayList;

/**
 * Created by shamyl on 10/18/15.
 */
public class InputStrokeTessellator {

	private InputStroke inputStroke;
	private float minWidth, maxWidth, maxVelDPps;
	private Path path;
	private FloatBuffer leftCoordinates = new FloatBuffer();
	private FloatBuffer rightCoordinates = new FloatBuffer();
	private CubicBezierInterpolator cbi = new CubicBezierInterpolator();

	public InputStrokeTessellator() {
	}

	public InputStrokeTessellator(InputStroke inputStroke, float minWidth, float maxWidth, float maxVelDPps) {
		this.inputStroke = inputStroke;
		this.minWidth = minWidth;
		this.maxWidth = maxWidth;
		this.maxVelDPps = maxVelDPps;
	}

	public InputStroke getInputStroke() {
		return inputStroke;
	}

	public void setInputStroke(InputStroke inputStroke) {
		this.inputStroke = inputStroke;
	}

	public float getMinWidth() {
		return minWidth;
	}

	public void setMinWidth(float minWidth) {
		this.minWidth = minWidth;
	}

	public float getMaxWidth() {
		return maxWidth;
	}

	public void setMaxWidth(float maxWidth) {
		this.maxWidth = maxWidth;
	}

	public float getMaxVelDPps() {
		return maxVelDPps;
	}

	public void setMaxVelDPps(float maxVelDPps) {
		this.maxVelDPps = maxVelDPps;
	}

	public Path getPath() {
		return path;
	}

	private Path tessellate(int startIndex, int endIndex, boolean startCap, boolean endCap) {
		path = new Path();
		path.setFillType(Path.FillType.WINDING);

		if (inputStroke.size() < 2) {
			return path;
		}

		ArrayList<InputStroke.Point> points = inputStroke.getPoints();

		if (startCap) {
			final InputStroke.Point firstPoint = points.get(startIndex);
			final float radius = getRadiusForInputStrokePoint(firstPoint);
			path.addCircle(firstPoint.position.x, firstPoint.position.y, radius, Path.Direction.CCW);
		}

		PointF aLeftAttachPoint = new PointF();
		PointF aRightAttachPoint = new PointF();
		PointF bLeftAttachPoint = new PointF();
		PointF bRightAttachPoint = new PointF();
		PointF aLeftControlPoint = new PointF();
		PointF aRightControlPoint = new PointF();
		PointF bLeftControlPoint = new PointF();
		PointF bRightControlPoint = new PointF();
		PointF previousSegmentDir = null;
		PointF currentSegmentDir = inputStroke.getSegmentDirection(startIndex);
		PointF nextSegmentDir = null;
		PointF bp = new PointF();

		for (int i = startIndex; i < endIndex; i++) {
			final InputStroke.Point a = points.get(i);
			final InputStroke.Point b = points.get(i + 1);
			final PointF dir = PointFUtil.dir(a.position, b.position).first;
			final float aRadius = getRadiusForInputStrokePoint(a);
			final float bRadius = getRadiusForInputStrokePoint(b);

			// aLeftAttachPoint and aRightAttachPoint are the start points of the two bezier curves
			PointF aLeftAttachDir = PointFUtil.scale(PointFUtil.rotateCCW(dir), aRadius);
			aLeftAttachPoint.x = a.position.x + aLeftAttachDir.x;
			aLeftAttachPoint.y = a.position.y + aLeftAttachDir.y;
			aRightAttachPoint.x = a.position.x - aLeftAttachDir.x;
			aRightAttachPoint.y = a.position.y - aLeftAttachDir.y;

			// bLeftAttachPoint and bRightAttachPoint are the end points of the two bezier curves
			PointF bLeftAttachDir = PointFUtil.scale(PointFUtil.rotateCCW(dir), bRadius);
			bLeftAttachPoint.x = b.position.x + bLeftAttachDir.x;
			bLeftAttachPoint.y = b.position.y + bLeftAttachDir.y;
			bRightAttachPoint.x = b.position.x - bLeftAttachDir.x;
			bRightAttachPoint.y = b.position.y - bLeftAttachDir.y;

			// now compute the bezier control points
			float leftControlPointLength = PointFUtil.distance(aLeftAttachPoint, bLeftAttachPoint) / 4;
			float rightControlPointLength = PointFUtil.distance(aRightAttachPoint, bRightAttachPoint) / 4;
			float aLeftControlPointLength = leftControlPointLength;
			float bLeftControlPointLength = leftControlPointLength;
			float aRightControlPointLength = rightControlPointLength;
			float bRightControlPointLength = rightControlPointLength;

			// scale down start bezier control points by acuteness of angle between current and previous segments
			if (previousSegmentDir != null) {
				float dot = PointFUtil.dot(previousSegmentDir, currentSegmentDir);
				float acuteness = -1 * Math.min(dot, 0); // clamp dot from [-1,0] and invert so we have an acuteness from 0 to 1
				float controlPointScale = 1 - acuteness;
				aLeftControlPointLength *= controlPointScale;
				aRightControlPointLength *= controlPointScale;
			}

			final PointF aTangent = inputStroke.getTangent(i);
			final PointF bTangent = inputStroke.getTangent(i + 1);
			aLeftControlPoint.x = aLeftAttachPoint.x + aTangent.x * aLeftControlPointLength;
			aLeftControlPoint.y = aLeftAttachPoint.y + aTangent.y * aLeftControlPointLength;
			aRightControlPoint.x = aRightAttachPoint.x + aTangent.x * aRightControlPointLength;
			aRightControlPoint.y = aRightAttachPoint.y + aTangent.y * aRightControlPointLength;

			nextSegmentDir = inputStroke.getSegmentDirection(i + 1);
			// scale down end bezier control points by acuteness of angle between current and next segments
			if (nextSegmentDir != null) {
				float dot = PointFUtil.dot(nextSegmentDir, currentSegmentDir);
				float acuteness = -1 * Math.min(dot, 0); // clamp dot from [-1,0] and invert so we have an acuteness from 0 to 1
				float controlPointScale = 1 - acuteness;
				bLeftControlPointLength *= controlPointScale;
				bRightControlPointLength *= controlPointScale;
			}

			bLeftControlPoint.x = bLeftAttachPoint.x + bTangent.x * -bLeftControlPointLength;
			bLeftControlPoint.y = bLeftAttachPoint.y + bTangent.y * -bLeftControlPointLength;
			bRightControlPoint.x = bRightAttachPoint.x + bTangent.x * -bRightControlPointLength;
			bRightControlPoint.y = bRightAttachPoint.y + bTangent.y * -bRightControlPointLength;

			// perform bezier interpolation of left side from aLeftAttachPoint up to but not including bLeftAttachPoint since next step will add bLeftAttachPoint
			cbi.set(aLeftAttachPoint, aLeftControlPoint, bLeftControlPoint, bLeftAttachPoint);
			int subdivisions = cbi.getRecommendedSubdivisions(1);
			leftCoordinates.add(aLeftAttachPoint.x);
			leftCoordinates.add(aLeftAttachPoint.y);
			if (subdivisions > 1) {
				// time interpolator
				final float dt = 1f / subdivisions;
				float t = dt;
				for (int j = 0; j < subdivisions; j++, t += dt) {
					cbi.getBezierPoint(t, bp);
					leftCoordinates.add(bp.x);
					leftCoordinates.add(bp.y);
				}
			}

			// perform bezier interpolation of right side from aRightAttachPoint up to but not including bRightAttachPoint since next step will add bRightAttachPoint
			cbi.set(aRightAttachPoint, aRightControlPoint, bRightControlPoint, bRightAttachPoint);
			subdivisions = cbi.getRecommendedSubdivisions(1);
			rightCoordinates.add(aRightAttachPoint.x);
			rightCoordinates.add(aRightAttachPoint.y);
			if (subdivisions > 1) {
				// time interpolator
				final float dt = 1f / subdivisions;
				float t = dt;
				for (int j = 0; j < subdivisions; j++, t += dt) {
					cbi.getBezierPoint(t, bp);
					rightCoordinates.add(bp.x);
					rightCoordinates.add(bp.y);
				}
			}

			// update segment directions for angle acuteness testing
			previousSegmentDir = currentSegmentDir;
			currentSegmentDir = nextSegmentDir;
		}

		// now that we've populated the left and right coord buffers, stitch a path together
		int coordStartIndex = 2 * startIndex;
		if (coordStartIndex >= leftCoordinates.size()) {
			return path;
		}

		float x = leftCoordinates.get(coordStartIndex);
		float y = leftCoordinates.get(coordStartIndex + 1);
		path.moveTo(x, y);

		for (int i = coordStartIndex + 2, N = leftCoordinates.size(); i < N; i += 2) {
			x = leftCoordinates.get(i);
			y = leftCoordinates.get(i + 1);
			path.lineTo(x, y);
		}

		// right coordinates must be iterated backwards from end to start
		for (int i = rightCoordinates.size() - 2; i >= coordStartIndex; i -= 2) {
			x = rightCoordinates.get(i);
			y = rightCoordinates.get(i + 1);
			path.lineTo(x, y);
		}

		path.close();

		if (endCap) {
			final InputStroke.Point endPoint = points.get(endIndex);
			final float radius = getRadiusForInputStrokePoint(endPoint);
			path.addCircle(endPoint.position.x, endPoint.position.y, radius, Path.Direction.CCW);
		}


		return path;
	}

	/**
	 * Tessellate InputStroke from startIndex to end
	 *
	 * @param startIndex index of point to start tessellation at
	 * @return Path representing InputStroke tessellated from startIndex to end
	 */
	public Path tessellate(int startIndex, boolean isContinuation, boolean startCap, boolean endCap) {
		clearCoordinateBuffers(isContinuation);
		return tessellate(startIndex, inputStroke.size() - 1, startCap, endCap);
	}

	/**
	 * Tessellate entire InputStroke
	 *
	 * @return Path representing entire InputStroke
	 */
	public Path tessellate(boolean isContinuation, boolean startCap, boolean endCap) {
		clearCoordinateBuffers(isContinuation);
		return tessellate(0, inputStroke.size() - 1, startCap, endCap);
	}

	public float getRadiusForInputStrokePoint(InputStroke.Point point) {
		float velScale = Math.min(point.velocity / maxVelDPps, 1f);
		float minRadius = minWidth * 0.5f;
		float maxRadius = maxWidth * 0.5f;
		float deltaRadius = maxRadius - minRadius;
		return minRadius + (velScale * velScale * deltaRadius);
	}

	private void clearCoordinateBuffers(boolean isContinuation) {
		if (isContinuation) {
			// TODO: This causes weird artifacting
			float lx = leftCoordinates.get(-2),
					ly = leftCoordinates.get(-1),
					rx = rightCoordinates.get(-2),
					ry = rightCoordinates.get(-1);

			leftCoordinates.clear();
			rightCoordinates.clear();

			leftCoordinates.add(lx);
			leftCoordinates.add(ly);
			rightCoordinates.add(rx);
			rightCoordinates.add(ry);
		} else {
			leftCoordinates.clear();
			rightCoordinates.clear();
		}
	}

}
