package ui;

import com.formdev.flatlaf.FlatClientProperties;
import dao.DB;
import dao.EmployeeDAO;
import model.Employee;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.util.List;

public class EmployeesDialog extends JDialog {

    private JTable table;
    private DefaultTableModel model;

    private JButton btnCreate, btnEdit, btnDeactivate, btnReactivate,
            btnDelete, btnRefresh;

    public EmployeesDialog(Window owner) {
        super(owner, "Employees", ModalityType.APPLICATION_MODAL);
        setSize(1200, 700);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        buildUI();
        refresh();
    }

    private void buildUI() {
        Color bg = UIManager.getColor("Panel.background");

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(bg);
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JLabel title = new JLabel("Employees");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize() + 8f));

        JLabel subtitle = new JLabel("Add, edit, deactivate, and reactivate employees.");
        subtitle.setForeground(new Color(0, 0, 0, 140));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitle);
        header.setBorder(new EmptyBorder(0, 0, 14, 0));

        model = new DefaultTableModel(new Object[]{
                "#", "EMP_DB_ID", "Emp No", "Name", "Position", "Pay Type", "Rate", "Status"
        }, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        table = new JTable(model);
        table.setRowHeight(36);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.putClientProperty("Table.showCellFocusIndicator", false);

        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD));
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(10, 38));

        // Hide EMP_DB_ID
        table.getColumnModel().getColumn(1).setMinWidth(0);
        table.getColumnModel().getColumn(1).setMaxWidth(0);
        table.getColumnModel().getColumn(1).setPreferredWidth(0);

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        table.getColumnModel().getColumn(0).setCellRenderer(center);
        table.getColumnModel().getColumn(2).setCellRenderer(center);
        table.getColumnModel().getColumn(5).setCellRenderer(center);
        table.getColumnModel().getColumn(6).setCellRenderer(center);
        table.getColumnModel().getColumn(7).setCellRenderer(center);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(
                UIManager.getColor("Component.borderColor"), 1));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        actions.setOpaque(false);

        btnCreate = new JButton("Create Employee Account");
        btnEdit = new JButton("Edit");
        btnDeactivate = new JButton("Deactivate");
        btnReactivate = new JButton("Reactivate");
        btnDelete = new JButton("Delete");
        btnRefresh = new JButton("Refresh");

        round(btnCreate, true);
        round(btnEdit, false);
        round(btnDeactivate, false);
        round(btnReactivate, false);
        round(btnDelete, false);
        round(btnRefresh, false);

        setHand(btnCreate); setHand(btnEdit); setHand(btnDeactivate); setHand(btnReactivate);
        setHand(btnDelete); setHand(btnRefresh);

        btnDelete.setBackground(new Color(220, 53, 69));
        btnDelete.setForeground(Color.WHITE);
        btnDelete.setOpaque(true);
        btnDelete.setBorderPainted(false);

        actions.add(btnCreate);
        actions.add(btnEdit);
        actions.add(btnDeactivate);
        actions.add(btnReactivate);
        actions.add(btnDelete);
        actions.add(btnRefresh);

        btnRefresh.addActionListener(e -> refresh());

        btnCreate.addActionListener(e -> openCreateEmployee());
        btnEdit.addActionListener(e -> openEditEmployee());

        btnDeactivate.addActionListener(e -> onDeactivate());
        btnReactivate.addActionListener(e -> onReactivate());
        btnDelete.addActionListener(e -> deleteSelectedEmployee());

        root.add(header, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(actions, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private void setHand(AbstractButton b) {
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusable(false);
    }

    private void round(AbstractButton b, boolean primary) {
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, "roundRect");
        b.putClientProperty(FlatClientProperties.STYLE,
                " arc:999; margin: 6,14,6,14;" + (primary ? "font: bold;" : ""));
    }

    private Integer getSelectedEmpId() {
        int row = table.getSelectedRow();
        if (row < 0) return null;
        Object v = model.getValueAt(row, 1);
        if (v == null) return null;
        return Integer.parseInt(v.toString());
    }

    private void openCreateEmployee() {
        try {
            Window owner = SwingUtilities.getWindowAncestor(table); // IMPORTANT (embedded!)
            EmployeeAccountDialog dlg = new EmployeeAccountDialog(owner, (Employee) null);
            dlg.setVisible(true);
            refresh();
            notifyPayrollEmployeeListChanged(); // ✅ refresh payroll dropdown
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Create Employee dialog failed to open.\n\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openEditEmployee() {
        Integer empId = getSelectedEmpId();
        if (empId == null) {
            JOptionPane.showMessageDialog(this, "Select an employee first.");
            return;
        }
        try {
            Employee full = EmployeeDAO.getById(empId);
            if (full == null) {
                JOptionPane.showMessageDialog(this, "Employee not found. Refresh and try again.");
                return;
            }
            Window owner = SwingUtilities.getWindowAncestor(table); // IMPORTANT (embedded!)
            EmployeeAccountDialog dlg = new EmployeeAccountDialog(owner, full);
            dlg.setVisible(true);
            refresh();
            notifyPayrollEmployeeListChanged(); // ✅ refresh payroll dropdown (in case status changed via edit)
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Edit failed:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onDeactivate() {
        Integer empId = getSelectedEmpId();
        if (empId == null) {
            JOptionPane.showMessageDialog(this, "Select an employee first.");
            return;
        }

        try {
            EmployeeDAO.deactivate(empId);
            refresh();
            notifyPayrollEmployeeListChanged(); // updates Payroll instantly
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Deactivate failed:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }


    private void onReactivate() {
        Integer empId = getSelectedEmpId();
        if (empId == null) {
            JOptionPane.showMessageDialog(this, "Select an employee first.");
            return;
        }

        try {
            EmployeeDAO.reactivate(empId);
            refresh();
            notifyPayrollEmployeeListChanged(); // updates Payroll instantly
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "Reactivate failed:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }


    private void deleteSelectedEmployee() {
        Integer empId = getSelectedEmpId();
        if (empId == null) {
            JOptionPane.showMessageDialog(this, "Select an employee first.");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Delete this employee permanently?\n\nThis will also remove related records (attendance/logs).\nThis CANNOT be undone.",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            int affected = deleteEmployeeCascade(empId);
            if (affected <= 0) {
                JOptionPane.showMessageDialog(this, "Delete failed. Employee not found.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            refresh();
            notifyPayrollEmployeeListChanged(); // ✅ refresh payroll dropdown
            JOptionPane.showMessageDialog(this, "Employee deleted.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error deleting employee:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private int deleteEmployeeCascade(int empId) throws Exception {
        try (Connection con = DB.getConnection()) {
            con.setAutoCommit(false);
            try {
                tryDeleteFromAnyColumn(con, "attendance", empId);
                tryDeleteFromAnyColumn(con, "time_logs", empId);
                tryDeleteFromAnyColumn(con, "timelogs", empId);
                tryDeleteFromAnyColumn(con, "time_log", empId);

                tryDeleteFromAnyColumn(con, "payslips", empId);
                tryDeleteFromAnyColumn(con, "payroll", empId);
                tryDeleteFromAnyColumn(con, "payroll_items", empId);
                tryDeleteFromAnyColumn(con, "payroll_runs", empId);

                tryDeleteFromAnyColumn(con, "users", empId);

                int affected = deleteEmployeeByAnyIdColumn(con, empId);

                con.commit();
                return affected;
            } catch (Exception ex) {
                con.rollback();
                throw ex;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    private void tryDeleteFromAnyColumn(Connection con, String tableName, int empId) throws Exception {
        String[] cols = {"emp_id", "empId", "employee_id", "employeeId", "empID"};
        for (String col : cols) {
            String sql = "DELETE FROM " + tableName + " WHERE " + col + "=?";
            try (var ps = con.prepareStatement(sql)) {
                ps.setInt(1, empId);
                ps.executeUpdate();
                return;
            } catch (Exception ex) {
                String msg = (ex.getMessage() == null) ? "" : ex.getMessage().toLowerCase();
                if (msg.contains("unknown column") || msg.contains("doesn't exist") || msg.contains("unknown table"))
                    continue;
                throw ex;
            }
        }
    }

    private int deleteEmployeeByAnyIdColumn(Connection con, int empId) throws Exception {
        String[] cols = {"emp_id", "id", "empId", "employee_id", "employeeId", "empID"};
        Exception last = null;
        for (String col : cols) {
            String sql = "DELETE FROM employees WHERE " + col + "=?";
            try (var ps = con.prepareStatement(sql)) {
                ps.setInt(1, empId);
                return ps.executeUpdate();
            } catch (Exception ex) {
                String msg = (ex.getMessage() == null) ? "" : ex.getMessage().toLowerCase();
                if (msg.contains("unknown column")) { last = ex; continue; }
                throw ex;
            }
        }
        throw new RuntimeException("Cannot delete employee. No matching id column found.", last);
    }

    private void refresh() {
        try {
            model.setRowCount(0);
            List<Employee> list = EmployeeDAO.listAll();

            int n = 1;
            for (Employee e : list) {
                model.addRow(new Object[]{
                        n++,
                        e.empId,
                        e.empNo,
                        safeFullName(e),
                        e.position,
                        e.payType,
                        e.rate,
                        e.status
                });
            }
            table.clearSelection();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error loading employees:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    

    private String safeFullName(Employee e) {
        try {
            String s = e.fullName();
            return (s == null || s.isBlank()) ? (e.firstName + " " + e.lastName).trim() : s;
        } catch (Exception ignore) {
            return (e.firstName + " " + e.lastName).trim();
        }
    }

    // ✅ IMPORTANT: Since this "dialog" is embedded, we grab the window ancestor from the TABLE
    private void notifyPayrollEmployeeListChanged() {
        try {
            Window w = SwingUtilities.getWindowAncestor(table);
            if (w instanceof DashboardFrame) {
                ((DashboardFrame) w).refreshPayrollEmployeeList();
            }
        } catch (Exception ignored) {}
    }
    
    
}
