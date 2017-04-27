package net.zjucvg.rtreconstruction;

/**
 * Created by lichen on 16-7-13.
 */

/**
 * A simple class for YUV420 Image
 * <p/>
 * A YUV 420 image has 3 channels -- Y channel, U channel and V channel, the Y
 * channel has full resolution,
 * the U and V channels has half width and half height resolution.
 */
public class YUV420Image {

  public static class Plane {
    int pixelStride;
    int rowStride;
    byte[] data;
  }

  /*
  * width of the image, in pixel
   */
  int width;
  /*
  * height of the image, in pixel
   */
  int height;
  /*
  * the image planes, the Y, U and V channel is stored at planes[0], planes[1]
  * and planes[2]
   */
  Plane[] planes;
}
