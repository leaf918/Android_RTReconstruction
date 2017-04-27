#include "net_zjucvg_rtreconstruction_OpenCLUtility.h"

#ifdef __cplusplus
extern "C" {
#endif

static unsigned char *ptrImgData = nullptr;

JNIEXPORT jbyteArray JNICALL Java_net_zjucvg_rtreconstruction_OpenCLUtility_gray2Bitmap
        (JNIEnv *env, jclass self, jbyteArray gray, jint width, jint height)
{
    ptrImgData = (unsigned char *)env->GetByteArrayElements(gray, JNI_FALSE);
    unsigned int * ptrConvertedData=new unsigned int[width*height];

    jbyteArray result = env->NewByteArray(width*height);

    unsigned int tmp=0;
    for(int i=0;i<height*width;i++){
        tmp|=255;
        tmp<<=8;
        tmp|=ptrImgData[i];
        tmp<<=8;
        tmp|=ptrImgData[i];
        tmp<<=8;
        tmp|=ptrImgData[i];
        ptrConvertedData[i]=tmp;
    }

    env->SetByteArrayRegion(result, 0, width*height, (jbyte *)ptrConvertedData);
    return result;
}

#ifdef __cplusplus
}
#endif
