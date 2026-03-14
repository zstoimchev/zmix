package dev.interfaces;

import dev.network.CircuitManager;
import dev.network.NetworkManager;
import dev.utils.Logger;

import javax.swing.*;
import java.awt.*;

public class Gui extends Thread {
    private final Logger logger;
    private static final int FRAME_WIDTH = 1920;
    private static final int FRAME_HEIGHT = 1080;

    private final boolean isEnabled;
    private final CircuitManager circuitManager;
    private JEditorPane editorPane;

    public Gui(boolean isEnabled, NetworkManager networkManager) {
        this.logger = Logger.getLogger(this.getClass());
        this.isEnabled = isEnabled;
        this.circuitManager = networkManager.getCircuitManager();
    }

    @Override
    public void run() {
        if (!isEnabled) {
            logger.warn("GUI is disabled. Skipping initialization.");
            return;
        }
        Font font = new Font("SansSerif", Font.PLAIN, 24);
        UIManager.getLookAndFeelDefaults().keySet()
                .forEach(key -> { if (key.toString().endsWith(".font")) UIManager.put(key, font); });
        SwingUtilities.invokeLater(this::buildUi);
    }

    private void buildUi() {
        JFrame frame = new JFrame("P2P Network Node");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(FRAME_WIDTH, FRAME_HEIGHT);

        JPanel panel = new JPanel(new BorderLayout());

        JPanel searchPanel = new JPanel(new BorderLayout());
        JTextField searchField = new JTextField();
        searchField.setFont(searchField.getFont().deriveFont(24f));
        JButton searchButton = new JButton("Search");
        searchButton.setFont(searchButton.getFont().deriveFont(24f));
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);
        panel.add(searchPanel, BorderLayout.NORTH);

        editorPane = new JEditorPane();
        editorPane.setContentType("text/html");
        editorPane.setEditable(false);
        editorPane.setText(circuitManager.getLastHtmlResponse());
        panel.add(new JScrollPane(editorPane), BorderLayout.CENTER);

        frame.add(panel);
        frame.setVisible(true);

        searchField.addActionListener(e -> performSearch(searchField.getText()));
        searchButton.addActionListener(e -> performSearch(searchField.getText()));
    }

    private void performSearch(String input) {
        if (input == null || input.isBlank()) return;
        logger.info("Search requested: {}", input);
        circuitManager.sendRequestToQueue(input);

        new Thread(() -> {
            try {
                circuitManager.waitForNewResponse();
                String html = circuitManager.getLastHtmlResponse();
                SwingUtilities.invokeLater(() -> {
                    editorPane.setContentType("text/html");
                    editorPane.setText(html);
                    editorPane.setCaretPosition(0);
                });
            } catch (InterruptedException e) {
                logger.error("Interrupted while waiting for response", e);
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}