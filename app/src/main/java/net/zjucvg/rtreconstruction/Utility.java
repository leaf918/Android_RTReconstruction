package net.zjucvg.rtreconstruction;

/**
 * Created by lichen on 16-6-20.
 */
public class Utility {

  public static float[] world2Image(float[] point, NativeVSLAM.Camera camera,
                                    NativeVSLAM.CameraIntrinsics intrinsics) {
    return camera2Image(world2Camera(point, camera), intrinsics);
  }

  public static float[] world2Camera(float[] point, NativeVSLAM.Camera camera) {
    for (int i = 0; i < 3; ++i)
      point[i] -= camera.position[i];
    float[] mat = transpose(quaternion2Matrix(camera.rotationVector));
    float[] result = new float[] {0, 0, 0};
    for (int i = 0; i < 3; ++i) {
      for (int j = 0; j < 3; ++j)
        result[i] += mat[i * 3 + j] * point[j];
    }
    return result;
  }

  public static float[] quaternion2Matrix(float[] Q) {
    float[] R = new float[9];
    // ref:
    // http://www.euclideanspace.com/maths/geometry/rotations/conversions/quaternionToMatrix/
    float x = Q[0], y = Q[1], z = Q[2], w = Q[3];
    float x2 = x * x, y2 = y * y, z2 = z * z;
    float xy = x * y, xz = x * z, yz = y * z;
    float xw = x * w, yw = y * w, zw = z * w;
    R[0 * 3 + 0] = 1 - 2 * y2 - 2 * z2;
    R[0 * 3 + 1] = 2 * xy - 2 * zw;
    R[0 * 3 + 2] = 2 * xz + 2 * yw;
    R[1 * 3 + 0] = 2 * xy + 2 * zw;
    R[1 * 3 + 1] = 1 - 2 * x2 - 2 * z2;
    R[1 * 3 + 2] = 2 * yz - 2 * xw;
    R[2 * 3 + 0] = 2 * xz - 2 * yw;
    R[2 * 3 + 1] = 2 * yz + 2 * xw;
    R[2 * 3 + 2] = 1 - 2 * x2 - 2 * y2;
    return R;
  }

  public static float[] transpose(float[] mat) {
    assert mat.length == 9;
    float[] trans = new float[9];
    for (int i = 0; i < 3; ++i)
      for (int j = 0; j < 3; ++j)
        trans[i * 3 + j] = mat[j * 3 + i];
    return trans;
  }

  public static float[] camera2Image(float[] point,
                                     NativeVSLAM.CameraIntrinsics intrinsics) {
    // map to normalized plane
    float x = point[0] / point[2];
    float y = point[1] / point[2];
    // map to distorted plane
    float[] tmp = new float[] {x, y};
    // FIXME
    tmp = distortPoint(tmp, intrinsics.k, intrinsics.fisheye);
    x = tmp[0];
    y = tmp[1];
    x = x * intrinsics.fx + y * intrinsics.skew + intrinsics.px;
    y = y * intrinsics.fy + intrinsics.py;
    return new float[] {x, y};
  }

  public static float[] distortPoint(float[] point, float[] k,
                                     boolean fisheye) {
    float x = point[0], y = point[1];
    if (fisheye) {
      // ref: http://docs.opencv.org/trunk/db/d58/group__calib3d__fisheye.html
      float k1 = k[0], k2 = k[1], k3 = k[2], k4 = k[3];
      float r = (float) Math.sqrt(x * x + y * y);
      float t = (float) Math.atan(r), t2 = t * t, t4 = t2 * t2, t6 = t2 * t4,
            t8 = t4 * t4;
      float t_d = t * (1 + k1 * t2 + k2 * t4 + k3 * t6 + k4 * t8);
      x = (t_d / r) * x;
      y = (t_d / r) * y;
    } else {
      // ref:
      // http://docs.opencv.org/2.4/modules/calib3d/doc/camera_calibration_and_3d_reconstruction.html
      float k1 = k[0], k2 = k[1], p1 = k[2], p2 = k[3], k3 = k[4];
      float x2 = x * x, y2 = y * y, xy = x * y;
      float r2 = x2 * x2, r4 = r2 * r2, r6 = r2 * r4;
      // radial distortion
      float radial = 1 + k1 * r2 + k2 * r4 + k3 * r6;
      x = radial * x;
      y = radial * y;
      // tangential distortion
      x += 2 * p1 * xy + p2 * (r2 + 2 * x2);
      y += p1 * (r2 + 2 * y2) + 2 * p2 * xy;
    }
    return new float[] {x, y};
  }
}
