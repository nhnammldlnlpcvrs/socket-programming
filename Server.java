
// Server.java
import java.net.*;
import java.io.*;

public class Server {
  public static void main(String[] args) {
    int SERVER_PORT = 8554;
    if (args.length >= 1) {
      try {
        SERVER_PORT = Integer.parseInt(args[0]);
      } catch (Exception e) {
      }
    }
    try (ServerSocket listenSocket = new ServerSocket(SERVER_PORT)) {
      System.out.println("RTSP Server listening on port " + SERVER_PORT);
      while (true) {
        Socket client = listenSocket.accept();
        System.out.println("Accepted connection from " + client.getInetAddress());
        ServerWorker worker = new ServerWorker(client);
        worker.start();
      }
    } catch (IOException e) {
      System.out.println("Server error: " + e.getMessage());
    }
  }
}