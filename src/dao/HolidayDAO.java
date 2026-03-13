package dao;

import model.Holiday;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class HolidayDAO {

    private static boolean hasColumn(ResultSet rs, String col) {
        try {
            ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                String name = md.getColumnLabel(i);
                if (name == null || name.isBlank()) name = md.getColumnName(i);
                if (col.equalsIgnoreCase(name)) return true;
            }
        } catch (Exception ignore) {}
        return false;
    }

    public static boolean safeGetBoolean(ResultSet rs, String col, boolean def) {
        try {
            Object v = rs.getObject(col);
            if (v == null) return def;
            if (v instanceof Boolean) return (Boolean) v;
            if (v instanceof Number) return ((Number) v).intValue() != 0;
            String s = String.valueOf(v).trim();
            if (s.isEmpty()) return def;
            return "1".equals(s) || "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s);
        } catch (Exception e) {
            return def;
        }
    }

    private Holiday map(ResultSet rs) throws Exception {
        Holiday h = new Holiday();
        h.id = rs.getInt("id");
        h.holDate = rs.getDate("hol_date").toLocalDate();
        h.name = rs.getString("name");
        h.type = rs.getString("type");
        h.scope = rs.getString("scope");
        h.notes = rs.getString("notes");
        h.rateMultiplier = rs.getDouble("rate_multiplier");
        if (hasColumn(rs, "enabled")) {
            h.enabled = safeGetBoolean(rs, "enabled", true);
        } else {
            h.enabled = true;
        }
        return h;
    }

    /**
     * Find holiday using an existing connection (prevents opening a new connection per call).
     */
    public Holiday findByDate(Connection con, LocalDate date) throws Exception {
        if (con == null) throw new IllegalArgumentException("Connection is required");
        String sql = "SELECT * FROM holidays WHERE hol_date = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
                return null;
            }
        }
    }

    public Holiday findByDate(LocalDate date) throws Exception {
        try (Connection con = DB.getConnection()) {
            return findByDate(con, date);
        }
    }

    /**
     * Batch read holidays for many dates in one query.
     * Returns a map of date -> Holiday.
     */
    public java.util.Map<LocalDate, Holiday> mapByDates(Connection con, java.util.Set<LocalDate> dates) throws Exception {
        java.util.Map<LocalDate, Holiday> out = new java.util.HashMap<>();
        if (con == null) throw new IllegalArgumentException("Connection is required");
        if (dates == null || dates.isEmpty()) return out;

        // Build IN (?, ?, ?, ...)
        StringBuilder sb = new StringBuilder();
        sb.append("SELECT * FROM holidays WHERE hol_date IN (");
        int i = 0;
        for (@SuppressWarnings("unused") LocalDate d : dates) {
            if (i++ > 0) sb.append(",");
            sb.append("?");
        }
        sb.append(")");

        try (PreparedStatement ps = con.prepareStatement(sb.toString())) {
            int idx = 1;
            for (LocalDate d : dates) {
                ps.setDate(idx++, Date.valueOf(d));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Holiday h = map(rs);
                    if (h != null && h.holDate != null) out.put(h.holDate, h);
                }
            }
        }
        return out;
    }

    public List<Holiday> listBetween(LocalDate start, LocalDate end) throws Exception {
        String sql = "SELECT * FROM holidays WHERE hol_date BETWEEN ? AND ? ORDER BY hol_date ASC";
        List<Holiday> out = new ArrayList<>();
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(start));
            ps.setDate(2, Date.valueOf(end));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    /** List only holidays that are enabled (apply=1). */
    public List<Holiday> listEnabledBetween(LocalDate start, LocalDate end) throws Exception {
        String sql = "SELECT * FROM holidays WHERE hol_date BETWEEN ? AND ? AND (enabled IS NULL OR enabled=1) ORDER BY hol_date ASC";
        List<Holiday> out = new ArrayList<>();
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(start));
            ps.setDate(2, Date.valueOf(end));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    public int countBetween(LocalDate start, LocalDate end) throws Exception {
        String sql = "SELECT COUNT(*) FROM holidays WHERE hol_date BETWEEN ? AND ? AND (enabled IS NULL OR enabled=1)";
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(start));
            ps.setDate(2, Date.valueOf(end));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /**
     * Insert if date doesn't exist; otherwise update.
     */
    public void upsert(Holiday h) throws Exception {
        if (h == null || h.holDate == null) throw new IllegalArgumentException("Holiday date is required.");

        Holiday existing = findByDate(h.holDate);
        if (existing == null) {
            String sql = "INSERT INTO holidays(hol_date, name, type, scope, notes, rate_multiplier, enabled) VALUES(?,?,?,?,?,?,?)";
            try (Connection con = DB.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setDate(1, Date.valueOf(h.holDate));
                ps.setString(2, safe(h.name));
                ps.setString(3, safe(h.type));
                ps.setString(4, safe(h.scope));
                ps.setString(5, safe(h.notes));
                ps.setDouble(6, h.rateMultiplier);
                ps.setInt(7, h.enabled ? 1 : 0);
                ps.executeUpdate();
            }
        } else {
            String sql = "UPDATE holidays SET name=?, type=?, scope=?, notes=?, rate_multiplier=?, enabled=? WHERE hol_date=?";
            try (Connection con = DB.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, safe(h.name));
                ps.setString(2, safe(h.type));
                ps.setString(3, safe(h.scope));
                ps.setString(4, safe(h.notes));
                ps.setDouble(5, h.rateMultiplier);
                ps.setInt(6, h.enabled ? 1 : 0);
                ps.setDate(7, Date.valueOf(h.holDate));
                ps.executeUpdate();
            }
        }
    }

    /** Toggle apply flag by id (used by HolidayReviewDialog Apply column). */
    public void setEnabled(int id, boolean enabled) throws Exception {
        String sql = "UPDATE holidays SET enabled=? WHERE id=?";
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, enabled ? 1 : 0);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    /** Toggle apply flag by date (convenience). */
    public void setEnabled(LocalDate date, boolean enabled) throws Exception {
        String sql = "UPDATE holidays SET enabled=? WHERE hol_date=?";
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, enabled ? 1 : 0);
            ps.setDate(2, Date.valueOf(date));
            ps.executeUpdate();
        }
    }

    public void deleteByDate(LocalDate date) throws Exception {
        String sql = "DELETE FROM holidays WHERE hol_date=?";
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            ps.executeUpdate();
        }
    }

    private String safe(String s) {
        return (s == null) ? "" : s.trim();
    }
}
