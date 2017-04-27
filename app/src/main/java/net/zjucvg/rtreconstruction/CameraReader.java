package net.zjucvg.rtreconstruction;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;

/**
 * Created by lichen on 16-6-19.
 */

/**
 * A class for easily accessing android.hardware.camera2 functionality
 */
public class CameraReader implements AutoCloseable {

  private static String LOG_TAG = "CameraReader";

  private Activity mActivity;
  boolean hasStarted;
  private CameraDevice mCameraDevice;
  private Boolean mCameraOpenSync;
  private Boolean mCaptureSessionCloseSync;
  private float mMaxFocalLength;
  private CameraCaptureSession mCaptureSession;

  private HandlerThread mBackgroundThread;
  private Handler mBackgroundHandler;

  /**
   * Constructor, it returns until acquired camera device or some error happens,
   * so it may takes some time before return
   */
  public CameraReader(Activity activity) {
    mActivity = activity;
    startBackgroundThread();
    mCameraOpenSync = new Boolean(false);
    mCaptureSessionCloseSync = new Boolean(false);
    synchronized (mCameraOpenSync) {
      openCamera();
      try {
        mCameraOpenSync.wait();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
    if (null == mCameraDevice)
      makeToast("Can not open camera");
  }

  /**
   * Get all support (YUV420_888) image size
   */
  public List<Size> getSizeList() {
    try {
      CameraManager cameraManager =
              (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
      String id = mCameraDevice.getId();
      CameraCharacteristics characteristics =
              cameraManager.getCameraCharacteristics(id);
      StreamConfigurationMap map = characteristics.get(
              CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

      // Log.d(LOG_TAG, "Support output format " +
      // Arrays.toString(map.getOutputFormats()));
      // Log.d(LOG_TAG, "YUV_420_888 " + ImageFormat.YUV_420_888);
      // Log.d(LOG_TAG, "YUV_420_888 " +
      // Arrays.toString(map.getOutputSizes(ImageFormat.YUV_420_888)));

      return Arrays.asList(map.getOutputSizes(ImageFormat.YUV_420_888));
    } catch (CameraAccessException e) {
      makeToast("Can not access camera.");
      return null;
    }
  }

  /**
   * Start repeating capture
   */
  public void start(List<Surface> surfaceList) {
    if (surfaceList.isEmpty())
      throw new IllegalArgumentException("Empty surface list");
    if (hasStarted)
      throw new IllegalStateException("Has started");

    // set up session
    try {
      final CaptureRequest.Builder builder =
              mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

      // FIXME: the codes below can turn off auto focus in Samsung Galaxy S7,
      // though I'm not clear
      builder.set(CaptureRequest.CONTROL_CAPTURE_INTENT,
              CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW);
      // control optical focusing, which is typically not support in most
      // device, that is, the code segment has no effect
      builder.set(CaptureRequest.CONTROL_AF_MODE,
              CaptureRequest.CONTROL_AF_MODE_OFF);
      builder.set(CaptureRequest.LENS_FOCAL_LENGTH, mMaxFocalLength);

      for (Surface surface : surfaceList)
        builder.addTarget(surface);
      mCameraDevice.createCaptureSession(
              surfaceList, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                  mCaptureSession = session;
                  try {
                    mCaptureSession.setRepeatingRequest(builder.build(), null,
                            null);
                  } catch (CameraAccessException e) {
                    Log.e(LOG_TAG, "setRepeatingRequest failed");
                  }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                  session.close();
                  Log.e(LOG_TAG, "configure failed");
                }

                @Override
                public void onClosed(CameraCaptureSession session) {
                  mCaptureSession = null;
                  synchronized (mCaptureSessionCloseSync) {
                    mCaptureSessionCloseSync.notifyAll();
                  }
                }
              }, mBackgroundHandler);
    } catch (CameraAccessException e) {
      makeToast("Can not access camera.");
      return;
    }
    hasStarted = true;
  }

  /**
   * Stop repeating capture
   */
  public void stop() {
    if (!hasStarted)
      throw new IllegalStateException("Not started");

    if (null != mCaptureSession) {
      synchronized (mCaptureSessionCloseSync) {
        mCaptureSession.close();
        mCaptureSession = null;
        try {
          mCaptureSessionCloseSync.wait();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    }

    hasStarted = false;
  }

  @Override
  public void close() throws Exception {
    if (hasStarted)
      stop();
    if (null != mCameraDevice) {
      mCameraDevice.close();
      mCameraDevice = null;
    }
    stopBackgroundThread();
  }

  private Activity getActivity() {
    return mActivity;
  }

  private void openCamera() {
    final CameraManager cameraManager =
            (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
    try {
      for (String id : cameraManager.getCameraIdList()) {

        CameraCharacteristics characteristics =
                cameraManager.getCameraCharacteristics(id);
        if (characteristics.get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_FRONT)
          continue;

        float[] focalLengths = characteristics.get(
                CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        Log.d(LOG_TAG,
                "available focal lengths: " + Arrays.toString(focalLengths));
        mMaxFocalLength = 0;
        for (float f : focalLengths)
          if (f > mMaxFocalLength)
            mMaxFocalLength = f;

        // choose this
        if (ActivityCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
          // TODO: Consider calling
          //    ActivityCompat#requestPermissions
          // here to request the missing permissions, and then overriding
          //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
          //                                          int[] grantResults)
          // to handle the case where the user grants the permission. See the documentation
          // for ActivityCompat#requestPermissions for more details.
          return;
        }
        cameraManager.openCamera(id, new CameraDevice.StateCallback() {
          @Override
          public void onOpened(CameraDevice camera) {
            mCameraDevice = camera;
            synchronized (mCameraOpenSync) {
              mCameraOpenSync.notifyAll();
            }
          }

          @Override
          public void onDisconnected(CameraDevice camera) {
            camera.close();
            synchronized (mCameraOpenSync) {
              mCameraOpenSync.notifyAll();
            }
          }

          @Override
          public void onError(CameraDevice camera, int error) {
            camera.close();
            synchronized (mCameraOpenSync) {
              mCameraOpenSync.notifyAll();
            }
            mCameraDevice = null;
          }
        }, mBackgroundHandler);
        break;
      }
    } catch (CameraAccessException e) {
      makeToast("Can not access camera");
    }
  }

  private void makeToast(String msg) {
    Toast.makeText(getActivity(), msg, Toast.LENGTH_LONG).show();
  }

  private void startBackgroundThread() {
    mBackgroundThread = new HandlerThread("CameraReader");
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
}
