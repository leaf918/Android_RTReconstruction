package net.zjucvg.rtreconstruction;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * Created by kuanlu on 2017/4/13.
 */

public class OpenCLUtility {

    public static Bitmap grayToBitmap(byte[] gray, int width, int height) {
        byte []graybmp = gray2Bitmap(gray, width, height);
        Bitmap bitmap = BitmapFactory.decodeByteArray(graybmp, 0, graybmp.length);
        return bitmap;
    }

    public static native byte[] gray2Bitmap(byte[] gray, int width, int height);
}
