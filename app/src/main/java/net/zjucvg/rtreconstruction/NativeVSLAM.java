package net.zjucvg.rtreconstruction;

import android.graphics.PointF;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by lichen on 16-6-12.
 */
public class NativeVSLAM implements AutoCloseable {

  private static String LOG_TAG = "NativeVSLAM";

  static { System.loadLibrary("native-lib"); }

  /**
   * Class for describing image size in pixels.
   */
  public static class Size {
    public int width;
    public int height;
  }

  /**
   * Simple wrapper for gray image
   */
  public static class Image {
    /**
     * the image data, row major order, one byte per pixel
     */
    public byte[] data;
    /**
     * timestamp, in seconds
     */
    public double timestamp;
  }

  /**
   * IMU data, @see
   * https://developer.android.com/reference/android/hardware/SensorEvent.html
   */
  public static class IMU {
    /**
     * TYPE_LINEAR_ACCELERATION, double[3] type
     */
    public double[] linearAcceleration;
    /**
     * TYPE_GYROSCOPE, double[3] type
     */
    public double[] gyroscope;
    /**
     * timestamp, in seconds
     */
    public double timestamp;
  }

  public static class Attitude {
    /**
     * TYPE_ROTATION_VECTOR, double[4] type
     */
    public double[] rotationVector;
    /**
     * TYPE_GRAVITY, double[3] type
     */
    public double[] gravity;
    /**
     * timestamp, in seconds
     */
    public double timestamp;
  }

  /**
   * A collection of image and IMU data
   */
  public static class Frame {
    public Image image;
    /**
     * All IMU measures between last frame and current frame
     */
    public List<IMU> imus;
    /**
     * The attitude <strong>just</strong> before current frame
     */
    public Attitude attitude;
    /**
     * Additional field may be useful to the user
     */
    public Object userData;
  }

  /**
   * A class describing the camera intrinsics
   */
  public static class CameraIntrinsics {
    /**
     * Is this camera use fisheye distortion model
     */
    public boolean fisheye;
    /**
     * The focal length and principal point
     */
    public float fx, fy, px, py;
    /**
     * Skew coefficient
     */
    public float skew;
    /**
     * Distortion factors
     */
    public float[] k;
  }

  public static class VSLAMConfig {
    public CameraIntrinsics intrinsics;
    public Size size;
    public float newImgF;
    /**
     * Displacement between IMU and camera in x direction
     */
    public float px;
    /**
     * Displacement between IMU and camera in y direction
     */
    public float py;
    /**
     * The initial distance between camera and A4 paper
     */
    public float initDist;

    /**
     * Use maker or not
     */
    public boolean useMarker;
    /**
     * Length of the marker, in meters, must set if useMarker
     */
    public float markerLength;
    /**
     * Width of the marker, in meters, must set if useMarker
     */
    public float markerWidth;

    public boolean parallel;
    /**
     * 0 -- silent, 1 -- debug, 2 -- verbose
     */
    public int logLevel;
    /**
     * The file path to write logs into
     */
    public String logFilePath;
  }

  /**
   * Describing the slam status
   */
  public enum State {
    /**
     * Detecting the A4 paper
     */
    SLAM_MARKER_DETECTING,
    /**
     * Detected the A4 paper
     */
    SLAM_MARKER_DETECTED,
    SLAM_TRACKING_SUCCESS,
    SLAM_TRAKCING_FAIL
  }

  /**
   * A class describing the camera position and rotation in world space
   */
  public static class Camera {
    public Camera() {
      rotationVector = new float[] {0, 0, 0, 1};
      position = new float[] {0, 0, 0};
    }
    /**
     * The rotation vector, in format (qx, qy, qz, qw)
     */
    public float[] rotationVector;
    /**
     * The position of the camera in world space
     */
    public float[] position;
  }

  /**
   * A class Describing the four A4 paper corners, in pixel
   */
  public static class Corners {
    public Corners() { corners = new PointF[4]; }
    public PointF[] corners;
  }

  /**
   * A class describing the running result of slam
   */
  public static class TrackingResult {
    TrackingResult() {
      camera = new Camera();
      corners = new Corners();
    }
    public State state;
    /**
     * the camera pose, can be null, valid if state = SLAM_TRACKING_SUCCESS
     */
    public Camera camera;
    /**
     * the 4 corners of the detected A4 paper, can be null, valid if state =
     * SLAM_CORNERS_DETECTED
     */
    public Corners corners;
    /**
     * diagnostic information about the SLAM
     */
    public String info;
  }

  /**
   * Pack the frame into byte array, which can be unpacked by calling
   * #unpackNativeFrame
   */
  // pack the Frame to byte[]
  // format:
  // image.width [4]
  // image.height [4]
  // image.data [image.width * image.height]
  // image.timestamp [8]
  // imus.size() * 2 [4]
  // for i = 0 : imus.size()
  // 0 [4]
  // imus[i].linearAcceleration [3 * 8]
  // imus[i].timestamp [8]
  // 1 [4]
  // imus[i].gyroscope [3 * 8]
  // imus[i].timestamp [8]
  // attitude.rotationVector[4 * 8]
  // attitude.gravity [3 * 8]
  // attitude.timestamp [8]
  public byte[] packNativeFrame(final Frame frame) {
    int width = mConfig.size.width;
    int height = mConfig.size.height;
    int imageFieldLen = 4 + 4 + 8 + width * height;
    int IMUFieldLen = 4 + frame.imus.size() * (4 + 24 + 8 + 4 + 24 + 8);
    int attitudeFieldLen = 32 + 24 + 8;
    int len = imageFieldLen + IMUFieldLen + attitudeFieldLen;
    ByteBuffer buffer = ByteBuffer.allocate(len);
    buffer.order(ByteOrder.LITTLE_ENDIAN);
    // image
    buffer.putInt(width)
        .putInt(height)
        .putDouble(frame.image.timestamp)
        .put(frame.image.data);
    // IMUs
    buffer.putInt(2 * frame.imus.size());
    for (IMU imu : frame.imus) {
      // linearAcceleration
      buffer.putInt(0);
      for (int i = 0; i < 3; ++i)
        buffer.putDouble(imu.linearAcceleration[i]);
      buffer.putDouble(imu.timestamp);

      // gyroscope
      buffer.putInt(1);
      for (int i = 0; i < 3; ++i)
        buffer.putDouble(imu.gyroscope[i]);
      buffer.putDouble(imu.timestamp);
    }
    // attitude
    for (int i = 0; i < 4; ++i)
      buffer.putDouble(frame.attitude.rotationVector[i]);
    for (int i = 0; i < 3; ++i)
      buffer.putDouble(frame.attitude.gravity[i]);
    buffer.putDouble(frame.attitude.timestamp);

    return buffer.array();
  }

  /**
   * Unpack byte array into a frame, @see #packNativeFrame
   */
  public static Frame unpackNativeFrame(byte[] bytes) {
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    buffer.order(ByteOrder.LITTLE_ENDIAN);
    Frame frame = new Frame();
    // image
    frame.image = new Image();
    int width = buffer.getInt();
    int height = buffer.getInt();
    frame.image.timestamp = buffer.getDouble();
    frame.image.data = new byte[width * height];
    buffer.get(frame.image.data, 0, width * height);
    // IMUs
    frame.imus = new ArrayList<>();
    int dblIMUCnt = buffer.getInt();
    assert dblIMUCnt % 2 == 0;
    for (int i = 0; i < dblIMUCnt / 2; ++i) {
      IMU imu = new IMU();

      int type1 = buffer.getInt();
      double[] vals1 = new double[3];
      for (int j = 0; j < 3; ++j)
        vals1[j] = buffer.getDouble();
      double timestamp1 = buffer.getDouble();

      int type2 = buffer.getInt();
      double[] vals2 = new double[3];
      for (int j = 0; j < 3; ++j)
        vals2[j] = buffer.getDouble();
      double timestamp2 = buffer.getDouble();

      assert(type1 == 0 && type2 == 1) || (type1 == 1 && type2 == 0);
      if (type1 == 0) // linearAcceleration
      {
        imu.linearAcceleration = vals1;
        imu.gyroscope = vals2;
      } else {
        imu.linearAcceleration = vals2;
        imu.gyroscope = vals1;
      }
      imu.timestamp = (timestamp1 + timestamp2) / 2;
      frame.imus.add(imu);
    }
    // Attitude
    frame.attitude = new Attitude();
    frame.attitude.rotationVector = new double[4];
    frame.attitude.gravity = new double[3];
    for (int i = 0; i < 4; ++i)
      frame.attitude.rotationVector[i] = buffer.getDouble();
    for (int i = 0; i < 3; ++i)
      frame.attitude.gravity[i] = buffer.getDouble();
    frame.attitude.timestamp = buffer.getDouble();

    // bitmap
    //frame.userData = RSUtility.grayToBitmap(frame.image.data, width, height);

    return frame;
  }

  // unpack byte[] to TrackingResult, format:
  // state [1 byte]
  //   0 -- DETECTING, 1 -- DETECTED, 2 -- SUCCESS, 3 -- FAIL
  // if state == DETECTED:
  //   corners [4 * 2 * FLOAT_SIZE bytes]
  // if state == SUCCESS:
  //   camera.rotationVector [4 * FLOAT_SIZE bytes]
  //   camera.position [3 * FLOAT_SIZE bytes]
  // Len [4 byte, integer]
  //   a byte array of length Len
  private static TrackingResult unpackNativeResult(byte[] nativeResult) {
    ByteBuffer buffer = ByteBuffer.wrap(nativeResult);
    buffer.order(ByteOrder.LITTLE_ENDIAN);
    TrackingResult result = new TrackingResult();
    byte state = buffer.get();
    switch (state) {
    case 0:
      result.state = State.SLAM_MARKER_DETECTING;
      break;
    case 1:
      result.state = State.SLAM_MARKER_DETECTED;
      break;
    case 2:
      result.state = State.SLAM_TRACKING_SUCCESS;
      break;
    case 3:
      result.state = State.SLAM_TRAKCING_FAIL;
      break;
    default:
      assert false;
      break;
    }

    if (result.state == State.SLAM_MARKER_DETECTED) {
      // set corners
      result.corners = new Corners();
      result.corners.corners = new PointF[4];
      for (int i = 0; i < 4; ++i)
        result.corners.corners[i] =
            new PointF(buffer.getFloat(), buffer.getFloat());
    } else if (result.state == State.SLAM_TRACKING_SUCCESS) {
      // set camera
      result.camera = new Camera();
      result.camera.rotationVector = new float[4];
      result.camera.position = new float[3];
      for (int i = 0; i < 4; ++i)
        result.camera.rotationVector[i] = buffer.getFloat();
      for (int i = 0; i < 3; ++i)
        result.camera.position[i] = buffer.getFloat();
    }

    int infoLen = buffer.getInt();
    byte[] str = new byte[infoLen];
    buffer.get(str, 0, infoLen);
    try {

      result.info = new String(str, "ASCII");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }

    return result;
  }

  private VSLAMConfig mConfig;
  // C++ pointer to the VSLAM instance
  private long mPtrVSLAM;
  // the index of the frame, starts from 0
  private int mIndex;

  // recording
  private boolean mIsRecording;
  // the recording file directory
  private File mDirectory;
  private Handler mFileSaverHandler;

  /**
   * Get a new NativeVSLAM instance
   *
   * @param config the configuration used to construct the NativeVSLAM
   * @param recording if true, record the frames into binary files, which can
   * then be unpacked, @see packNativeFrame, @see #unpackNativeFrame
   */
  public static NativeVSLAM newInstance(final VSLAMConfig config,
                                        boolean recording) {
    return new NativeVSLAM(config, recording);
  }

  /**
   * Run slam with one frame
   *
   * @param frame the new frame
   * @result the tracking result
   */
  public TrackingResult processFrame(final Frame frame) {
    final byte[] nativeFrame = packNativeFrame(frame);
    if (mIsRecording) {
      final File file =
          new File(mDirectory, Integer.toString(mIndex++) + ".txt");
      mFileSaverHandler.post(new Runnable() {
        @Override
        public void run() {
          // File file = new File(mDirectory, Integer.toString(mIndex) +
          // ".txt");
          try {
            FileOutputStream output = new FileOutputStream(file);
            output.write(nativeFrame);
            output.close();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      });
    }

    byte[] nativeResult = process_frame(mPtrVSLAM, nativeFrame);
    return unpackNativeResult(nativeResult);

    /*
    TrackingResult result = new TrackingResult();
    result.state = State.SLAM_TRACKING_SUCCESS;
    result.camera = new Camera();
    result.camera.rotationVector = new float[]{0, 0, 0, 1};
    result.camera.position = new float[]{0, 0, 0};
    return result;
    */
  }

  @Override
  public void close() throws Exception {
    destroy_instance(mPtrVSLAM);
  }

  private NativeVSLAM(final VSLAMConfig config, boolean recording) {
    mConfig = config;
    mPtrVSLAM = get_instance(config);
    assert mPtrVSLAM != 0;
    mIndex = 0;

    mIsRecording = recording;
    if (recording) {
      mDirectory =
          new File(Environment.getExternalStorageDirectory(),
                   "VSLAMTest" + File.separator +
                       Long.toString(SystemClock.elapsedRealtime() / 10000));
      mDirectory.mkdirs();
      mFileSaverHandler = new Handler();
    }
  }

  private native long get_instance(final VSLAMConfig config);

  private native void destroy_instance(final long ptr);

  private native byte[] process_frame(final long ptr, byte[] frame);
}
