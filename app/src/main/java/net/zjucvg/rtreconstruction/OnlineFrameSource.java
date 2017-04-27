package net.zjucvg.rtreconstruction;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Created by lichen on 16-6-19.
 */
public class OnlineFrameSource extends FrameSource implements AutoCloseable {

  private static final String LOG_TAG = "OnlineFrameSource";

  private Activity mActivity;

  private CameraReader mCameraReader;
  private IMUReader mIMUReader;
  private ImageReader mYUVImageReader;

  private boolean mIsStarted;

  private long mBaseTime;
  private static class GrayBitmapPair {
    byte[] gray;
    //YUV420Image yuvImage;
    Bitmap bitmap;
    long timestamp;
  }
  private BlockingQueue<GrayBitmapPair> mLastGrayBitmapPair;
  private HandlerThread mBackgroundThread;
  private Handler mBackgroundHandler;
  private ImageReader.OnImageAvailableListener mOnYUVImageAvailableListener =
      new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
          Image image = reader.acquireLatestImage();
          if (null != image) {
//            YUV420Image yuvImage = new YUV420Image();
//            yuvImage.width = image.getWidth();
//            yuvImage.height = image.getHeight();
//            yuvImage.planes = new YUV420Image.Plane[3];
//            for (int i = 0; i < 3; ++i) {
//              ByteBuffer buffer = image.getPlanes()[i].getBuffer();
//              buffer.rewind();
//              yuvImage.planes[i] = new YUV420Image.Plane();
//              yuvImage.planes[i].pixelStride =
//                  image.getPlanes()[i].getPixelStride();
//              yuvImage.planes[i].rowStride =
//                  image.getPlanes()[i].getRowStride();
//              yuvImage.planes[i].data = new byte[buffer.remaining()];
//              buffer.get(yuvImage.planes[i].data);
//            }

            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            buffer.rewind();
            byte[] gray = new byte[buffer.remaining()];
            buffer.get(gray);
            GrayBitmapPair pair = new GrayBitmapPair();
            pair.gray = gray;
//            pair.yuvImage = yuvImage;
//            pair.bitmap = RSUtility.grayToBitmap(gray, image.getWidth(), image.getHeight());
            pair.bitmap = OpenCLUtility.grayToBitmap(gray, image.getWidth(),image.getHeight());
            pair.timestamp = image.getTimestamp();
            try {
              mLastGrayBitmapPair.clear();
              mLastGrayBitmapPair.put(pair);
              Log.d(LOG_TAG, "acquired new image.");
            } catch (InterruptedException e) {
              e.printStackTrace();
            } finally {
              image.close();
            }
          }
        }
      };

  public OnlineFrameSource(Activity activity) {
    mActivity = activity;
    mCameraReader = new CameraReader(activity);
    mIMUReader = new IMUReader(activity);
    mLastGrayBitmapPair = new ArrayBlockingQueue<GrayBitmapPair>(1);
  }

  private Activity getActivity() { return mActivity; }

  @Override
  public List<Size> getAvailableImageSize() {
    return mCameraReader.getSizeList();
  }

  @Override
  public void start(Size size) {
    if (!getAvailableImageSize().contains(size))
      throw new IllegalArgumentException("Invalid image size");
    if (mIsStarted)
      throw new IllegalStateException("Already started");

    startBackgroundHandler();
    mLastGrayBitmapPair.clear();
    mBaseTime = SystemClock.elapsedRealtimeNanos();
    mYUVImageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(),
                                              ImageFormat.YUV_420_888, 5);
    mYUVImageReader.setOnImageAvailableListener(mOnYUVImageAvailableListener,
                                                mBackgroundHandler);
    mIMUReader.start();
    mCameraReader.start(Arrays.asList(mYUVImageReader.getSurface()));

    mIsStarted = true;
  }

  @Override
  public boolean hasMoreFrame() {
    return mIsStarted;
  }

  @Override
  public NativeVSLAM.Frame getNextFrame(boolean blocking) {
    if (!mIsStarted)
      throw new IllegalStateException("Not started");

    GrayBitmapPair pair = null;
    try {
      if (blocking)
        pair = mLastGrayBitmapPair.take();
      else
        pair = mLastGrayBitmapPair.poll();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    if (null != pair) {
      List<IMUReader.ValueTimePair> linearAccelerations = new ArrayList<>();
      List<IMUReader.ValueTimePair> gyroscopes = new ArrayList<>();
      IMUReader.ValueTimePair rotationVector = new IMUReader.ValueTimePair();
      IMUReader.ValueTimePair gravity = new IMUReader.ValueTimePair();

      mIMUReader.get(linearAccelerations, gyroscopes, rotationVector, gravity,
                     pair.timestamp);
      mIMUReader.mergeIMUs(linearAccelerations, gyroscopes);

      NativeVSLAM.Frame frame = new NativeVSLAM.Frame();
      // image
      frame.image = new NativeVSLAM.Image();
      frame.image.data = pair.gray;
      frame.image.timestamp = adjustTime(pair.timestamp);
      //frame.userData = pair.yuvImage;
      frame.userData = pair.bitmap;
      // imus
      frame.imus = new ArrayList<>();
      for (int i = 0; i < linearAccelerations.size(); ++i) {
        NativeVSLAM.IMU imu = new NativeVSLAM.IMU();
        imu.linearAcceleration = new double[3];
        for (int j = 0; j < 3; ++j)
          imu.linearAcceleration[j] = linearAccelerations.get(i).values[j];
        imu.gyroscope = new double[3];
        for (int j = 0; j < 3; ++j)
          imu.gyroscope[j] = gyroscopes.get(i).values[j];
        imu.timestamp = adjustTime((linearAccelerations.get(i).timestamp +
                                    gyroscopes.get(i).timestamp) /
                                   2);
        frame.imus.add(imu);
      }
      // attitude
      frame.attitude = new NativeVSLAM.Attitude();
      frame.attitude.rotationVector = new double[4];
      for (int i = 0; i < 4; ++i)
        frame.attitude.rotationVector[i] = rotationVector.values[i];
      frame.attitude.gravity = new double[3];
      for (int i = 0; i < 3; ++i)
        frame.attitude.gravity[i] = gravity.values[i];
      frame.attitude.timestamp =
          adjustTime((rotationVector.timestamp + gravity.timestamp) / 2);

      Log.d(LOG_TAG, "acquired new frame");
      return frame;
    }

    return null;
  }

  @Override
  public void stop() {
    if (!mIsStarted)
      throw new IllegalStateException("Not started");

    mIMUReader.stop();
    mCameraReader.stop();
    stopBackgroundThread();
    mYUVImageReader.close();
    mYUVImageReader = null;

    mIsStarted = false;
  }

  @Override
  public void close() throws Exception {
    if (mIsStarted)
      stop();
    mIMUReader.close();
    mCameraReader.close();
  }

  private void startBackgroundHandler() {
    mBackgroundThread = new HandlerThread(LOG_TAG);
    mBackgroundThread.start();
    mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
  }

  private void stopBackgroundThread() {
    mBackgroundThread.quitSafely();
    try {
      mBackgroundThread.join();
      mBackgroundThread = null;
      mBackgroundHandler = null;
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private double adjustTime(long time) { return (time - mBaseTime) * 1E-9; }
}
