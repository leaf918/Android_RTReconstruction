package net.zjucvg.rtreconstruction;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * Created by lichen on 16-6-20.
 */
public class AutoFitImageView extends ImageView {

  private int mRatioWidth = 0;
  private int mRatioHeight = 0;

  public AutoFitImageView(Context context, AttributeSet attrs, int defStyleAttr,
                          int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
  }

  public AutoFitImageView(Context context) { super(context); }

  public AutoFitImageView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public AutoFitImageView(Context context, AttributeSet attrs,
                          int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public void setAspectRatio(int width, int height) {
    if (width < 0 || height < 0) {
      throw new IllegalArgumentException("Size can not be negative.");
    }

    mRatioWidth = width;
    mRatioHeight = height;
    requestLayout();
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    int width = MeasureSpec.getSize(widthMeasureSpec);
    int height = MeasureSpec.getSize(heightMeasureSpec);
    if (0 == mRatioWidth || 0 == mRatioHeight) {
      setMeasuredDimension(width, height);
    } else {
      if (width * mRatioHeight < height * mRatioWidth) {
        setMeasuredDimension(width, height * mRatioHeight / mRatioWidth);
      } else {
        setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
      }
    }
  }
}
