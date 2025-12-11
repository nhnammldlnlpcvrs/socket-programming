import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class ClientLauncher extends JFrame {

    private Client client; // client RTSP
    private JTextArea logArea; // hiện log

    public ClientLauncher() {
        setTitle("Java RTSP Client Launcher");
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // ===== Log panel =====
        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scroll = new JScrollPane(logArea);

        // ===== Button panel =====
        JPanel panel = new JPanel();
        JButton btnConnect = new JButton("Connect");
        JButton btnPlay = new JButton("Play");
        JButton btnPause = new JButton("Pause");
        JButton btnTeardown = new JButton("Teardown");

        panel.add(btnConnect);
        panel.add(btnPlay);
        panel.add(btnPause);
        panel.add(btnTeardown);

        add(scroll, BorderLayout.CENTER);
        add(panel, BorderLayout.SOUTH);

        // ===== Actions =====
        btnConnect.addActionListener(e -> connectToServer());
        btnPlay.addActionListener(e -> playVideo());
        btnPause.addActionListener(e -> pauseVideo());
        btnTeardown.addActionListener(e -> teardown());

        setVisible(true);
    }

    private void connectToServer() {
        append("Connecting...");
        try {
            client = new Client(logArea);
            append("Connected!");
        } catch (Exception ex) {
            append("Connect failed: " + ex.getMessage());
        }
    }

    private void playVideo() {
        if (client == null) {
            append("Not connected.");
            return;
        }
        append("Sending PLAY...");
        client.sendPlay();
    }

    private void pauseVideo() {
        if (client == null) {
            append("Not connected.");
            return;
        }
        append("Sending PAUSE...");
        client.sendPause();
    }

    private void teardown() {
        if (client == null) {
            append("Not connected.");
            return;
        }
        append("Sending TEARDOWN...");
        client.sendTeardown();
    }

    private void append(String msg) {
        logArea.append(msg + "\n");
    }

    public static void main(String[] args) {
        new ClientLauncher();
    }
}