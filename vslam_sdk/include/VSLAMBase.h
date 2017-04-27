#ifndef _VSLAMBASE_H_
#define _VSLAMBASE_H_

#include <string>
#include <vector>

namespace vslam
{
struct Size {
    int width;
    int height;
};

// simple wrapper of intensity image, row major order
struct Image {
    unsigned char *data;
    double timestamp;
};

// IMU measures, ref
// http://developer.android.com/guide/topics/sensors/sensors_motion.html
struct IMU {
    // TYPE_LINEAR_ACCELERATION
    double linearAcceleration[3];
    // TYPE_GYROSCOPE
    double gyroscope[3];
    double timestamp;
};

struct Attitude {
    // TYPE_ROTATION_VECTOR
    double rotationVector[4];
    // TYPE_GRAVITY
    double gravity[3];
    double timestamp;
};

struct Frame {
    Image image;
    // all IMU measures after last and before current frame
    std::vector<IMU> imus;
    // tha atttide *just* before this frame
    Attitude attitude;
};

struct CameraIntrinsics {
    bool fisheye;
    // focal length
    float fx, fy;
    // principal point
    float px, py;
    // skew
    float skew;
    // distortion coefficients
    float k[5];
};

struct VSLAMConfig {
    CameraIntrinsics intrinsics;
    // the image size;
    Size size;
    // focal length in the undistorted image, fx = fy = newImgF
    float newImgF;
    // displacement between camera and IMU
    float px, py;
    // initial distance between marker and camera
    float initDist;

    // use marker?
    bool useMarker;
    // length and width of the marker, measure in meters; For A4 paper,
    // markerLength = 0.297, markerWidth = 0.21
    float markerLength;
    float markerWidth;

    bool parallel;
    // log level, 0 -- quiet, 1 -- debug, 2 -- verbose
    int logLevel;
    std::string logFilePath;
};

struct Camera {
    // camera rotation vector and position in world space
    float rotationVector[4];
    float position[3];
};

// state of the VSLAM
enum State {
    SLAM_MARKER_DETECTING, // detecting the A4 paper
    SLAM_MARKER_DETECTED,  // A4 paper detected
    SLAM_TRACKING_SUCCESS,
    SLAM_TRACKING_FAIL
};

struct Point2f {
    float x, y;
};

// the tracking result
// if state = SLAM_MARKER_DETECTED, the field
struct TrackingResult {
    State state;
    union {
        Camera camera; // camera pose, valid if state = SLAM_TRACKING_SUCCESS
        Point2f corners[4]; // corners of the A4 paper, valid if state =
                            // SLAM_MARKER_DETECTED
        // otherwise, both camera and corners are undefined
    };
    // diagnostic information about the SLAM
    std::string info="nothing";
};

class VSLAMBase
{
  public:
    static VSLAMBase *GetInstance(const VSLAMConfig &config);

    virtual TrackingResult ProcessFrame(const Frame &newFrame) = 0;
    virtual ~VSLAMBase(){};
};
}

#endif
