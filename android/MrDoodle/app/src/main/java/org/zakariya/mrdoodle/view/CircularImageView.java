package org.zakariya.mrdoodle.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

/**
 * Created by shamyl on 1/3/16.
 * Massively simplified from https://github.com/lopspower/CircularImageView
 */
public class CircularImageView extends ImageView {

	private int canvasSize;
	private Bitmap image;
	private Drawable drawable;
	private Paint paint;

	public CircularImageView(final Context context) {
		super(context, null);
		init(context, null, 0);
	}

	public CircularImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context, attrs, 0);
	}

	public CircularImageView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init(context, attrs, defStyleAttr);
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public CircularImageView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		init(context, attrs, defStyleAttr);
	}

	private void init(Context context, AttributeSet attrs, int defStyleAttr) {
		paint = new Paint();
		paint.setAntiAlias(true);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		canvasSize = w;
		if (h < canvasSize) {
			canvasSize = h;
		}

		if (image != null) {
			updateShader();
		}
	}

	@Override
	public void onDraw(Canvas canvas) {
		// Load the bitmap
		loadBitmap();

		// Check if image isn't null
		if (image == null)
			return;

		canvasSize = canvas.getWidth();
		if (canvas.getHeight() < canvasSize) {
			canvasSize = canvas.getHeight();
		}

		int circleCenter = canvasSize / 2;
		canvas.drawCircle(circleCenter, circleCenter, circleCenter, paint);
	}

	private void loadBitmap() {
		if (this.drawable == getDrawable())
			return;

		this.drawable = getDrawable();
		this.image = drawableToBitmap(this.drawable);
		updateShader();
	}

	private void updateShader() {
		if (this.image == null) {
			return;
		}

		BitmapShader shader = new BitmapShader(Bitmap.createScaledBitmap(
				ThumbnailUtils.extractThumbnail(image, canvasSize, canvasSize), canvasSize, canvasSize, false),
				Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);

		paint.setShader(shader);
	}

	private Bitmap drawableToBitmap(Drawable drawable) {
		if (drawable == null) {
			return null;
		} else if (drawable instanceof BitmapDrawable) {
			return ((BitmapDrawable) drawable).getBitmap();
		}

		int intrinsicWidth = drawable.getIntrinsicWidth();
		int intrinsicHeight = drawable.getIntrinsicHeight();

		if (!(intrinsicWidth > 0 && intrinsicHeight > 0)) {
			return null;
		}

		try {
			// Create Bitmap object out of the drawable
			Bitmap bitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888);
			Canvas canvas = new Canvas(bitmap);
			drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
			drawable.draw(canvas);
			return bitmap;
		} catch (OutOfMemoryError e) {
			// Simply return null of failed bitmap creations
			Log.e(getClass().toString(), "Encountered OutOfMemoryError while generating bitmap!");
			return null;
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int width = measureWidth(widthMeasureSpec);
		int height = measureHeight(heightMeasureSpec);
		int imageSize = (width < height) ? width : height;
		setMeasuredDimension(imageSize, imageSize);
	}

	private int measureWidth(int measureSpec) {
		int result;
		int specMode = MeasureSpec.getMode(measureSpec);
		int specSize = MeasureSpec.getSize(measureSpec);

		if (specMode == MeasureSpec.EXACTLY) {
			// The parent has determined an exact size for the child.
			result = specSize;
		} else if (specMode == MeasureSpec.AT_MOST) {
			// The child can be as large as it wants up to the specified size.
			result = specSize;
		} else {
			// The parent has not imposed any constraint on the child.
			result = canvasSize;
		}

		return result;
	}

	private int measureHeight(int measureSpecHeight) {
		int result;
		int specMode = MeasureSpec.getMode(measureSpecHeight);
		int specSize = MeasureSpec.getSize(measureSpecHeight);

		if (specMode == MeasureSpec.EXACTLY) {
			// We were told how big to be
			result = specSize;
		} else if (specMode == MeasureSpec.AT_MOST) {
			// The child can be as large as it wants up to the specified size.
			result = specSize;
		} else {
			// Measure the text (beware: ascent is a negative number)
			result = canvasSize;
		}

		return (result + 2);
	}


}
