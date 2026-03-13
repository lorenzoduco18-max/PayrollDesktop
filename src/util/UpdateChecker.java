package util;

import javax.swing.*;
import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Properties;

public class UpdateChecker {

    private static final String VERSION_URL =
            "https://raw.githubusercontent.com/lorenzoduco18-max/PayrollDesktop/main/version.txt";

    private static final String RELEASES_URL =
            "https://github.com/lorenzoduco18-max/PayrollDesktop/releases/latest";

    public static void checkForUpdates() {
        try {
            String currentVersion = getCurrentVersion();
            if (currentVersion == null || currentVersion.isBlank()) {
                return;
            }

            URL url = new URL(VERSION_URL);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(url.openStream()));

            String latestVersion = reader.readLine();
            reader.close();

            if (latestVersion == null || latestVersion.isBlank()) {
                return;
            }

            latestVersion = latestVersion.trim();
            currentVersion = currentVersion.trim();

            if (!currentVersion.equals(latestVersion)) {

                int option = JOptionPane.showOptionDialog(
                        null,
                        "A new version is available: " + latestVersion + "\n\nCurrent version: " + currentVersion,
                        "Update Available",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.INFORMATION_MESSAGE,
                        null,
                        new String[]{"Update Now", "Later"},
                        "Update Now"
                );

                if (option == JOptionPane.YES_OPTION) {
                    Desktop.getDesktop().browse(new URL(RELEASES_URL).toURI());
                    System.exit(0);
                }
            }

        } catch (Exception e) {
            // Ignore update check failures
        }
    }

    private static String getCurrentVersion() {
        try (InputStream in = UpdateChecker.class.getResourceAsStream(
                "/META-INF/maven/com.payroll/payroll-desktop/pom.properties")) {

            if (in == null) {
                return null;
            }

            Properties props = new Properties();
            props.load(in);
            return props.getProperty("version");

        } catch (Exception e) {
            return null;
        }
    }
}