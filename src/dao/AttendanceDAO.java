package dao;

import java.sql.*;
import java.util.*;

/**
 * AttendanceDAO (robust totals)
 * Fixes: Unknown column 'hours' by auto-detecting the correct column names.
 */
public class AttendanceDAO {

    public static class Totals {
        public final double totalHours;
        public final double totalOT;
        public Totals(double totalHours, double totalOT) {
            this.totalHours = totalHours;
            this.totalOT = totalOT;
        }
    }

    // -----------------------------
    // Public APIs (many aliases to avoid compile issues)
    // -----------------------------

    /** Most common usage: totals for an employee between start and end (YYYY-MM-DD). */
    public static Totals totals(int empId, String start, String end) {
        return fetchTotals(empId, start, end);
    }

    /** Alternate parameter order (some codebases use this). */
    public static Totals totals(String start, String end, int empId) {
        return fetchTotals(empId, start, end);
    }

    /** Some code uses getTotals(...) returning Totals. */
    public static Totals getTotals(int empId, String start, String end) {
        return fetchTotals(empId, start, end);
    }

    public static Totals getTotals(String start, String end, int empId) {
        return fetchTotals(empId, start, end);
    }

    /** Some code expects double[] {hours, ot}. */
    public static double[] getTotalsArray(int empId, String start, String end) {
        Totals t = fetchTotals(empId, start, end);
        return new double[]{ t.totalHours, t.totalOT };
    }

    // Common helpers if you ever need them
    public static double getTotalHours(int empId, String start, String end) {
        return fetchTotals(empId, start, end).totalHours;
    }

    public static double getTotalOT(int empId, String start, String end) {
        return fetchTotals(empId, start, end).totalOT;
    }

    // -----------------------------
    // Core implementation
    // -----------------------------

    private static Totals fetchTotals(int empId, String start, String end) {
        try (Connection conn = getConnection()) {

            // Try common table names (adjust if yours is different)
            String table = findExistingTable(conn, Arrays.asList(
                    "attendance",
                    "attendances",
                    "employee_attendance",
                    "attendance_records"
            ));

            if (table == null) {
                throw new SQLException("Attendance table not found. Tried: attendance, attendances, employee_attendance, attendance_records");
            }

            Set<String> cols = getColumnsLower(conn, table);

            String empCol  = pickFirstExisting(cols, "emp_id", "employee_id", "empId", "employeeId");
            String dateCol = pickFirstExisting(cols, "date", "work_date", "att_date", "attendance_date", "day");

            // Hours column candidates
            String hoursCol = pickFirstExisting(cols,
                    "hours", "total_hours", "work_hours", "regular_hours", "hrs", "totalHours", "workHours"
            );

            // OT column candidates (optional)
            String otCol = pickFirstExisting(cols,
                    "ot_hours", "overtime_hours", "ot", "otHours", "overtimeHours", "total_ot", "total_ot_hours"
            );

            if (empCol == null || dateCol == null) {
                throw new SQLException("Attendance table columns missing. Need employee + date columns. Found columns: " + cols);
            }
            if (hoursCol == null) {
                throw new SQLException("Attendance table hours column not found (hours/total_hours/work_hours/etc). Found columns: " + cols);
            }

            // Build query (OT optional)
            String sql;
            if (otCol != null) {
                sql = "SELECT COALESCE(SUM(" + hoursCol + "),0) AS th, COALESCE(SUM(" + otCol + "),0) AS tot " +
                      "FROM " + table + " WHERE " + empCol + "=? AND " + dateCol + " BETWEEN ? AND ?";
            } else {
                sql = "SELECT COALESCE(SUM(" + hoursCol + "),0) AS th, 0 AS tot " +
                      "FROM " + table + " WHERE " + empCol + "=? AND " + dateCol + " BETWEEN ? AND ?";
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, empId);
                ps.setString(2, start);
                ps.setString(3, end);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new Totals(rs.getDouble("th"), rs.getDouble("tot"));
                    }
                }
            }

            return new Totals(0, 0);

        } catch (SQLException ex) {
            throw new RuntimeException("Attendance totals query failed: " + ex.getMessage(), ex);
        }
    }

    // -----------------------------
    // Connection helper (works with your existing DB.java even if method name differs)
    // -----------------------------
    private static Connection getConnection() throws SQLException {
        try {
            // Try DB.getConnection()
            try {
                return (Connection) DB.class.getMethod("getConnection").invoke(null);
            } catch (NoSuchMethodException ignore) {}

            // Try DB.get()
            try {
                return (Connection) DB.class.getMethod("get").invoke(null);
            } catch (NoSuchMethodException ignore) {}

            // Try DB.connect()
            try {
                return (Connection) DB.class.getMethod("connect").invoke(null);
            } catch (NoSuchMethodException ignore) {}

            throw new SQLException("No supported DB connection method found in dao.DB (tried getConnection(), get(), connect()).");
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException("Failed to obtain DB connection: " + e.getMessage(), e);
        }
    }

    // -----------------------------
    // Metadata helpers
    // -----------------------------
    private static Set<String> getColumnsLower(Connection conn, String table) throws SQLException {
        Set<String> cols = new HashSet<>();
        DatabaseMetaData md = conn.getMetaData();
        try (ResultSet rs = md.getColumns(conn.getCatalog(), null, table, null)) {
            while (rs.next()) {
                String c = rs.getString("COLUMN_NAME");
                if (c != null) cols.add(c.toLowerCase(Locale.ROOT));
            }
        }
        return cols;
    }

    private static String pickFirstExisting(Set<String> colsLower, String... candidates) {
        for (String c : candidates) {
            if (c == null) continue;
            if (colsLower.contains(c.toLowerCase(Locale.ROOT))) return c;
        }
        return null;
    }

    private static String findExistingTable(Connection conn, List<String> candidates) throws SQLException {
        DatabaseMetaData md = conn.getMetaData();
        String catalog = conn.getCatalog();

        for (String t : candidates) {
            try (ResultSet rs = md.getTables(catalog, null, t, new String[]{"TABLE"})) {
                if (rs.next()) return t;
            }
            // also try uppercase/lowercase variations
            try (ResultSet rs2 = md.getTables(catalog, null, t.toUpperCase(Locale.ROOT), new String[]{"TABLE"})) {
                if (rs2.next()) return t.toUpperCase(Locale.ROOT);
            }
            try (ResultSet rs3 = md.getTables(catalog, null, t.toLowerCase(Locale.ROOT), new String[]{"TABLE"})) {
                if (rs3.next()) return t.toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }
}
