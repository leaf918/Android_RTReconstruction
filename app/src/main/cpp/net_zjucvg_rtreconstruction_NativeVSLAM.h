/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class net_zjucvg_rtreconstruction_NativeVSLAM */

#ifndef _Included_net_zjucvg_rtreconstruction_NativeVSLAM
#define _Included_net_zjucvg_rtreconstruction_NativeVSLAM
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     net_zjucvg_rtreconstruction_NativeVSLAM
 * Method:    get_instance
 * Signature: (Lnet/zjucvg/rtreconstruction/NativeVSLAM/VSLAMConfig;)J
 */
JNIEXPORT jlong JNICALL Java_net_zjucvg_rtreconstruction_NativeVSLAM_get_1instance
  (JNIEnv *, jobject, jobject);

/*
 * Class:     net_zjucvg_rtreconstruction_NativeVSLAM
 * Method:    destroy_instance
 * Signature: (J)V
 */
JNIEXPORT void JNICALL Java_net_zjucvg_rtreconstruction_NativeVSLAM_destroy_1instance
  (JNIEnv *, jobject, jlong);

/*
 * Class:     net_zjucvg_rtreconstruction_NativeVSLAM
 * Method:    process_frame
 * Signature: (J[B)[B
 */
JNIEXPORT jbyteArray JNICALL Java_net_zjucvg_rtreconstruction_NativeVSLAM_process_1frame
  (JNIEnv *, jobject, jlong, jbyteArray);

#ifdef __cplusplus
}
#endif
#endif
