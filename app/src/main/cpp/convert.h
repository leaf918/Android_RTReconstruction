#ifndef _CONVERT_H_
#define _CONVERT_H_
#include <jni.h>
#include "VSLAMBase.h"

// convert NativeVSLAM$Size to vslam::Size
void javaSize2cppSize(JNIEnv *env, jobject javaSize, vslam::Size &cppSize);

// convert NativeVSLAM$CameraIntrinsics to vslam::Intrinsics
void javaCameraIntrinsics2cppCameraIntrinsics(
        JNIEnv *env, jobject javaIntrinsics,
        vslam::CameraIntrinsics &cppIntrinsics);

// convert NativeVSLAM$VSLAMConfig to vslam::VSLAMConfig
void javaVSLAMConfig2cppVSLAMConfig(JNIEnv *env, jobject javaVSLAMConfig,
                                    vslam::VSLAMConfig &cppVSLAMConfig);

// unpack java byte[] frame to vslam::Frame, the format is:
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
void unpackNativeFrame(JNIEnv *env, jobject javaFrame, vslam::Frame &cppFrame);

// pack vslam::TrackingResult to java byte[], format
// state [1 byte]
//   0 -- DETECTING, 1 -- DETECTED, 2 -- SUCCESS, 3 -- FAIL
// if state == DETECTED:
//   corners [4 * 2 * FLOAT_SIZE bytes]
// if state == SUCCESS:
//   camera.rotationVector [4 * FLOAT_SIZE bytes]
//   camera.position [3 * FLOAT_SIZE bytes]
// Len [4 byte, integer]
//   a byte array of length Len
jbyteArray packNativeResult(JNIEnv *env,
                            const vslam::TrackingResult &cppResult);

#endif
