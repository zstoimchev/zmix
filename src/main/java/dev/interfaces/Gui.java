package dev.interfaces;

import dev.utils.Logger;

import javax.swing.*;

public class Gui extends Thread {
    private final Logger logger;
    private final static int FRAME_WIDTH = 1920;
    private final static int FRAME_HEIGHT = 1080;

    private final boolean isEnabled;

    private JFrame frame;
    private JPanel panel;

    public Gui(boolean isEnabled) {
        this.logger = Logger.getLogger(this.getClass());
        this.isEnabled = isEnabled;
    }

    @Override
    public void run() {
        if (!isEnabled) {
            logger.warn("GUI is disabled. Skipping initialization & continuing only with CLI.");
            return;
        }

        panel = new JPanel();
        frame = new JFrame("P2P Network Node");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(FRAME_WIDTH, FRAME_HEIGHT); // + 76
        frame.add(panel);
        frame.setVisible(true);
    }
}
