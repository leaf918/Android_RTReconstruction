package net.zjucvg.rtreconstruction;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.support.v4.app.INotificationSideChannel;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

// example
// newF is optional
// width, height -- type int
// fisheye -- false/true
// others -- type double
// {
// "width" : 640
// "height" : 480
// "fisheye" : false
// "fx" : 500.0
// "fy" : 500.0
// "px" : 319.5
// "py" : 239.5
// "skew" : 2.0
// "k" : [0 0.1 0.1 0.1 0.2]
// "newF" : 500.0
// }

public class SettingsFragment
    extends PreferenceFragment implements Preference.OnPreferenceClickListener {

  static final String LOG_TAG = "SettingsFragment";

  static final int REQUEST_CHOOSE_FILE = 1;

  public static NativeVSLAM.CameraIntrinsics
  getCameraIntrinsics(SharedPreferences preferences, int width, int height) {
    NativeVSLAM.CameraIntrinsics intrinsics =
        new NativeVSLAM.CameraIntrinsics();
    if (!preferences.contains("intrinsics.fisheye"))
      return null;
    int w = preferences.getInt("intrinsics.width", 0);
    int h = preferences.getInt("intrinsics.height", 0);
    intrinsics.fisheye = preferences.getBoolean("intrinsics.fisheye", false);
    intrinsics.fx = width * preferences.getFloat("intrinsics.fx", 0) / w;
    intrinsics.fy = height * preferences.getFloat("intrinsics.fy", 0) / h;
    intrinsics.px = width * preferences.getFloat("intrinsics.px", 0) / w;
    intrinsics.py = height * preferences.getFloat("intrinsics.py", 0) / h;
    intrinsics.skew = width * preferences.getFloat("intrinsics.skew", 0) / w;
    intrinsics.k = new float[] {0, 0, 0, 0, 0};
    for (int i = 0; i < 5; ++i)
      intrinsics.k[i] =
          preferences.getFloat("intrinsics.k" + Integer.toString(i), 0);
    return intrinsics;
  }

  public static float getNewImageFocalLength(SharedPreferences preferences,
                                             int width, int height) {
    if (!preferences.contains("intrinsics.fisheye"))
      return -1;
    int w = preferences.getInt("intrinsics.width", 0);
    int h = preferences.getInt("intrinsics.height", 0);
    float newF = preferences.getFloat("intrinsics.newF", -1);
    if (newF < 0) // not exists
      newF = (preferences.getFloat("intrinsics.fx", 0) +
              preferences.getFloat("intrinsics.fy", 0)) /
             2;
    return (float)Math.sqrt((double)width * height / (double)(w * h)) * newF;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.preference);
    getPreferenceScreen().setOnPreferenceClickListener(this);
    findPreference("settings_intrinsics").setOnPreferenceClickListener(this);
  }

  public static SettingsFragment newInstance() {
    return new SettingsFragment();
  }

  @Override
  public boolean onPreferenceClick(Preference preference) {
    if (preference.getKey().equals("settings_intrinsics")) {
      Log.d(LOG_TAG, "settings_intrinsics clicked");
      // choose a json file, which specifies the camera intrinsics
      Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
      intent.addCategory(Intent.CATEGORY_OPENABLE);
      intent.setType("*/*");
      if (intent.resolveActivity(getActivity().getPackageManager()) == null) {
        Toast.makeText(getActivity(), "Please install file manager app first",
                       Toast.LENGTH_LONG)
            .show();
        return false;
      }
      startActivityForResult(intent, REQUEST_CHOOSE_FILE);
      return true;
    }
    return false;
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == REQUEST_CHOOSE_FILE &&
        resultCode == Activity.RESULT_OK) {
      // open the file and parse
      Log.d(LOG_TAG, "user choose" + data.getData().toString());
      ParcelFileDescriptor.AutoCloseInputStream inputStream = null;
      try {
        inputStream = new ParcelFileDescriptor.AutoCloseInputStream(
            getActivity().getContentResolver().openFileDescriptor(
                data.getData(), "r"));

        BufferedReader streamReader =
            new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
        StringBuilder responseStrBuilder = new StringBuilder();
        String inputStr;
        while ((inputStr = streamReader.readLine()) != null)
          responseStrBuilder.append(inputStr);

        SharedPreferences preferences =
            PreferenceManager.getDefaultSharedPreferences(getActivity());
        SharedPreferences.Editor editor = preferences.edit();
        boolean success = true;
        try {
          JSONObject configJson = new JSONObject(responseStrBuilder.toString());
          editor.putInt("intrinsics.width", configJson.getInt("width"));
          editor.putInt("intrinsics.height", configJson.getInt("height"));
          editor.putBoolean("intrinsics.fisheye",
                            configJson.getBoolean("fisheye"));
          editor.putFloat("intrinsics.fx", (float)configJson.getDouble("fx"));
          editor.putFloat("intrinsics.fy", (float)configJson.getDouble("fy"));
          editor.putFloat("intrinsics.px", (float)configJson.getDouble("px"));
          editor.putFloat("intrinsics.py", (float)configJson.getDouble("py"));
          editor.putFloat("intrinsics.skew",
                          (float)configJson.getDouble("skew"));
          JSONArray k = configJson.getJSONArray("k");
          for (int i = 0; i < k.length(); ++i)
            editor.putFloat("intrinsics.k" + Integer.toString(i),
                            (float)k.getDouble(i));
          try {
            editor.putFloat("intrinsics.newF",
                            (float)configJson.getDouble("newF"));
          } catch (JSONException e) {
          }
        } catch (JSONException e) {
          success = false;
          makeToast("Error on parsing JSON file");
        }
        if (success)
          editor.apply();

      } catch (FileNotFoundException e) {
        makeToast("Can not open " + data.getData().toString());
      } catch (UnsupportedEncodingException e) {
        makeToast("Unsupported Encoding");
      } catch (IOException e) {
        makeToast("Error on reading file");
      } finally {
        if (null != inputStream) {
          try {
            inputStream.close();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
      NativeVSLAM.CameraIntrinsics intrinsics = getCameraIntrinsics(
          PreferenceManager.getDefaultSharedPreferences(getActivity()), 640,
          480);
      Log.d(LOG_TAG, intrinsics.toString());
    }
  }

  private void makeToast(String info) {
    Toast.makeText(getActivity(), info, Toast.LENGTH_LONG).show();
  }

  private void add(String key, int val) {
    SharedPreferences preferences =
        PreferenceManager.getDefaultSharedPreferences(getActivity());
    SharedPreferences.Editor editor = preferences.edit();
  }
}
