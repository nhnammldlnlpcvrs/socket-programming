package java;

// ServerWorker.java
import java.net.*;
import java.io.*;
import java.util.Random;

public class ServerWorker extends Thread {

    // RTSP states
    static final int INIT = 0;
    static final int READY = 1;
    static final int PLAYING = 2;

    // RTSP reply codes
    static final int OK_200 = 0;
    static final int FILE_NOT_FOUND_404 = 1;
    static final int CON_ERR_500 = 2;

    Socket rtspSocket;
    BufferedReader rtspReader;
    BufferedWriter rtspWriter;

    InetAddress clientIP;
    int clientRTPPort = 25000;

    int state;
    int RTSPid;
    int RtspSeqNb = 0;

    VideoStream video;
    DatagramSocket rtpSocket = null;
    int RTPseq = 0;
    int framePeriod = 40; // ms (~25fps)

    volatile boolean teardown = false;
    volatile boolean sending = false;

    public ServerWorker(Socket client) throws IOException {
        this.rtspSocket = client;
        rtspReader = new BufferedReader(new InputStreamReader(rtspSocket.getInputStream()));
        rtspWriter = new BufferedWriter(new OutputStreamWriter(rtspSocket.getOutputStream()));
        clientIP = client.getInetAddress();
        state = INIT;
        RTSPid = new Random().nextInt(900000) + 100000;
    }

    public void run() {
        try {
            while (!teardown) {
                String request = recvRtspRequest();
                if (request == null)
                    break;
                processRtspRequest(request);
            }
        } catch (Exception e) {
            System.out.println("ServerWorker error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    // Read RTSP request (read first line + up to several headers)
    private String recvRtspRequest() throws IOException {
        StringBuilder sb = new StringBuilder();
        String line = rtspReader.readLine();
        if (line == null)
            return null;
        sb.append(line).append("\n");
        // read next header lines (CSeq, Transport/Session...)
        for (int i = 0; i < 10; i++) {
            rtspSocket.setSoTimeout(50);
            String l;
            try {
                l = rtspReader.readLine();
            } catch (SocketTimeoutException ste) {
                l = null;
            }
            if (l == null || l.length() == 0)
                break;
            sb.append(l).append("\n");
        }
        return sb.toString();
    }

    private void processRtspRequest(String data) throws Exception {
        System.out.println("Received RTSP:\n" + data);
        String[] lines = data.split("\n");
        String[] requestLine = lines[0].split(" ");
        String requestType = requestLine[0];
        String fileName = requestLine.length > 1 ? requestLine[1] : "";
        String cseq = (lines.length > 1) ? lines[1].split(" ")[1] : "0";
        RtspSeqNb = Integer.parseInt(cseq);

        if (requestType.equals("SETUP") && state == INIT) {
            try {
                video = new VideoStream(fileName);
            } catch (IOException ioe) {
                replyRtsp(FILE_NOT_FOUND_404, cseq);
                return;
            }
            // parse Transport header for client port
            for (int i = 2; i < lines.length; i++) {
                if (lines[i].toLowerCase().contains("transport")) {
                    String transport = lines[i];
                    int idx = transport.indexOf("client_port=");
                    if (idx >= 0) {
                        String portPart = transport.substring(idx + "client_port=".length()).trim();
                        if (portPart.contains("-"))
                            portPart = portPart.split("-")[0];
                        try {
                            clientRTPPort = Integer.parseInt(portPart);
                        } catch (NumberFormatException e) {
                            clientRTPPort = 25000;
                        }
                    }
                }
            }

            // create DatagramSocket for sending RTP
            rtpSocket = new DatagramSocket();
            state = READY;
            replyRtsp(OK_200, cseq);

        } else if (requestType.equals("PLAY") && state == READY) {
            replyRtsp(OK_200, cseq);
            state = PLAYING;
            sending = true;
            Thread sender = new Thread(() -> {
                try {
                    sendRtp();
                } catch (Exception e) {
                    System.out.println("sendRtp error: " + e.getMessage());
                }
            });
            sender.start();

        } else if (requestType.equals("PAUSE") && state == PLAYING) {
            replyRtsp(OK_200, cseq);
            state = READY;
            sending = false;

        } else if (requestType.equals("TEARDOWN")) {
            replyRtsp(OK_200, cseq);
            teardown = true;
            sending = false;
        } else {
            replyRtsp(CON_ERR_500, cseq);
        }
    }

    private void sendRtp() throws Exception {
        byte[] frameBuf = new byte[2000000]; // large buffer for HD
        while (sending && !teardown) {
            int frameLen = video.getnextframe(frameBuf);
            if (frameLen <= 0) {
                sending = false;
                break;
            }
            RTPseq++;
            int timestamp = RTPseq; // simple timestamp
            RTPpacket rtpPacket = new RTPpacket(26, RTPseq, timestamp, frameBuf, frameLen);

            int packetLen = rtpPacket.getlength();
            byte[] packet = new byte[packetLen];
            rtpPacket.getpacket(packet);

            DatagramPacket dp = new DatagramPacket(packet, packet.length, clientIP, clientRTPPort);
            try {
                rtpSocket.send(dp);
            } catch (IOException ioe) {
                System.out.println("Connection Error while sending RTP: " + ioe.getMessage());
                sending = false;
                break;
            }

            try {
                Thread.sleep(framePeriod);
            } catch (InterruptedException ie) {
                break;
            }
        }
    }

    private void replyRtsp(int code, String cseq) {
        String reply;
        if (code == OK_200) {
            reply = "RTSP/1.0 200 OK\nCSeq: " + cseq + "\nSession: " + RTSPid + "\n";
        } else if (code == FILE_NOT_FOUND_404) {
            reply = "RTSP/1.0 404 NOT FOUND\nCSeq: " + cseq + "\nSession: " + RTSPid + "\n";
        } else {
            reply = "RTSP/1.0 500 ERROR\nCSeq: " + cseq + "\nSession: " + RTSPid + "\n";
        }
        try {
            rtspWriter.write(reply);
            rtspWriter.flush();
            System.out.println("Sent RTSP reply:\n" + reply);
        } catch (IOException e) {
            System.out.println("Failed to send RTSP reply: " + e.getMessage());
        }
    }

    private void cleanup() {
        try {
            if (video != null)
                video.close();
        } catch (Exception e) {
        }
        try {
            if (rtpSocket != null)
                rtpSocket.close();
        } catch (Exception e) {
        }
        try {
            if (rtspSocket != null)
                rtspSocket.close();
        } catch (Exception e) {
        }
    }
}