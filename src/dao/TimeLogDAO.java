package dao;

import model.AttendanceSummary;
import model.WorkSummary;
import model.Holiday;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.Locale;

public class TimeLogDAO {

    // A safe cutoff that "zero dates" will always be less than.
    private static final String SAFE_CUTOFF = "1971-01-02 00:00:00";
    private static final Timestamp SAFE_CUTOFF_TS = Timestamp.valueOf(SAFE_CUTOFF);
    private static final ZoneId PH_ZONE = ZoneId.of("Asia/Manila");
    private static Timestamp nowPH() {
        // Store wall-clock PH time in the DB (works best with DATETIME columns)
        return Timestamp.valueOf(LocalDateTime.now(PH_ZONE));
    }


    // =========================
    // Schema detection (so Logbook + Payroll always match)
    // =========================

    private static volatile Columns CACHED;

    private static final class Columns {
        final String tlId;
        final String tlEmpFk;
        final String tlTimeIn;
        final String tlLunchOut;
        final String tlLunchIn;
        final String tlTimeOut;
        final String empPk;

        Columns(String tlId, String tlEmpFk, String tlTimeIn, String tlLunchOut, String tlLunchIn, String tlTimeOut, String empPk) {
            this.tlId = tlId;
            this.tlEmpFk = tlEmpFk;
            this.tlTimeIn = tlTimeIn;
            this.tlLunchOut = tlLunchOut;
            this.tlLunchIn = tlLunchIn;
            this.tlTimeOut = tlTimeOut;
            this.empPk = empPk;
        }
    }

    private static Columns cols(Connection con) throws SQLException {
        Columns c = CACHED;
        if (c != null) return c;

        synchronized (TimeLogDAO.class) {
            c = CACHED;
            if (c != null) return c;

            String tlId = pickColumn(con, "time_logs", "id", "log_id", "time_log_id");
            String tlEmpFk = pickColumn(con, "time_logs", "emp_id", "employee_id", "empno", "employee_no");
            String tlTimeIn = pickColumn(con, "time_logs", "time_in", "timein", "clock_in");
            String tlLunchOut = pickColumnOptional(con, "time_logs", "lunch_out", "lunchout");
            String tlLunchIn = pickColumnOptional(con, "time_logs", "lunch_in", "lunchin");
            String tlTimeOut = pickColumn(con, "time_logs", "time_out", "timeout", "clock_out");

            String empPk = pickColumn(con, "employees", "emp_id", "employee_id", "id", "empno", "employee_no");

            c = new Columns(tlId, tlEmpFk, tlTimeIn, tlLunchOut, tlLunchIn, tlTimeOut, empPk);
            CACHED = c;
            return c;
        }
    }

    private static boolean columnExists(Connection con, String table, String column) throws SQLException {
        DatabaseMetaData md = con.getMetaData();

        try (ResultSet rs = md.getColumns(con.getCatalog(), null, table, column)) {
            if (rs.next()) return true;
        }
        // case-insensitive fallback
        try (ResultSet rs = md.getColumns(con.getCatalog(), null,
                table.toUpperCase(Locale.ROOT), column.toUpperCase(Locale.ROOT))) {
            return rs.next();
        }
    }

    private static String pickColumn(Connection con, String table, String... candidates) throws SQLException {
        for (String c : candidates) {
            if (c == null) continue;
            if (columnExists(con, table, c)) return c;
        }
        throw new SQLException("No matching column found in table '" + table + "' for candidates " + Arrays.toString(candidates));
    }

    private static String pickColumnOptional(Connection con, String table, String... candidates) throws SQLException {
        for (String c : candidates) {
            if (c == null) continue;
            if (columnExists(con, table, c)) return c;
        }
        return null;
    }

    // =========================
    // Reflection helpers (safe setters)
    // =========================

    private static void setAny(Object target, Object value, String... possibleFieldsOrSetters) {
        if (target == null || possibleFieldsOrSetters == null) return;
        Class<?> c = target.getClass();

        // Try setters first
        for (String name : possibleFieldsOrSetters) {
            if (name == null || name.isEmpty()) continue;
            String setter = name.startsWith("set") ? name : ("set" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
            for (Method m : c.getMethods()) {
                if (!m.getName().equals(setter)) continue;
                if (m.getParameterCount() != 1) continue;
                try {
                    Class<?> p = m.getParameterTypes()[0];
                    Object coerced = coerce(value, p);
                    m.invoke(target, coerced);
                    return;
                } catch (Exception ignore) {}
            }
        }

        // Try fields
        for (String fName : possibleFieldsOrSetters) {
            if (fName == null || fName.isEmpty()) continue;
            try {
                Field f = null;
                Class<?> cur = c;
                while (cur != null && cur != Object.class) {
                    try {
                        f = cur.getDeclaredField(fName);
                        break;
                    } catch (NoSuchFieldException ex) {
                        cur = cur.getSuperclass();
                    }
                }
                if (f == null) continue;
                f.setAccessible(true);
                Object coerced = coerce(value, f.getType());
                f.set(target, coerced);
                return;
            } catch (Exception ignore) {}
        }
    }

    private static Object coerce(Object value, Class<?> targetType) {
        if (value == null) {
            if (targetType == double.class) return 0.0;
            if (targetType == int.class) return 0;
            if (targetType == long.class) return 0L;
            if (targetType == boolean.class) return false;
            return null;
        }

        if (targetType.isInstance(value)) return value;

        if (targetType == String.class) return String.valueOf(value);

        if (value instanceof Number n) {
            if (targetType == double.class || targetType == Double.class) return n.doubleValue();
            if (targetType == int.class || targetType == Integer.class) return n.intValue();
            if (targetType == long.class || targetType == Long.class) return n.longValue();
        }

        return value;
    }

    // =========================
    // Employee existence check
    // =========================

    private static boolean employeeExists(int empId) throws Exception {
        try (Connection con = DB.getConnection()) {
            Columns c = cols(con);
            String sql = "SELECT 1 FROM employees WHERE " + c.empPk + "=? LIMIT 1";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, empId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        }
    }

    // =========================
    // Portal status + actions
    // =========================

    public enum PortalState {
        TIMED_OUT,
        TIMED_IN,
        ON_LUNCH
    }

    public static PortalState getPortalState(int empId) throws Exception {
        if (empId <= 0) return PortalState.TIMED_OUT;

        try (Connection con = DB.getConnection()) {
            Columns c = cols(con);

            String lunchSelect = (c.tlLunchOut != null ? ", " + c.tlLunchOut + " AS lunch_out" : ", NULL AS lunch_out") +
                    (c.tlLunchIn != null ? ", " + c.tlLunchIn + " AS lunch_in" : ", NULL AS lunch_in");

            String sql =
                    "SELECT " + c.tlTimeIn + " AS time_in, " + c.tlTimeOut + " AS time_out" + lunchSelect + " FROM time_logs " +
                    "WHERE " + c.tlEmpFk + "=? AND " + c.tlTimeIn + " IS NOT NULL " +
                    "AND (" + c.tlTimeOut + " IS NULL OR " + c.tlTimeOut + " < ?) " +
                    "ORDER BY COALESCE(" + c.tlTimeOut + ", " + c.tlTimeIn + ") DESC, " + c.tlId + " DESC LIMIT 1";

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, empId);
                ps.setTimestamp(2, SAFE_CUTOFF_TS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return PortalState.TIMED_OUT;
                    Timestamp tout = rs.getTimestamp("time_out");
                    Timestamp lunchOut = rs.getTimestamp("lunch_out");
                    Timestamp lunchIn = rs.getTimestamp("lunch_in");
                    if (tout == null || tout.before(SAFE_CUTOFF_TS)) {
                        if (lunchOut != null && lunchIn == null) return PortalState.ON_LUNCH;
                        return PortalState.TIMED_IN;
                    }
                    return PortalState.TIMED_OUT;
                }
            }
        }
    }

    public static boolean hasOpenTimeIn(int empId) throws Exception {
        PortalState state = getPortalState(empId);
        return state == PortalState.TIMED_IN || state == PortalState.ON_LUNCH;
    }

    public static boolean isOnLunchBreak(int empId) throws Exception {
        return getPortalState(empId) == PortalState.ON_LUNCH;
    }

    public static void timeIn(int empId) throws Exception {
        if (empId <= 0) throw new IllegalArgumentException("Invalid employee id.");

        if (!employeeExists(empId)) {
            throw new Exception("Employee record not found (id=" + empId + ").\n" +
                    "This usually means the portal is using Employee No instead of the employees primary key.");
        }

        PortalState state = getPortalState(empId);
        if (state == PortalState.ON_LUNCH) {
            endLunch(empId);
            return;
        }
        if (state == PortalState.TIMED_IN) {
            throw new Exception("You are already TIMED IN.");
        }

        try (Connection con = DB.getConnection()) {
            Columns c = cols(con);

            String sql = "INSERT INTO time_logs(" + c.tlEmpFk + ", " + c.tlTimeIn + ", " + c.tlTimeOut + ") VALUES(?, ?, NULL)";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, empId);
                ps.setTimestamp(2, nowPH());
                ps.executeUpdate();
            }
        }
    }

    public static void startLunch(int empId) throws Exception {
        if (empId <= 0) throw new IllegalArgumentException("Invalid employee id.");
        if (!employeeExists(empId)) throw new Exception("Employee record not found (id=" + empId + ").");

        try (Connection con = DB.getConnection()) {
            Columns c = cols(con);
            if (c.tlLunchOut == null || c.tlLunchIn == null) {
                throw new Exception("Database is missing lunch columns. Run the ALTER TABLE script first.");
            }
            if (getPortalState(empId) != PortalState.TIMED_IN) {
                throw new Exception("No active work session found to start lunch.");
            }
            String pickSql =
                    "SELECT " + c.tlId + " FROM time_logs " +
                    "WHERE " + c.tlEmpFk + "=? AND " + c.tlTimeIn + " IS NOT NULL AND (" + c.tlTimeOut + " IS NULL OR " + c.tlTimeOut + " < ?) " +
                    "AND " + c.tlLunchOut + " IS NULL ORDER BY COALESCE(" + c.tlTimeOut + ", " + c.tlTimeIn + ") DESC, " + c.tlId + " DESC LIMIT 1";
            Integer logId = null;
            try (PreparedStatement ps = con.prepareStatement(pickSql)) {
                ps.setInt(1, empId);
                ps.setTimestamp(2, SAFE_CUTOFF_TS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) logId = rs.getInt(1);
                }
            }
            if (logId == null) throw new Exception("No active TIME IN found to start lunch.");
            String updSql = "UPDATE time_logs SET " + c.tlLunchOut + "=? WHERE " + c.tlId + "=?";
            try (PreparedStatement ps = con.prepareStatement(updSql)) {
                ps.setTimestamp(1, nowPH());
                ps.setInt(2, logId);
                if (ps.executeUpdate() == 0) throw new Exception("Lunch start failed (record not updated).");
            }
        }
    }

    public static void endLunch(int empId) throws Exception {
        if (empId <= 0) throw new IllegalArgumentException("Invalid employee id.");
        if (!employeeExists(empId)) throw new Exception("Employee record not found (id=" + empId + ").");

        try (Connection con = DB.getConnection()) {
            Columns c = cols(con);
            if (c.tlLunchOut == null || c.tlLunchIn == null) {
                throw new Exception("Database is missing lunch columns. Run the ALTER TABLE script first.");
            }
            if (getPortalState(empId) != PortalState.ON_LUNCH) {
                throw new Exception("No active lunch break found to end.");
            }
            String pickSql =
                    "SELECT " + c.tlId + " FROM time_logs WHERE " + c.tlEmpFk + "=? AND " + c.tlLunchOut + " IS NOT NULL AND " + c.tlLunchIn + " IS NULL " +
                    "AND (" + c.tlTimeOut + " IS NULL OR " + c.tlTimeOut + " < ?) ORDER BY COALESCE(" + c.tlTimeOut + ", " + c.tlTimeIn + ") DESC, " + c.tlId + " DESC LIMIT 1";
            Integer logId = null;
            try (PreparedStatement ps = con.prepareStatement(pickSql)) {
                ps.setInt(1, empId);
                ps.setTimestamp(2, SAFE_CUTOFF_TS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) logId = rs.getInt(1);
                }
            }
            if (logId == null) throw new Exception("No active lunch break found to end.");
            String updSql = "UPDATE time_logs SET " + c.tlLunchIn + "=? WHERE " + c.tlId + "=?";
            try (PreparedStatement ps = con.prepareStatement(updSql)) {
                ps.setTimestamp(1, nowPH());
                ps.setInt(2, logId);
                if (ps.executeUpdate() == 0) throw new Exception("Lunch return failed (record not updated).");
            }
        }
    }

    public static void timeOut(int empId) throws Exception {
        if (empId <= 0) throw new IllegalArgumentException("Invalid employee id.");

        if (!employeeExists(empId)) {
            throw new Exception("Employee record not found (id=" + empId + ").");
        }

        try (Connection con = DB.getConnection()) {
            Columns c = cols(con);

            // 1) Find the latest open TIME IN record
            String pickSql =
                    "SELECT " + c.tlId + " FROM time_logs " +
                            "WHERE " + c.tlEmpFk + "=? AND " + c.tlTimeIn + " IS NOT NULL " +
                            "AND (" + c.tlTimeOut + " IS NULL OR " + c.tlTimeOut + " < ?) " +
                            "ORDER BY COALESCE(" + c.tlTimeOut + ", " + c.tlTimeIn + ") DESC, " + c.tlId + " DESC " +
                            "LIMIT 1";

            Integer logId = null;
            try (PreparedStatement ps = con.prepareStatement(pickSql)) {
                ps.setInt(1, empId);
                ps.setTimestamp(2, SAFE_CUTOFF_TS);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        logId = rs.getInt(1);
                    }
                }
            }

            if (logId == null) {
                throw new Exception("No active TIME IN found to TIME OUT.");
            }

            // 2) Update that record (avoid MySQL target-table update errors)
            Timestamp now = nowPH();
            StringBuilder updSql = new StringBuilder("UPDATE time_logs SET ");
            if (c.tlLunchOut != null && c.tlLunchIn != null) {
                updSql.append(c.tlLunchIn)
                        .append("=CASE WHEN ").append(c.tlLunchOut).append(" IS NOT NULL AND ").append(c.tlLunchIn).append(" IS NULL THEN ? ELSE ").append(c.tlLunchIn).append(" END, ");
            }
            updSql.append(c.tlTimeOut).append("=? WHERE ").append(c.tlId).append("=?");

            try (PreparedStatement ps = con.prepareStatement(updSql.toString())) {
                int ix = 1;
                if (c.tlLunchOut != null && c.tlLunchIn != null) {
                    ps.setTimestamp(ix++, now);
                }
                ps.setTimestamp(ix++, now);
                ps.setInt(ix, logId);
                int updated = ps.executeUpdate();
                if (updated == 0) {
                    throw new Exception("TIME OUT failed (record not updated).");
                }
            }
        }
    }

    // =========================
    // Recent Activity (EmployeeTimePanel depends on this)
    // =========================

    public static class ActivityRow {
        public final String whenText;
        public final String action;
        public final int logId;

        public ActivityRow(String whenText, String action, int logId) {
            this.whenText = whenText;
            this.action = action;
            this.logId = logId;
        }
    }

    public static List<ActivityRow> getRecentActivity(int empId, int limit) throws Exception {
        if (empId <= 0) return Collections.emptyList();

        ArrayList<ActivityRow> out = new ArrayList<>();
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");

        // Build events so a single DB row can produce BOTH TIME IN and TIME OUT records.
        class Event {
            final LocalDateTime dt;
            final ActivityRow row;
            Event(LocalDateTime dt, ActivityRow row) { this.dt = dt; this.row = row; }
        }
        ArrayList<Event> events = new ArrayList<>();

        try (Connection con = DB.getConnection()) {
            Columns c = cols(con);

            // Fetch more base rows because each row can expand into up to 2 events (IN + OUT).
            int fetch = Math.max(10, limit) * 2 + 10;

            String sql =
                    "SELECT " + c.tlId + " AS id, " + c.tlTimeIn + " AS time_in, " + c.tlTimeOut + " AS time_out" +
                            (c.tlLunchOut != null ? ", " + c.tlLunchOut + " AS lunch_out" : ", NULL AS lunch_out") +
                            (c.tlLunchIn != null ? ", " + c.tlLunchIn + " AS lunch_in" : ", NULL AS lunch_in") +
                            " FROM time_logs WHERE " + c.tlEmpFk + "=? " +
                            "ORDER BY COALESCE(" + c.tlTimeOut + ", " + c.tlTimeIn + ") DESC, " + c.tlId + " DESC LIMIT ?";

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, empId);
                ps.setInt(2, fetch);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        Timestamp tin = rs.getTimestamp("time_in");
                        Timestamp lunchOut = rs.getTimestamp("lunch_out");
                        Timestamp lunchIn = rs.getTimestamp("lunch_in");
                        Timestamp tout = rs.getTimestamp("time_out");

                        // Add TIME IN event (if present)
                        if (tin != null) {
                            LocalDateTime dt = tin.toLocalDateTime();
                            events.add(new Event(dt, new ActivityRow(dt.format(fmt), "TIME IN", id)));
                        }

                        if (lunchOut != null) {
                            LocalDateTime dt = lunchOut.toLocalDateTime();
                            events.add(new Event(dt, new ActivityRow(dt.format(fmt), "LUNCH OUT", id)));
                        }

                        if (lunchIn != null) {
                            LocalDateTime dt = lunchIn.toLocalDateTime();
                            events.add(new Event(dt, new ActivityRow(dt.format(fmt), "LUNCH IN", id)));
                        }

                        // Add TIME OUT event (if present)
                        if (tout != null) {
                            LocalDateTime dt = tout.toLocalDateTime();
                            events.add(new Event(dt, new ActivityRow(dt.format(fmt), "TIME OUT", id)));
                        }
                    }
                }
            }
        }

        // Sort newest first
        events.sort((a, b) -> b.dt.compareTo(a.dt));

        int max = Math.max(1, limit);
        for (int i = 0; i < events.size() && out.size() < max; i++) {
            out.add(events.get(i).row);
        }

        return out;
    }

    /**
     * Recent Activity between two dates (inclusive), expanded as TIME IN / TIME OUT events,
     * sorted newest-first. This is used by the Employee Portal pay-period filter.
     */
    public static List<ActivityRow> getActivityBetween(int empId, java.time.LocalDate start, java.time.LocalDate end, int limit) throws Exception {
        if (empId <= 0 || start == null || end == null) return Collections.emptyList();

        ArrayList<ActivityRow> out = new ArrayList<>();
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");

        Timestamp startTs = Timestamp.valueOf(start.atStartOfDay());
        // inclusive end-of-day in PH time
        Timestamp endTs = Timestamp.valueOf(end.plusDays(1).atStartOfDay().minusNanos(1));

        class Event {
            final LocalDateTime dt;
            final ActivityRow row;
            Event(LocalDateTime dt, ActivityRow row) { this.dt = dt; this.row = row; }
        }
        ArrayList<Event> events = new ArrayList<>();

        try (Connection con = DB.getConnection()) {
            Columns c = cols(con);

            int fetch = Math.max(10, limit) * 2 + 20;

            // Include rows where either time_in or time_out falls within the range,
            // or where the shift overlaps the range.
            String sql =
                    "SELECT " + c.tlId + " AS id, " + c.tlTimeIn + " AS time_in, " + c.tlTimeOut + " AS time_out" +
                    (c.tlLunchOut != null ? ", " + c.tlLunchOut + " AS lunch_out" : ", NULL AS lunch_out") +
                    (c.tlLunchIn != null ? ", " + c.tlLunchIn + " AS lunch_in" : ", NULL AS lunch_in") +
                    " FROM time_logs WHERE " + c.tlEmpFk + "=? AND (" +
                    "   (" + c.tlTimeIn + " IS NOT NULL AND " + c.tlTimeIn + " BETWEEN ? AND ?) OR " +
                    "   (" + c.tlTimeOut + " IS NOT NULL AND " + c.tlTimeOut + " BETWEEN ? AND ?) OR " +
                    "   (" + c.tlTimeIn + " IS NOT NULL AND " + c.tlTimeOut + " IS NOT NULL AND " + c.tlTimeIn + " < ? AND " + c.tlTimeOut + " > ?)" +
                    ") " +
                    "ORDER BY COALESCE(" + c.tlTimeOut + ", " + c.tlTimeIn + ") DESC, " + c.tlId + " DESC LIMIT ?";

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, empId);
                ps.setTimestamp(2, startTs);
                ps.setTimestamp(3, endTs);
                ps.setTimestamp(4, startTs);
                ps.setTimestamp(5, endTs);
                ps.setTimestamp(6, endTs);
                ps.setTimestamp(7, startTs);
                ps.setInt(8, fetch);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        Timestamp tin = rs.getTimestamp("time_in");
                        Timestamp lunchOut = rs.getTimestamp("lunch_out");
                        Timestamp lunchIn = rs.getTimestamp("lunch_in");
                        Timestamp tout = rs.getTimestamp("time_out");

                        if (tin != null) {
                            LocalDateTime dt = tin.toLocalDateTime();
                            ActivityRow r = new ActivityRow(fmt.format(dt), "TIME IN", id);
                            events.add(new Event(dt, r));
                        }
                        if (lunchOut != null) {
                            LocalDateTime dt = lunchOut.toLocalDateTime();
                            events.add(new Event(dt, new ActivityRow(fmt.format(dt), "LUNCH OUT", id)));
                        }
                        if (lunchIn != null) {
                            LocalDateTime dt = lunchIn.toLocalDateTime();
                            events.add(new Event(dt, new ActivityRow(fmt.format(dt), "LUNCH IN", id)));
                        }
                        if (tout != null) {
                            LocalDateTime dt = tout.toLocalDateTime();
                            ActivityRow r = new ActivityRow(fmt.format(dt), "TIME OUT", id);
                            events.add(new Event(dt, r));
                        }
                    }
                }
            }
        }

        events.sort((a, b) -> b.dt.compareTo(a.dt));

        for (Event e : events) {
            LocalDate d = e.dt.toLocalDate();
            if (d.isBefore(start) || d.isAfter(end)) continue;
            out.add(e.row);
            if (out.size() >= limit) break;
        }
        return out;
    }



    private static long lunchMinutes(Columns c, ResultSet rs) throws SQLException {
        if (c.tlLunchOut == null || c.tlLunchIn == null) return 0L;
        Timestamp lunchOut = rs.getTimestamp("lunch_out");
        Timestamp lunchIn = rs.getTimestamp("lunch_in");
        if (lunchOut == null || lunchIn == null) return 0L;
        long mins = Duration.between(lunchOut.toInstant(), lunchIn.toInstant()).toMinutes();
        return Math.max(0L, mins);
    }

    // =========================
    // Attendance Summary
    // =========================

    public static AttendanceSummary getAttendanceSummary(int empId, LocalDate start, LocalDate end) throws Exception {
        AttendanceSummary out = new AttendanceSummary();
        if (empId <= 0 || start == null || end == null) return out;

        double totalHours = 0.0;
        Set<LocalDate> days = new HashSet<>();

        try (Connection con = DB.getConnection()) {
            Columns c = cols(con);

            String sql =
                    "SELECT " + c.tlTimeIn + " AS time_in, " + c.tlTimeOut + " AS time_out" +
                            (c.tlLunchOut != null ? ", " + c.tlLunchOut + " AS lunch_out" : ", NULL AS lunch_out") +
                            (c.tlLunchIn != null ? ", " + c.tlLunchIn + " AS lunch_in" : ", NULL AS lunch_in") +
                            " FROM time_logs WHERE " + c.tlEmpFk + "=? ORDER BY " + c.tlTimeIn + " ASC";

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, empId);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Timestamp tin = rs.getTimestamp("time_in");
                        Timestamp tout = rs.getTimestamp("time_out");
                        if (tin == null || tout == null) continue;

                        LocalDate d = tin.toLocalDateTime().toLocalDate();
                        if (d.isBefore(start) || d.isAfter(end)) continue;

                        days.add(d);
                        long workMin = Duration.between(tin.toInstant(), tout.toInstant()).toMinutes() - lunchMinutes(c, rs);
                        double h = Math.max(0L, workMin) / 60.0;
                        totalHours += h;
                    }
                }
            }
        }

        setAny(out, totalHours, "totalHours", "hours");
        setAny(out, days.size(), "daysWorked", "workedDays", "days");

        return out;
    }

    // =========================
    // Work Summary (Holiday wiring matches LogbookPanel now)
    // =========================

    public static WorkSummary computeWorkSummary(int empId, LocalDate start, LocalDate end) throws Exception {
        WorkSummary ws = new WorkSummary();
        if (empId <= 0 || start == null || end == null) return ws;

        double regDayHours = 0.0;
        double regDayOT = 0.0;

        double regHolHours = 0.0;
        double regHolOT = 0.0;

        double specHolHours = 0.0;
        double specHolOT = 0.0;

        Set<LocalDate> workedDays = new TreeSet<>();

        // ✅ Cache holidays and IMPORTANT: reuse the SAME connection for holiday lookups
        // (avoid opening a new connection per day when on Azure)
        HolidayDAO holidayDAO = new HolidayDAO();
        Map<LocalDate, String> holidayTypeByDate = new HashMap<>();

        try (Connection con = DB.getConnection()) {
            Columns c = cols(con);

            String sql =
                    "SELECT " + c.tlTimeIn + " AS time_in, " + c.tlTimeOut + " AS time_out" +
                            (c.tlLunchOut != null ? ", " + c.tlLunchOut + " AS lunch_out" : ", NULL AS lunch_out") +
                            (c.tlLunchIn != null ? ", " + c.tlLunchIn + " AS lunch_in" : ", NULL AS lunch_in") +
                            " FROM time_logs " +
                            "WHERE " + c.tlEmpFk + "=? AND " + c.tlTimeIn + ">=? AND " + c.tlTimeIn + "<? " +
                            "ORDER BY " + c.tlTimeIn;

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, empId);
                ps.setTimestamp(2, Timestamp.valueOf(start.atStartOfDay()));
                ps.setTimestamp(3, Timestamp.valueOf(end.plusDays(1).atStartOfDay()));

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Timestamp tin = rs.getTimestamp("time_in");
                        Timestamp tout = rs.getTimestamp("time_out");
                        if (tin == null || tout == null) continue;

                        LocalDateTime in = tin.toLocalDateTime();
                        LocalDateTime out = tout.toLocalDateTime();
                        if (out.isBefore(in)) continue;

                        LocalDate day = in.toLocalDate();
                        workedDays.add(day);

                        long minutes = Duration.between(in, out).toMinutes() - lunchMinutes(c, rs);
                        if (minutes < 0) minutes = 0;

                        // ✅ Payroll uses 30-minute blocks only (floor to 0 or 30 mins).
                        // This prevents tiny minutes from accumulating across days (e.g., 6h59 + 0h2 should NOT become 7h0).
                        minutes = (minutes / 30) * 30;

                        long baseMin = Math.min(8L * 60L, minutes);   // up to 8h
                        long otMin = Math.max(0L, minutes - 8L * 60L);

                        double base = baseMin / 60.0;
                        double ot = otMin / 60.0;

                        // ✅ Same holiday lookup + normalization as LogbookPanel
                        String normType = holidayTypeByDate.get(day);
                        if (!holidayTypeByDate.containsKey(day)) {
                            try {
                                Holiday h = holidayDAO.findByDate(con, day);
                                if (h != null && !h.enabled) h = null;
                                normType = (h != null && h.type != null) ? normalizeHolidayType(h.type) : null;
                            } catch (Exception ignored) {
                                normType = null;
                            }
                            // store even if null (avoid repeated calls)
                            holidayTypeByDate.put(day, normType);
                        }

                        if ("REGULAR".equals(normType)) {
                            regHolHours += base;
                            regHolOT += ot;
                        } else if ("SPECIAL".equals(normType)) {
                            specHolHours += base;
                            specHolOT += ot;
                        } else {
                            regDayHours += base;
                            regDayOT += ot;
                        }
                    }
                }
            }
        }

        double totalHours = regDayHours + regHolHours + specHolHours;
        double totalOT = regDayOT + regHolOT + specHolOT;

        // ✅ write to the exact fields PayrollRunDialog reads
        setAny(ws, regDayHours, "regularDayHours");
        setAny(ws, regDayOT, "regularDayOtHours");

        setAny(ws, regHolHours, "regularHolidayHours");
        setAny(ws, regHolOT, "regularHolidayOtHours");

        setAny(ws, specHolHours, "specialHolidayHours");
        setAny(ws, specHolOT, "specialHolidayOtHours");

        setAny(ws, totalHours, "totalHours");
        setAny(ws, totalOT, "otHours");

        setAny(ws, workedDays, "workedDays", "datesWorked", "daysWorked");

        return ws;
    }

    // ✅ Copied behavior from LogbookPanel.normalizeHolidayType(...)
    private static String normalizeHolidayType(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toUpperCase(Locale.ROOT);

        // Covers: "REGULAR", "REGULAR HOLIDAY", "REG HOL", "RH"
        if (s.contains("REG")) return "REGULAR";
        if (s.equals("RH")) return "REGULAR";

        // Covers: "SPECIAL", "SPECIAL HOLIDAY", "SP HOL", "SH"
        if (s.contains("SPEC")) return "SPECIAL";
        if (s.equals("SH")) return "SPECIAL";

        return null;
    }
}
