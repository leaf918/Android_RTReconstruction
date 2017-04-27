package net.zjucvg.rtreconstruction;

/**
 * Created by pe on 16/6/17.
 */
public class PluginNative {

    static {
        System.loadLibrary("native-lib");
    }
    public static native void OnCameraFrame(float[] camData,byte[] imgData,int imageWidth,int imageHeight,double timestamp);
    public static native void ClearFrame();
}
