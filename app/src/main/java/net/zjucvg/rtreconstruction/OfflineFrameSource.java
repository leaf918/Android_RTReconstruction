package net.zjucvg.rtreconstruction;

import android.app.Activity;
import android.util.Log;
import android.util.Size;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Created by lichen on 16-7-4.
 */
public class OfflineFrameSource extends FrameSource {

  private static final String LOG_TAG = "OfflineFrameSource";

  private final Activity mActivity;
  private boolean mIsStarted;

  private NativeVSLAM.Frame mNextFrame;
  private String mFilePathFormat;
  private int mCur, mStep, mEnd;

  public OfflineFrameSource(Activity activity, String filePathFormat, int beg,
                            int step, int end) {
    mActivity = activity;
    mFilePathFormat = filePathFormat;
    mCur = beg;
    mStep = step;
    mEnd = end;
  }

  public Activity getActivity() { return mActivity; }

  @Override
  public List<Size> getAvailableImageSize() {
    // FIXME
    return Arrays.asList(new Size(640, 480));
  }

  @Override
  public void start(Size size) {
    if (mIsStarted)
      throw new IllegalStateException("Has started");
    mIsStarted = true;
  }

  @Override
  public boolean hasMoreFrame() {
    if (!mIsStarted)
      return false;

    return mIsStarted && loadNextFrame();
  }

  @Override
  public NativeVSLAM.Frame getNextFrame(boolean blocking) {
    if (null == mNextFrame)
      loadNextFrame();
    NativeVSLAM.Frame result = mNextFrame;
    mNextFrame = null;
    return result;
  }

  @Override
  public void stop() {
    if (!mIsStarted)
      throw new IllegalStateException("Not started");
    mIsStarted = false;
  }

  @Override
  public void close() throws Exception {}

  private boolean loadNextFrame() {
    if (mCur >= mEnd)
      return false;

    final String curFilePath = String.format(mFilePathFormat, mCur);
    byte[] bytes;
    try {
      File file = new File(curFilePath);
      bytes = FileUtils.readFileToByteArray(file);
    } catch (IOException e) {
      Log.d(LOG_TAG, "Can not open file " + curFilePath);
      return false;
    }

    mNextFrame = null;
    try {
      mNextFrame = NativeVSLAM.unpackNativeFrame(bytes);
    } catch (Exception e) {
      Log.d(LOG_TAG, "Error parse file " + curFilePath);
      return false;
    }

    Log.d(LOG_TAG, "load new frame " + curFilePath);
    mCur += mStep;
    return true;
  }
}
