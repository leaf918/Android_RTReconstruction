package net.zjucvg.rtreconstruction;

import android.util.Log;
import android.util.Size;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by kuanlu on 2017/4/11.
 */

public class VSLAMThread extends Thread {

    private static final String LOG_TAG = "VSLAMThread";
    private NativeVSLAM.VSLAMConfig mConfig;
    private boolean isA4Paper = true;
    private MainActivity activity;

    final int IDEL_WIDTH = 640;
    final int IDEL_HEIGHT = 480;

    public boolean isThreadAlive = false;

    public VSLAMThread(MainActivity mainActivity)
    {
        this.activity = mainActivity;
    }

    @Override
    public void run()
    {
        isThreadAlive =true;
        FrameSource frameSource;
        Log.d(LOG_TAG, "Getting FrameSource");
        frameSource = new OnlineFrameSource(activity);

        Log.d(LOG_TAG, "Setting up config file");
        setupVSLAMConfig();
        Log.d(LOG_TAG, "Getting SLAM instance");
        NativeVSLAM slam = NativeVSLAM.newInstance(mConfig, false);
        Log.d(LOG_TAG, "Start getting frame");
        frameSource.start(new Size(mConfig.size.width, mConfig.size.height));

        while (frameSource.hasMoreFrame()) {

            NativeVSLAM.Frame frame = frameSource.getNextFrame(true);
            NativeVSLAM.TrackingResult result = slam.processFrame(frame);

            if (result.state == NativeVSLAM.State.SLAM_TRACKING_SUCCESS) {
                activity.changeText(result.camera.position[0]+" "
                        +result.camera.position[1]+" "
                        +result.camera.position[2]+" "
                        +result.camera.rotationVector[0]+" "
                        +result.camera.rotationVector[1]+" "
                        +result.camera.rotationVector[2]+" "
                        +result.camera.rotationVector[3]+" ");
            }
            else
                activity.changeText("DETECTING");
        }

        try {
            frameSource.close();
            slam.close();
        } catch (Exception ex) {
            Log.d(LOG_TAG, ex.getMessage());
        }
        finally {
            isThreadAlive=false;
        }
    }

    private void setupVSLAMConfig() {

        String content = ReadTxtFile("/mnt/sdcard/vslamconfig.json");
        if(!content.equals("")) {
            JSONTokener jsonParser = new JSONTokener(content);
            try {
                JSONObject config = (JSONObject) jsonParser.nextValue();
                int width = config.getInt("width");
                int height = config.getInt("height");

                mConfig = new NativeVSLAM.VSLAMConfig();
                mConfig.intrinsics = new NativeVSLAM.CameraIntrinsics();
                mConfig.intrinsics.fisheye = config.getBoolean("fisheye");
                mConfig.intrinsics.fx = (float) (config.getDouble("fx") * IDEL_WIDTH / width);
                mConfig.intrinsics.fy = (float) (config.getDouble("fy") * IDEL_HEIGHT / height);
                mConfig.intrinsics.skew = (float) (config.getDouble("skew") * IDEL_WIDTH / width);
                mConfig.intrinsics.px = (float) (config.getDouble("px") * IDEL_WIDTH / width);
                mConfig.intrinsics.py = (float) (config.getDouble("py") * IDEL_HEIGHT / height);
                JSONArray k = config.getJSONArray("k");
                mConfig.intrinsics.k = new float[]{(float) k.getDouble(0), (float) k.getDouble(1), (float) k.getDouble(2), (float) k.getDouble(3), (float) k.getDouble(4)};

                mConfig.size = new NativeVSLAM.Size();
                mConfig.size.width = IDEL_WIDTH;
                mConfig.size.height = IDEL_HEIGHT;
                mConfig.px = (float) (config.getDouble("dx"));
                mConfig.py = (float) (config.getDouble("dy"));
                mConfig.newImgF = (float) (Math.sqrt((double) IDEL_WIDTH * IDEL_HEIGHT / (double) (width * height)) * config.getDouble("newF"));
                mConfig.initDist = 1;

                mConfig.useMarker = true;

                if (isA4Paper) {
                    mConfig.markerLength = 0.297f;
                    mConfig.markerWidth = 0.21f;
                } else {
                    mConfig.markerLength = 0.279f;
                    mConfig.markerWidth = 0.216f;
                }

                mConfig.parallel = true;
                mConfig.logLevel = 1;
                mConfig.logFilePath = "";

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        else {
            mConfig = new NativeVSLAM.VSLAMConfig();
            mConfig.intrinsics = new NativeVSLAM.CameraIntrinsics();
            mConfig.intrinsics.fisheye=false;
            mConfig.intrinsics.fx=498.0f;//(float)(IDEL_WIDTH/(2*Math.tan((double)fovx)));
            mConfig.intrinsics.fy=498.0f;//(float)(IDEL_HEIGHT/(2*Math.tan((double)fovy)));
            mConfig.intrinsics.skew=0.0f;
            mConfig.intrinsics.px=(IDEL_WIDTH-1)/2.0f;
            mConfig.intrinsics.py=(IDEL_HEIGHT-1)/2.0f;
            mConfig.intrinsics.k = new float[]{0.0f,0.0f,0.0f,0.0f,0.0f};

            mConfig.size = new NativeVSLAM.Size();
            mConfig.size.width = IDEL_WIDTH;
            mConfig.size.height = IDEL_HEIGHT;
            mConfig.px = 0.0f;
            mConfig.py = 0.0f;
            mConfig.newImgF = (mConfig.intrinsics.fx+mConfig.intrinsics.fy)/2.0f;
            mConfig.initDist = 1;

            mConfig.useMarker = true;
            if (isA4Paper) {
                mConfig.markerLength = 0.297f;
                mConfig.markerWidth = 0.21f;
            } else {
                mConfig.markerLength = 0.279f;
                mConfig.markerWidth = 0.216f;
            }
            mConfig.parallel = true;
            mConfig.logLevel = 1;
            mConfig.logFilePath = "";
        }
        Log.d(LOG_TAG,"fisheye: " + mConfig.intrinsics.fisheye + "");
        Log.d(LOG_TAG,"fx: " + mConfig.intrinsics.fx + "");
        Log.d(LOG_TAG,"fy: " + mConfig.intrinsics.fy + "");
        Log.d(LOG_TAG,"skew: " + mConfig.intrinsics.skew + "");
        Log.d(LOG_TAG,"px: " + mConfig.intrinsics.px + "");
        Log.d(LOG_TAG,"py: " + mConfig.intrinsics.py + "");
        Log.d(LOG_TAG,"k[0]: " + mConfig.intrinsics.k[0] + "");
        Log.d(LOG_TAG,"k[1]: " + mConfig.intrinsics.k[1] + "");
        Log.d(LOG_TAG,"k[2]: " + mConfig.intrinsics.k[2] + "");
        Log.d(LOG_TAG,"k[3]: " + mConfig.intrinsics.k[3] + "");
        Log.d(LOG_TAG,"k[4]: " + mConfig.intrinsics.k[4] + "");
        Log.d(LOG_TAG,"dx: " + mConfig.px + "");
        Log.d(LOG_TAG,"dy: " + mConfig.py + "");
        Log.d(LOG_TAG,"newImgF: " + mConfig.newImgF + "");
        Log.d(LOG_TAG,"useMarker: " + mConfig.useMarker + "");
        Log.d(LOG_TAG,"MarkerLength: " + mConfig.markerLength + "");
        Log.d(LOG_TAG,"MarkerWidth: " + mConfig.markerWidth + "");
    }

    public static String ReadTxtFile(String strFilePath) {
        String content = "";

        File file = new File(strFilePath);

        if (file.isDirectory()) {
            Log.d(LOG_TAG, "Config File does not exist");
            return "";
        } else {
            try {
                InputStream instream = new FileInputStream(file);
                InputStreamReader inputreader = new InputStreamReader(instream);
                BufferedReader buffreader = new BufferedReader(inputreader);
                String line;

                while ((line = buffreader.readLine()) != null) {
                    content += line + "\n";
                }
                instream.close();
            } catch (java.io.FileNotFoundException e) {
                Log.d(LOG_TAG, "Config File does not exist");
                return "";
            } catch (IOException e) {
                Log.d(LOG_TAG, e.getMessage());
                return "";
            }
        }
        return content;
    }
}
