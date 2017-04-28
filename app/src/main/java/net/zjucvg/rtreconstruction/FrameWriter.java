package net.zjucvg.rtreconstruction;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Created by Lucas on 2016/9/4.
 */
public class FrameWriter extends Thread {

    BlockingDeque<NativeVSLAM.Frame> mFrameQueue;
    public boolean isStarted;

    private int mPreviewWidth;
    private int mPreviewHeight;
    private int mFrameCount;
    private String mPath;

    public FrameWriter(String path, int frameWidth, int frameHeight) {
        mFrameQueue = new LinkedBlockingDeque<NativeVSLAM.Frame>();
        this.mPreviewHeight=frameHeight;
        this.mPreviewWidth=frameWidth;
        this.mPath=path;
        isStarted=true;
    }

    @Override
    public void run()
    {
        while(isStarted)
        {
            try {
                NativeVSLAM.Frame frame = mFrameQueue.take();
                byte[] nativeFrame=NativeVSLAM.packNativeFrame(frame);
                writeFile(mPath+"/"+mFrameCount+".txt", nativeFrame);
                mFrameCount++;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        for(int i=0;i<mFrameQueue.size();i++)
        {
            NativeVSLAM.Frame frame = mFrameQueue.poll();
            byte[] nativeFrame=NativeVSLAM.packNativeFrame(frame);
            writeFile(mPath+"/"+mFrameCount+".txt", nativeFrame);
            mFrameCount++;
        }
    }

    public void write(NativeVSLAM.Frame frame) {
        mFrameQueue.push(frame);
    }

    public static void writeFile(String string, byte[] bytes) {
        FileOutputStream fileOutputStream=null;
        BufferedOutputStream bufferedOutputStream=null;
        try{
            fileOutputStream=new FileOutputStream(string);
            bufferedOutputStream =new BufferedOutputStream(fileOutputStream);
            bufferedOutputStream.write(bytes);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }finally{
            try {
                bufferedOutputStream.flush();
                fileOutputStream.close();
                bufferedOutputStream.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
}
