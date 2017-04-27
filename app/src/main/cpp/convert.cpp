#include <algorithm>
#include <cassert>
#include <android/log.h>
#define LOGI(...)                                                              \
    ((void)__android_log_print(ANDROID_LOG_DEBUG, "convert", __VA_ARGS__))
#define ECHO LOGI("file: %s, line: %d", __FILE__, __LINE__);

#include "convert.h"
// the mapping between two class is just one-to-one, with same name
void javaSize2cppSize(JNIEnv *env, jobject javaSize, vslam::Size &cppSize)
{
    jclass cls = env->GetObjectClass(javaSize);
    jfieldID width = env->GetFieldID(cls, "width", "I");
    jfieldID height = env->GetFieldID(cls, "height", "I");
    cppSize.width = env->GetIntField(javaSize, width);
    cppSize.height = env->GetIntField(javaSize, height);
}

// the mapping between two class is just one-to-one, with same name
void javaCameraIntrinsics2cppCameraIntrinsics(
        JNIEnv *env, jobject javaIntrinsics, vslam::CameraIntrinsics &cppIntrinsics)
{
    jclass cls = env->GetObjectClass(javaIntrinsics);
    jfieldID fisheye = env->GetFieldID(cls, "fisheye", "Z");
    jfieldID fx = env->GetFieldID(cls, "fx", "F");
    jfieldID fy = env->GetFieldID(cls, "fy", "F");
    jfieldID px = env->GetFieldID(cls, "px", "F");
    jfieldID py = env->GetFieldID(cls, "py", "F");
    jfieldID skew = env->GetFieldID(cls, "skew", "F");
    jfieldID k = env->GetFieldID(cls, "k", "[F");

    cppIntrinsics.fisheye = env->GetBooleanField(javaIntrinsics, fisheye);
    cppIntrinsics.fx = env->GetFloatField(javaIntrinsics, fx);
    cppIntrinsics.fy = env->GetFloatField(javaIntrinsics, fy);
    cppIntrinsics.px = env->GetFloatField(javaIntrinsics, px);
    cppIntrinsics.py = env->GetFloatField(javaIntrinsics, py);
    cppIntrinsics.skew = env->GetFloatField(javaIntrinsics, skew);

    jobject arrObj = env->GetObjectField(javaIntrinsics, k);
    jfloatArray *kArr = (jfloatArray *)&arrObj;
    jsize len = env->GetArrayLength(*kArr);
    assert(len == 5);
    jfloat *arr = env->GetFloatArrayElements(*kArr, 0);
    std::copy(arr, arr + 5, cppIntrinsics.k);
    env->ReleaseFloatArrayElements(*kArr, arr, 0);
}

// the mapping between two class is just one-to-one, with same name
void javaVSLAMConfig2cppVSLAMConfig(JNIEnv *env, jobject javaVSLAMConfig,
                                    vslam::VSLAMConfig &cppVSLAMConfig)
{
    jclass cls = env->GetObjectClass(javaVSLAMConfig);
    jfieldID intrinsics =
            env->GetFieldID(cls, "intrinsics",
                            "Lnet/zjucvg/rtreconstruction/NativeVSLAM$CameraIntrinsics;");
    jfieldID size =
            env->GetFieldID(cls, "size", "Lnet/zjucvg/rtreconstruction/NativeVSLAM$Size;");
    jfieldID newImgF = env->GetFieldID(cls, "newImgF", "F");
    jfieldID px = env->GetFieldID(cls, "px", "F");
    jfieldID py = env->GetFieldID(cls, "py", "F");
    jfieldID initDist = env->GetFieldID(cls, "initDist", "F");

    jfieldID useMarker = env->GetFieldID(cls, "useMarker", "Z");
    jfieldID markerLength = env->GetFieldID(cls, "markerLength", "F");
    jfieldID markerWidth = env->GetFieldID(cls, "markerWidth", "F");
    jfieldID parallel = env->GetFieldID(cls, "parallel", "Z");
    jfieldID logLevel = env->GetFieldID(cls, "logLevel", "I");
    jfieldID logFilePath =
            env->GetFieldID(cls, "logFilePath", "Ljava/lang/String;");

    jobject javaIntrinsics = env->GetObjectField(javaVSLAMConfig, intrinsics);
    javaCameraIntrinsics2cppCameraIntrinsics(env, javaIntrinsics,
                                             cppVSLAMConfig.intrinsics);
    jobject javaSize = env->GetObjectField(javaVSLAMConfig, size);
    javaSize2cppSize(env, javaSize, cppVSLAMConfig.size);
    cppVSLAMConfig.newImgF = env->GetFloatField(javaVSLAMConfig, newImgF);
    cppVSLAMConfig.px = env->GetFloatField(javaVSLAMConfig, px);
    cppVSLAMConfig.py = env->GetFloatField(javaVSLAMConfig, py);
    cppVSLAMConfig.initDist = env->GetFloatField(javaVSLAMConfig, initDist);

    cppVSLAMConfig.useMarker = env->GetBooleanField(javaVSLAMConfig, useMarker);
    cppVSLAMConfig.markerLength =
            env->GetFloatField(javaVSLAMConfig, markerLength);
    cppVSLAMConfig.markerWidth =
            env->GetFloatField(javaVSLAMConfig, markerWidth);
    cppVSLAMConfig.parallel = env->GetBooleanField(javaVSLAMConfig, parallel);
    cppVSLAMConfig.logLevel = env->GetIntField(javaVSLAMConfig, logLevel);
    jobject strObj = env->GetObjectField(javaVSLAMConfig, logFilePath);
    jstring *jstr = (jstring *)&strObj;
    const char *str = env->GetStringUTFChars(*jstr, 0);
    cppVSLAMConfig.logFilePath = std::string(str);
    env->ReleaseStringUTFChars(*jstr, str);

    // show what I read
//    LOGI("intrinsics.fisheye: %i", (int)cppVSLAMConfig.intrinsics.fisheye);
//    LOGI("intrinsics.fx: %f", cppVSLAMConfig.intrinsics.fx);
//    LOGI("intrinsics.fy: %f", cppVSLAMConfig.intrinsics.fy);
//    LOGI("intrinsics.px: %f", cppVSLAMConfig.intrinsics.px);
//    LOGI("intrinsics.py: %f", cppVSLAMConfig.intrinsics.py);
//    LOGI("intrinsics.skew: %f", cppVSLAMConfig.intrinsics.skew);
//    for (int i = 0; i < 5; ++i)
//        LOGI("intrinsics.k[%i]: %f", i, cppVSLAMConfig.intrinsics.k[i]);
//    LOGI("size.width: %i", cppVSLAMConfig.size.width);
//    LOGI("size.height: %i", cppVSLAMConfig.size.height);
//    LOGI("newImgF: %f", cppVSLAMConfig.newImgF);
//
//    LOGI("useMarker: %i", (int)cppVSLAMConfig.useMarker);
//    LOGI("markerLength: %f", cppVSLAMConfig.markerLength);
//    LOGI("markerWidth: %f", cppVSLAMConfig.markerWidth);
//    LOGI("parallel: %i", (int)cppVSLAMConfig.parallel);
//    LOGI("logLevel: %i", cppVSLAMConfig.logLevel);
//    LOGI("logFilePath: %s", cppVSLAMConfig.logFilePath.c_str());
}

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
void unpackNativeFrame(JNIEnv *env, jobject javaFrame, vslam::Frame &cppFrame)
{
    jbyteArray *byteArray = (jbyteArray *)&javaFrame;
    int len = env->GetArrayLength(*byteArray);
    LOGI("packed frame length: %d", len);
    jbyte *bytes = env->GetByteArrayElements(*byteArray, 0);

    unsigned char *vals = (unsigned char *)bytes;
#define COPY_N(src, dst, n)                                                    \
    do {                                                                       \
        std::copy(src, src + n, (unsigned char *)dst);                         \
        src += n;                                                              \
    } while (0)
#define COPY_INT(src, dst) COPY_N(src, dst, 4)
#define COPY_DOUBLE(src, dst) COPY_N(src, dst, 8)
    // image
    int width, height;
    COPY_INT(vals, &width);
    COPY_INT(vals, &height);
    COPY_DOUBLE(vals, &cppFrame.image.timestamp);
    cppFrame.image.data = new unsigned char[width * height];
    COPY_N(vals, cppFrame.image.data, width * height);
    // IMUs
    int dbl_imu_cnt;
    COPY_INT(vals, &dbl_imu_cnt);
    assert(dbl_imu_cnt % 2 == 0);
    cppFrame.imus.resize(dbl_imu_cnt / 2);
    for (int i = 0; i < dbl_imu_cnt / 2; ++i) {
#ifndef NDEBUG
        int type;
#endif
#ifndef NDEBUG
        COPY_INT(vals, &type);
        assert(type == 0); // linearAcceleration
#else
        vals += 4; // skip type
#endif
        COPY_N(vals, cppFrame.imus[i].linearAcceleration, 3 * 8);
        vals += 8; // skip timestamp

#ifndef NDEBUG
        COPY_INT(vals, &type);
        assert(type == 1); // gyroscope
#else
        vals += 4; // skip type
#endif
        COPY_N(vals, cppFrame.imus[i].gyroscope, 3 * 8);
        COPY_DOUBLE(vals, &cppFrame.imus[i].timestamp);
    }
    // attitude
    COPY_N(vals, cppFrame.attitude.rotationVector, 4 * 8);
    COPY_N(vals, cppFrame.attitude.gravity, 3 * 8);
    COPY_DOUBLE(vals, &cppFrame.attitude.timestamp);

    // log what I read
    LOGI("frame.image.width: %d", width);
    LOGI("frame.image.height: %d", height);
    LOGI("frame.image.timestamp: %f", cppFrame.image.timestamp);
    LOGI("frame.imus.size: %d", cppFrame.imus.size());
    for (int i = 0; i < cppFrame.imus.size(); ++i) {
        LOGI("frame.imus[%d]:linearAcceleration-[%f, %f, %f], "
                     "gyroscope-[%f, %f, %f], timestamp-%f",
             i, cppFrame.imus[i].linearAcceleration[0],
             cppFrame.imus[i].linearAcceleration[1],
             cppFrame.imus[i].linearAcceleration[2],
             cppFrame.imus[i].gyroscope[0], cppFrame.imus[i].gyroscope[1],
             cppFrame.imus[i].gyroscope[2], cppFrame.imus[i].timestamp);
    }
    LOGI("frame.attitude:rotationVector-[%f, %f, %f, %f], gravity-[%f, %f, "
                 "%f], timestamp-%f",
         cppFrame.attitude.rotationVector[0],
         cppFrame.attitude.rotationVector[1],
         cppFrame.attitude.rotationVector[2],
         cppFrame.attitude.rotationVector[3], cppFrame.attitude.gravity[0],
         cppFrame.attitude.gravity[1], cppFrame.attitude.gravity[2],
         cppFrame.attitude.timestamp);

    env->ReleaseByteArrayElements(*byteArray, bytes, 0);
#undef COPY_DOUBLE
#undef COPY_INT
#undef COPY_N
}

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
jbyteArray packNativeResult(JNIEnv *env, const vslam::TrackingResult &cppResult)
{
    static const int FLOAT_SIZE = 4;
    static const int INT_SIZE = 4;

    jbyte state;
    switch (cppResult.state) {
        case vslam::SLAM_MARKER_DETECTING: state = 0; break;
        case vslam::SLAM_MARKER_DETECTED: state = 1; break;
        case vslam::SLAM_TRACKING_SUCCESS: state = 2; break;
        case vslam::SLAM_TRACKING_FAIL: state = 3; break;
        default: assert(0); break;
    }

    int totalLen = 0; // total byte array size required
    totalLen += 1;    // state
    if (cppResult.state == vslam::SLAM_MARKER_DETECTED)
        totalLen += 4 /* corners */ * 2 /* x, y */ * FLOAT_SIZE;
    else if (cppResult.state == vslam::SLAM_TRACKING_SUCCESS)
        totalLen += (4 /* rotation vector */ + 3 /* position */) * FLOAT_SIZE;

    totalLen += INT_SIZE + cppResult.info.size();

    jbyteArray packedResult = env->NewByteArray(totalLen);

    jbyte *bytes = env->GetByteArrayElements(packedResult, 0);
    jbyte *vals = bytes;

#define PUT_N(src, dst, n)                                                     \
    do {                                                                       \
        std::copy((jbyte *)src, (jbyte *)src + n, dst);                        \
        dst += n;                                                              \
    } while (0)
#define PUT_FLOAT(src, dst) PUT_N(src, dst, 4)
#define PUT_INT(src, dst) PUT_N(src, dst, 4)

    // state
    PUT_N(&state, vals, 1);

    // corners or camera pose
    if (cppResult.state == vslam::SLAM_MARKER_DETECTED) {
        // set corners
        for (int i = 0; i < 4; ++i) {
            PUT_FLOAT(&cppResult.corners[i].x, vals);
            PUT_FLOAT(&cppResult.corners[i].y, vals);
        }
    } else if (cppResult.state == vslam::SLAM_TRACKING_SUCCESS) {
        // set camera
        PUT_N(cppResult.camera.rotationVector, vals, 4 * FLOAT_SIZE);
        PUT_N(cppResult.camera.position, vals, 3 * FLOAT_SIZE);
    }

    // info
    int infoLen = cppResult.info.size();
    PUT_INT(&infoLen, vals);
    PUT_N(cppResult.info.c_str(), vals, infoLen);

    env->ReleaseByteArrayElements(packedResult, bytes, JNI_COMMIT);

    return packedResult;
#undef PUT_FLOAT
#undef PUT_N
}
