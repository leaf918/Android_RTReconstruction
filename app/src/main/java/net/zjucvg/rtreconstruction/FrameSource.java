package net.zjucvg.rtreconstruction;

import android.graphics.Bitmap;
import android.util.Size;
import android.view.Surface;

import java.util.List;

/**
 * Created by lichen on 16-6-19.
 */
public abstract class FrameSource implements AutoCloseable {
  /**
   * retrieve all supported image size
   *
   * @return all supported image size
   */
  public abstract List<Size> getAvailableImageSize();

  /**
   * Start this frame source with the specified image size
   *
   * @param size must be one of #getAvailableImageSize
   */

  public abstract void start(Size size);

  /**
   * Check if the frame source has more frames
   *
   *<p> returns false if not started </p>
   */
  public abstract boolean hasMoreFrame();

  /**
   * Get one more frame
   *
   * @param blocking if true, block until get one more frame; if false, once no
   * more frame quickly available, return null
   *
   * <p class="note"><strong>Note:</strong>make sure #hasMoreFrame returns ture
   * before calling this method, or the behavior is undefined</p>
   */
  public abstract NativeVSLAM.Frame getNextFrame(boolean blocking);

  /**
   * Stop the frame source
   *
   * <p>You can still all #start after calling this method</p>
   */
  public abstract void stop();
}
