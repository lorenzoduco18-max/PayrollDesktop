package util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities for working with hour values that may be entered either as:
 * - decimal hours (e.g. "2.66")
 * - hours:minutes (e.g. "2:40")
 * - hour/minute text (e.g. "2h 40m", "2h40m", "00h 00m")
 *
 * Internally we keep everything as decimal hours.
 */
public final class HoursUtil {

    private HoursUtil() {}

    // Accepts: "8h 48m", "8h48m", "8h", "48m", "00h 00m"
    private static final Pattern HM_TEXT = Pattern.compile(
            "^\\s*(?:(\\d+)\\s*(?:h|hr|hrs|hour|hours))?\\s*(?:(\\d+)\\s*(?:m|min|mins|minute|minutes))?\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    public static double parseHours(String raw) {
        if (raw == null) return 0.0;
        String s = raw.trim();
        if (s.isEmpty()) return 0.0;

        s = s.replace('\u00A0', ' ').trim();

        // 1) Text form: "8h 48m"
        Matcher mt = HM_TEXT.matcher(s);
        if (mt.matches()) {
            String hs = mt.group(1);
            String ms = mt.group(2);

            if (hs != null || ms != null) {
                try {
                    int h = (hs == null || hs.isEmpty()) ? 0 : Integer.parseInt(hs);
                    int m = (ms == null || ms.isEmpty()) ? 0 : Integer.parseInt(ms);

                    if (m >= 60) {
                        h += (m / 60);
                        m = (m % 60);
                    }
                    if (h < 0) h = 0;
                    if (m < 0) m = 0;

                    return h + (m / 60.0);
                } catch (Exception ignored) {}
            }
        }

        s = s.replace(" ", "");

        // 2) Hours:Minutes form: "2:40"
        int colon = s.indexOf(':');
        if (colon >= 0) {
            String hPart = s.substring(0, colon);
            String mPart = s.substring(colon + 1);

            if (hPart.isEmpty()) hPart = "0";
            if (mPart.isEmpty()) mPart = "0";

            try {
                int h = Integer.parseInt(hPart);
                int m = Integer.parseInt(mPart);

                if (m >= 60) {
                    h += (m / 60);
                    m = (m % 60);
                }
                if (h < 0) h = 0;
                if (m < 0) m = 0;

                return h + (m / 60.0);
            } catch (Exception ignored) {
                return 0.0;
            }
        }

        // 3) Decimal hours: "2.66"
        try {
            return Double.parseDouble(s);
        } catch (Exception ignored) {
            return 0.0;
        }
    }

    /**
     * Old style: "2h 05m"
     */
    public static String formatHM(double hours) {
        if (Double.isNaN(hours) || Double.isInfinite(hours)) return "0h 00m";
        if (hours < 0) hours = 0;

        long totalMinutes = Math.round(hours * 60.0);
        long h = totalMinutes / 60;
        long m = totalMinutes % 60;

        return h + "h " + (m < 10 ? "0" + m : String.valueOf(m)) + "m";
    }

    /**
     * ✅ Requested: "00h 00m" style (always 2 digits)
     */
    public static String formatHM2(double hours) {
        if (Double.isNaN(hours) || Double.isInfinite(hours)) return "00h 00m";
        if (hours < 0) hours = 0;

        long totalMinutes = Math.round(hours * 60.0);
        long h = totalMinutes / 60;
        long m = totalMinutes % 60;

        String hh = (h < 10) ? ("0" + h) : String.valueOf(h);
        String mm = (m < 10) ? ("0" + m) : String.valueOf(m);

        return hh + "h " + mm + "m";
    }

    /**
     * Convenience: parse then normalize input into 00h 00m.
     */
    public static String normalizeToHM(String raw) {
        return formatHM2(parseHours(raw));
    }
}
