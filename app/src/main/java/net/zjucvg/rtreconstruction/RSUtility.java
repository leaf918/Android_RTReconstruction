//package net.zjucvg.rtreconstruction;
//
///**
// * Created by kuanlu on 2017/4/13.
// */
//
//import android.content.Context;
//import android.graphics.Bitmap;
//import android.graphics.ImageFormat;
//import android.media.Image;
//import android.support.v8.renderscript.Allocation;
//import android.support.v8.renderscript.Element;
//import android.support.v8.renderscript.RenderScript;
//import android.support.v8.renderscript.Script;
//import android.support.v8.renderscript.Type;
//import android.util.Log;
//
//import java.nio.ByteBuffer;
//
///**
// * A class for easily accessing RenderScript functions
// */
//public class RSUtility {
//
//    private static final String LOG_TAG = "RSUtility";
//
//    private static RenderScript mRs;
//    private static ScriptC_yuv420_to_rgba mYUV2RGBA;
//    private static ScriptC_gray_to_rgba mGray2RGBA;
//
//    /**
//     * Set up necessary resources, must be called before accessing any other
//     * functions
//     */
//    public static void init(Context context) {
//        if (null == mRs) {
//            mRs = RenderScript.create(context);
//            mYUV2RGBA = new ScriptC_yuv420_to_rgba(mRs);
//            mGray2RGBA = new ScriptC_gray_to_rgba(mRs);
//        }
//    }
//
//    /**
//     * Convert a YUV420_888 image to RGBA bitmap
//     *
//     * @param yuv420Image image in YUV420_888 format
//     *
//     * @return RGBA bitmap
//     */
//    public static Bitmap yuv420ToBitmap(Image yuv420Image) {
//        assert yuv420Image.getFormat() == ImageFormat.YUV_420_888;
//
//        int width = yuv420Image.getWidth();
//        int height = yuv420Image.getHeight();
//
//        Image.Plane[] planes = yuv420Image.getPlanes();
//        ByteBuffer buffer;
//        // the pixel stride of Y channel is guaranteed to be 0
//        int yRowStride = planes[0].getRowStride();
//        buffer = planes[0].getBuffer();
//        buffer.rewind();
//        byte[] y = new byte[yRowStride * height];
//        buffer.get(y, 0, buffer.remaining());
//        Log.d(LOG_TAG, Integer.toString(y.length));
//
//        // the pixel and row strides of U and V channels is guaranteed to be same
//        int uvPixelStride = planes[1].getPixelStride();
//        int uvRowStride = planes[1].getRowStride();
//        buffer = planes[1].getBuffer();
//        buffer.rewind();
//        byte[] u = new byte[uvRowStride * height / 2];
//        buffer.get(u, 0, buffer.remaining());
//        Log.d(LOG_TAG, Integer.toString(u.length));
//        buffer = planes[2].getBuffer();
//        buffer.rewind();
//        byte[] v = new byte[uvRowStride * height / 2];
//        buffer.get(v, 0, buffer.remaining());
//        Log.d(LOG_TAG, Integer.toString(v.length));
//
//        Allocation yIn = Allocation.createTyped(
//                mRs, Type.createXY(mRs, Element.U8(mRs), yRowStride, height));
//        yIn.copyFrom(y);
//
//        Allocation uIn = Allocation.createSized(mRs, Element.U8(mRs), u.length);
//        uIn.copyFrom(u);
//
//        Allocation vIn = Allocation.createSized(mRs, Element.U8(mRs), v.length);
//        vIn.copyFrom(v);
//
//        Allocation out = Allocation.createTyped(
//                mRs, Type.createXY(mRs, Element.RGBA_8888(mRs), width, height));
//
//        mYUV2RGBA.set_yRowStride(yRowStride);
//        mYUV2RGBA.set_uvPixelStride(uvPixelStride);
//        mYUV2RGBA.set_uvRowStride(uvRowStride);
//
//        mYUV2RGBA.set_yIn(yIn);
//        mYUV2RGBA.set_uIn(uIn);
//        mYUV2RGBA.set_vIn(vIn);
//
//        Script.LaunchOptions launchOptions = new Script.LaunchOptions();
//        launchOptions.setX(0, width).setY(0, height);
//
//        mYUV2RGBA.forEach_convert(out, launchOptions);
//
//        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
//        out.copyTo(bitmap);
//
//        return bitmap;
//    }
//
//    /**
//     * Convert intensity image to RGBA bitmap
//     *
//     * @param gray raw intensity image, row major, one byte per pixel
//     * @param width image width
//     * @param height image height
//     */
//    public static Bitmap grayToBitmap(byte[] gray, int width, int height) {
//
//        Allocation in = Allocation.createTyped(
//                mRs, Type.createXY(mRs, Element.U8(mRs), width, height));
//        in.copyFrom(gray);
//        Allocation out = Allocation.createTyped(
//                mRs, Type.createXY(mRs, Element.RGBA_8888(mRs), width, height));
//        mGray2RGBA.forEach_convert(in, out);
//        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
//        out.copyTo(bitmap);
//
//        return bitmap;
//    }
//}
