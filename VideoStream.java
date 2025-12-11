
// VideoStream.java
import java.io.*;

public class VideoStream {
  private FileInputStream fis;
  private String filename;

  public VideoStream(String filename) throws IOException {
    this.filename = filename;
    fis = new FileInputStream(filename);
  }

  /**
   * Read next frame following lab format:
   * First 5 bytes ASCII digits represent frame length (decimal), then frame
   * bytes.
   * Fill into 'frame' buffer and return number of bytes read, or -1 on EOF/error.
   */
  public int getnextframe(byte[] frame) throws IOException {
    byte[] lenBuf = new byte[5];
    int r = 0;
    // read exactly 5 bytes for length header
    while (r < 5) {
      int n = fis.read(lenBuf, r, 5 - r);
      if (n == -1) {
        return -1; // EOF
      }
      r += n;
    }
    String lenStr = new String(lenBuf, "US-ASCII");
    int frameLen;
    try {
      frameLen = Integer.parseInt(lenStr);
    } catch (NumberFormatException e) {
      // malformed length header
      return -1;
    }

    if (frameLen <= 0)
      return -1;
    if (frameLen > frame.length) {
      // frame too large for provided buffer -> return error (caller should allocate
      // large buffer)
      // alternatively we could read up to frame.length and skip the rest
      int totalRead = 0;
      while (totalRead < frameLen) {
        int toRead = Math.min(frameLen - totalRead, 8192);
        byte[] skipBuf = new byte[toRead];
        int n = fis.read(skipBuf);
        if (n == -1)
          break;
        totalRead += n;
      }
      return -1;
    }

    int off = 0;
    while (off < frameLen) {
      int rd = fis.read(frame, off, frameLen - off);
      if (rd == -1)
        return -1;
      off += rd;
    }
    return frameLen;
  }

  public void close() {
    try {
      if (fis != null)
        fis.close();
    } catch (Exception e) {
    }
  }
}
