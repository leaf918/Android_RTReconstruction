#pragma version(1)
#pragma rs java_package_name(net.zjucvg.rtreconstruction)
#pragma rs_fp_relaxed // this line is *VERY* import, remove will cause weird
                      // result

uchar4 __attribute__((kernel)) convert(uchar g)
{
    return (uchar4){g, g, g, 255};
}
