#include "net_zjucvg_rtreconstruction_NativeVSLAM.h"
#include "VSLAMBase.h"
#include "convert.h"

#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     net_zjucvg_rtreconstruction_NativeVSLAM
 * Method:    get_instance
 * Signature: (Lnet/zjucvg/rtreconstruction/NativeVSLAM/VSLAMConfig;)J
 */
JNIEXPORT jlong JNICALL Java_net_zjucvg_rtreconstruction_NativeVSLAM_get_1instance
  (JNIEnv *env, jobject self, jobject config)
{
    vslam::VSLAMConfig cppConfig;
    javaVSLAMConfig2cppVSLAMConfig(env, config, cppConfig);
    vslam::VSLAMBase *ptr = vslam::VSLAMBase::GetInstance(cppConfig);
    return *(jlong *)&ptr;
}

/*
 * Class:     net_zjucvg_rtreconstruction_NativeVSLAM
 * Method:    destroy_instance
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_net_zjucvg_rtreconstruction_NativeVSLAM_destroy_1instance
  (JNIEnv *env, jobject self, jlong ptr)
{
    vslam::VSLAMBase *vslam = *(vslam::VSLAMBase **)&ptr;
    delete vslam;
}

/*
 * Class:     net_zjucvg_rtreconstruction_NativeVSLAM
 * Method:    process_frame
 * Signature: (J[B)[B
 */
JNIEXPORT jbyteArray JNICALL Java_net_zjucvg_rtreconstruction_NativeVSLAM_process_1frame
  (JNIEnv *env, jobject self, jlong ptr, jbyteArray frame)
{
    vslam::VSLAMBase *vslam = *(vslam::VSLAMBase **)&ptr;
    vslam::Frame cppFrame;
    unpackNativeFrame(env, frame, cppFrame);
    vslam::TrackingResult cppResult = vslam->ProcessFrame(cppFrame);
    delete[] cppFrame.image.data;
    return packNativeResult(env, cppResult);
}

#ifdef __cplusplus
}
#endif
