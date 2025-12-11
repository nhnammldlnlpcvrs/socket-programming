package java;

import java.io.*;
import java.net.*;

public class Client {

    static final String SERVER_HOST = "localhost";
    static final int SERVER_PORT = 8554;

    public static void main(String[] args) {
        try {
            Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
            System.out.println("Connected to server " + SERVER_HOST + ":" + SERVER_PORT);

            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // RTSP sequence number
            int cseq = 1;

            sendRTSP(writer, "OPTIONS rtsp://localhost:8554/video RTSP/1.0", cseq++);
            readResponse(reader);

            sendRTSP(writer, "DESCRIBE rtsp://localhost:8554/video RTSP/1.0", cseq++);
            readResponse(reader);

            sendRTSP(writer, "SETUP rtsp://localhost:8554/video RTSP/1.0", cseq++);
            readResponse(reader);

            sendRTSP(writer, "PLAY rtsp://localhost:8554/video RTSP/1.0", cseq++);
            readResponse(reader);

            socket.close();
            System.out.println("Client done.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void sendRTSP(BufferedWriter writer, String request, int cseq) throws IOException {
        writer.write(request + "\r\n");
        writer.write("CSeq: " + cseq + "\r\n");
        writer.write("\r\n");
        writer.flush();
        System.out.println("\n>>> Sent:");
        System.out.println(request);
    }

    static void readResponse(BufferedReader reader) throws IOException {
        System.out.println("<<< Response:");
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty())
                break; // hết header
            System.out.println(line);
        }
    }
}
