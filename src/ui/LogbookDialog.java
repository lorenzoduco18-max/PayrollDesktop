package ui;

import com.formdev.flatlaf.FlatClientProperties;
import dao.DB;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class LogbookDialog extends JDialog {

    private JTable table;
    private DefaultTableModel model;

    private JButton btnRefresh, btnDelete;

    public LogbookDialog(Window owner) {
        super(owner, "Logbook", ModalityType.APPLICATION_MODAL);
        setSize(1200, 700);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        buildUI();
        refresh();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBorder(new EmptyBorder(18, 18, 18, 18));

        JLabel title = new JLabel("Logbook");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize() + 8f));

        JLabel subtitle = new JLabel("View attendance logs. You can refresh or delete records.");
        subtitle.setForeground(new Color(0, 0, 0, 140));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitle);
        header.setBorder(new EmptyBorder(0, 0, 14, 0));

        model = new DefaultTableModel(new Object[]{
                "ID", "Emp ID", "Employee", "Time In", "Time Out"
        }, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        table = new JTable(model);
        table.setRowHeight(34);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.putClientProperty("Table.showCellFocusIndicator", false);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(10, 36));

        // Hide internal ID columns
        table.getColumnModel().getColumn(0).setMinWidth(0);
        table.getColumnModel().getColumn(0).setMaxWidth(0);
        table.getColumnModel().getColumn(0).setPreferredWidth(0);

        table.getColumnModel().getColumn(1).setMinWidth(0);
        table.getColumnModel().getColumn(1).setMaxWidth(0);
        table.getColumnModel().getColumn(1).setPreferredWidth(0);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        actions.setOpaque(false);

        btnRefresh = new JButton("Refresh");
        btnDelete = new JButton("Delete");

        pill(btnRefresh, false);
        pill(btnDelete, false);

        btnDelete.setBackground(new Color(220, 53, 69));
        btnDelete.setForeground(Color.WHITE);
        btnDelete.setOpaque(true);
        btnDelete.setBorderPainted(false);

        btnRefresh.addActionListener(e -> refresh());
        btnDelete.addActionListener(e -> deleteSelected());

        actions.add(btnDelete);
        actions.add(btnRefresh);

        root.add(header, BorderLayout.NORTH);
        root.add(scroll, BorderLayout.CENTER);
        root.add(actions, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private void pill(JButton b, boolean primary) {
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, "roundRect");
        b.putClientProperty(FlatClientProperties.STYLE,
                " margin: 6,14,6,14;" + (primary ? "font: bold;" : ""));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusable(false);
    }

    private void refresh() {
        model.setRowCount(0);

        String sql = ""
                + "SELECT tl.id, tl.emp_id, "
                + "CONCAT(e.first_name,' ',e.last_name) AS emp_name, "
                + "tl.time_in, tl.time_out "
                + "FROM time_logs tl "
                + "LEFT JOIN employees e ON e.emp_id = tl.emp_id "
                + "ORDER BY tl.time_in DESC";

        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                model.addRow(new Object[]{
                        rs.getInt("id"),
                        rs.getInt("emp_id"),
                        rs.getString("emp_name"),
                        rs.getTimestamp("time_in"),
                        rs.getTimestamp("time_out")
                });
            }

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error loading logbook:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a log record first.");
            return;
        }

        int id = Integer.parseInt(model.getValueAt(row, 0).toString());

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Delete this log record?\n\nThis cannot be undone.",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.YES_OPTION) return;

        String sql = "DELETE FROM time_logs WHERE id=?";
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, id);
            ps.executeUpdate();
            refresh();

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Delete failed:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
