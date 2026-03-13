package app;

import javax.swing.SwingUtilities;
import util.UpdateChecker;

import com.formdev.flatlaf.FlatLightLaf;
import ui.LoginFrame;

public class Main {

    public static void main(String[] args) {

        // ✅ Hide noisy webcam logs (NOT errors)
        // Must be set BEFORE any webcam classes are loaded.
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "error");

        // System.setProperty("org.slf4j.simpleLogger.showShortLogName", "false");
        UpdateChecker.checkForUpdates();
        FlatLightLaf.setup(); // ✅ must be BEFORE creating Swing windows

        SwingUtilities.invokeLater(LoginFrame::new);
    }
}
