#include "net_zjucvg_rtreconstruction_OpenCLUtility.h"

#ifdef __cplusplus
extern "C" {
#endif

static unsigned char *ptrImgData = nullptr;

JNIEXPORT jbyteArray JNICALL Java_net_zjucvg_rtreconstruction_OpenCLUtility_gray2Bitmap
        (JNIEnv *env, jclass self, jbyteArray gray, jint width, jint height)
{
    ptrImgData = (unsigned char *)env->GetByteArrayElements(gray, JNI_FALSE);
    unsigned char * ptrConvertedData=new unsigned char[width*height*3];

    jbyteArray result = env->NewByteArray(width*height*3);

    unsigned char tmp=0;
    for(int i=0;i<height*width;i++){
        tmp=ptrImgData[i];
        ptrConvertedData[i*3]=tmp;
        ptrConvertedData[i*3+1]=tmp;
        ptrConvertedData[i*3+2]=tmp;
    }

    env->SetByteArrayRegion(result, 0, width*height*3, (jbyte *)ptrConvertedData);
    return result;
}

#ifdef __cplusplus
}
#endif
