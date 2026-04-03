package ui;

import dao.EmployeeDAO;
import model.Employee;

import java.awt.Font;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;
import javax.swing.RowFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class EmployeesPanel extends JPanel {

    private final EmployeeDAO employeeDAO = new EmployeeDAO();

    private final PillTextField txtSearch = new PillTextField(22);
    private final Timer searchTimer;
    private final JButton btnViewDetails = new RoundedButton("View Employee Details", new Color(245, 245, 245), new Color(35, 35, 35));
    private final JButton btnRefresh = new RoundedButton("Refresh", new Color(245, 245, 245), new Color(35, 35, 35));

    private final JButton btnCreate = new RoundedButton("Create Employee Account", new Color(245, 245, 245), new Color(35, 35, 35));
    private final JButton btnEdit = new RoundedButton("Edit", new Color(245, 245, 245), new Color(35, 35, 35));
    private final JButton btnDeactivate = new RoundedButton("Deactivate", new Color(245, 245, 245), new Color(35, 35, 35));
    private final JButton btnReactivate = new RoundedButton("Reactivate", new Color(245, 245, 245), new Color(35, 35, 35));
    private final JButton btnDelete = new RoundedButton("Delete", new Color(214, 92, 92), Color.WHITE);

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"#", "Emp No", "Name", "Position", "Pay Type", "Rate", "Status", "EMP_ID"}, 0
    ) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };

    private final JTable table = new JTable(model) {
            @Override
            public java.awt.Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int col) {
                java.awt.Component c = super.prepareRenderer(renderer, row, col);

                // Make sure background paints
                if (c instanceof javax.swing.JComponent jc) {
                    jc.setOpaque(true);

                    // selection colors (force strong contrast if theme is subtle)
                    java.awt.Color selBg = getSelectionBackground();
                    java.awt.Color selFg = getSelectionForeground();
                    if (selBg == null || (selBg.getRed() > 230 && selBg.getGreen() > 230 && selBg.getBlue() > 230)) {
                        selBg = new java.awt.Color(132, 178, 241);
                    }
                    if (selFg == null) selFg = java.awt.Color.WHITE;

                    if (isRowSelected(row)) {
                        jc.setBackground(selBg);
                        jc.setForeground(selFg);
                        jc.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
                    } else {
                        // Non-selected: keep text readable, but do not overwrite custom Status colors
                        if (col != 6) {
                            jc.setForeground(java.awt.Color.BLACK);
                        }
                        jc.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
                    }
                }
                return c;
            }
        };
// Column header dropdown filters (single-select per column)
    private final TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
    private final Map<Integer, String> columnFilters = new HashMap<>();
    // Guard to prevent recursive selection/filter events while updating row filters
    private boolean applyingFilters = false;

    private JPopupMenu activeFilterPopup;

    public EmployeesPanel() {
        setLayout(new BorderLayout(12, 12));
        setBorder(new EmptyBorder(16, 16, 16, 16));

        // Header
        JLabel title = new JLabel("Employees");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize() + 6f));

        JLabel sub = new JLabel("Add, edit, deactivate, and reactivate employees.");
        sub.setForeground(new Color(0, 0, 0, 140));

        JPanel leftHeader = new JPanel();
        leftHeader.setOpaque(false);
        leftHeader.setLayout(new BoxLayout(leftHeader, BoxLayout.Y_AXIS));
        leftHeader.add(title);
        leftHeader.add(Box.createVerticalStrut(4));
        leftHeader.add(sub);


        styleTopPill(btnViewDetails);
        styleTopPill(btnRefresh);

        JPanel rightHeader = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightHeader.setOpaque(false);
        rightHeader.add(new JLabel("Search:"));

        txtSearch.setPreferredSize(new Dimension(255, 38));
        txtSearch.setMinimumSize(new Dimension(255, 38));

        // Live search (debounced) so results update while typing
        searchTimer = new Timer(280, e -> loadEmployeesKeepSelection(null));
        searchTimer.setRepeats(false);
        txtSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void changed() { searchTimer.restart(); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { changed(); }
        });
        rightHeader.add(txtSearch);
        rightHeader.add(btnViewDetails);
        rightHeader.add(btnRefresh);

        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        header.add(leftHeader, BorderLayout.WEST);
        header.add(rightHeader, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);

        // Table
        table.setRowHeight(30);
        table.setFillsViewportHeight(true);

        // Enable sorting + our combined (header) filters
        table.setRowSorter(sorter);
        sorter.setSortsOnUpdates(false);
        sorter.setComparator(0, (a, b) -> Integer.compare(parseIntSafe(a), parseIntSafe(b)));
        sorter.setComparator(5, (a, b) -> Double.compare(parseDoubleSafe(a), parseDoubleSafe(b)));

        // Prevent header clicks from sorting/shuffling rows (filters only)
        for (int c = 0; c < model.getColumnCount(); c++) {
            sorter.setSortable(c, false);
        }

        applyTableStyling();
        // Row selection only (works well on macOS trackpads)
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowSelectionAllowed(true);
        table.setColumnSelectionAllowed(false);
        table.setCellSelectionEnabled(false);
        table.putClientProperty("JTable.showCellFocusIndicator", Boolean.FALSE);

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                if (row >= 0) {
                    table.setRowSelectionInterval(row, row);
                    table.requestFocusInWindow();
                }
            }
        });


        installHeaderFilters();

        // hide EMP_ID column
        table.getColumnModel().getColumn(7).setMinWidth(0);
        table.getColumnModel().getColumn(7).setMaxWidth(0);
        table.getColumnModel().getColumn(7).setWidth(0);

        add(new JScrollPane(table), BorderLayout.CENTER);

        // Buttons
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);

        stylePill(btnCreate, true);
        stylePill(btnEdit, false);
        stylePill(btnDeactivate, false);
        stylePill(btnReactivate, false);
        styleDanger(btnDelete);

        actions.add(btnCreate);
        actions.add(btnEdit);
        actions.add(btnDeactivate);
        actions.add(btnReactivate);
        actions.add(btnDelete);

        add(actions, BorderLayout.SOUTH);

        // Events
        btnRefresh.addActionListener(e -> {
            if (searchTimer != null) searchTimer.stop();
            txtSearch.setText("");
            clearColumnFilters();
            loadEmployeesKeepSelection(null);
        });
        txtSearch.addActionListener(e -> loadEmployeesKeepSelection(null));

        btnViewDetails.addActionListener(e -> openSelectedEmployeeDetails());

        btnCreate.addActionListener(e -> openCreate());
        btnEdit.addActionListener(e -> openEdit());
        btnDeactivate.addActionListener(e -> deactivateSelected());
        btnReactivate.addActionListener(e -> reactivateSelected());
        btnDelete.addActionListener(e -> deleteSelected());

        // enable/disable details button based on selection
        btnViewDetails.setEnabled(false);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !applyingFilters) {
                applyCombinedFilters();
            table.getTableHeader().repaint();

            btnViewDetails.setEnabled(table.getSelectedRow() >= 0);
            }
        });

        loadEmployeesKeepSelection(null);
    }

    private void styleTopPill(JButton b) {
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusable(false);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setMargin(new java.awt.Insets(7, 16, 7, 16));
        b.setPreferredSize(new Dimension(Math.max(b.getPreferredSize().width, 96), 38));
        if (b instanceof RoundedButton rb) rb.setCornerRadius(18);
    }

    private void stylePill(JButton b, boolean primary) {
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusable(false);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setMargin(new java.awt.Insets(7, 16, 7, 16));
        if (primary) {
            b.setBackground(new Color(24, 130, 90));
            b.setForeground(Color.WHITE);
        } else {
            b.setBackground(new Color(245, 245, 245));
            b.setForeground(new Color(35, 35, 35));
        }
        b.setPreferredSize(new Dimension(Math.max(b.getPreferredSize().width, 94), 38));
        if (b instanceof RoundedButton rb) rb.setCornerRadius(18);
    }

    private void styleDanger(JButton b) {
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusable(false);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setMargin(new java.awt.Insets(7, 16, 7, 16));
        b.setBackground(new Color(214, 92, 92));
        b.setForeground(Color.WHITE);
        b.setPreferredSize(new Dimension(Math.max(b.getPreferredSize().width, 94), 38));
        if (b instanceof RoundedButton rb) rb.setCornerRadius(18);
    }

    public Integer getSelectedEmpId() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return null;

        int modelRow = table.convertRowIndexToModel(viewRow);

        Object v = model.getValueAt(modelRow, 7);
        if (v == null) return null;

        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return null;
        }
    }

    private void openSelectedEmployeeDetails() {
        Integer empId = getSelectedEmpId();
        if (empId == null) {
            JOptionPane.showMessageDialog(this,
                    "Please select an employee first.",
                    "No selection",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        Window w = SwingUtilities.getWindowAncestor(this);
        new EmployeeDetailsDialog(w, empId).setVisible(true);
    }

    // ✅ refresh table but try to keep selected employee
    private void loadEmployeesKeepSelection(Integer keepEmpId) {
        // Capture current UI state on EDT
        final Integer preserve = (keepEmpId != null) ? keepEmpId : getSelectedEmpId();
        final String keyword = txtSearch.getText().trim();

        // Prevent double-load spamming
        btnRefresh.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        new javax.swing.SwingWorker<java.util.List<Employee>, Void>() {
            @Override
            protected java.util.List<Employee> doInBackground() throws Exception {
                return employeeDAO.listEmployees(keyword);
            }

            @Override
            protected void done() {
                try {
                    java.util.List<Employee> list = get();

                    model.setRowCount(0);
                    int i = 1;
                    for (Employee e : list) {
                        String name = (nz(e.firstName) + " " + nz(e.middleName) + " " + nz(e.lastName))
                                .trim().replaceAll("\\s+", " ");

                        model.addRow(new Object[]{
                                i++,
                                e.empNo,
                                name,
                                e.position,
                                e.payType,
                                e.rate,
                                (e.status == null || e.status.isBlank()) ? "ACTIVE" : e.status,
                                e.empId
                        });
                    }

                    // restore selection best-effort
                    if (preserve != null) {
                        for (int r = 0; r < model.getRowCount(); r++) {
                            Object v = model.getValueAt(r, 7);
                            if (v != null && String.valueOf(v).equals(String.valueOf(preserve))) {
                                int viewRow = table.convertRowIndexToView(r);
                                table.getSelectionModel().setSelectionInterval(viewRow, viewRow);
                                table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
                                break;
                            }
                        }
                    }

                    applyCombinedFilters();
                    table.getTableHeader().repaint();
                    btnViewDetails.setEnabled(table.getSelectedRow() >= 0);

                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(EmployeesPanel.this,
                            "Load failed:\n" + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    btnRefresh.setEnabled(true);
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        }.execute();
    }

    private void openCreate() {
        Window w = SwingUtilities.getWindowAncestor(this);
        EmployeeAccountDialog dlg = new EmployeeAccountDialog(w);
        dlg.setVisible(true);

        loadEmployeesKeepSelection(null);
        notifyPayrollEmployeeListChanged(); // ✅ refresh payroll dropdown
    }

    private void openEdit() {
        Integer empId = getSelectedEmpId();
        if (empId == null) {
            JOptionPane.showMessageDialog(this, "Select an employee first.", "No selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Window w = SwingUtilities.getWindowAncestor(this);
            EmployeeAccountDialog dlg = new EmployeeAccountDialog(w, empId);
            dlg.setVisible(true);

            loadEmployeesKeepSelection(empId);
            notifyPayrollEmployeeListChanged(); // ✅ refresh payroll dropdown
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Open edit failed:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deactivateSelected() {
        Integer empId = getSelectedEmpId();
        if (empId == null) {
            JOptionPane.showMessageDialog(this, "Select an employee first.", "No selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // ✅ NO confirmation
        try {
            EmployeeDAO.deactivate(empId);
            loadEmployeesKeepSelection(empId);
            notifyPayrollEmployeeListChanged(); // ✅ THIS is what removes the “restart to update” issue
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Deactivate failed:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void reactivateSelected() {
        Integer empId = getSelectedEmpId();
        if (empId == null) {
            JOptionPane.showMessageDialog(this, "Select an employee first.", "No selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // ✅ NO confirmation
        try {
            EmployeeDAO.reactivate(empId);
            loadEmployeesKeepSelection(empId);
            notifyPayrollEmployeeListChanged(); // ✅ updates payroll immediately
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Reactivate failed:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteSelected() {
        Integer empId = getSelectedEmpId();
        if (empId == null) {
            JOptionPane.showMessageDialog(this, "Select an employee first.", "No selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int ok = JOptionPane.showConfirmDialog(this,
                "Delete this employee permanently?",
                "Confirm delete",
                JOptionPane.YES_NO_OPTION);

        if (ok != JOptionPane.YES_OPTION) return;

        try {
            employeeDAO.deleteEmployee(empId);
            loadEmployeesKeepSelection(null);
            notifyPayrollEmployeeListChanged(); // ✅ keep payroll dropdown in sync too
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Delete failed:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ✅ This is the missing “wire”
    private void notifyPayrollEmployeeListChanged() {
        Window w = SwingUtilities.getWindowAncestor(this);
        if (w instanceof DashboardFrame df) {
            df.refreshPayrollEmployeeList();
        }
    }

    private String nz(String s) { return s == null ? "" : s; }


    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /**
     * Creates a slightly lighter/darker color for zebra striping based on a base UI color.
     */
    private static Color tweak(Color c, int delta) {
        if (c == null) c = Color.white;
        return new Color(
                clamp(c.getRed() + delta),
                clamp(c.getGreen() + delta),
                clamp(c.getBlue() + delta)
        );
    }

    /**
     * UI-only tweaks:
     * - Make grid/column separator lines more visible
     * - Center ALL values per column
     * - Center header text and give header a stronger bottom divider
     */
    
    private void installHeaderFilters() {
        JTableHeader header = table.getTableHeader();
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;

                int viewCol = header.columnAtPoint(e.getPoint());
                if (viewCol < 0) return;

                int modelCol = table.convertColumnIndexToModel(viewCol);
                if (!isFilterableColumn(modelCol)) return;

                showFilterPopup(viewCol, e.getX(), e.getY());
            }
        });
    }

    private boolean isFilterableColumn(int modelCol) {
        // 0=#, 1=Emp No, 2=Name, 3=Position, 4=Pay Type, 5=Rate, 6=Status, 7=EMP_ID(hidden)
        return modelCol == 1 || modelCol == 2 || modelCol == 3 || modelCol == 4 || modelCol == 6;
    }

    private void clearColumnFilters() {
        columnFilters.clear();
        applyCombinedFilters();
        table.getTableHeader().repaint();
    }

    private void showFilterPopup(int viewCol, int x, int y) {
        if (activeFilterPopup != null && activeFilterPopup.isVisible()) {
            activeFilterPopup.setVisible(false);
        }

        int modelCol = table.convertColumnIndexToModel(viewCol);

        // Collect distinct values from the *currently visible* rows (after filters/search).
        // This avoids "blank table" surprises when stacking filters.
        // Collect distinct values from the FULL model so options never disappear after filtering.
        Set<String> values = new LinkedHashSet<>();
        for (int modelRow = 0; modelRow < model.getRowCount(); modelRow++) {
            Object v = model.getValueAt(modelRow, modelCol);
            if (v == null) continue;
            String s = String.valueOf(v).trim();
            if (!s.isEmpty()) values.add(s);
        }

        List<String> sorted = new ArrayList<>(values);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);

        String current = columnFilters.get(modelCol);

        JPopupMenu menu = new JPopupMenu();

        ButtonGroup group = new ButtonGroup();

        JRadioButtonMenuItem all = new JRadioButtonMenuItem("All");
        all.setSelected(current == null);
        all.addActionListener(e -> {
            columnFilters.remove(modelCol);
            applyCombinedFilters();
            table.getTableHeader().repaint();
        });
        group.add(all);
        menu.add(all);
        menu.addSeparator();

        for (String v : sorted) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(v);
            item.setSelected(v.equals(current));

            // Keep full option list visible, but disable choices that would yield 0 rows
            // when combined with the other active column filters.
            int matches = countMatchesWithCandidate(modelCol, v);
            if (matches == 0 && !v.equals(current)) {
                item.setEnabled(false);
            }

            item.addActionListener(e -> {
                columnFilters.put(modelCol, v);
                applyCombinedFilters();
                table.getTableHeader().repaint();
            });
            group.add(item);
            menu.add(item);
        }

        activeFilterPopup = menu;
        menu.show(table.getTableHeader(), x, y);
    }

    
    /**
     * Counts how many model rows would be visible if we apply the current column filters,
     * but with the given column set to the given candidate value.
     * Used to keep dropdown options stable while preventing "dead" selections.
     */
    private int countMatchesWithCandidate(int targetCol, String candidateValue) {
        if (model == null) return 0;

        int count = 0;
        for (int r = 0; r < model.getRowCount(); r++) {
            boolean ok = true;

            for (Map.Entry<Integer, String> e : columnFilters.entrySet()) {
                int col = e.getKey();
                String want = e.getValue();
                if (col == targetCol) continue; // handled by candidateValue
                if (want == null || want.isBlank()) continue;

                Object cell = model.getValueAt(r, col);
                String have = cell == null ? "" : String.valueOf(cell).trim();
                if (!have.equalsIgnoreCase(want.trim())) {
                    ok = false;
                    break;
                }
            }

            if (!ok) continue;

            // candidate for targetCol (if any)
            if (candidateValue != null && !candidateValue.isBlank()) {
                Object cell = model.getValueAt(r, targetCol);
                String have = cell == null ? "" : String.valueOf(cell).trim();
                if (!have.equalsIgnoreCase(candidateValue.trim())) continue;
            }

            count++;
        }
        return count;
    }

private void applyCombinedFilters() {
    if (applyingFilters) return;
    applyingFilters = true;
    try {
        // Try to keep the same selected employee after filtering (by Emp No column)
        Object selectedEmpNo = null;
        int viewSel = table.getSelectedRow();
        if (viewSel >= 0) {
            int modelSel = table.convertRowIndexToModel(viewSel);
            if (modelSel >= 0 && modelSel < model.getRowCount()) {
                selectedEmpNo = model.getValueAt(modelSel, 1); // "Emp No"
            }
        }

        List<RowFilter<DefaultTableModel, Object>> filters = new ArrayList<>();

        for (Map.Entry<Integer, String> e : columnFilters.entrySet()) {
            int col = e.getKey();
            String val = e.getValue();
            if (val == null || val.isBlank()) continue;

            String rx = "(?i)^" + Pattern.quote(val.trim()) + "$";
            filters.add(RowFilter.regexFilter(rx, col));
        }

        if (filters.isEmpty()) {
            sorter.setRowFilter(null);
        } else if (filters.size() == 1) {
            sorter.setRowFilter(filters.get(0));
        } else {
            sorter.setRowFilter(RowFilter.andFilter(filters));
        }

        // Restore selection if the previously selected Emp No is still visible
        if (selectedEmpNo != null) {
            for (int vr = 0; vr < table.getRowCount(); vr++) {
                int mr = table.convertRowIndexToModel(vr);
                if (mr >= 0 && mr < model.getRowCount()) {
                    Object empNo = model.getValueAt(mr, 1);
                    if (selectedEmpNo.equals(empNo)) {
                        table.getSelectionModel().setSelectionInterval(vr, vr);
                        break;
                    }
                }
            }
        }
    } finally {
        applyingFilters = false;
    }
}


    private static int parseIntSafe(Object v) {
        if (v == null) return 0;
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (Exception ex) {
            return 0;
        }
    }

    private static double parseDoubleSafe(Object v) {
        if (v == null) return 0.0;
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (Exception ex) {
            return 0.0;
        }
    }

private void applyTableStyling() {
        // Visible separators
        table.setShowGrid(true);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(true);
        table.setGridColor(new Color(180, 180, 180));
        table.setIntercellSpacing(new Dimension(1, 1));

        // Center all cell values + zebra striping (except the hidden EMP_ID column)
        final Color base = UIManager.getColor("Table.background");
        final Color zebraEven = base != null ? base : Color.white;
        final Color zebraOdd  = tweak(zebraEven, -8);

        DefaultTableCellRenderer zebraCenter = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int col) {
                super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, col);
                setHorizontalAlignment(SwingConstants.CENTER);

                // Zebra effect (keep selection colors intact)
                if (!isSelected) {
                    setBackground((row % 2 == 0) ? zebraEven : zebraOdd);
                }
                setOpaque(true);
                return this;
            }
        };

        for (int c = 0; c < table.getColumnModel().getColumnCount(); c++) {
            // Column 7 is EMP_ID (hidden)
            if (c == 7) continue;
            if (c == 6) {
                table.getColumnModel().getColumn(c).setCellRenderer(new StatusBadgeRenderer(zebraEven, zebraOdd));
            } else {
                table.getColumnModel().getColumn(c).setCellRenderer(zebraCenter);
            }
        }

// Center header text + stronger divider lines
        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);

        final TableCellRenderer defaultHeaderRenderer = header.getDefaultRenderer();
        header.setDefaultRenderer((tbl, value, isSelected, hasFocus, row, col) -> {
            Component comp = defaultHeaderRenderer.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, col);

            if (comp instanceof JLabel) {
                JLabel l = (JLabel) comp;
                l.setHorizontalAlignment(SwingConstants.CENTER);

                int modelCol = table.convertColumnIndexToModel(col);
                String baseText = value == null ? "" : String.valueOf(value);
                if (isFilterableColumn(modelCol)) {
                    String t = baseText + " ▾";
                    if (columnFilters.containsKey(modelCol)) t += " ✓";
                    l.setText(t);
                } else {
                    l.setText(baseText);
                }
                // Make header separators a bit more visible
                l.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 1, new Color(170, 170, 170)));
                l.setOpaque(true);
            } else if (comp instanceof JComponent) {
                ((JComponent) comp).setBorder(BorderFactory.createMatteBorder(0, 0, 2, 1, new Color(170, 170, 170)));
            }
            return comp;
        });
    }

    private static class PillTextField extends JTextField {
        private static final long serialVersionUID = 1L;
        private final Color border = new Color(220, 206, 186);

        PillTextField(int columns) {
            super(columns);
            setFont(new Font("Segoe UI", Font.PLAIN, 12));
            setOpaque(false);
            setBorder(new EmptyBorder(9, 14, 9, 14));
            setPreferredSize(new Dimension(280, 38));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = h;

            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, w, h, arc, arc);

            g2.setColor(border);
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

            g2.dispose();
            super.paintComponent(g);
        }
    }

}


class StatusBadgeRenderer extends DefaultTableCellRenderer {
    private final Color zebraEven;
    private final Color zebraOdd;

    StatusBadgeRenderer(Color zebraEven, Color zebraOdd) {
        this.zebraEven = zebraEven;
        this.zebraOdd = zebraOdd;
        setHorizontalAlignment(SwingConstants.CENTER);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                   boolean hasFocus, int row, int column) {
        String status = value == null ? "" : value.toString().trim().toUpperCase();

        if (table.isRowSelected(row)) {
            JPanel flatCell = new JPanel(new BorderLayout());
            flatCell.setOpaque(true);
            flatCell.setBackground(table.getSelectionBackground());
            flatCell.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

            JLabel flatText = new JLabel(status, SwingConstants.CENTER);
            flatText.setOpaque(false);
            flatText.setForeground(table.getSelectionForeground());
            flatText.setFont(flatText.getFont().deriveFont(Font.BOLD, 12f));

            flatCell.add(flatText, BorderLayout.CENTER);
            return flatCell;
        }

        Color rowBase = (row % 2 == 0) ? zebraEven : zebraOdd;

        JPanel outer = new JPanel(new GridBagLayout());
        outer.setOpaque(true);
        outer.setBackground(rowBase);
        outer.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));

        RoundedBadge pill = new RoundedBadge(status);
        if ("ACTIVE".equals(status)) {
            pill.setBadgeColors(new Color(232, 247, 238), new Color(18, 120, 72));
        } else if ("INACTIVE".equals(status)) {
            pill.setBadgeColors(new Color(252, 237, 237), new Color(185, 52, 52));
        } else {
            pill.setBadgeColors(new Color(242, 244, 247), new Color(83, 90, 98));
        }

        outer.add(pill);
        return outer;
    }

    private static class RoundedBadge extends JLabel {
        private Color bg = new Color(232, 247, 238);
        private Color fg = new Color(18, 120, 72);

        RoundedBadge(String text) {
            super(text, SwingConstants.CENTER);
            setFont(getFont().deriveFont(Font.BOLD, 12f));
            setBorder(BorderFactory.createEmptyBorder(7, 22, 7, 22));
            setOpaque(false);
        }

        void setBadgeColors(Color bg, Color fg) {
            this.bg = bg;
            this.fg = fg;
            setForeground(fg);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d.height = Math.max(d.height, 32);
            return d;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 999, 999);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
