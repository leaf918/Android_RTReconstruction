package net.zjucvg.rtreconstruction;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Created by lichen on 16-5-31.
 */

/**
 * A class designed for reading IMU data, the class is thread safe
 */
public class IMUReader implements SensorEventListener, AutoCloseable {

  private static final String LOG_TAG = "IMUReader";

  /**
   * A simple class contains value and timestamp pair
   */
  public static class ValueTimePair {
    public ValueTimePair() {}

    public ValueTimePair(float[] val, long time) {
      values = val;
      timestamp = time;
    }

    public float[] values;
    /**
     * timestamp in nanoseconds
     */
    public long timestamp;
  }

  private Activity mContext;
  private Semaphore mLock = new Semaphore(1);
  private ArrayList<ValueTimePair> mLinearAccelerations = new ArrayList<>();
  private ArrayList<ValueTimePair> mGyroscopes = new ArrayList<>();
  private ArrayList<ValueTimePair> mRotationVectors = new ArrayList<>();
  private ArrayList<ValueTimePair> mGravitys = new ArrayList<>();

  private Sensor mLinearAccelerationSensor;
  private Sensor mGyroscopeSensor;
  private Sensor mRotationVectorSensor;
  private Sensor mGravitySensor;

  private void logSystemTime() {
    Log.d(LOG_TAG, "System.currentTimeMillis " + System.currentTimeMillis());
    Log.d(LOG_TAG, "SystemClock.uptimeMills " + SystemClock.uptimeMillis());
    Log.d(LOG_TAG,
          "SystemClock.elapsedRealtime " + SystemClock.elapsedRealtime());
  }

  public IMUReader(Activity context) { mContext = context; }

  private Activity getContext() { return mContext; }

  /*
   * Start recording IMU data for further retrieve
   */
  public void start() {
    mLinearAccelerations.clear();
    mGyroscopes.clear();
    mRotationVectors.clear();
    mGravitys.clear();

    // make sure at least one value exists, and we'll not get a invalid one
    ValueTimePair rot = new ValueTimePair();
    rot.values = new float[] {0, 0, 0, 1};
    rot.timestamp = 0;
    mRotationVectors.add(rot);

    ValueTimePair g = new ValueTimePair();
    g.values = new float[] {0, 0, 9.8f};
    g.timestamp = 0;
    mGravitys.add(g);

    RegisterListener();
  }

  /**
   * Stop recording
   */
  public void stop() { UnregisterListener(); }

  @Override
  public void close() throws Exception {
    stop();
  }

  @Override
  public void onSensorChanged(SensorEvent event) {

    float[] values = event.values;
    long timestamp = event.timestamp;
    Sensor sensor = event.sensor;

    ValueTimePair valueTimePair = new ValueTimePair(values, timestamp);
    try {
      mLock.acquire();
      if (sensor == mLinearAccelerationSensor) {
        // Log.d(LOG_TAG, "linear acceleration");
        mLinearAccelerations.add(valueTimePair);
      } else if (sensor == mGyroscopeSensor) {
        // Log.d(LOG_TAG, "gyroscope");
        mGyroscopes.add(valueTimePair);
      } else if (sensor == mRotationVectorSensor) {
        mRotationVectors.add(valueTimePair);
        // Log.d(LOG_TAG, "rotation vector");
      } else if (sensor == mGravitySensor) {
        mGravitys.add(valueTimePair);
        // Log.d(LOG_TAG, "gravity");
      }
    } catch (InterruptedException e) {
      throw new RuntimeException("Can not acquire IMU lock");
    } finally {
      mLock.release();
    }

    // Log.d(LOG_TAG, "onSensorChanges timestamp " + timestamp);
    // logSystemTime();
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {}

  private void RegisterListener() {
    SensorManager manager =
        (SensorManager)getContext().getSystemService(Context.SENSOR_SERVICE);

    mLinearAccelerationSensor =
        manager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
    if (null == mLinearAccelerationSensor) {
      Log.e(LOG_TAG, "Do not support linear acceleration sensor");
      getContext().finish();
    }

    mGyroscopeSensor = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    if (null == mGyroscopeSensor) {
      Log.e(LOG_TAG, "Do not support gyroscope sensor");
      getContext().finish();
    }

    mRotationVectorSensor =
        manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    if (null == mRotationVectorSensor) {
      Log.e(LOG_TAG, "Do not support rotation vector sensor");
      getContext().finish();
    }

    mGravitySensor = manager.getDefaultSensor(Sensor.TYPE_GRAVITY);
    if (null == mGravitySensor) {
      Log.e(LOG_TAG, "Do not support gravity sensor");
      getContext().finish();
    }

    manager.registerListener(this, mLinearAccelerationSensor,
                             SensorManager.SENSOR_DELAY_FASTEST);
    manager.registerListener(this, mGyroscopeSensor,
                             SensorManager.SENSOR_DELAY_FASTEST);
    manager.registerListener(this, mRotationVectorSensor,
                             SensorManager.SENSOR_DELAY_FASTEST);
    manager.registerListener(this, mGravitySensor,
                             SensorManager.SENSOR_DELAY_FASTEST);
  }

  private void UnregisterListener() {
    SensorManager manager =
        (SensorManager)getContext().getSystemService(Context.SENSOR_SERVICE);
    manager.unregisterListener(this);
  }

  /**
   * Get all linear accelerations before @param time, the retrieved data will be
   * removed internally
   *
   * @param time time in nanoseconds
   *
   * @return All linear accelerations before
   */
  public List<ValueTimePair> getAllLinearAccelerations(long time) {
    try {
      mLock.acquire();
      return getAndRemoveAllBefore(mLinearAccelerations, time);
    } catch (InterruptedException e) {
      throw new RuntimeException("Can not acquire IMU lock");
    } finally {
      mLock.release();
    }
  }

  /**
   * Get all gyroscopes before @param time, the retrieved data will be  removed
   * internally
   *
   * @param time time in nanoseconds
   *
   * @return All gyroscopes before
   */
  public List<ValueTimePair> getAllGyroscopes(long time) {
    try {
      mLock.acquire();
      return getAndRemoveAllBefore(mGyroscopes, time);
    } catch (InterruptedException e) {
      throw new RuntimeException("Can not acquire IMU lock");
    } finally {
      mLock.release();
    }
  }

  /**
   * Get last recorded rotation vector before @param time, the data will not be
   * removed
   *
   * @param time time in nanoseconds
   *
   * @return last recorded rotation vector, if has not recorded any data,
   * returns null
   */
  public ValueTimePair getLastRotationVector(long time) {
    try {
      mLock.acquire();
      List<ValueTimePair> allBefore = getAllBefore(mRotationVectors, time);
      // and remove several
      for (int i = 0; i + 1 < allBefore.size(); ++i)
        mRotationVectors.remove(0);
      if (!allBefore.isEmpty())
        return allBefore.get(allBefore.size() - 1);
      else
        return null;
    } catch (InterruptedException e) {
      throw new RuntimeException("Can not acquire IMU lock");
    } finally {
      mLock.release();
    }
  }

  /**
   * Get last gravity before @param time, the data will not be removed
   *
   * @param time time in nanoseconds
   *
   * @return last recorded gravity, if has not recorded any data, return null
   */
  public ValueTimePair getLastGravity(long time) {
    try {
      mLock.acquire();
      List<ValueTimePair> allBefore = getAllBefore(mGravitys, time);
      for (int i = 0; i + 1 < allBefore.size(); ++i)
        mGravitys.remove(0);
      if (!allBefore.isEmpty())
        return allBefore.get(allBefore.size() - 1);
      else
        return null;
    } catch (InterruptedException e) {
      throw new RuntimeException("Can not acquire IMU lock");
    } finally {
      mLock.release();
    }
  }

  /**
   * Get all linear accleration, gyroscopes, last rotation vector and gravity
   * before @param time
   *
   * @param time time in nanoseconds
   */
  public void get(List<ValueTimePair> linearAccelerations,
                  List<ValueTimePair> gyroscopes, ValueTimePair rotationVector,
                  ValueTimePair gravity, long time) {
    linearAccelerations.clear();
    gyroscopes.clear();
    rotationVector.values = null;
    rotationVector.timestamp = -1;
    gravity.values = null;
    gravity.timestamp = -1;
    try {
      mLock.acquire();
      linearAccelerations.addAll(
          getAndRemoveAllBefore(mLinearAccelerations, time));
      gyroscopes.addAll(getAndRemoveAllBefore(mGyroscopes, time));

      List<ValueTimePair> allBeforeRotationVector =
          getAllBefore(mRotationVectors, time);
      for (int i = 0; i + 1 < allBeforeRotationVector.size(); ++i)
        mRotationVectors.remove(0);
      ValueTimePair last =
          allBeforeRotationVector.get(allBeforeRotationVector.size() - 1);
      rotationVector.values = last.values;
      rotationVector.timestamp = last.timestamp;
      List<ValueTimePair> allBeforeGravity = getAllBefore(mGravitys, time);
      for (int i = 0; i + 1 < allBeforeGravity.size(); ++i)
        mGravitys.remove(0);
      last = allBeforeGravity.get(allBeforeGravity.size() - 1);
      gravity.values = last.values;
      gravity.timestamp = last.timestamp;
    } catch (InterruptedException e) {
      throw new RuntimeException("Can not acquire IMU lock");
    } finally {
      mLock.release();
    }
  }

  /**
   * Align linear accelerations and gyroscopes such that the the two list have
   * same length, and the timestamp at same index will be identical
   */
  public static void mergeIMUs(List<ValueTimePair> linearAccelerations,
                               List<ValueTimePair> gyroscopes) {
    int diff = linearAccelerations.size() - gyroscopes.size();
    // Log.d(LOG_TAG, "linearAccelerations.size() - gyroscopes.size(): " +
    // diff);
    if (linearAccelerations.isEmpty()) {
      gyroscopes.clear();
      return;
    }
    if (gyroscopes.isEmpty()) {
      linearAccelerations.clear();
      return;
    }

    if (diff > 0) // this should be rare
    {
      // drop head or tail ?
      long headDiff = linearAccelerations.get(diff - 1).timestamp -
                      gyroscopes.get(0).timestamp;
      long tailDiff =
          linearAccelerations.get(linearAccelerations.size() - 1 - diff)
              .timestamp -
          gyroscopes.get(gyroscopes.size() - 1).timestamp;

      if (Math.abs(headDiff) > Math.abs(tailDiff))
        for (int i = 0; i < diff; ++i)
          linearAccelerations.remove(0);
      else
        for (int i = 0; i < diff; ++i)
          linearAccelerations.remove(linearAccelerations.size() - 1);
    } else if (diff < 0) {
      diff = -diff;
      // drop head or tail ?
      long headDiff = gyroscopes.get(diff - 1).timestamp -
                      linearAccelerations.get(0).timestamp;
      long tailDiff =
          gyroscopes.get(gyroscopes.size() - 1 - diff).timestamp -
          linearAccelerations.get(linearAccelerations.size() - 1).timestamp;

      if (Math.abs(headDiff) > Math.abs(tailDiff))
        for (int i = 0; i < diff; ++i)
          gyroscopes.remove(0);
      else
        for (int i = 0; i < diff; ++i)
          gyroscopes.remove(linearAccelerations.size() - 1);
    }

    for (int idx = 0; idx < linearAccelerations.size(); ++idx)
      linearAccelerations.get(idx).timestamp = gyroscopes.get(idx).timestamp =
          (linearAccelerations.get(idx).timestamp +
           gyroscopes.get(idx).timestamp) /
          2;
  }

  private static List<ValueTimePair>
  getAndRemoveAllBefore(List<ValueTimePair> lst, long time) {
    List<ValueTimePair> ret = new ArrayList<>();
    while (!lst.isEmpty() && lst.get(0).timestamp <= time) {
      ret.add(lst.get(0));
      lst.remove(0);
    }
    return ret;
  }

  private static List<ValueTimePair> getAllBefore(List<ValueTimePair> lst,
                                                  long time) {
    List<ValueTimePair> ret = new ArrayList<>();
    for (int i = 0; i < lst.size() && lst.get(i).timestamp <= time; ++i)
      ret.add(lst.get(i));
    return ret;
  }
}
