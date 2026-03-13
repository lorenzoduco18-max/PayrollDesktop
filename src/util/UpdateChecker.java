package util;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;

public class UpdateChecker {

    private static final String CURRENT_VERSION = "1.0.11";
    private static final String VERSION_URL =
            "https://raw.githubusercontent.com/lorenzoduco18-max/PayrollDesktop/main/version.txt";

    public static void checkForUpdates() {
        try {
            URL url = new URL(VERSION_URL);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(url.openStream()));

            String latestVersion = reader.readLine();
            reader.close();

            if (!CURRENT_VERSION.equals(latestVersion)) {

                int option = JOptionPane.showOptionDialog(
                        null,
                        "A new version is available: " + latestVersion,
                        "Update Available",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.INFORMATION_MESSAGE,
                        null,
                        new String[]{"Update Now", "Later"},
                        "Update Now"
                );

                if (option == JOptionPane.YES_OPTION) {

                    java.awt.Desktop.getDesktop().browse(
                            new URL("https://github.com/lorenzoduco18-max/PayrollDesktop/releases/latest").toURI()
                    );

                    System.exit(0);
                }
            }

        } catch (Exception e) {
            // ignore errors
        }
    }
}