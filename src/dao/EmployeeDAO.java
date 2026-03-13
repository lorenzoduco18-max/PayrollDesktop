package dao;

import model.Employee;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EmployeeDAO {

    /* =========================================================
       ✅ Used by LoginFrame: EmployeeDAO.getByUserId(userId)
       FIX: Support BOTH link styles:
         New: users.emp_id -> employees.emp_id
         Old: employees.user_id -> users.user_id
       ========================================================= */
    public static Employee getByUserId(int userId) {
        try (Connection con = DB.getConnection()) {

            String empIdCol = pickEmpIdCol(con);

            // users primary key column
            String usersUserIdCol = hasColumn(con, "users", "user_id") ? "user_id"
                    : (hasColumn(con, "users", "id") ? "id" : null);

            // link column on users
            String usersEmpIdCol = hasColumn(con, "users", "emp_id") ? "emp_id"
                    : (hasColumn(con, "users", "employee_id") ? "employee_id"
                    : (hasColumn(con, "users", "empId") ? "empId" : null));

            // old link column on employees
            String employeesUserIdCol = hasColumn(con, "employees", "user_id") ? "user_id"
                    : (hasColumn(con, "employees", "userId") ? "userId" : null);

            if (usersUserIdCol == null) return null;

            // ✅ Preferred: users.emp_id -> employees.emp_id
            if (usersEmpIdCol != null) {
                String sql = "SELECT e.* FROM employees e " +
                        "JOIN users u ON u." + usersEmpIdCol + " = e." + empIdCol + " " +
                        "WHERE u." + usersUserIdCol + "=? LIMIT 1";
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setInt(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) return mapEmployee(rs, empIdCol);
                    }
                }
            }

            // ✅ Fallback: employees.user_id (old schema)
            if (employeesUserIdCol != null) {
                String sql = "SELECT * FROM employees WHERE " + employeesUserIdCol + "=? LIMIT 1";
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setInt(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) return mapEmployee(rs, empIdCol);
                    }
                }
            }

            return null;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /* =========================================================
       ✅ Used by payroll/employee screens
       ========================================================= */
    public static List<Employee> listActive() {
        try (Connection con = DB.getConnection()) {
            String empIdCol = pickEmpIdCol(con);

            if (!hasColumn(con, "employees", "status")) {
                return listAll();
            }

            String sql = "SELECT * FROM employees WHERE status='ACTIVE' ORDER BY " + empIdCol + " DESC";
            try (PreparedStatement ps = con.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                List<Employee> out = new ArrayList<>();
                while (rs.next()) out.add(mapEmployee(rs, empIdCol));
                return out;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public static List<Employee> listAll() {
        try (Connection con = DB.getConnection()) {
            String empIdCol = pickEmpIdCol(con);
            String sql = "SELECT * FROM employees ORDER BY " + empIdCol + " DESC";
            try (PreparedStatement ps = con.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                List<Employee> out = new ArrayList<>();
                while (rs.next()) out.add(mapEmployee(rs, empIdCol));
                return out;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // ✅ Fixes: “listEmployees(String) is undefined …”
    public List<Employee> listEmployees(String keyword) throws Exception {
        try (Connection con = DB.getConnection()) {
            String empIdCol = pickEmpIdCol(con);

            // Build a robust, multi-field search that supports multi-word queries.
            // Each word must match at least one of the searchable columns.
            List<String> searchable = new ArrayList<>();
            if (hasColumn(con, "employees", "emp_no")) searchable.add("emp_no");
            if (hasColumn(con, "employees", "first_name")) searchable.add("first_name");
            if (hasColumn(con, "employees", "middle_name")) searchable.add("middle_name");
            if (hasColumn(con, "employees", "last_name")) searchable.add("last_name");
            if (hasColumn(con, "employees", "position")) searchable.add("position");
            if (hasColumn(con, "employees", "pay_type")) searchable.add("pay_type");
            if (hasColumn(con, "employees", "status")) searchable.add("status");

            // Optional columns (only used if they exist in your schema)
            if (hasColumn(con, "employees", "company_contact_no")) searchable.add("company_contact_no");
            if (hasColumn(con, "employees", "personal_contact_no")) searchable.add("personal_contact_no");
            if (hasColumn(con, "employees", "company_email")) searchable.add("company_email");
            if (hasColumn(con, "employees", "personal_email")) searchable.add("personal_email");
            if (hasColumn(con, "employees", "email")) searchable.add("email");
            if (hasColumn(con, "employees", "address")) searchable.add("address");

            String sql = "SELECT * FROM employees";
            boolean hasKey = keyword != null && !keyword.trim().isEmpty();

            List<String> tokens = new ArrayList<>();
            if (hasKey) {
                for (String t : keyword.trim().split("\\s+")) {
                    if (!t.isBlank()) tokens.add(t.toLowerCase());
                }
                // Limit tokens to avoid excessive parameters
                if (tokens.size() > 6) tokens = tokens.subList(0, 6);

                StringBuilder where = new StringBuilder();
                for (int i = 0; i < tokens.size(); i++) {
                    if (i > 0) where.append(" AND ");
                    where.append("(");
                    for (int c = 0; c < searchable.size(); c++) {
                        if (c > 0) where.append(" OR ");
                        where.append("LOWER(").append(searchable.get(c)).append(") LIKE ?");
                    }
                    where.append(")");
                }
                if (where.length() > 0) {
                    sql += " WHERE " + where;
                }
            }

            sql += " ORDER BY " + empIdCol + " DESC";

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                if (hasKey && !tokens.isEmpty() && !searchable.isEmpty()) {
                    int p = 1;
                    for (String tok : tokens) {
                        String k = "%" + tok + "%";
                        for (int c = 0; c < searchable.size(); c++) {
                            ps.setString(p++, k);
                        }
                    }
                }

                List<Employee> out = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(mapEmployee(rs, empIdCol));
                    }
                }
                return out;
            }
        }
    }

    public static Employee getById(int empId) throws Exception {
        try (Connection con = DB.getConnection()) {
            String empIdCol = pickEmpIdCol(con);
            String sql = "SELECT * FROM employees WHERE " + empIdCol + "=? LIMIT 1";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, empId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    return mapEmployee(rs, empIdCol);
                }
            }
        }
    }

    public static Employee getById(Integer empId) throws Exception {
        if (empId == null) return null;
        return getById(empId.intValue());
    }

    public static void deactivate(Integer empId) throws Exception {
        if (empId == null) throw new Exception("No employee selected.");
        setActive(empId, false);
    }

    public static void reactivate(Integer empId) throws Exception {
        if (empId == null) throw new Exception("No employee selected.");
        setActive(empId, true);
    }

    public void setEmployeeActive(int empId, boolean active) throws Exception {
        setActive(empId, active);
    }

    public void deleteEmployee(int empId) throws Exception {
        try (Connection con = DB.getConnection()) {
            con.setAutoCommit(false);
            try {
                // 1) Payslips (FK-safe)
                if (hasTable(con, "payslips") && hasColumn(con, "payslips", "emp_id")) {
                    try (PreparedStatement ps = con.prepareStatement("DELETE FROM payslips WHERE emp_id=?")) {
                        ps.setInt(1, empId);
                        ps.executeUpdate();
                    }
                }

                // 2) Time logs / logbook (tries common table names for compatibility)
                String[] timeTables = {"time_logs", "time_log", "timelogs", "logbook", "attendance_logs", "employee_logs", "time_entries"};
                for (String t : timeTables) {
                    if (!hasTable(con, t)) continue;

                    String col = hasColumn(con, t, "emp_id") ? "emp_id"
                            : (hasColumn(con, t, "employee_id") ? "employee_id" : null);

                    if (col == null) continue;

                    String sql = "DELETE FROM " + t + " WHERE " + col + "=?";
                    try (PreparedStatement ps = con.prepareStatement(sql)) {
                        ps.setInt(1, empId);
                        ps.executeUpdate();
                    }
                }

                // 3) Linked user account
                if (hasTable(con, "users")) {
                    String usersEmpCol = hasColumn(con, "users", "emp_id") ? "emp_id"
                            : (hasColumn(con, "users", "employee_id") ? "employee_id" : null);

                    if (usersEmpCol != null) {
                        try (PreparedStatement ps = con.prepareStatement("DELETE FROM users WHERE " + usersEmpCol + "=?")) {
                            ps.setInt(1, empId);
                            ps.executeUpdate();
                        }
                    }
                }

                // 4) Employee (last)
                String empIdCol = pickEmpIdCol(con);
                String sql = "DELETE FROM employees WHERE " + empIdCol + "=?";
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setInt(1, empId);
                    ps.executeUpdate();
                }

                con.commit();
            } catch (Exception ex) {
                con.rollback();
                throw ex;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    /* =========================================================
       Methods used by EmployeeAccountDialog
       ========================================================= */

    public static boolean empNoExists(String empNo) throws Exception {
        try (Connection con = DB.getConnection()) {
            if (!hasColumn(con, "employees", "emp_no")) return false;
            String sql = "SELECT 1 FROM employees WHERE emp_no=? LIMIT 1";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, empNo);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        }
    }

    public static boolean empNoExists(String empNo, int excludeEmpId) throws Exception {
        try (Connection con = DB.getConnection()) {
            if (!hasColumn(con, "employees", "emp_no")) return false;
            String empIdCol = pickEmpIdCol(con);
            String sql = "SELECT 1 FROM employees WHERE emp_no=? AND " + empIdCol + "<>? LIMIT 1";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, empNo);
                ps.setInt(2, excludeEmpId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        }
    }

    public static int insertFull(String empNo, String first, String middle, String last,
                                 String address, String mobile, String position,
                                 String payType, double rate) throws Exception {
        // Backward compatible: treat `mobile` as personal contact no if newer columns exist
        return insertFull(empNo, first, middle, last,
                address,
                mobile,   // personalContactNo
                "",      // companyContactNo
                "",      // personalEmail
                "",      // companyEmail
                position,
                payType,
                rate,
                0.0,
                0.0);
    }

    // New overload (supports personal/company contacts + emails)
    public static int insertFull(String empNo, String first, String middle, String last,
                                 String address,
                                 String personalContactNo,
                                 String companyContactNo,
                                 String personalEmail,
                                 String companyEmail,
                                 String position,
                                 String payType, double rate,
                                 double regularHolidayRate,
                                 double specialHolidayRate) throws Exception {

        try (Connection con = DB.getConnection()) {
            String empIdCol = pickEmpIdCol(con);

            boolean hasMiddle = hasColumn(con, "employees", "middle_name");
            boolean hasAddress = hasColumn(con, "employees", "address");
            boolean hasMobile = hasColumn(con, "employees", "mobile");
            boolean hasPersonalContact = hasColumn(con, "employees", "personal_contact_no");
            boolean hasCompanyContact = hasColumn(con, "employees", "company_contact_no");
            boolean hasPersonalEmail = hasColumn(con, "employees", "personal_email");
            boolean hasCompanyEmail = hasColumn(con, "employees", "company_email");
            boolean hasPosition = hasColumn(con, "employees", "position");
            boolean hasHourlyRate = hasColumn(con, "employees", "hourly_rate");
            boolean hasRegularHolidayRate = hasColumn(con, "employees", "regular_holiday_rate");
            boolean hasSpecialHolidayRate = hasColumn(con, "employees", "special_holiday_rate");
            boolean hasStatus = hasColumn(con, "employees", "status");

            String sql = "INSERT INTO employees(emp_no, first_name,"
                    + (hasMiddle ? " middle_name," : "")
                    + " last_name,"
                    + (hasAddress ? " address," : "")
                    + (hasMobile ? " mobile," : "")
                    + (hasPersonalContact ? " personal_contact_no," : "")
                    + (hasCompanyContact ? " company_contact_no," : "")
                    + (hasPersonalEmail ? " personal_email," : "")
                    + (hasCompanyEmail ? " company_email," : "")
                    + (hasPosition ? " position," : "")
                    + " pay_type, rate"
                    + (hasHourlyRate ? ", hourly_rate" : "")
                    + (hasRegularHolidayRate ? ", regular_holiday_rate" : "")
                    + (hasSpecialHolidayRate ? ", special_holiday_rate" : "")
                    + (hasStatus ? ", status" : "")
                    + ") VALUES(?, ?,"
                    + (hasMiddle ? " ?," : "")
                    + " ?,"
                    + (hasAddress ? " ?," : "")
                    + (hasMobile ? " ?," : "")
                    + (hasPersonalContact ? " ?," : "")
                    + (hasCompanyContact ? " ?," : "")
                    + (hasPersonalEmail ? " ?," : "")
                    + (hasCompanyEmail ? " ?," : "")
                    + (hasPosition ? " ?," : "")
                    + " ?, ?"
                    + (hasHourlyRate ? ", ?" : "")
                    + (hasRegularHolidayRate ? ", ?" : "")
                    + (hasSpecialHolidayRate ? ", ?" : "")
                    + (hasStatus ? ", ?" : "")
                    + ")";

            try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                int i = 1;
                ps.setString(i++, empNo);
                ps.setString(i++, first);
                if (hasMiddle) ps.setString(i++, middle);
                ps.setString(i++, last);
                if (hasAddress) ps.setString(i++, address);

                // keep backward compatibility: mobile column stores personal contact
                if (hasMobile) ps.setString(i++, personalContactNo);

                if (hasPersonalContact) ps.setString(i++, personalContactNo);
                if (hasCompanyContact) ps.setString(i++, companyContactNo);
                if (hasPersonalEmail) ps.setString(i++, personalEmail);
                if (hasCompanyEmail) ps.setString(i++, companyEmail);
                if (hasPosition) ps.setString(i++, position);

                ps.setString(i++, payType);
                ps.setDouble(i++, rate);

                if (hasHourlyRate) {
                    double hr = (payType != null && payType.trim().equalsIgnoreCase("HOURLY")) ? rate : 0.0;
                    ps.setDouble(i++, hr);
                }
                if (hasRegularHolidayRate) ps.setDouble(i++, regularHolidayRate);
                if (hasSpecialHolidayRate) ps.setDouble(i++, specialHolidayRate);

                if (hasStatus) ps.setString(i++, "ACTIVE");

                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            }

            // fallback when driver doesn't return generated keys
            String fb = "SELECT " + empIdCol + " FROM employees WHERE emp_no=? ORDER BY " + empIdCol + " DESC LIMIT 1";
            try (PreparedStatement ps2 = con.prepareStatement(fb)) {
                ps2.setString(1, empNo);
                try (ResultSet rs = ps2.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
            return -1;
        }
    }

    public static void updateFull(int empId, String empNo, String first, String middle, String last,
                                  String address, String mobile, String position,
                                  String payType, double rate) throws Exception {
        // Backward compatible: treat `mobile` as personal contact no
        updateFull(empId, empNo, first, middle, last,
                address,
                mobile,   // personalContactNo
                "",      // companyContactNo
                "",      // personalEmail
                "",      // companyEmail
                position,
                payType,
                rate,
                0.0,
                0.0);
    }

    // New overload (supports personal/company contacts + emails)
    public static void updateFull(int empId, String empNo, String first, String middle, String last,
                                  String address,
                                  String personalContactNo,
                                  String companyContactNo,
                                  String personalEmail,
                                  String companyEmail,
                                  String position,
                                  String payType, double rate,
                                  double regularHolidayRate,
                                  double specialHolidayRate) throws Exception {

        try (Connection con = DB.getConnection()) {
            String empIdCol = pickEmpIdCol(con);

            boolean hasMiddle = hasColumn(con, "employees", "middle_name");
            boolean hasAddress = hasColumn(con, "employees", "address");
            boolean hasMobile = hasColumn(con, "employees", "mobile");
            boolean hasPersonalContact = hasColumn(con, "employees", "personal_contact_no");
            boolean hasCompanyContact = hasColumn(con, "employees", "company_contact_no");
            boolean hasPersonalEmail = hasColumn(con, "employees", "personal_email");
            boolean hasCompanyEmail = hasColumn(con, "employees", "company_email");
            boolean hasPosition = hasColumn(con, "employees", "position");
            boolean hasHourlyRate = hasColumn(con, "employees", "hourly_rate");
            boolean hasRegularHolidayRate = hasColumn(con, "employees", "regular_holiday_rate");
            boolean hasSpecialHolidayRate = hasColumn(con, "employees", "special_holiday_rate");
            boolean hasStatus = hasColumn(con, "employees", "status");

            String sql = "UPDATE employees SET emp_no=?, first_name=?"
                    + (hasMiddle ? ", middle_name=?" : "")
                    + ", last_name=?"
                    + (hasAddress ? ", address=?" : "")
                    + (hasMobile ? ", mobile=?" : "")
                    + (hasPersonalContact ? ", personal_contact_no=?" : "")
                    + (hasCompanyContact ? ", company_contact_no=?" : "")
                    + (hasPersonalEmail ? ", personal_email=?" : "")
                    + (hasCompanyEmail ? ", company_email=?" : "")
                    + (hasPosition ? ", position=?" : "")
                    + ", pay_type=?, rate=?"
                    + (hasHourlyRate ? ", hourly_rate=?" : "")
                    + (hasRegularHolidayRate ? ", regular_holiday_rate=?" : "")
                    + (hasSpecialHolidayRate ? ", special_holiday_rate=?" : "")
                    + (hasStatus ? ", status=?" : "")
                    + " WHERE " + empIdCol + "=?";

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                int i = 1;
                ps.setString(i++, empNo);
                ps.setString(i++, first);
                if (hasMiddle) ps.setString(i++, middle);
                ps.setString(i++, last);
                if (hasAddress) ps.setString(i++, address);

                if (hasMobile) ps.setString(i++, personalContactNo);

                if (hasPersonalContact) ps.setString(i++, personalContactNo);
                if (hasCompanyContact) ps.setString(i++, companyContactNo);
                if (hasPersonalEmail) ps.setString(i++, personalEmail);
                if (hasCompanyEmail) ps.setString(i++, companyEmail);
                if (hasPosition) ps.setString(i++, position);

                ps.setString(i++, payType);
                ps.setDouble(i++, rate);

                if (hasHourlyRate) {
                    double hr = (payType != null && payType.trim().equalsIgnoreCase("HOURLY")) ? rate : 0.0;
                    ps.setDouble(i++, hr);
                }
                if (hasRegularHolidayRate) ps.setDouble(i++, regularHolidayRate);
                if (hasSpecialHolidayRate) ps.setDouble(i++, specialHolidayRate);

                if (hasStatus) {
                    // keep current status unless you manage it elsewhere; set ACTIVE by default for safety
                    ps.setString(i++, "ACTIVE");
                }

                ps.setInt(i++, empId);

                ps.executeUpdate();
            }
        }
    }

    /* =========================================================
       ✅ Link helpers (supports both schemas)
       ========================================================= */

    public static Integer getLinkedUserId(int empId) throws Exception {
        try (Connection con = DB.getConnection()) {

            String usersEmpIdCol = hasColumn(con, "users", "emp_id") ? "emp_id"
                    : (hasColumn(con, "users", "employee_id") ? "employee_id"
                    : (hasColumn(con, "users", "empId") ? "empId" : null));

            String usersUserIdCol = hasColumn(con, "users", "user_id") ? "user_id"
                    : (hasColumn(con, "users", "id") ? "id" : null);

            if (usersEmpIdCol != null && usersUserIdCol != null) {
                String sql = "SELECT " + usersUserIdCol + " FROM users WHERE " + usersEmpIdCol + "=? LIMIT 1";
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setInt(1, empId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) return null;
                        int v = rs.getInt(1);
                        return rs.wasNull() ? null : v;
                    }
                }
            }

            // fallback old schema
            String empIdCol = pickEmpIdCol(con);
            if (!hasColumn(con, "employees", "user_id")) return null;
            String sql = "SELECT user_id FROM employees WHERE " + empIdCol + "=? LIMIT 1";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, empId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    int v = rs.getInt(1);
                    return rs.wasNull() ? null : v;
                }
            }
        }
    }

    public static void createLoginForEmployee(int empId, String username, String password,
                                              String middleName, String address, String mobile) throws Exception {
        try (Connection con = DB.getConnection()) {
            con.setAutoCommit(false);
            try {
                int newUserId = insertUser(con, username, password);

                String empIdCol = pickEmpIdCol(con);

                boolean hasMiddle = hasColumn(con, "employees", "middle_name");
                boolean hasAddress = hasColumn(con, "employees", "address");
                boolean hasMobile = hasColumn(con, "employees", "mobile");
            boolean hasPersonalContact = hasColumn(con, "employees", "personal_contact_no");
            boolean hasCompanyContact = hasColumn(con, "employees", "company_contact_no");
            boolean hasPersonalEmail = hasColumn(con, "employees", "personal_email");
            boolean hasCompanyEmail = hasColumn(con, "employees", "company_email");

                // ✅ Prefer new schema: users.emp_id
                String usersEmpIdCol = hasColumn(con, "users", "emp_id") ? "emp_id"
                        : (hasColumn(con, "users", "employee_id") ? "employee_id"
                        : (hasColumn(con, "users", "empId") ? "empId" : null));

                String usersUserIdCol = hasColumn(con, "users", "user_id") ? "user_id"
                        : (hasColumn(con, "users", "id") ? "id" : null);

                if (usersEmpIdCol != null && usersUserIdCol != null) {
                    String sqlLink = "UPDATE users SET " + usersEmpIdCol + "=? WHERE " + usersUserIdCol + "=?";
                    try (PreparedStatement ps = con.prepareStatement(sqlLink)) {
                        ps.setInt(1, empId);
                        ps.setInt(2, newUserId);
                        ps.executeUpdate();
                    }
                } else {
                    // fallback old schema: employees.user_id
                    if (!hasColumn(con, "employees", "user_id")) {
                        throw new Exception("No link column found. Add users.emp_id OR employees.user_id.");
                    }
                    String sqlLink = "UPDATE employees SET user_id=? WHERE " + empIdCol + "=?";
                    try (PreparedStatement ps = con.prepareStatement(sqlLink)) {
                        ps.setInt(1, newUserId);
                        ps.setInt(2, empId);
                        ps.executeUpdate();
                    }
                }

                // Keep your original “optional field fill-in” behavior
                String sqlEmp = "UPDATE employees SET "
                        + (hasMiddle ? "middle_name=?," : "")
                        + (hasAddress ? "address=?," : "")
                        + (hasMobile ? "mobile=?," : "");

                // remove trailing comma safely
                sqlEmp = sqlEmp.replaceAll(",\\s*$", "") + " WHERE " + empIdCol + "=?";

                boolean willUpdate = hasMiddle || hasAddress || hasMobile;
                if (willUpdate) {
                    try (PreparedStatement ps = con.prepareStatement(sqlEmp)) {
                        int i = 1;
                        if (hasMiddle) ps.setString(i++, middleName);
                        if (hasAddress) ps.setString(i++, address);
                        if (hasMobile) ps.setString(i++, mobile);
                        ps.setInt(i++, empId);
                        ps.executeUpdate();
                    }
                }

                con.commit();
            } catch (Exception ex) {
                con.rollback();
                throw ex;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    /* =========================================================
       INTERNAL
       ========================================================= */

    private static void setActive(int empId, boolean active) throws Exception {
        try (Connection con = DB.getConnection()) {
            String empIdCol = pickEmpIdCol(con);

            if (!hasColumn(con, "employees", "status")) {
                throw new Exception("employees.status column missing.");
            }

            String sql = "UPDATE employees SET status=? WHERE " + empIdCol + "=?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, active ? "ACTIVE" : "INACTIVE");
                ps.setInt(2, empId);
                ps.executeUpdate();
            }
        }
    }

    private static int insertUser(Connection con, String username, String password) throws Exception {
        boolean hasRole = hasColumn(con, "users", "role");
        String sql = hasRole
                ? "INSERT INTO users(username, password, role) VALUES(?,?,?)"
                : "INSERT INTO users(username, password) VALUES(?,?)";

        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, password);
            if (hasRole) ps.setString(3, "EMPLOYEE");
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }

        String idCol = hasColumn(con, "users", "user_id") ? "user_id" : "id";
        try (PreparedStatement ps2 = con.prepareStatement(
                "SELECT " + idCol + " FROM users WHERE username=? ORDER BY " + idCol + " DESC LIMIT 1"
        )) {
            ps2.setString(1, username);
            try (ResultSet rs = ps2.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }

        throw new Exception("Failed to create user.");
    }

    private static String pickEmpIdCol(Connection con) throws Exception {
        if (hasColumn(con, "employees", "emp_id")) return "emp_id";
        if (hasColumn(con, "employees", "id")) return "id";
        DatabaseMetaData md = con.getMetaData();
        try (ResultSet rs = md.getColumns(con.getCatalog(), null, "employees", null)) {
            if (rs.next()) return rs.getString("COLUMN_NAME");
        }
        throw new Exception("Cannot detect employees PK column.");
    }

    private static Employee mapEmployee(ResultSet rs, String empIdCol) throws Exception {
        Employee e = new Employee();
        e.empId = rs.getInt(empIdCol);
        e.empNo = safeGet(rs, "emp_no");
        e.firstName = safeGet(rs, "first_name");
        e.middleName = safeGet(rs, "middle_name");
        e.lastName = safeGet(rs, "last_name");
        e.address = safeGet(rs, "address");
        e.mobile = safeGet(rs, "mobile");
        // New columns (safeGet returns "" if column doesn't exist)
        e.personalContactNo = safeGet(rs, "personal_contact_no");
        e.companyContactNo = safeGet(rs, "company_contact_no");
        e.personalEmail = safeGet(rs, "personal_email");
        e.companyEmail = safeGet(rs, "company_email");
        // NEW govt/HR fields (safeGet returns "" if column doesn't exist)
        e.dateOfBirth = safeGet(rs, "date_of_birth");
        e.civilStatus = safeGet(rs, "civil_status");
        e.philHealthNo = safeGet(rs, "philhealth_no");
        e.sssNo = safeGet(rs, "sss_no");
        e.tinNo = safeGet(rs, "tin_no");
        e.pagIbigNo = safeGet(rs, "pagibig_no");
        // Fallback: if personal_contact_no doesn't exist, reuse mobile
        if (e.personalContactNo == null || e.personalContactNo.trim().isEmpty()) {
            e.personalContactNo = e.mobile;
        }
        e.position = safeGet(rs, "position");
        e.payType = safeGet(rs, "pay_type");
        e.rate = safeGetDouble(rs, "rate");
        e.hourlyRate = safeGetDouble(rs, "hourly_rate");
        e.regularHolidayRate = safeGetDouble(rs, "regular_holiday_rate");
        e.specialHolidayRate = safeGetDouble(rs, "special_holiday_rate");
        e.status = safeGet(rs, "status");
        return e;
    }

    private static String safeGet(ResultSet rs, String col) {
        try {
            String v = rs.getString(col);
            return v == null ? "" : v;
        } catch (Exception e) {
            return "";
        }
    }

    private static double safeGetDouble(ResultSet rs, String col) {
        try {
            return rs.getDouble(col);
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static boolean hasTable(Connection con, String tableName) {
        try {
            DatabaseMetaData md = con.getMetaData();
            try (ResultSet rs = md.getTables(con.getCatalog(), null, tableName, new String[]{"TABLE"})) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean hasColumn(Connection con, String table, String column) throws Exception {
        DatabaseMetaData md = con.getMetaData();
        try (ResultSet rs = md.getColumns(con.getCatalog(), null, table, column)) {
            if (rs.next()) return true;
        }
        try (ResultSet rs = md.getColumns(con.getCatalog(), null, table.toUpperCase(), column)) {
            if (rs.next()) return true;
        }
        try (ResultSet rs = md.getColumns(con.getCatalog(), null, table.toLowerCase(), column)) {
            return rs.next();
        }
    }


    // =========================================================
    // NEW overloads for govt/HR fields (DOB, Civil Status, IDs)
    // These are SAFE on older databases: they only update columns if present.
    // =========================================================

    public static int insertFull(String empNo, String first, String middle, String last,
                                 String address,
                                 String personalContactNo,
                                 String companyContactNo,
                                 String personalEmail,
                                 String companyEmail,
                                 String position,
                                 String payType, double rate,
                                 String dateOfBirth,
                                 String civilStatus,
                                 String philHealthNo,
                                 String sssNo,
                                 String tinNo,
                                 String pagIbigNo,
                                 double regularHolidayRate,
                                 double specialHolidayRate) throws Exception {

        try (Connection con = DB.getConnection()) {
            String empIdCol = pickEmpIdCol(con);

            boolean hasMiddle = hasColumn(con, "employees", "middle_name");
            boolean hasAddress = hasColumn(con, "employees", "address");
            boolean hasMobile = hasColumn(con, "employees", "mobile");
            boolean hasPersonalContact = hasColumn(con, "employees", "personal_contact_no");
            boolean hasCompanyContact = hasColumn(con, "employees", "company_contact_no");
            boolean hasPersonalEmail = hasColumn(con, "employees", "personal_email");
            boolean hasCompanyEmail = hasColumn(con, "employees", "company_email");
            boolean hasPosition = hasColumn(con, "employees", "position");
            boolean hasHourlyRate = hasColumn(con, "employees", "hourly_rate");
            boolean hasRegularHolidayRate = hasColumn(con, "employees", "regular_holiday_rate");
            boolean hasSpecialHolidayRate = hasColumn(con, "employees", "special_holiday_rate");
            boolean hasStatus = hasColumn(con, "employees", "status");

            boolean hasDob = hasColumn(con, "employees", "date_of_birth");
            boolean hasCivil = hasColumn(con, "employees", "civil_status");
            boolean hasPhil = hasColumn(con, "employees", "philhealth_no");
            boolean hasSss = hasColumn(con, "employees", "sss_no");
            boolean hasTin = hasColumn(con, "employees", "tin_no");
            boolean hasPagibig = hasColumn(con, "employees", "pagibig_no");

            String sql = "INSERT INTO employees(emp_no, first_name,"
                    + (hasMiddle ? " middle_name," : "")
                    + " last_name,"
                    + (hasAddress ? " address," : "")
                    + (hasMobile ? " mobile," : "")
                    + (hasPersonalContact ? " personal_contact_no," : "")
                    + (hasCompanyContact ? " company_contact_no," : "")
                    + (hasPersonalEmail ? " personal_email," : "")
                    + (hasCompanyEmail ? " company_email," : "")
                    + (hasPosition ? " position," : "")
                    + (hasDob ? " date_of_birth," : "")
                    + (hasCivil ? " civil_status," : "")
                    + (hasPhil ? " philhealth_no," : "")
                    + (hasSss ? " sss_no," : "")
                    + (hasTin ? " tin_no," : "")
                    + (hasPagibig ? " pagibig_no," : "")
                    + " pay_type, rate"
                    + (hasHourlyRate ? ", hourly_rate" : "")
                    + (hasRegularHolidayRate ? ", regular_holiday_rate" : "")
                    + (hasSpecialHolidayRate ? ", special_holiday_rate" : "")
                    + (hasStatus ? ", status" : "")
                    + ") VALUES(?, ?,"
                    + (hasMiddle ? " ?," : "")
                    + " ?,"
                    + (hasAddress ? " ?," : "")
                    + (hasMobile ? " ?," : "")
                    + (hasPersonalContact ? " ?," : "")
                    + (hasCompanyContact ? " ?," : "")
                    + (hasPersonalEmail ? " ?," : "")
                    + (hasCompanyEmail ? " ?," : "")
                    + (hasPosition ? " ?," : "")
                    + (hasDob ? " ?," : "")
                    + (hasCivil ? " ?," : "")
                    + (hasPhil ? " ?," : "")
                    + (hasSss ? " ?," : "")
                    + (hasTin ? " ?," : "")
                    + (hasPagibig ? " ?," : "")
                    + " ?, ?"
                    + (hasHourlyRate ? ", ?" : "")
                    + (hasRegularHolidayRate ? ", ?" : "")
                    + (hasSpecialHolidayRate ? ", ?" : "")
                    + (hasStatus ? ", ?" : "")
                    + ")";

            try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                int i = 1;
                ps.setString(i++, empNo);
                ps.setString(i++, first);
                if (hasMiddle) ps.setString(i++, middle);
                ps.setString(i++, last);
                if (hasAddress) ps.setString(i++, address);

                if (hasMobile) ps.setString(i++, personalContactNo);
                if (hasPersonalContact) ps.setString(i++, personalContactNo);
                if (hasCompanyContact) ps.setString(i++, companyContactNo);
                if (hasPersonalEmail) ps.setString(i++, personalEmail);
                if (hasCompanyEmail) ps.setString(i++, companyEmail);
                if (hasPosition) ps.setString(i++, position);

                if (hasDob) ps.setString(i++, dateOfBirth);
                if (hasCivil) ps.setString(i++, civilStatus);
                if (hasPhil) ps.setString(i++, philHealthNo);
                if (hasSss) ps.setString(i++, sssNo);
                if (hasTin) ps.setString(i++, tinNo);
                if (hasPagibig) ps.setString(i++, pagIbigNo);

                ps.setString(i++, payType);
                ps.setDouble(i++, rate);

                if (hasHourlyRate) {
                    double hr = (payType != null && payType.trim().equalsIgnoreCase("HOURLY")) ? rate : 0.0;
                    ps.setDouble(i++, hr);
                }
                if (hasRegularHolidayRate) ps.setDouble(i++, regularHolidayRate);
                if (hasSpecialHolidayRate) ps.setDouble(i++, specialHolidayRate);

                if (hasStatus) ps.setString(i++, "ACTIVE");

                ps.executeUpdate();

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) return keys.getInt(1);
                }
            }

            String fb = "SELECT " + empIdCol + " FROM employees WHERE emp_no=? ORDER BY " + empIdCol + " DESC LIMIT 1";
            try (PreparedStatement ps2 = con.prepareStatement(fb)) {
                ps2.setString(1, empNo);
                try (ResultSet rs = ps2.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
            return -1;
        }
    }

    public static void updateFull(int empId, String empNo, String first, String middle, String last,
                                  String address,
                                  String personalContactNo,
                                  String companyContactNo,
                                  String personalEmail,
                                  String companyEmail,
                                  String position,
                                  String payType, double rate,
                                  String dateOfBirth,
                                  String civilStatus,
                                  String philHealthNo,
                                  String sssNo,
                                  String tinNo,
                                  String pagIbigNo,
                                  double regularHolidayRate,
                                  double specialHolidayRate) throws Exception {

        try (Connection con = DB.getConnection()) {
            String empIdCol = pickEmpIdCol(con);

            boolean hasMiddle = hasColumn(con, "employees", "middle_name");
            boolean hasAddress = hasColumn(con, "employees", "address");
            boolean hasMobile = hasColumn(con, "employees", "mobile");
            boolean hasPersonalContact = hasColumn(con, "employees", "personal_contact_no");
            boolean hasCompanyContact = hasColumn(con, "employees", "company_contact_no");
            boolean hasPersonalEmail = hasColumn(con, "employees", "personal_email");
            boolean hasCompanyEmail = hasColumn(con, "employees", "company_email");
            boolean hasPosition = hasColumn(con, "employees", "position");
            boolean hasHourlyRate = hasColumn(con, "employees", "hourly_rate");
            boolean hasRegularHolidayRate = hasColumn(con, "employees", "regular_holiday_rate");
            boolean hasSpecialHolidayRate = hasColumn(con, "employees", "special_holiday_rate");
            boolean hasStatus = hasColumn(con, "employees", "status");

            boolean hasDob = hasColumn(con, "employees", "date_of_birth");
            boolean hasCivil = hasColumn(con, "employees", "civil_status");
            boolean hasPhil = hasColumn(con, "employees", "philhealth_no");
            boolean hasSss = hasColumn(con, "employees", "sss_no");
            boolean hasTin = hasColumn(con, "employees", "tin_no");
            boolean hasPagibig = hasColumn(con, "employees", "pagibig_no");

            String sql = "UPDATE employees SET emp_no=?, first_name=?"
                    + (hasMiddle ? ", middle_name=?" : "")
                    + ", last_name=?"
                    + (hasAddress ? ", address=?" : "")
                    + (hasMobile ? ", mobile=?" : "")
                    + (hasPersonalContact ? ", personal_contact_no=?" : "")
                    + (hasCompanyContact ? ", company_contact_no=?" : "")
                    + (hasPersonalEmail ? ", personal_email=?" : "")
                    + (hasCompanyEmail ? ", company_email=?" : "")
                    + (hasPosition ? ", position=?" : "")
                    + (hasDob ? ", date_of_birth=?" : "")
                    + (hasCivil ? ", civil_status=?" : "")
                    + (hasPhil ? ", philhealth_no=?" : "")
                    + (hasSss ? ", sss_no=?" : "")
                    + (hasTin ? ", tin_no=?" : "")
                    + (hasPagibig ? ", pagibig_no=?" : "")
                    + ", pay_type=?, rate=?"
                    + (hasHourlyRate ? ", hourly_rate=?" : "")
                    + (hasRegularHolidayRate ? ", regular_holiday_rate=?" : "")
                    + (hasSpecialHolidayRate ? ", special_holiday_rate=?" : "")
                    + (hasStatus ? ", status=?" : "")
                    + " WHERE " + empIdCol + "=?";

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                int i = 1;
                ps.setString(i++, empNo);
                ps.setString(i++, first);
                if (hasMiddle) ps.setString(i++, middle);
                ps.setString(i++, last);
                if (hasAddress) ps.setString(i++, address);

                if (hasMobile) ps.setString(i++, personalContactNo);
                if (hasPersonalContact) ps.setString(i++, personalContactNo);
                if (hasCompanyContact) ps.setString(i++, companyContactNo);
                if (hasPersonalEmail) ps.setString(i++, personalEmail);
                if (hasCompanyEmail) ps.setString(i++, companyEmail);
                if (hasPosition) ps.setString(i++, position);

                if (hasDob) ps.setString(i++, dateOfBirth);
                if (hasCivil) ps.setString(i++, civilStatus);
                if (hasPhil) ps.setString(i++, philHealthNo);
                if (hasSss) ps.setString(i++, sssNo);
                if (hasTin) ps.setString(i++, tinNo);
                if (hasPagibig) ps.setString(i++, pagIbigNo);

                ps.setString(i++, payType);
                ps.setDouble(i++, rate);

                if (hasHourlyRate) {
                    double hr = (payType != null && payType.trim().equalsIgnoreCase("HOURLY")) ? rate : 0.0;
                    ps.setDouble(i++, hr);
                }
                if (hasRegularHolidayRate) ps.setDouble(i++, regularHolidayRate);
                if (hasSpecialHolidayRate) ps.setDouble(i++, specialHolidayRate);

                if (hasStatus) ps.setString(i++, "ACTIVE");

                ps.setInt(i++, empId);

                ps.executeUpdate();
            }
        }
    }

    private static void updateGovFields(int empId,
                                        String dateOfBirth,
                                        String civilStatus,
                                        String philHealthNo,
                                        String sssNo,
                                        String tinNo,
                                        String pagIbigNo) {
        try (Connection con = DB.getConnection()) {
            String empIdCol = pickEmpIdCol(con);

            boolean hasDob = hasColumn(con, "employees", "date_of_birth");
            boolean hasCivil = hasColumn(con, "employees", "civil_status");
            boolean hasPhil = hasColumn(con, "employees", "philhealth_no");
            boolean hasSss = hasColumn(con, "employees", "sss_no");
            boolean hasTin = hasColumn(con, "employees", "tin_no");
            boolean hasPag = hasColumn(con, "employees", "pagibig_no");

            if (!(hasDob || hasCivil || hasPhil || hasSss || hasTin || hasPag)) return;

            StringBuilder sb = new StringBuilder("UPDATE employees SET ");
            List<Object> params = new ArrayList<>();

            if (hasDob) { sb.append("date_of_birth=?,"); params.add(nz(dateOfBirth)); }
            if (hasCivil) { sb.append("civil_status=?,"); params.add(nz(civilStatus)); }
            if (hasPhil) { sb.append("philhealth_no=?,"); params.add(nz(philHealthNo)); }
            if (hasSss) { sb.append("sss_no=?,"); params.add(nz(sssNo)); }
            if (hasTin) { sb.append("tin_no=?,"); params.add(nz(tinNo)); }
            if (hasPag) { sb.append("pagibig_no=?,"); params.add(nz(pagIbigNo)); }

            // remove trailing comma
            sb.setLength(sb.length() - 1);
            sb.append(" WHERE ").append(empIdCol).append("=?");
            params.add(empId);

            try (PreparedStatement ps = con.prepareStatement(sb.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                ps.executeUpdate();
            }
        } catch (Exception ignored) {
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }


}
