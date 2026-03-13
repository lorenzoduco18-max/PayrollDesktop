package util;

import java.io.InputStream;
import java.util.Properties;

public final class AppVersion {

    private static final String DEFAULT_VERSION = "DEV";

    private AppVersion() {}

    public static String getVersion() {

        try (InputStream in = AppVersion.class.getResourceAsStream("/app.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                String version = props.getProperty("app.version");

                if (version != null && !version.isBlank() && !version.contains("${")) {
                    return version.trim();
                }
            }
        } catch (Exception ignored) {}

        Package pkg = AppVersion.class.getPackage();
        if (pkg != null) {
            String impl = pkg.getImplementationVersion();
            if (impl != null) return impl;
        }

        return DEFAULT_VERSION;
    }
}