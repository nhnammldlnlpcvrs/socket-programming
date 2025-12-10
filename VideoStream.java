// ------------------------------------------------------
// VideoStream.java  (clean, error–free version)
// Used in the RTP/RTSP Streaming Lab – Kurose & Ross
// ------------------------------------------------------

import java.io.FileInputStream;
import java.io.IOException;

public class VideoStream {

  private FileInputStream fis; // video file input stream
  private int frame_nb; // current frame number

  // ------------------------------------------------------
  // Constructor
  // ------------------------------------------------------
  public VideoStream(String filename) throws Exception {
    fis = new FileInputStream(filename);
    frame_nb = 0;
  }

  // ------------------------------------------------------
  // getnextframe
  // Reads:
  // 1) 5 bytes: ASCII length of frame
  // 2) 'length' bytes: JPEG frame data
  //
  // Returns number of bytes actually read into 'frame'
  // ------------------------------------------------------
  public int getnextframe(byte[] frame) throws IOException {

    byte[] frame_length_bytes = new byte[5];

    // Read the 5-byte header (ASCII length)
    int read_len = fis.read(frame_length_bytes, 0, 5);
    if (read_len < 5) {
      return -1; // End of file
    }

    // Convert "00024" → 24
    String length_str = new String(frame_length_bytes);
    int length = Integer.parseInt(length_str.trim());

    // Read 'length' bytes into the frame buffer
    return fis.read(frame, 0, length);
  }
}