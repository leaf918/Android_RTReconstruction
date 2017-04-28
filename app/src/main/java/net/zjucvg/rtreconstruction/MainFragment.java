package net.zjucvg.rtreconstruction;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.app.Fragment;
import android.support.v13.app.FragmentCompat;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class MainFragment extends Fragment
    implements View.OnClickListener, MenuItem.OnMenuItemClickListener,
               FragmentCompat.OnRequestPermissionsResultCallback {

  private static final String LOG_TAG = "MainFragment";

  private static final int REQUEST_RECORD_PERMISSIONS = 1;
  private static final String[] RECORD_PERMISSIONS = {
      android.Manifest.permission.CAMERA,
      android.Manifest.permission.READ_EXTERNAL_STORAGE,
      android.Manifest.permission.WRITE_EXTERNAL_STORAGE};
  private static final String FRAGMENT_DIALOG = "dialog";

  private boolean mUseOnlineFrameSource = true;
  private FrameSource mFrameSource;
  private boolean mIsPreview;

  boolean mIsSLAMRunning;
  private NativeVSLAM.VSLAMConfig mConfig;
  private NativeVSLAM mNativeVSLAM;

  private class BackgroundPreviewLooper extends Thread {

    private volatile boolean mContinueRunning = true;

    @Override
    public void run() {
      while (mContinueRunning && mFrameSource.hasMoreFrame()) {
        final long startTimeMilliseconds = SystemClock.elapsedRealtime();
        Log.d(LOG_TAG, "try acquire new frame.");
        final NativeVSLAM.Frame frame = mFrameSource.getNextFrame(true);
        final long getFrameTimeMilliseconds = SystemClock.elapsedRealtime();
        Log.d(LOG_TAG,
              Long.toString(getFrameTimeMilliseconds - startTimeMilliseconds) +
                  " milliseconds used to get new frame.");
        getActivity().runOnUiThread(new Runnable() {
          @Override
          public void run() {
            mImageView.setImageBitmap((Bitmap)frame.userData);
          }
        });
        if (!mUseOnlineFrameSource)
          break;
      }
    }

    public void requestStop() { mContinueRunning = false; }
  }

  private class BackgroundVSLAMLooper extends Thread {

    private volatile boolean mContinueRunning = true;

    @Override
    public void run() {
      int frameCount=0;
      while (mContinueRunning && mFrameSource.hasMoreFrame()) {
        Log.d(LOG_TAG, Boolean.toString(mContinueRunning));
        final long startTimeMilliseconds = SystemClock.elapsedRealtime();
        Log.d(LOG_TAG, "try acquire new frame.");
        final NativeVSLAM.Frame frame = mFrameSource.getNextFrame(true);
        final long getFrameTimeMilliseconds = SystemClock.elapsedRealtime();
        Log.d(LOG_TAG,
              Long.toString(getFrameTimeMilliseconds - startTimeMilliseconds) +
                  " milliseconds used to get new frame.");
        final NativeVSLAM.TrackingResult result =
            mNativeVSLAM.processFrame(frame);

        Log.e("Main Activity", "frame: "+frameCount++);

        Log.e("Main Activity", "position: "+result.camera.position[0]+", "+result.camera.position[1]+", "+result.camera.position[2]);

        Log.e("Main Activity", "rotation vector: "+result.camera.rotationVector[0]+", "+result.camera.rotationVector[1]+", "+result.camera.rotationVector[2]+", "+result.camera.rotationVector[3]);


        final long finishTimeMilliseconds = SystemClock.elapsedRealtime();
        Log.d(LOG_TAG,
              Long.toString(finishTimeMilliseconds - getFrameTimeMilliseconds) +
                  " milliseconds used to process the new frame");
        final long elapsedTimeMilliseconds =
            finishTimeMilliseconds - startTimeMilliseconds;
        Log.d(LOG_TAG, Long.toString(elapsedTimeMilliseconds) +
                           " milliseconds total time used.");
        getActivity().runOnUiThread(new Runnable() {
          @Override
          public void run() {
            onTrackingResult(frame, result, elapsedTimeMilliseconds);
          }
        });
      }
    }

    public void requestStop() { mContinueRunning = false; }
  }

  private AutoFitImageView mImageView;
  private Button mControlButton;
  private TextView mVSLAMFPS;
  private TextView mVSLAMState;
  private TextView mCameraRotAxis;
  private TextView mCameraRotAngle;
  private TextView mCameraPos;

  private BackgroundPreviewLooper
      mBackgroundPreviewLooper;                         // re-acquire when start
  private BackgroundVSLAMLooper mBackgroundVSLAMLooper; // re-acquire when start

  public MainFragment() {}

  public static MainFragment newInstance() { return new MainFragment(); }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(true);
    //RSUtility.init(getActivity());
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    // Inflate the layout for this fragment
    return inflater.inflate(R.layout.fragment_main, container, false);
  }

  @Override
  public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    mImageView = (AutoFitImageView)view.findViewById(R.id.image_view);

    mControlButton = (Button)view.findViewById(R.id.control);
    mControlButton.setOnClickListener(this);

    mVSLAMFPS = (TextView)view.findViewById(R.id.slam_fps);
    mVSLAMState = (TextView)view.findViewById(R.id.slam_state);
    mCameraRotAxis = (TextView)view.findViewById(R.id.camera_rot_axis);
    mCameraRotAngle = (TextView)view.findViewById(R.id.camera_rot_angle);
    mCameraPos = (TextView)view.findViewById(R.id.camera_pos);
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    inflater.inflate(R.menu.menu, menu);
    menu.findItem(R.id.menu_choose_data).setOnMenuItemClickListener(this);
    menu.findItem(R.id.menu_use_offline).setOnMenuItemClickListener(this);
    menu.findItem(R.id.menu_settings).setOnMenuItemClickListener(this);
    menu.findItem(R.id.menu_help).setOnMenuItemClickListener(this);
  }

  @Override
  public void onPrepareOptionsMenu(Menu menu) {
    super.onPrepareOptionsMenu(menu);
    menu.findItem(R.id.menu_choose_data).setVisible(!mUseOnlineFrameSource);
    menu.findItem(R.id.menu_use_offline).setChecked(!mUseOnlineFrameSource);
  }

  @Override
  public void onResume() {
    super.onResume();

    if (!hasPermissionsGranted(RECORD_PERMISSIONS)) {
      requestRecordPermissions();
      return;
    }

    mControlButton.setClickable(initSLAM());
    mControlButton.setText(getString(R.string.start));
  }

  @Override
  public void onPause() {
    super.onPause();
    destroySLAM();
  }

  @Override
  public void onClick(View v) {
    switch (v.getId()) {
    case R.id.control:
      if (mIsSLAMRunning) {
        stopSLAM();
        startPreview();
        mVSLAMFPS.setText(getString(R.string.slam_fps_unknown));
        mVSLAMState.setText(getString(R.string.slam_state_notstarted));
        setDefaultCameraPose();
        mControlButton.setText(getString(R.string.start));
      } else {
        stopPreview();
        startSLAM();
        mControlButton.setText(getString(R.string.stop));
      }
      break;
    default:
      break;
    }
  }

  @Override
  public boolean onMenuItemClick(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.menu_choose_data:
      startActivity(
          new Intent(getActivity(), OfflineSourceSettingsActivity.class));
      return true;
    case R.id.menu_use_offline:
      item.setChecked(!item.isChecked());
      mUseOnlineFrameSource = !item.isChecked();
      getActivity().invalidateOptionsMenu();
      mControlButton.setClickable(initSLAM());
      return true;
    case R.id.menu_settings:
      startActivity(new Intent(getActivity(), SettingsActivity.class));
    case R.id.menu_help:
      return true;
    default:
      return false;
    }
  }

  private boolean initSLAM() {
    destroySLAM();
    if (!setupFrameSource())
      return false;
    if (!setupVSLAMConfig())
      return false;
    mImageView.setAspectRatio(mConfig.size.width, mConfig.size.height);
    startPreview();
    return true;
  }

  private void destroySLAM() {
    if (mIsPreview)
      stopPreview();
    if (mIsSLAMRunning)
      stopSLAM();
    try {
      if (null != mFrameSource)
        mFrameSource.close();
      mFrameSource = null;
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private boolean setupFrameSource() {
    if (mUseOnlineFrameSource)
      mFrameSource = new OnlineFrameSource(getActivity());
    else {
      SharedPreferences preferences =
          PreferenceManager.getDefaultSharedPreferences(getActivity());
      String pathFormat = preferences.getString(
          getString(R.string.offline_file_path_format_key), "");
      Log.d(LOG_TAG, preferences.getString(
                         getString(R.string.offline_file_beg_key), ""));
      int beg = Integer.valueOf(
          preferences.getString(getString(R.string.offline_file_beg_key), "0"));
      int step = Integer.valueOf(preferences.getString(
          getString(R.string.offline_file_step_key), "1"));
      int end = Integer.valueOf(
          preferences.getString(getString(R.string.offline_file_end_key),
                                new Integer(Integer.MAX_VALUE).toString()));
      /*
      String pathFormat = "/sdcard/VSLAMTest/83109/%d.txt";
      int beg = 0;
      int step = 1;
      int end = 340;
      */
      mFrameSource =
          new OfflineFrameSource(getActivity(), pathFormat, beg, step, end);
    }
    return true;
  }

  private Size chooseBestSize(List<Size> sizeList, Size idealSize) {
    // check whether sizeList contains idealSize, return idealSize in the case
    if (sizeList.contains(idealSize))
      return idealSize;
    // choose the size who's area no less than, and most close to idealSize's
    // area
    // if all size's area less than idealSize's area, choose the max one
    Size best = null;
    int bestDiff = 0;

    for (Size s : sizeList) {
      int diff = s.getWidth() * s.getHeight() -
                 idealSize.getWidth() * idealSize.getHeight();
      if (null == best || diff > bestDiff && bestDiff < 0 ||
          diff <= bestDiff && diff >= 0) {
        best = s;
        bestDiff = diff;
      }
    }
    return best;
  }

  private boolean setupVSLAMConfig() {
    Size size = chooseBestSize(mFrameSource.getAvailableImageSize(),
                               new Size(640, 480));
    SharedPreferences preferences =
        PreferenceManager.getDefaultSharedPreferences(getActivity());
    NativeVSLAM.CameraIntrinsics intrinsics = null;
    intrinsics = SettingsFragment.getCameraIntrinsics(
        preferences, size.getWidth(), size.getHeight());
    if (null == intrinsics) {
      Toast.makeText(getActivity(), "Please set the camera intrinsics first",
                     Toast.LENGTH_LONG)
          .show();
      mConfig = null;
      startActivity(new Intent(getActivity(), SettingsActivity.class));
      return false;
    }

    mConfig = new NativeVSLAM.VSLAMConfig();
    mConfig.intrinsics = intrinsics;

    mConfig.size = new NativeVSLAM.Size();
    mConfig.size.width = size.getWidth();
    mConfig.size.height = size.getHeight();
    mConfig.px = -0.05F;
    mConfig.py = -0.002F;
    mConfig.newImgF = SettingsFragment.getNewImageFocalLength(
        preferences, size.getWidth(), size.getHeight());
    mConfig.initDist = 1;

    mConfig.useMarker = true;
    mConfig.parallel = true;
    mConfig.logLevel = 0;
    mConfig.logFilePath = "/sdcard/VSLAMTest/log.txt";
    return true;
  }

  private void startPreview() {
    mFrameSource.start(new Size(mConfig.size.width, mConfig.size.height));
    mBackgroundPreviewLooper = new BackgroundPreviewLooper();
    mBackgroundPreviewLooper.start();
    mIsPreview = true;
  }

  private void stopPreview() {
    if (mIsPreview) {
      mBackgroundPreviewLooper.requestStop();
      try {
        mBackgroundPreviewLooper.join();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      mBackgroundPreviewLooper = null;
      mFrameSource.stop();
      mIsPreview = false;
    }
  }

  private void startSLAM() {
    mFrameSource.start(new Size(mConfig.size.width, mConfig.size.height));
    mNativeVSLAM = NativeVSLAM.newInstance(mConfig, false);
    mBackgroundVSLAMLooper = new BackgroundVSLAMLooper();
    mBackgroundVSLAMLooper.start();
    mIsSLAMRunning = true;
  }

  private void setDefaultCameraPose() {
    mCameraRotAxis.setText(getString(R.string.camera_rot_axis_unknown));
    mCameraRotAngle.setText(getString(R.string.camera_rot_angle_unknown));
    mCameraPos.setText(getString(R.string.camera_pos_unknown));
  }

  private void updateCameraPose(NativeVSLAM.Camera camera) {
    float qx = camera.rotationVector[0];
    float qy = camera.rotationVector[1];
    float qz = camera.rotationVector[2];
    float qw = camera.rotationVector[3];
    float norm = (float)Math.sqrt(qx * qx + qy * qy + qz * qz);

    float[] axis = new float[] {qx / norm, qy / norm, qz / norm};
    float angle = 2 * (float)Math.acos(qw);
    angle *= 180 / (float)Math.PI;

    mCameraRotAxis.setText(
        String.format("[%.2f, %.2f, %.2f]", axis[0], axis[1], axis[2]));
    mCameraRotAngle.setText(String.format("%.2f", angle));
    mCameraPos.setText(String.format("[%.2f, %.2f, %.2f]", camera.position[0],
                                     camera.position[1], camera.position[2]));
  }

  private void drawA4Paper(Bitmap bitmap, NativeVSLAM.Camera camera) {
    final float K_WIDTH = 0.21F;
    final float K_LENGTH = 0.297F;
    // the four A4 paper corners
    float[][] worldPoints = new float[4][];
    worldPoints[0] = new float[] {-K_WIDTH / 2, -K_LENGTH / 2, 0};
    worldPoints[1] = new float[] {+K_WIDTH / 2, -K_LENGTH / 2, 0};
    worldPoints[2] = new float[] {+K_WIDTH / 2, +K_LENGTH / 2, 0};
    worldPoints[3] = new float[] {-K_WIDTH / 2, +K_LENGTH / 2, 0};
    float[][] imagePoints = new float[4][];
    for (int i = 0; i < 4; ++i) {
      imagePoints[i] =
          Utility.world2Image(worldPoints[i], camera, mConfig.intrinsics);
    }

    Canvas canvas = new Canvas(bitmap);
    Path path = new Path();
    path.moveTo(imagePoints[0][0], imagePoints[0][1]);
    for (int i = 0; i < 4; ++i) {
      path.lineTo(imagePoints[i][0], imagePoints[i][1]);
    }
    path.lineTo(imagePoints[0][0], imagePoints[0][1]);

    Paint paint = new Paint();
    paint.setStyle(Paint.Style.STROKE);
    paint.setColor(Color.GREEN);
    canvas.drawPath(path, paint);

    // draw 3 axis
    final float AXIS_LEN = 0.3F;
    float[] ori =
        Utility.world2Image(new float[] {0, 0, 0}, camera, mConfig.intrinsics);
    float[] x = Utility.world2Image(new float[] {AXIS_LEN, 0, 0}, camera,
                                    mConfig.intrinsics);
    float[] y = Utility.world2Image(new float[] {0, AXIS_LEN, 0}, camera,
                                    mConfig.intrinsics);
    float[] z = Utility.world2Image(new float[] {0, 0, AXIS_LEN}, camera,
                                    mConfig.intrinsics);
    paint.setColor(Color.RED);
    canvas.drawLine(ori[0], ori[1], x[0], x[1], paint);
    paint.setColor(Color.GREEN);
    canvas.drawLine(ori[0], ori[1], y[0], y[1], paint);
    paint.setColor(Color.BLUE);
    canvas.drawLine(ori[0], ori[1], z[0], z[1], paint);
  }

  private void onTrackingResult(NativeVSLAM.Frame frame,
                                NativeVSLAM.TrackingResult result,
                                long timeMilliseconds) {
    // due to latency, this function may be called after clicked "STOP" button
    if (!mIsSLAMRunning)
      return;
    mVSLAMFPS.setText(String.format("%.2f", 1000.0F / timeMilliseconds));
    switch (result.state) {
    case SLAM_MARKER_DETECTING:
      Log.d(LOG_TAG, "SLAM_MARKER_DETECING");
      mVSLAMState.setText(getString(R.string.slam_state_detecting));
      break;
    case SLAM_MARKER_DETECTED:
      Log.d(LOG_TAG, "SLAM_MARKER_DETECTED");
      mVSLAMState.setText(getString(R.string.slam_state_detected));
      break;
    case SLAM_TRACKING_SUCCESS:
      Log.d(LOG_TAG, "SLAM_TRACKIGN_SUCCESS");
      mVSLAMState.setText(getString(R.string.slam_state_success));
      updateCameraPose(result.camera);
      break;
    case SLAM_TRAKCING_FAIL:
      Log.d(LOG_TAG, "SLAM_TRACKING_FAIL");
      mVSLAMState.setText(getString(R.string.slam_state_fail));
      setDefaultCameraPose();
      break;
    default:
      assert false;
      break;
    }

    Bitmap bitmap = (Bitmap)frame.userData;
    if (null == bitmap)
      return;
    if (result.state == NativeVSLAM.State.SLAM_TRACKING_SUCCESS) {
      drawA4Paper(bitmap, result.camera);
    }
    updateImageView(bitmap);
  }

  private void stopSLAM() {
    if (mIsSLAMRunning) {
      mBackgroundVSLAMLooper.requestStop();
      try {
        mBackgroundVSLAMLooper.join();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      mBackgroundVSLAMLooper = null;
      try {
        mNativeVSLAM.close();
        mNativeVSLAM = null;
      } catch (Exception e) {
        e.printStackTrace();
      }
      mFrameSource.stop();
      mIsSLAMRunning = false;
    }
  }

  private void updateImageView(Bitmap bitmap) {
    mImageView.setImageBitmap(bitmap);
  }

  private boolean shouldShowRequestPermissionRationale(String[] permissions) {
    for (String permission : permissions) {
      if (FragmentCompat.shouldShowRequestPermissionRationale(this,
                                                              permission)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Requests permissions needed for recording video.
   */
  private void requestRecordPermissions() {
    if (shouldShowRequestPermissionRationale(RECORD_PERMISSIONS)) {
      new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
    } else {
      FragmentCompat.requestPermissions(this, RECORD_PERMISSIONS,
                                        REQUEST_RECORD_PERMISSIONS);
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                         int[] grantResults) {
    Log.d(LOG_TAG, "onRequestPermissionsResult");
    if (requestCode == REQUEST_RECORD_PERMISSIONS) {
      if (grantResults.length == RECORD_PERMISSIONS.length) {
        for (int result : grantResults) {
          if (result != PackageManager.PERMISSION_GRANTED) {
            ErrorDialog.newInstance(getString(R.string.permission_request))
                .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            break;
          }
        }
      } else {
        ErrorDialog.newInstance(getString(R.string.permission_request))
            .show(getChildFragmentManager(), FRAGMENT_DIALOG);
      }
    } else {
      super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
  }

  private boolean hasPermissionsGranted(String[] permissions) {
    for (String permission : permissions) {
      if (ActivityCompat.checkSelfPermission(getActivity(), permission) !=
          PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }

  public static class ConfirmationDialog extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      final Fragment parent = getParentFragment();
      return new AlertDialog.Builder(getActivity())
          .setMessage(R.string.permission_request)
          .setPositiveButton(
              android.R.string.ok,
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                  FragmentCompat.requestPermissions(parent, RECORD_PERMISSIONS,
                                                    REQUEST_RECORD_PERMISSIONS);
                }
              })
          .setNegativeButton(android.R.string.cancel,
                             new DialogInterface.OnClickListener() {
                               @Override
                               public void onClick(DialogInterface dialog,
                                                   int which) {
                                 parent.getActivity().finish();
                               }
                             })
          .create();
    }
  }

  public static class ErrorDialog extends DialogFragment {

    private static final String ARG_MESSAGE = "message";

    public static ErrorDialog newInstance(String message) {
      ErrorDialog dialog = new ErrorDialog();
      Bundle args = new Bundle();
      args.putString(ARG_MESSAGE, message);
      dialog.setArguments(args);
      return dialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      final Activity activity = getActivity();
      return new AlertDialog.Builder(activity)
          .setMessage(getArguments().getString(ARG_MESSAGE))
          .setPositiveButton(android.R.string.ok,
                             new DialogInterface.OnClickListener() {
                               @Override
                               public void onClick(
                                   DialogInterface dialogInterface, int i) {
                                 activity.finish();
                               }
                             })
          .create();
    }
  }
}
