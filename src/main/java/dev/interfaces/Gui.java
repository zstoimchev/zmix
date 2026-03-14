package dev.interfaces;

import dev.network.CircuitManager;
import dev.network.NetworkManager;
import dev.utils.Logger;

import javax.swing.*;
import java.awt.*;

public class Gui extends Thread {
    private final Logger logger;
    private final static int FRAME_WIDTH = 1920;
    private final static int FRAME_HEIGHT = 1080;

    private final boolean isEnabled;
    private final CircuitManager circuitManager;

    private JFrame frame;
    private JPanel panel;

    public Gui(boolean isEnabled, NetworkManager networkManager) {
        this.logger = Logger.getLogger(this.getClass());
        this.isEnabled = isEnabled;
        this.circuitManager = networkManager.getCircuitManager();
    }

    @Override
    public void run() {
        if (!isEnabled) {
            logger.warn("GUI is disabled. Skipping initialization & continuing only with CLI.");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("P2P Network Node");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(FRAME_WIDTH, FRAME_HEIGHT);

            panel = new JPanel();
            panel.setLayout(new BorderLayout());

            // Search bar panel
            JPanel searchPanel = new JPanel(new BorderLayout());

            JTextField searchField = new JTextField();
            searchField.setFont(searchField.getFont().deriveFont(24f));
            JButton searchButton = new JButton("Search");
            searchButton.setFont(searchButton.getFont().deriveFont(24f));

            searchPanel.add(searchField, BorderLayout.CENTER);
            searchPanel.add(searchButton, BorderLayout.EAST);

            panel.add(searchPanel, BorderLayout.NORTH);

            // Temporary placeholder for response area
            JTextArea responseArea = new JTextArea();
            JScrollPane scrollPane = new JScrollPane(responseArea);

            panel.add(scrollPane, BorderLayout.CENTER);

            frame.add(panel);
            frame.setVisible(true);
            searchField.addActionListener(e -> performSearch(searchField.getText()));
            searchButton.addActionListener(e -> performSearch(searchField.getText()));
        });
    }

    private void performSearch(String input) {
        logger.info("Search requested: {}", input);

        if (input == null || input.isBlank()) {
            return;
        }

        circuitManager.sendRequestToQueue(input);
    }
}
