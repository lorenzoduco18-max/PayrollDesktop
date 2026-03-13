package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Date;

/**
 * Stores published payslip PDFs in the database so employees on other devices
 * can download them (Azure MySQL / online).
 *
 * NOTE: This does NOT touch any existing payroll/timezone logic. It only adds
 * a new table used by the Admin "Upload/Send Payslip" and the Employee
 * "Download Payslip" feature.
 */
public final class PayslipPublishDAO {

    private PayslipPublishDAO() {}

    /** Simple carrier for a stored PDF. */
    public static final class PublishedPdf {
        public final String fileName;
        public final byte[] pdfBytes;
        public PublishedPdf(String fileName, byte[] pdfBytes) {
            this.fileName = fileName;
            this.pdfBytes = pdfBytes;
        }
    }

    private static void ensureTable() throws Exception {
        String sql =
                "CREATE TABLE IF NOT EXISTS published_payslips (" +
                "  id BIGINT NOT NULL AUTO_INCREMENT," +
                "  emp_id INT NOT NULL," +
                "  period_start DATE NOT NULL," +
                "  period_end DATE NOT NULL," +
                "  file_name VARCHAR(255) NOT NULL," +
                "  pdf_blob LONGBLOB NOT NULL," +
                "  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  PRIMARY KEY (id)," +
                "  UNIQUE KEY uq_emp_period (emp_id, period_start, period_end)" +
                ")";

        try (Connection con = DB.getConnection();
             Statement st = con.createStatement()) {
            st.execute(sql);
        }
    }

    /**
     * Insert or replace a published payslip for (emp_id + cutoff).
     */
    public static void upsert(int empId, java.time.LocalDate start, java.time.LocalDate end,
                              String fileName, byte[] pdfBytes) throws Exception {
        if (start == null || end == null) throw new IllegalArgumentException("Cutoff dates are required.");
        if (fileName == null || fileName.trim().isEmpty()) throw new IllegalArgumentException("fileName is required.");
        if (pdfBytes == null || pdfBytes.length == 0) throw new IllegalArgumentException("pdfBytes is required.");

        ensureTable();

        String sql =
                "INSERT INTO published_payslips(emp_id, period_start, period_end, file_name, pdf_blob) " +
                "VALUES(?,?,?,?,?) " +
                "ON DUPLICATE KEY UPDATE file_name=VALUES(file_name), pdf_blob=VALUES(pdf_blob), created_at=CURRENT_TIMESTAMP";

        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, empId);
            ps.setDate(2, Date.valueOf(start));
            ps.setDate(3, Date.valueOf(end));
            ps.setString(4, fileName);
            ps.setBytes(5, pdfBytes);
            ps.executeUpdate();
        }
    }

    /** Check if a published payslip exists for the employee + cutoff. */
    public static boolean exists(int empId, java.time.LocalDate start, java.time.LocalDate end) throws Exception {
        ensureTable();
        String sql = "SELECT 1 FROM published_payslips WHERE emp_id=? AND period_start=? AND period_end=? LIMIT 1";
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, empId);
            ps.setDate(2, Date.valueOf(start));
            ps.setDate(3, Date.valueOf(end));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Fetch the published PDF bytes for the employee + cutoff (returns null if not found). */
    public static PublishedPdf fetch(int empId, java.time.LocalDate start, java.time.LocalDate end) throws Exception {
        ensureTable();
        String sql = "SELECT file_name, pdf_blob FROM published_payslips WHERE emp_id=? AND period_start=? AND period_end=? LIMIT 1";
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, empId);
            ps.setDate(2, Date.valueOf(start));
            ps.setDate(3, Date.valueOf(end));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                String fn = rs.getString("file_name");
                byte[] bytes = rs.getBytes("pdf_blob");
                return new PublishedPdf(fn, bytes);
            }
        }
    }
}
