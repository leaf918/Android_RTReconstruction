#pragma version(1)
#pragma rs java_package_name(net.zjucvg.rtreconstruction)
#pragma rs_fp_relaxed // this line is *VERY* import, remove will cause weird
                      // result

int yRowStride;
int uvPixelStride, uvRowStride;

rs_allocation yIn, uIn, vIn;

uchar4 __attribute__((kernel)) convert(uint32_t x, uint32_t y)
{
    int uvIdx = uvPixelStride * (x / 2) + uvRowStride * (y / 2);
    uchar y_ = rsGetElementAt_uchar(yIn, x, y);
    uchar u_ = rsGetElementAt_uchar(uIn, uvIdx);
    uchar v_ = rsGetElementAt_uchar(vIn, uvIdx);
    return rsYuvToRGBA_uchar4(y_, u_, v_);
}
