package dao;

import model.Payslip;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PayrollDAO {

    public static int createRun(String startYYYYMMDD, String endYYYYMMDD) throws Exception {
        String sql = "INSERT INTO payroll_runs(period_start, period_end) VALUES(?,?)";
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, startYYYYMMDD);
            ps.setString(2, endYYYYMMDD);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        throw new Exception("Failed to create payroll run (no generated key).");
    }

    public static int savePayslip(Payslip p) throws Exception {

        try (Connection con = DB.getConnection()) {

            // Optional column support (keeps compatibility with your existing DB)
            boolean hasIncentives = hasColumn(con, "payslips", "incentives")
                    || hasColumn(con, "payslips", "incentive")
                    || hasColumn(con, "payslips", "bonus");

            String sql;
            if (hasIncentives) {
                sql = "INSERT INTO payslips(run_id, emp_id, basic_pay, overtime_pay, incentives, gross_pay, deductions, net_pay) " +
                      "VALUES(?,?,?,?,?,?,?,?)";
            } else {
                sql = "INSERT INTO payslips(run_id, emp_id, basic_pay, overtime_pay, gross_pay, deductions, net_pay) " +
                      "VALUES(?,?,?,?,?,?,?)";
            }

            try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

                ps.setInt(1, p.runId);
                ps.setInt(2, p.emp.empId);
                ps.setDouble(3, p.basicPay);
                ps.setDouble(4, p.overtimePay);

                if (hasIncentives) {
                    ps.setDouble(5, p.incentives);
                    ps.setDouble(6, p.grossPay);
                    ps.setDouble(7, p.deductions);
                    ps.setDouble(8, p.netPay);
                } else {
                    ps.setDouble(5, p.grossPay);
                    ps.setDouble(6, p.deductions);
                    ps.setDouble(7, p.netPay);
                }

                ps.executeUpdate();

                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        }

        throw new Exception("Failed to save payslip (no generated key).");
    }

    public static List<Object[]> listPayslipsByRun(int runId) throws Exception {
        List<Object[]> list = new ArrayList<>();

        // employees pk might be emp_id or id
        String empJoinCol = detectEmployeeIdColumn();

        String sql =
                "SELECT p.payslip_id, e.emp_no, e.first_name, e.last_name, p.net_pay, p.generated_at " +
                "FROM payslips p " +
                "JOIN employees e ON e." + empJoinCol + " = p.emp_id " +
                "WHERE p.run_id=? " +
                "ORDER BY p.payslip_id DESC";

        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, runId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Object[]{
                            rs.getInt("payslip_id"),
                            rs.getString("emp_no"),
                            rs.getString("first_name") + " " + rs.getString("last_name"),
                            rs.getDouble("net_pay"),
                            String.valueOf(rs.getTimestamp("generated_at"))
                    });
                }
            }
        }
        return list;
    }

    private static String detectEmployeeIdColumn() {
        try (Connection con = DB.getConnection()) {
            if (hasColumn(con, "employees", "emp_id")) return "emp_id";
            if (hasColumn(con, "employees", "id")) return "id";
        } catch (Exception ignore) {}
        return "emp_id"; // fallback
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
}
