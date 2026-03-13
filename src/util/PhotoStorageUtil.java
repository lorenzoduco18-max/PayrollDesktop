package util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class PhotoStorageUtil {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private PhotoStorageUtil() {}

    /**
     * Saves to user home so it works on different laptops + Windows/Mac:
     *  ~/PayrollDesktop/photos/<employeeId>/<timestamp>_<action>.jpg
     */
    public static File saveJpg(BufferedImage img, String employeeId, String action, ZonedDateTime when) throws IOException {
        if (img == null) throw new IOException("No image captured.");

        String base = System.getProperty("user.home") + File.separator
                + "PayrollDesktop" + File.separator
                + "photos" + File.separator
                + safe(employeeId);

        File dir = new File(base);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Failed to create folder: " + dir.getAbsolutePath());
        }

        String filename = TS.format(when) + "_" + safe(action) + ".jpg";
        File out = new File(dir, filename);

        ImageIO.write(img, "JPG", out);
        return out;
    }

    private static String safe(String s) {
        if (s == null || s.trim().isEmpty()) return "UNKNOWN";
        return s.trim().replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
