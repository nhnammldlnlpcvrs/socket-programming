/*
 * VideoStreamingLab.java
 * Clean version – no HTML, no errors.
 * Works with RTPpacket.java and VideoStream.java.
 */

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.io.*;
import java.net.*;

public class VideoStreamingLab extends JFrame implements ActionListener {

  // GUI components
  JButton playButton = new JButton("Play");
  JButton pauseButton = new JButton("Pause");
  JLabel iconLabel = new JLabel();

  // RTP variables
  DatagramPacket rcvdp;
  DatagramSocket RTPsocket;
  static int RTP_RCV_PORT = 25000;

  Timer timer; // receives RTP packets regularly

  // Video stream
  VideoStream video;

  byte[] buf; // receive buffer

  // States
  final static int INIT = 0;
  final static int PLAYING = 1;
  final static int PAUSED = 2;

  int state;

  // Constructor
  public VideoStreamingLab() {
    super("Video Streaming Client (RTP)");

    // Setup GUI
    playButton.addActionListener(this);
    pauseButton.addActionListener(this);

    JPanel buttons = new JPanel();
    buttons.add(playButton);
    buttons.add(pauseButton);

    iconLabel.setText("Client Ready.");

    this.getContentPane().add(buttons, BorderLayout.NORTH);
    this.getContentPane().add(iconLabel, BorderLayout.CENTER);
    this.setSize(400, 300);
    this.setVisible(true);

    // Setup timer: ~20 fps
    timer = new Timer(50, new TimerListener());
    timer.setInitialDelay(0);
    timer.setCoalesce(true);

    // Initialize RTP socket
    try {
      RTPsocket = new DatagramSocket(RTP_RCV_PORT);
      RTPsocket.setSoTimeout(200);
    } catch (Exception e) {
      System.out.println("Error creating RTP socket: " + e.getMessage());
      System.exit(0);
    }

    buf = new byte[15000];
    state = INIT;
  }

  // ---------------------------------------------
  // button listener
  // ---------------------------------------------
  public void actionPerformed(ActionEvent e) {
    if (e.getSource() == playButton) {
      if (state == INIT || state == PAUSED) {
        timer.start();
        state = PLAYING;
        System.out.println("PLAY");
      }
    }

    if (e.getSource() == pauseButton) {
      if (state == PLAYING) {
        timer.stop();
        state = PAUSED;
        System.out.println("PAUSE");
      }
    }
  }

  // ---------------------------------------------
  // TimerListener: receives RTP packet every tick
  // ---------------------------------------------
  class TimerListener implements ActionListener {
    public void actionPerformed(ActionEvent e) {

      rcvdp = new DatagramPacket(buf, buf.length);

      try {
        RTPsocket.receive(rcvdp);

        RTPpacket rtp = new RTPpacket(rcvdp.getData(), rcvdp.getLength());
        int payloadLength = rtp.getpayload_length();

        byte[] payload = new byte[payloadLength];
        rtp.getpayload(payload);

        Image img = Toolkit.getDefaultToolkit().createImage(payload, 0, payloadLength);
        ImageIcon icon = new ImageIcon(img);

        iconLabel.setIcon(icon);

      } catch (InterruptedIOException iioe) {
        // timeout → no packet received
      } catch (Exception ex) {
        System.out.println("Exception: " + ex.getMessage());
      }
    }
  }

  // ---------------------------------------------
  // main
  // ---------------------------------------------
  public static void main(String[] args) {
    VideoStreamingLab client = new VideoStreamingLab();
    client.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        System.exit(0);
      }
    });
  }
}