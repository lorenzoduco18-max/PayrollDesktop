package dao;

import model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class UserDAO {

    /**
     * ✅ Case-sensitive login.
     * Assumes users table has columns: user_id, username, password, role
     */
    public static User login(String username, String password) throws Exception {
        String sql = "SELECT user_id, username, role FROM users WHERE BINARY username=? AND BINARY password=? LIMIT 1";
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setString(2, password);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                User u = new User();
                u.setId(rs.getInt("user_id"));
                u.setUsername(rs.getString("username"));
                u.setRole(rs.getString("role"));
                return u;
            }
        }
    }

    public boolean hasAccountForEmployee(int empId) throws Exception {
        String sql = "SELECT 1 FROM users WHERE emp_id=? LIMIT 1";
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, empId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Return user_id linked to employees.emp_id, or null. */
    public Integer getUserIdByEmpId(int empId) throws Exception {
        String sql = "SELECT user_id FROM users WHERE emp_id=? LIMIT 1";
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, empId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("user_id") : null;
            }
        }
    }

    /** Employee self-change: verify current password matches. */
    public boolean verifyPasswordByEmpId(int empId, String password) throws Exception {
        String sql = "SELECT 1 FROM users WHERE emp_id=? AND password=? LIMIT 1";
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, empId);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Admin/Employee: set new password for a linked user_id. */
    public boolean updatePasswordByUserId(int userId, String newPassword) throws Exception {
        String sql = "UPDATE users SET password=? WHERE user_id=?";
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, newPassword);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Convenience: set new password using emp_id. */
    public boolean updatePasswordByEmpId(int empId, String newPassword) throws Exception {
        String sql = "UPDATE users SET password=? WHERE emp_id=?";
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, newPassword);
            ps.setInt(2, empId);
            return ps.executeUpdate() > 0;
        }
    }
}
