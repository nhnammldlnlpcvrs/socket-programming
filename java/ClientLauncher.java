import java.io.*;
import java.net.*;

public class Client {

    private Socket socket;
    private BufferedWriter writer;
    private BufferedReader reader;
    private int cseq = 1;

    private JTextArea log;

    public Client(JTextArea logArea) throws Exception {
        this.log = logArea;

        socket = new Socket("localhost", 8554);
        writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        append("Connected to server.");

        sendRequest("OPTIONS rtsp://localhost:8554/video RTSP/1.0");
        sendRequest("DESCRIBE rtsp://localhost:8554/video RTSP/1.0");
        sendRequest("SETUP rtsp://localhost:8554/video RTSP/1.0");
    }

    private void sendRequest(String req) throws IOException {
        writer.write(req + "\r\n");
        writer.write("CSeq: " + cseq++ + "\r\n\r\n");
        writer.flush();

        append(">>> " + req);

        String line;
        while ((line = reader.readLine()) != null) {
            append("<<< " + line);
            if (line.trim().isEmpty())
                break;
        }
    }

    public void sendPlay() {
        try {
            sendRequest("PLAY rtsp://localhost:8554/video RTSP/1.0");
        } catch (Exception e) {
            append(e.getMessage());
        }
    }

    public void sendPause() {
        try {
            sendRequest("PAUSE rtsp://localhost:8554/video RTSP/1.0");
        } catch (Exception e) {
            append(e.getMessage());
        }
    }

    public void sendTeardown() {
        try {
            sendRequest("TEARDOWN rtsp://localhost:8554/video RTSP/1.0");
        } catch (Exception e) {
            append(e.getMessage());
        }
    }

    private void append(String s) {
        if (log != null)
            log.append(s + "\n");
    }
}