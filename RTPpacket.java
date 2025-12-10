/*
 * RTPpacket.java
 * This class represents an RTP packet.
 * Based on Kurose & Ross Video Streaming Lab
 */

public class RTPpacket {

  // size of the RTP header:
  static int HEADER_SIZE = 12;

  // Fields that compose the RTP header
  public int Version = 2;
  public int Padding = 0;
  public int Extension = 0;
  public int CC = 0;
  public int Marker = 0;
  public int PayloadType;
  public int SequenceNumber;
  public int TimeStamp;
  public int Ssrc = 0;

  // Bitstream of the RTP header and payload
  public byte[] header;
  public int payload_size;
  public byte[] payload;

  // Constructor of an RTPpacket object from header fields and payload bitstream
  public RTPpacket(int PType, int Framenb, int Time, byte[] data, int data_length) {
    PayloadType = PType;
    SequenceNumber = Framenb;
    TimeStamp = Time;

    // Allocate memory for header
    header = new byte[HEADER_SIZE];

    // ---------------------------
    // Build the RTP Header (12 bytes)
    // ---------------------------

    // Byte 0: V (2), P (1), X (1), CC (4)
    header[0] = (byte) ((Version << 6) | (Padding << 5) | (Extension << 4) | (CC & 0x0F));

    // Byte 1: M (1), PT (7)
    header[1] = (byte) ((Marker << 7) | (PayloadType & 0x7F));

    // Byte 2–3: Sequence Number
    header[2] = (byte) (SequenceNumber >> 8);
    header[3] = (byte) (SequenceNumber & 0xFF);

    // Byte 4–7: Timestamp
    header[4] = (byte) (TimeStamp >> 24);
    header[5] = (byte) (TimeStamp >> 16);
    header[6] = (byte) (TimeStamp >> 8);
    header[7] = (byte) (TimeStamp & 0xFF);

    // Byte 8–11: SSRC (set to zero)
    header[8] = 0;
    header[9] = 0;
    header[10] = 0;
    header[11] = 0;

    // ---------------------------
    // Add the payload
    // ---------------------------
    payload_size = data_length;
    payload = new byte[data_length];
    System.arraycopy(data, 0, payload, 0, data_length);
  }

  // return the payload length
  public int getPayloadLength() {
    return payload_size;
  }

  // returns the length of the packet
  public int getLength() {
    return payload_size + HEADER_SIZE;
  }

  // returns the timestamp
  public int getTimeStamp() {
    return TimeStamp;
  }

  // returns the sequence number
  public int getSequenceNumber() {
    return SequenceNumber;
  }

  // returns the payload type
  public int getPayloadType() {
    return PayloadType;
  }

  // copy the payload into data[]
  public int getPayload(byte[] data) {
    System.arraycopy(payload, 0, data, 0, payload_size);
    return payload_size;
  }

  // return the header of the packet
  public int getHeader(byte[] data) {
    System.arraycopy(header, 0, data, 0, HEADER_SIZE);
    return HEADER_SIZE;
  }

  // return the entire packet = header + payload
  public int getPacket(byte[] packet) {
    System.arraycopy(header, 0, packet, 0, HEADER_SIZE);
    System.arraycopy(payload, 0, packet, HEADER_SIZE, payload_size);
    return getLength();
  }

  // print packet header (for debug)
  public void printHeader() {
    System.out.printf(
        "[RTP-Header] Version: %d, Padding: %d, Extension: %d, CC: %d, Marker: %d, PayloadType: %d, Seq: %d, TimeStamp: %d, SSRC: %d\n",
        Version, Padding, Extension, CC, Marker, PayloadType, SequenceNumber, TimeStamp, Ssrc);

    System.out.print("Header Bytes: ");
    for (int i = 0; i < HEADER_SIZE; i++) {
      System.out.print((header[i] & 0xFF) + " ");
    }
    System.out.println();
  }
}