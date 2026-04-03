package ui;

import dao.DB;
import dao.EmployeeDAO;
import dao.HolidayDAO;
import model.Employee;
import model.Holiday;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

public class LogbookPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final DefaultTableModel model;
    private final JTable table;
    
    private final TableRowSorter<DefaultTableModel> sorter;

    // Hover support
    private int hoverRow = -1;

    // Top controls
    private final PillTextField txtSearch = new PillTextField(22);

    // Date filter (per date)
    private boolean dateFilterEnabled = false; // default: show all dates
    private final DateTimeValue filterDateVal = new DateTimeValue(LocalDate.now(), 12, 0, false);
    private final DatePickerField dpFilterDate = new DatePickerField(filterDateVal, this::onFilterDatePicked);
    private final PillButton btnAllDates = new PillButton("All", false);

    private final PillButton btnAdd = new PillButton("Add Log", false);
    private final PillButton btnRefresh = new PillButton("Refresh", false);
    private final PillButton btnEdit = new PillButton("Edit", false);
    private final PillButton btnDelete = new PillButton("Delete", true);

    // Live timer for running shifts (no time-out yet)
    private final javax.swing.Timer liveTimer;

    // Columns
    private static final int COL_LOG_ID = 0;  // hidden
    private static final int COL_EMP = 1;
    private static final int COL_IN = 2;
    private static final int COL_LUNCH_OUT = 3;
    private static final int COL_LUNCH_IN = 4;
    private static final int COL_OUT = 5;
    private static final int COL_HOURS = 6;
    private static final int COL_IN_MS = 7;   // hidden
    private static final int COL_LUNCH_OUT_MS = 8; // hidden
    private static final int COL_LUNCH_IN_MS = 9;  // hidden
    private static final int COL_OUT_MS = 10;  // hidden

    // 12-hour format with AM/PM
    private final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd  hh:mm:ss a");

    private final HolidayDAO holidayDAO = new HolidayDAO();
    private final Map<LocalDate, String> holidayTypeByDate = new HashMap<>();

    private static final Color COLOR_REGULAR_HOLIDAY = new Color(255, 242, 220);
    private static final Color COLOR_SPECIAL_HOLIDAY = new Color(226, 245, 228);
    private static final Color COLOR_NORMAL = Color.WHITE;

    private static final Color COLOR_HOVER = new Color(245, 248, 252);

    public LogbookPanel() {
        setLayout(new BorderLayout(0, 10));
        setOpaque(false);
        setBorder(new EmptyBorder(10, 12, 10, 12));

        // ===== TOP TOOLBAR =====
        JPanel toolbar = new JPanel(new BorderLayout(10, 0));
        toolbar.setOpaque(false);
        toolbar.setBorder(new EmptyBorder(6, 0, 10, 0));

        JPanel leftTools = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        leftTools.setOpaque(false);

        JLabel lblSearch = new JLabel("Search:");
        lblSearch.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblSearch.setForeground(new Color(70, 70, 70));

        leftTools.add(lblSearch);
        leftTools.add(txtSearch);


        JLabel lblDateFilter = new JLabel("Date:");
        lblDateFilter.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblDateFilter.setForeground(new Color(70, 70, 70));

        dpFilterDate.setPreferredSize(new Dimension(140, 32));
        btnAllDates.setForeground(new Color(70, 70, 70));
        btnAllDates.setToolTipText("Show all dates");

        btnAllDates.setBackground(new Color(245, 245, 245));
        btnAdd.setBackground(new Color(24, 130, 90));
        btnAdd.setForeground(Color.WHITE);
        btnRefresh.setBackground(new Color(245, 245, 245));
        btnRefresh.setForeground(new Color(55, 55, 55));
        btnEdit.setBackground(new Color(235, 244, 255));
        btnEdit.setForeground(new Color(40, 90, 160));
        btnDelete.setBackground(new Color(214, 92, 92));
        btnDelete.setForeground(Color.WHITE);

        leftTools.add(lblDateFilter);
        leftTools.add(dpFilterDate);
        leftTools.add(btnAllDates);
        JPanel rightTools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 6));
        rightTools.setOpaque(false);

        rightTools.add(btnAdd);
        rightTools.add(btnRefresh);
        rightTools.add(btnEdit);
        rightTools.add(btnDelete);
        rightTools.add(createLegendPanel());

        toolbar.add(leftTools, BorderLayout.WEST);
        toolbar.add(rightTools, BorderLayout.EAST);
        add(toolbar, BorderLayout.NORTH);

        // ===== TABLE =====
        model = new DefaultTableModel(
                new Object[]{"LOG_ID", "Employee", "Time In", "Lunch Out", "Lunch In", "Time Out", "Total Hours", "IN_MS", "LUNCH_OUT_MS", "LUNCH_IN_MS", "OUT_MS"}, 0
        ) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        table = new JTable(model);
        styleTable(table);

        sorter = new TableRowSorter<>(model);
        // FIX: keep sorter for search filtering, but DISABLE sorting to prevent shuffle on header click
        for (int i = 0; i < model.getColumnCount(); i++) {
            sorter.setSortable(i, false);
        }
        sorter.setSortKeys(null);
        table.setRowSorter(sorter);
hideColumn(COL_LOG_ID);
        hideColumn(COL_IN_MS);
        hideColumn(COL_LUNCH_OUT_MS);
        hideColumn(COL_LUNCH_IN_MS);
        hideColumn(COL_OUT_MS);

        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(Color.WHITE);
        add(sp, BorderLayout.CENTER);

        // ===== HOVER LISTENERS =====
        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                int r = table.rowAtPoint(e.getPoint());
                if (r != hoverRow) {
                    hoverRow = r;
                    table.repaint();
                }
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseExited(MouseEvent e) {
                hoverRow = -1;
                table.repaint();
            }

            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    editSelected();
                }
            }
        });

        // ===== EVENTS =====
        btnAdd.addActionListener(e -> addLog());
        btnRefresh.addActionListener(e -> refreshLogbook());
        btnEdit.addActionListener(e -> editSelected());
        btnDelete.addActionListener(e -> deleteSelected());


        btnAllDates.addActionListener(e -> {
            dateFilterEnabled = false;
            applyFilters();
        });
        txtSearch.getDocument().addDocumentListener(new DocumentListener() {
            private void apply() { applyFilters(); }
            @Override public void insertUpdate(DocumentEvent e) { apply(); }
            @Override public void removeUpdate(DocumentEvent e) { apply(); }
            @Override public void changedUpdate(DocumentEvent e) { apply(); }
        });

        // LIVE update uses PC time -> no +8 shift bug
        liveTimer = new javax.swing.Timer(10_000, e -> updateLiveTotals());
        liveTimer.setRepeats(true);
        liveTimer.start();

        // Initial load should not freeze UI
        loadLogsAsync(true);
    }


    // ===================== FILTERING =====================
    private void onFilterDatePicked() {
        // user picked a date -> enable date filter
        dateFilterEnabled = true;
        applyFilters();
    }

    private void applyFilters() {
        final String searchText = txtSearch.getText() == null ? "" : txtSearch.getText().trim();
        final boolean hasSearch = !searchText.isEmpty();
        final boolean useDate = dateFilterEnabled;
        final LocalDate targetDate = filterDateVal == null ? null : filterDateVal.date;

        if (!hasSearch && !useDate) {
            sorter.setRowFilter(null);
            return;
        }

        sorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                // Date filter uses hidden IN_MS column
                if (useDate && targetDate != null) {
                    Object inMsObj = entry.getValue(COL_IN_MS);
                    long inMs = 0L;
                    try { inMs = Long.parseLong(String.valueOf(inMsObj)); } catch (Exception ignored) {}
                    if (inMs <= 0L) return false;

                    LocalDate rowDate = Instant.ofEpochMilli(inMs)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate();
                    if (!targetDate.equals(rowDate)) return false;
                }

                if (hasSearch) {
                    String needle = searchText.toLowerCase(Locale.ROOT);

                    // Only search visible text columns (Employee, Time In, Time Out, Total Hours)
                    for (int c = COL_EMP; c <= COL_HOURS; c++) {
                        Object v = entry.getValue(c);
                        if (v != null && String.valueOf(v).toLowerCase(Locale.ROOT).contains(needle)) {
                            return true;
                        }
                    }
                    return false;
                }

                return true;
            }
        });
    }

    /**
     * Re-loads logs and holiday coloring from the database without restarting the app.
     * This is needed when other computers add new time logs, or when admin adds/edits holidays.
     */
    private void refreshLogbook() {
        // keep current search + date filter state (applyFilters() will re-apply)
        loadLogsAsync(true);
    }

    /**
     * Loads logs on a background thread so the UI doesn't freeze on Azure.
     */
    private void loadLogsAsync(boolean enableBusyUi) {
        if (enableBusyUi) {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            btnRefresh.setEnabled(false);
        }

        final String searchKeep = txtSearch.getText(); // keep state
        final boolean dateEnabledKeep = dateFilterEnabled;
        // DateTimeValue is a mutable holder used by DatePickerField.
        // Keep only the date part and restore it later without reassigning the holder.
        final LocalDate dateKeep = (filterDateVal == null ? null : filterDateVal.date);

        new javax.swing.SwingWorker<LogbookData, Void>() {
            @Override
            protected LogbookData doInBackground() throws Exception {
                return fetchLogbookData();
            }

            @Override
            protected void done() {
                try {
                    LogbookData data = get();
                    if (data == null) return;

                    // restore filter state
                    txtSearch.setText(searchKeep == null ? "" : searchKeep);
                    dateFilterEnabled = dateEnabledKeep;
                    if (filterDateVal != null) {
                        filterDateVal.date = dateKeep;
                    }

                    model.setRowCount(0);
                    holidayTypeByDate.clear();
                    holidayTypeByDate.putAll(data.holidayTypeByDate);

                    for (Object[] row : data.rows) {
                        model.addRow(row);
                    }

                    applyFilters();
                    table.repaint();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(LogbookPanel.this,
                            "Failed to load logbook:\n" + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    if (enableBusyUi) {
                        btnRefresh.setEnabled(true);
                        setCursor(Cursor.getDefaultCursor());
                    }
                }
            }
        }.execute();
    }

    private static final class LogbookData {
        final java.util.List<Object[]> rows;
        final java.util.Map<LocalDate, String> holidayTypeByDate;

        LogbookData(java.util.List<Object[]> rows, java.util.Map<LocalDate, String> holidayTypeByDate) {
            this.rows = rows;
            this.holidayTypeByDate = holidayTypeByDate;
        }
    }


    // ===================== LEGEND UI =====================
    private JPanel createLegendPanel() {
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        legend.setOpaque(false);

        JLabel title = new JLabel("Legend:");
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        title.setForeground(new Color(90, 90, 90));
        legend.add(title);

        legend.add(createLegendItem(COLOR_REGULAR_HOLIDAY, "Regular Holiday"));
        legend.add(createLegendItem(COLOR_SPECIAL_HOLIDAY, "Special Holiday"));
        legend.add(createLegendItem(COLOR_NORMAL, "Normal"));

        return legend;
    }

    private JPanel createLegendItem(Color color, String text) {
        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        item.setOpaque(false);

        JLabel box = new JLabel();
        box.setPreferredSize(new Dimension(14, 14));
        box.setOpaque(true);
        box.setBackground(color);
        box.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));

        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        label.setForeground(new Color(70, 70, 70));

        item.add(box);
        item.add(label);
        return item;
    }

    // ===================== HOLIDAY + HOVER RENDERER =====================
    private class HolidayRowRenderer extends DefaultTableCellRenderer {
        HolidayRowRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean selected,
                boolean focused, int row, int col) {

            super.getTableCellRendererComponent(table, value, selected, focused, row, col);

            if (selected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground());
                return this;
            }

            int modelRow = table.convertRowIndexToModel(row);
            long inMs = toLong(model.getValueAt(modelRow, COL_IN_MS));

            Color base = COLOR_NORMAL;
            if (inMs > 0) {
                LocalDate d = Instant.ofEpochMilli(inMs).atZone(ZoneId.systemDefault()).toLocalDate();
                String type = holidayTypeByDate.get(d);
                if ("REGULAR".equalsIgnoreCase(type)) base = COLOR_REGULAR_HOLIDAY;
                else if (type != null && type.toUpperCase().startsWith("SPECIAL")) base = COLOR_SPECIAL_HOLIDAY;
            }

            // hover: if normal -> hover tint, if holiday -> mix
            if (row == hoverRow) {
                setBackground(base.equals(COLOR_NORMAL) ? COLOR_HOVER : mix(base, COLOR_HOVER, 0.65f));
            } else {
                setBackground(base);
            }

            setForeground(Color.BLACK);
            return this;
        }
    }

    private static Color mix(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int) (a.getRed() * t + b.getRed() * (1f - t));
        int g = (int) (a.getGreen() * t + b.getGreen() * (1f - t));
        int bl = (int) (a.getBlue() * t + b.getBlue() * (1f - t));
        return new Color(r, g, bl);
    }

    private void styleTable(JTable t) {
        t.setRowHeight(36);
        t.setFillsViewportHeight(true);

        // ✅ visible separators
        t.setShowVerticalLines(true);
        t.setShowHorizontalLines(true);
        t.setGridColor(new Color(185, 185, 185)); // stronger lines

        t.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);

        JTableHeader th = t.getTableHeader();
        th.setReorderingAllowed(false);
        th.setFont(new Font("Segoe UI", Font.BOLD, 12));

        
        // FIX: prevent header click/hover behavior (no sort dropdown/arrow, no shuffling)
        th.setCursor(Cursor.getDefaultCursor());
        th.setToolTipText(null);
        for (MouseListener ml : th.getMouseListeners()) th.removeMouseListener(ml);
        for (MouseMotionListener mml : th.getMouseMotionListeners()) th.removeMouseMotionListener(mml);

        // Make grid lines look solid/continuous (no gaps)
        t.setIntercellSpacing(new Dimension(0, 0));
        t.setRowMargin(0);
TableColumnModel cm = t.getColumnModel();
        cm.getColumn(COL_EMP).setPreferredWidth(240);
        cm.getColumn(COL_IN).setPreferredWidth(190);
        cm.getColumn(COL_LUNCH_OUT).setPreferredWidth(170);
        cm.getColumn(COL_LUNCH_IN).setPreferredWidth(170);
        cm.getColumn(COL_OUT).setPreferredWidth(190);
        cm.getColumn(COL_HOURS).setPreferredWidth(120);

        HolidayRowRenderer rr = new HolidayRowRenderer();
        for (int i = COL_EMP; i <= COL_HOURS; i++) cm.getColumn(i).setCellRenderer(rr);

        DefaultTableCellRenderer headerR = (DefaultTableCellRenderer) t.getTableHeader().getDefaultRenderer();
        headerR.setHorizontalAlignment(SwingConstants.CENTER);

        
        // Solid column separators (header + cells)
        final Color grid = new Color(185, 185, 185);
        headerR.setBorder(new MatteBorder(0, 0, 1, 1, grid));
        // Use HolidayRowRenderer for cells so holiday + hover coloring works; just give it the same solid borders
        rr.setBorder(new MatteBorder(0, 0, 1, 1, grid));
        // Apply HolidayRowRenderer to all VISIBLE columns (emp, in, out, hours)
        for (int i = COL_EMP; i <= COL_HOURS; i++) {
            t.getColumnModel().getColumn(i).setCellRenderer(rr);
        }
t.setSelectionBackground(new Color(35, 120, 195));
        t.setSelectionForeground(Color.WHITE);
    }

    private void hideColumn(int modelIndex) {
        TableColumn col = table.getColumnModel().getColumn(modelIndex);
        col.setMinWidth(0);
        col.setMaxWidth(0);
        col.setPreferredWidth(0);
    }

    // ===================== LOAD LOGS =====================
    private static class RowData {
        final Object logIdObj;
        final String emp;
        final Timestamp tin;
        final Timestamp lunchOut;
        final Timestamp lunchIn;
        final Timestamp tout;

        RowData(Object logIdObj, String emp, Timestamp tin, Timestamp lunchOut, Timestamp lunchIn, Timestamp tout) {
            this.logIdObj = logIdObj;
            this.emp = emp;
            this.tin = tin;
            this.lunchOut = lunchOut;
            this.lunchIn = lunchIn;
            this.tout = tout;
        }
    }

    /**
     * Fetch logbook rows + holiday types from DB (runs on background thread).
     */
    private LogbookData fetchLogbookData() throws Exception {
        java.util.List<Object[]> outRows = new ArrayList<>();
        java.util.Map<LocalDate, String> holType = new java.util.HashMap<>();

        try (Connection con = DB.getConnection()) {
            String tlLogId = pickColumn(con, "time_logs", "id", "log_id", "time_log_id");
            String tlEmpFk = pickColumn(con, "time_logs", "emp_id", "employee_id", "empno", "employee_no");
            String tlTimeIn = pickColumn(con, "time_logs", "time_in", "timein", "clock_in");
            String tlLunchOut = pickColumnOptional(con, "time_logs", "lunch_out", "lunchout");
            String tlLunchIn = pickColumnOptional(con, "time_logs", "lunch_in", "lunchin");
            String tlTimeOut = pickColumn(con, "time_logs", "time_out", "timeout", "clock_out");

            String empPk = pickColumn(con, "employees", "emp_id", "employee_id", "id", "empno", "employee_no");
            String fName = pickColumnOptional(con, "employees", "first_name", "fname", "firstName", "given_name");
            String lName = pickColumnOptional(con, "employees", "last_name", "lname", "lastName", "surname");
            String fullName = pickColumnOptional(con, "employees", "full_name", "name");

            String nameExpr;
            if (fullName != null) nameExpr = "e." + fullName;
            else if (fName != null && lName != null)
                nameExpr = "CONCAT(IFNULL(e." + fName + ",''),' ',IFNULL(e." + lName + ",''))";
            else if (fName != null) nameExpr = "e." + fName;
            else nameExpr = "CAST(t." + tlEmpFk + " AS CHAR)";

            String sql =
                    "SELECT t." + tlLogId + " AS log_id, " +
                            nameExpr + " AS employee_name, " +
                            "t." + tlTimeIn + " AS time_in, " +
                            (tlLunchOut != null ? "t." + tlLunchOut + " AS lunch_out, " : "NULL AS lunch_out, ") +
                            (tlLunchIn != null ? "t." + tlLunchIn + " AS lunch_in, " : "NULL AS lunch_in, ") +
                            "t." + tlTimeOut + " AS time_out " +
                            "FROM time_logs t " +
                            "LEFT JOIN employees e ON e." + empPk + " = t." + tlEmpFk + " " +
                            "ORDER BY t." + tlTimeIn + " DESC";

            java.util.List<RowData> rows = new ArrayList<>();
            Set<LocalDate> dates = new HashSet<>();

            try (PreparedStatement ps = con.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Object logIdObj = rs.getObject("log_id");
                    String emp = rs.getString("employee_name");
                    Timestamp tin = rs.getTimestamp("time_in");
                    Timestamp lunchOut = rs.getTimestamp("lunch_out");
                    Timestamp lunchIn = rs.getTimestamp("lunch_in");
                    Timestamp tout = rs.getTimestamp("time_out");

                    rows.add(new RowData(logIdObj, emp, tin, lunchOut, lunchIn, tout));

                    if (tin != null) {
                        LocalDate d = Instant.ofEpochMilli(tin.getTime())
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate();
                        dates.add(d);
                    }
                }
            }

            // Cache holiday types per date (ONE query, reuse same connection)
            try {
                java.util.Map<LocalDate, Holiday> holByDate = holidayDAO.mapByDates(con, dates);
                for (java.util.Map.Entry<LocalDate, Holiday> en : holByDate.entrySet()) {
                    Holiday h = en.getValue();
                    if (h != null && h.type != null) {
                        String norm = normalizeHolidayType(h.type);
                        if (norm != null) holType.put(en.getKey(), norm);
                    }
                }
            } catch (Exception ignored) {
                // fallback: no holiday coloring if query fails
            }

            for (RowData rd : rows) {
                Timestamp tin = rd.tin;
                Timestamp lunchOut = rd.lunchOut;
                Timestamp lunchIn = rd.lunchIn;
                Timestamp tout = rd.tout;

                String sIn = (tin == null) ? "-" : df.format(tin);
                String sLunchOut = (lunchOut == null) ? "-" : df.format(lunchOut);
                String sLunchIn = (lunchIn == null) ? "-" : df.format(lunchIn);
                String sOut = (tout == null) ? "-" : df.format(tout);

                long inMs = (tin == null) ? 0L : tin.getTime();
                long lunchOutMs = (lunchOut == null) ? 0L : lunchOut.getTime();
                long lunchInMs = (lunchIn == null) ? 0L : lunchIn.getTime();
                long outMs = (tout == null) ? 0L : tout.getTime();

                String total = "-";
                if (tin != null) {
                    total = fmtDurationMsWithLunch(inMs, lunchOutMs, lunchInMs, outMs > 0 ? outMs : System.currentTimeMillis());
                }

                outRows.add(new Object[]{
                        rd.logIdObj == null ? "" : String.valueOf(rd.logIdObj),
                        (rd.emp == null || rd.emp.trim().isEmpty()) ? "Unknown" : rd.emp.trim(),
                        sIn, sLunchOut, sLunchIn, sOut, total, inMs, lunchOutMs, lunchInMs, outMs
                });
            }
        }

        return new LogbookData(outRows, holType);
    }

    /**
     * Legacy helper (kept for compatibility with older calls): uses async loader.
     */
    private void loadLogs() {
        loadLogsAsync(true);
    }

    private String normalizeHolidayType(String raw) {
        String s = raw.trim().toUpperCase(Locale.ROOT);
        if (s.contains("REG")) return "REGULAR";
        if (s.equals("RH")) return "REGULAR";
        if (s.contains("SPEC")) return "SPECIAL";
        if (s.equals("SH")) return "SPECIAL";
        return null;
    }

    private void updateLiveTotals() {
        if (model.getRowCount() == 0) return;
        long now = System.currentTimeMillis();

        for (int r = 0; r < model.getRowCount(); r++) {
            long inMs = toLong(model.getValueAt(r, COL_IN_MS));
            long lunchOutMs = toLong(model.getValueAt(r, COL_LUNCH_OUT_MS));
            long lunchInMs = toLong(model.getValueAt(r, COL_LUNCH_IN_MS));
            long outMs = toLong(model.getValueAt(r, COL_OUT_MS));
            if (inMs <= 0) continue;
            if (outMs > 0) continue;
            model.setValueAt(fmtDurationMsWithLunch(inMs, lunchOutMs, lunchInMs, now), r, COL_HOURS);
        }
    }

    private long toLong(Object v) {
        if (v == null) return 0L;
        try { return Long.parseLong(String.valueOf(v)); }
        catch (Exception e) { return 0L; }
    }

    private String fmtDurationMs(long startMs, long endMs) {
        long diffMs = endMs - startMs;
        if (diffMs < 0) diffMs = 0;
        long mins = diffMs / 60000;
        long hrs = mins / 60;
        long rem = mins % 60;
        return hrs + "h " + rem + "m";
    }

    private String fmtDurationMsWithLunch(long startMs, long lunchOutMs, long lunchInMs, long endMs) {
        long diffMs = endMs - startMs;
        if (diffMs < 0) diffMs = 0;
        long lunchMs = 0L;
        if (lunchOutMs > 0) {
            long effectiveLunchIn = lunchInMs > 0 ? lunchInMs : endMs;
            lunchMs = Math.max(0L, effectiveLunchIn - lunchOutMs);
        }
        long mins = Math.max(0L, (diffMs - lunchMs) / 60000L);
        long hrs = mins / 60;
        long rem = mins % 60;
        return hrs + "h " + rem + "m";
    }

    // ===================== ADD / EDIT / DELETE =====================
    private void addLog() {
        Window w = SwingUtilities.getWindowAncestor(this);
        LogEditDialog dlg = new LogEditDialog(w, null);
        dlg.setVisible(true);
        if (dlg.saved) loadLogsAsync(true);
    }

    private void editSelected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Select a log row first.", "No selection",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int row = table.convertRowIndexToModel(viewRow);
        String logId = String.valueOf(model.getValueAt(row, COL_LOG_ID));
        if (logId == null || logId.isBlank()) {
            JOptionPane.showMessageDialog(this, "This record has no log id.", "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        Window w = SwingUtilities.getWindowAncestor(this);
        LogEditDialog dlg = new LogEditDialog(w, logId);
        dlg.setVisible(true);
        if (dlg.saved) loadLogsAsync(true);
    }

    private void deleteSelected() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Select a log row first.", "No selection",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int row = table.convertRowIndexToModel(viewRow);
        String logId = String.valueOf(model.getValueAt(row, COL_LOG_ID));
        if (logId == null || logId.isBlank()) return;

        int ok = JOptionPane.showConfirmDialog(this,
                "Delete this log record?",
                "Confirm delete", JOptionPane.YES_NO_OPTION);

        if (ok != JOptionPane.YES_OPTION) return;

        try (Connection con = DB.getConnection()) {
            String tlLogId = pickColumn(con, "time_logs", "id", "log_id", "time_log_id");
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM time_logs WHERE " + tlLogId + "=?")) {
                ps.setString(1, logId);
                ps.executeUpdate();
            }
            loadLogsAsync(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Delete failed:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ==========================
    // Date + Digital Time Picker
    // ==========================
    private static class DateTimeValue {
        LocalDate date;
        int hour12;      // 1..12
        int minute;      // 0..59
        boolean pm;

        DateTimeValue(LocalDate d, int h12, int m, boolean pm) {
            this.date = d;
            this.hour12 = clamp(h12, 1, 12);
            this.minute = clamp(m, 0, 59);
            this.pm = pm;
        }

        static int clamp(int v, int a, int b) { return Math.max(a, Math.min(b, v)); }

        LocalDateTime toLocalDateTime() {
            int h = hour12 % 12;
            if (pm) h += 12;
            return LocalDateTime.of(date, LocalTime.of(h, minute, 0));
        }

        void fromLocalDateTime(LocalDateTime ldt) {
            this.date = ldt.toLocalDate();
            int h24 = ldt.getHour();
            this.pm = h24 >= 12;
            int h = h24 % 12;
            if (h == 0) h = 12;
            this.hour12 = h;
            this.minute = ldt.getMinute();
        }

        String dateText() {
            return String.format("%04d-%02d-%02d",
                    date.getYear(), date.getMonthValue(), date.getDayOfMonth());
        }

        String timeText() {
            return String.format("%02d:%02d %s", hour12, minute, pm ? "PM" : "AM");
        }
    }

    private static class PopupDialogUtil {
        static JDialog createPopupDialog(Component anchor, JComponent content) {
            Window owner = SwingUtilities.getWindowAncestor(anchor);
            JDialog dlg = new JDialog(owner);
            dlg.setUndecorated(true);
            dlg.setModalityType(Dialog.ModalityType.MODELESS);

            JPanel wrap = new JPanel(new BorderLayout());
            wrap.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(220, 220, 220)),
                    new EmptyBorder(8, 8, 8, 8)
            ));
            wrap.setBackground(Color.WHITE);
            wrap.add(content, BorderLayout.CENTER);

            dlg.setContentPane(wrap);
            dlg.pack();

            Point p = anchor.getLocationOnScreen();
            dlg.setLocation(p.x, p.y + anchor.getHeight() + 2);

            // close when clicking outside
            AWTEventListener outside = new AWTEventListener() {
                @Override public void eventDispatched(AWTEvent event) {
                    if (!(event instanceof MouseEvent)) return;
                    MouseEvent me = (MouseEvent) event;
                    if (me.getID() != MouseEvent.MOUSE_PRESSED) return;

                    Component src = me.getComponent();
                    if (src == null) return;

                    if (SwingUtilities.isDescendingFrom(src, dlg)) return;
                    if (SwingUtilities.isDescendingFrom(src, anchor)) return;

                    dlg.setVisible(false);
                    dlg.dispose();
                    Toolkit.getDefaultToolkit().removeAWTEventListener(this);
                }
            };
            Toolkit.getDefaultToolkit().addAWTEventListener(outside, AWTEvent.MOUSE_EVENT_MASK);

            dlg.getRootPane().registerKeyboardAction(e -> {
                dlg.setVisible(false);
                dlg.dispose();
                Toolkit.getDefaultToolkit().removeAWTEventListener(outside);
            }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

            return dlg;
        }
    }

    private static class DatePickerField extends JPanel {
        private static final long serialVersionUID = 1L;

        private final JTextField txt = new JTextField();
        private final JButton btn = new JButton("📅");
        private DateTimeValue value;

        private final Runnable onChange;
        DatePickerField(DateTimeValue v) {
            this(v, null);
        }

        DatePickerField(DateTimeValue v, Runnable onChange) {
            this.value = v;
            this.onChange = onChange;
            setLayout(new BorderLayout(6, 0));
            setOpaque(false);

            txt.setEditable(false);
            txt.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            btn.setFocusable(false);
            btn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 12));
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            add(txt, BorderLayout.CENTER);
            add(btn, BorderLayout.EAST);

            refreshText();

            MouseAdapter open = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { if (isEnabled()) showDialog(); }
            };
            txt.addMouseListener(open);
            btn.addActionListener(e -> { if (isEnabled()) showDialog(); });
        }

        void setValue(DateTimeValue v) { this.value = v; refreshText(); }
        DateTimeValue getValue() { return value; }

        @Override public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            txt.setEnabled(enabled);
            btn.setEnabled(enabled);
            txt.setCursor(enabled ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            btn.setCursor(enabled ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        }

        private void refreshText() { txt.setText(value.dateText()); }

        private void showDialog() {
            CalendarPanel cal = new CalendarPanel(value.date, picked -> {
                value.date = picked;
                refreshText();
                if (onChange != null) onChange.run();
            });

            JDialog dlg = PopupDialogUtil.createPopupDialog(this, cal);
            cal.setCloseCallback(() -> {
                dlg.setVisible(false);
                dlg.dispose();
            });

            dlg.setVisible(true);
            SwingUtilities.invokeLater(cal::focusMonthYear);
        }
    }

    private static class TimePickerField extends JPanel {
        private static final long serialVersionUID = 1L;

        private final JTextField txt = new JTextField();
        private final JButton btn = new JButton("🕒");
        private DateTimeValue value;

        TimePickerField(DateTimeValue v) {
            this.value = v;
            setLayout(new BorderLayout(6, 0));
            setOpaque(false);

            txt.setEditable(false);
            txt.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            btn.setFocusable(false);
            btn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 12));
            btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            add(txt, BorderLayout.CENTER);
            add(btn, BorderLayout.EAST);

            refreshText();

            MouseAdapter open = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { if (isEnabled()) showDialog(); }
            };
            txt.addMouseListener(open);
            btn.addActionListener(e -> { if (isEnabled()) showDialog(); });
        }

        void setValue(DateTimeValue v) { this.value = v; refreshText(); }
        DateTimeValue getValue() { return value; }

        @Override public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            txt.setEnabled(enabled);
            btn.setEnabled(enabled);
            txt.setCursor(enabled ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            btn.setCursor(enabled ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        }

        private void refreshText() { txt.setText(value.timeText()); }

        private void showDialog() {
            DigitalTimePickerPanel panel = new DigitalTimePickerPanel(
                    value.hour12, value.minute, value.pm,
                    (h12, min, pmSel) -> {
                        value.hour12 = h12;
                        value.minute = min;
                        value.pm = pmSel;
                        refreshText();
                    }
            );

            JPanel wrap = new JPanel(new BorderLayout(0, 8));
            wrap.setOpaque(false);
            wrap.add(panel, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            bottom.setOpaque(false);
            JButton done = new JButton("Done");
            done.setFocusable(false);
            bottom.add(done);
            wrap.add(bottom, BorderLayout.SOUTH);

            JDialog dlg = PopupDialogUtil.createPopupDialog(this, wrap);
            done.addActionListener(e -> {
                dlg.setVisible(false);
                dlg.dispose();
            });

            dlg.setVisible(true);
        }
    }

    private interface DatePickedListener { void onPicked(LocalDate date); }

    private static class CalendarPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        private YearMonth ym;
        private LocalDate selected;
        private final DatePickedListener listener;

        private final JComboBox<String> cbMonth = new JComboBox<>();
        private final JSpinner spYear;
        private final JPanel grid = new JPanel(new GridLayout(0, 7, 6, 6));

        private Runnable closeCallback;

        CalendarPanel(LocalDate initial, DatePickedListener l) {
            this.listener = l;
            this.selected = initial;
            this.ym = YearMonth.from(initial);

            setLayout(new BorderLayout(8, 8));
            setBackground(Color.WHITE);

            spYear = new JSpinner(new SpinnerNumberModel(LocalDate.now().getYear(), 1970, 2100, 1));
            spYear.setEditor(new JSpinner.NumberEditor(spYear, "####")); // no comma year
            spYear.setValue(ym.getYear());

            JPanel top = new JPanel(new BorderLayout(8, 0));
            top.setOpaque(false);

            JButton prev = new JButton("‹");
            JButton next = new JButton("›");
            prev.setFocusable(false);
            next.setFocusable(false);

            prev.addActionListener(e -> { ym = ym.minusMonths(1); syncHeaderFromYM(); rebuild(); });
            next.addActionListener(e -> { ym = ym.plusMonths(1); syncHeaderFromYM(); rebuild(); });

            JPanel mid = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
            mid.setOpaque(false);

            cbMonth.setFocusable(false);
            cbMonth.setFont(new Font("Segoe UI", Font.BOLD, 12));
            for (Month m : Month.values()) cbMonth.addItem(cap(m.name()));
            cbMonth.setSelectedIndex(ym.getMonthValue() - 1);

            spYear.setFont(new Font("Segoe UI", Font.BOLD, 12));

            cbMonth.addActionListener(e -> {
                int mv = cbMonth.getSelectedIndex() + 1;
                ym = YearMonth.of((int) spYear.getValue(), mv);
                rebuild();
            });
            spYear.addChangeListener(e -> {
                int y = (int) spYear.getValue();
                ym = YearMonth.of(y, ym.getMonthValue());
                rebuild();
            });

            mid.add(cbMonth);
            mid.add(spYear);

            top.add(prev, BorderLayout.WEST);
            top.add(mid, BorderLayout.CENTER);
            top.add(next, BorderLayout.EAST);

            add(top, BorderLayout.NORTH);

            JPanel days = new JPanel(new GridLayout(1, 7, 6, 0));
            days.setOpaque(false);
            String[] d = {"Sun","Mon","Tue","Wed","Thu","Fri","Sat"};
            for (String s : d) {
                JLabel lab = new JLabel(s, SwingConstants.CENTER);
                lab.setFont(new Font("Segoe UI", Font.BOLD, 11));
                lab.setForeground(new Color(0,0,0,160));
                days.add(lab);
            }

            JPanel center = new JPanel(new BorderLayout(0, 8));
            center.setOpaque(false);
            center.add(days, BorderLayout.NORTH);

            grid.setOpaque(false);
            center.add(grid, BorderLayout.CENTER);

            add(center, BorderLayout.CENTER);

            rebuild();
        }

        void setCloseCallback(Runnable r) { this.closeCallback = r; }
        void focusMonthYear() { cbMonth.requestFocusInWindow(); }

        private static String cap(String s) {
            if (s == null || s.isEmpty()) return s;
            return s.substring(0,1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
        }

        private void syncHeaderFromYM() {
            cbMonth.setSelectedIndex(ym.getMonthValue() - 1);
            spYear.setValue(ym.getYear());
        }

        private void rebuild() {
            grid.removeAll();

            LocalDate first = ym.atDay(1);
            int dow = first.getDayOfWeek().getValue(); // Mon=1..Sun=7
            int start = (dow % 7); // Sun=0

            for (int i = 0; i < start; i++) grid.add(new JLabel(""));

            int len = ym.lengthOfMonth();
            for (int day = 1; day <= len; day++) {
                LocalDate cur = ym.atDay(day);
                DayButton b = new DayButton(String.valueOf(day));

                boolean isSelected = cur.equals(selected);
                boolean isToday = cur.equals(LocalDate.now());

                if (isSelected) b.setDaySelected(true);
                if (isToday) b.setDayToday(true);

                b.addActionListener(e -> {
                    selected = cur;
                    listener.onPicked(cur);
                    if (closeCallback != null) closeCallback.run();
                });

                grid.add(b);
            }

            revalidate();
            repaint();
        }

        private static class DayButton extends JButton {
            private boolean selected;
            private boolean today;

            DayButton(String text) {
                super(text);
                setFocusable(false);
                setFont(new Font("Segoe UI", Font.PLAIN, 12));
                setMargin(new Insets(6, 6, 6, 6));
                setContentAreaFilled(false);
                setBorderPainted(false);
                setOpaque(false);
            }

            void setDaySelected(boolean v) { selected = v; repaint(); }
            void setDayToday(boolean v) { today = v; repaint(); }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();
                int arc = 14;

                if (selected) {
                    g2.setColor(new Color(35, 120, 195));
                    g2.fillRoundRect(0, 0, w, h, arc, arc);
                    setForeground(Color.WHITE);
                } else if (today) {
                    g2.setColor(new Color(0, 0, 0, 18));
                    g2.fillRoundRect(0, 0, w, h, arc, arc);
                    setForeground(new Color(20, 20, 20));
                } else {
                    setForeground(new Color(20, 20, 20));
                }

                super.paintComponent(g);
                g2.dispose();
            }
        }
    }

    private interface TimePickedListener { void onPicked(int hour12, int minute, boolean pm); }

    private static class DigitalTimePickerPanel extends JPanel {
        private static final long serialVersionUID = 1L;

        private final JComboBox<Integer> cbHour = new JComboBox<>();
        private final JComboBox<String> cbMin = new JComboBox<>();
        private final JToggleButton btnAM = new JToggleButton("AM");
        private final JToggleButton btnPM = new JToggleButton("PM");

        DigitalTimePickerPanel(int hour12, int minute, boolean pm, TimePickedListener listener) {
            setOpaque(false);
            setLayout(new GridBagLayout());
            GridBagConstraints gc = new GridBagConstraints();
            gc.insets = new Insets(6, 6, 6, 6);
            gc.fill = GridBagConstraints.HORIZONTAL;

            for (int h = 1; h <= 12; h++) cbHour.addItem(h);
            cbHour.setSelectedItem(DateTimeValue.clamp(hour12, 1, 12));
            cbHour.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            cbHour.setFocusable(false);

            for (int m = 0; m < 60; m++) cbMin.addItem(String.format("%02d", m));
            cbMin.setSelectedItem(String.format("%02d", DateTimeValue.clamp(minute, 0, 59)));
            cbMin.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            cbMin.setFocusable(false);

            ButtonGroup g = new ButtonGroup();
            g.add(btnAM);
            g.add(btnPM);
            btnAM.setFocusable(false);
            btnPM.setFocusable(false);
            btnAM.setFont(new Font("Segoe UI", Font.BOLD, 12));
            btnPM.setFont(new Font("Segoe UI", Font.BOLD, 12));
            if (pm) btnPM.setSelected(true); else btnAM.setSelected(true);

            gc.gridx = 0; gc.gridy = 0;
            add(cbHour, gc);
            gc.gridx = 1;
            add(cbMin, gc);

            JPanel ampm = new JPanel(new GridLayout(1, 2, 6, 0));
            ampm.setOpaque(false);
            ampm.add(btnAM);
            ampm.add(btnPM);
            gc.gridx = 2;
            add(ampm, gc);

            cbHour.addActionListener(e -> fire(listener));
            cbMin.addActionListener(e -> fire(listener));
            btnAM.addActionListener(e -> fire(listener));
            btnPM.addActionListener(e -> fire(listener));
        }

        private void fire(TimePickedListener listener) {
            int h = (Integer) cbHour.getSelectedItem();
            int m = Integer.parseInt((String) cbMin.getSelectedItem());
            boolean pm = btnPM.isSelected();
            listener.onPicked(h, m, pm);
        }
    }

    // ===================== MODERN DIALOG (USED BY ADD + EDIT) =====================
    private class LogEditDialog extends JDialog {
        private static final long serialVersionUID = 1L;

        private final String editingLogId; // null = add
        boolean saved = false;

        private final JComboBox<EmployeeItem> cbEmployee = new JComboBox<>();

        private final DateTimeValue inVal = new DateTimeValue(LocalDate.now(), 9, 0, false);
        private final DateTimeValue lunchOutVal = new DateTimeValue(LocalDate.now(), 12, 0, false);
        private final DateTimeValue lunchInVal = new DateTimeValue(LocalDate.now(), 1, 0, true);
        private final DateTimeValue outVal = new DateTimeValue(LocalDate.now(), 6, 0, true);

        private final DatePickerField inDate = new DatePickerField(inVal, this::syncOptionalDatesToBaseLogDate);
        private final TimePickerField inTime = new TimePickerField(inVal) {
            private static final long serialVersionUID = 1L;
            @Override
            void setValue(DateTimeValue v) {
                super.setValue(v);
                syncOptionalDatesToBaseLogDate();
            }
        };

        private final DatePickerField lunchOutDate = new DatePickerField(lunchOutVal);
        private final TimePickerField lunchOutTime = new TimePickerField(lunchOutVal);

        private final DatePickerField lunchInDate = new DatePickerField(lunchInVal);
        private final TimePickerField lunchInTime = new TimePickerField(lunchInVal);

        private final DatePickerField outDate = new DatePickerField(outVal);
        private final TimePickerField outTime = new TimePickerField(outVal);

        private final JCheckBox chkHasLunchOut = new JCheckBox("Lunch Out");
        private final JCheckBox chkHasLunchIn = new JCheckBox("Lunch In");

        // ✅ CHANGED TEXT
        private final JCheckBox chkHasOut = new JCheckBox("Time Out");

        LogEditDialog(Window owner, String logId) {
            super(owner, (logId == null ? "Add Log" : "Edit Log"), ModalityType.APPLICATION_MODAL);
            this.editingLogId = logId;

            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            setResizable(false);

            buildUI();
            loadEmployees();

            if (editingLogId != null) loadExisting(editingLogId);
            else {
                chkHasLunchOut.setSelected(false);
                chkHasLunchIn.setSelected(false);
                chkHasOut.setSelected(false);
                toggleLunchOutEnabled();
                toggleLunchInEnabled();
                toggleOutEnabled();
            }

            pack();
            setLocationRelativeTo(owner);
        }

        private void buildUI() {
            JPanel root = new JPanel(new BorderLayout());
            root.setBackground(new Color(248, 246, 242));
            root.setBorder(new EmptyBorder(16, 16, 16, 16));

            RoundedCard card = new RoundedCard();
            card.setLayout(new BorderLayout(12, 14));
            card.setBorder(new EmptyBorder(18, 18, 16, 18));

            JLabel title = new JLabel(editingLogId == null ? "Add attendance log" : "Edit attendance log");
            title.setFont(new Font("Segoe UI", Font.BOLD, 18));
            title.setForeground(new Color(25, 25, 25));

            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            header.add(title, BorderLayout.WEST);

            JPanel form = new JPanel(new GridBagLayout());
            form.setOpaque(false);
            GridBagConstraints gc = new GridBagConstraints();
            gc.insets = new Insets(10, 0, 10, 0);
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.weightx = 1;

            // Employee
            gc.gridx = 0; gc.gridy = 0;
            form.add(sectionLabel("Employee"), gc);

            gc.gridy = 1;
            cbEmployee.setPreferredSize(new Dimension(460, 36));
            styleInput(cbEmployee);
            form.add(cbEmployee, gc);

            // Time In
            gc.gridy = 2;
            form.add(sectionLabel("Time In"), gc);

            gc.gridy = 3;
            form.add(dateTimeRow(inDate, inTime), gc);

            // Lunch Out + checkbox
            gc.gridy = 4;
            JPanel lunchOutTop = new JPanel(new BorderLayout());
            lunchOutTop.setOpaque(false);
            lunchOutTop.add(sectionLabel("Lunch Out"), BorderLayout.WEST);
            chkHasLunchOut.setOpaque(false);
            chkHasLunchOut.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            chkHasLunchOut.setForeground(new Color(0, 0, 0, 170));
            lunchOutTop.add(chkHasLunchOut, BorderLayout.EAST);
            form.add(lunchOutTop, gc);

            gc.gridy = 5;
            form.add(dateTimeRow(lunchOutDate, lunchOutTime), gc);

            // Lunch In + checkbox
            gc.gridy = 6;
            JPanel lunchInTop = new JPanel(new BorderLayout());
            lunchInTop.setOpaque(false);
            lunchInTop.add(sectionLabel("Lunch In"), BorderLayout.WEST);
            chkHasLunchIn.setOpaque(false);
            chkHasLunchIn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            chkHasLunchIn.setForeground(new Color(0, 0, 0, 170));
            lunchInTop.add(chkHasLunchIn, BorderLayout.EAST);
            form.add(lunchInTop, gc);

            gc.gridy = 7;
            form.add(dateTimeRow(lunchInDate, lunchInTime), gc);

            // Time Out + checkbox
            gc.gridy = 8;
            JPanel outTop = new JPanel(new BorderLayout());
            outTop.setOpaque(false);

            outTop.add(sectionLabel("Time Out"), BorderLayout.WEST);

            chkHasOut.setOpaque(false);
            chkHasOut.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            chkHasOut.setForeground(new Color(0, 0, 0, 170));
            outTop.add(chkHasOut, BorderLayout.EAST);

            form.add(outTop, gc);

            gc.gridy = 9;
            form.add(dateTimeRow(outDate, outTime), gc);

            chkHasLunchOut.addActionListener(e -> {
                if (!chkHasLunchOut.isSelected()) {
                    lunchOutVal.date = inVal.date;
                    chkHasLunchIn.setSelected(false);
                    toggleLunchInEnabled();
                }
                syncOptionalDatesToBaseLogDate();
                toggleLunchOutEnabled();
            });
            chkHasLunchIn.addActionListener(e -> {
                if (chkHasLunchIn.isSelected()) chkHasLunchOut.setSelected(true);
                else lunchInVal.date = inVal.date;
                syncOptionalDatesToBaseLogDate();
                toggleLunchOutEnabled();
                toggleLunchInEnabled();
            });
            chkHasOut.addActionListener(e -> {
                if (!chkHasOut.isSelected()) outVal.date = inVal.date;
                syncOptionalDatesToBaseLogDate();
                toggleOutEnabled();
            });

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            actions.setOpaque(false);

            PillButton btnCancel = new PillButton("Cancel", false);
            PillButton btnSave = new PillButton("Save", false);

            btnCancel.setBackground(new Color(245, 245, 245));
            btnCancel.setForeground(new Color(40, 40, 40));
            btnSave.setBackground(new Color(24, 130, 90));
            btnSave.setForeground(Color.WHITE);

            btnCancel.addActionListener(e -> dispose());
            btnSave.addActionListener(e -> onSave());

            actions.add(btnCancel);
            actions.add(btnSave);

            // Apply modern input styling for date/time text + icon buttons
            styleDateTimeField(inDate);
            styleDateTimeField(inTime);
            styleDateTimeField(lunchOutDate);
            styleDateTimeField(lunchOutTime);
            styleDateTimeField(lunchInDate);
            styleDateTimeField(lunchInTime);
            styleDateTimeField(outDate);
            styleDateTimeField(outTime);

            card.add(header, BorderLayout.NORTH);
            card.add(form, BorderLayout.CENTER);
            card.add(actions, BorderLayout.SOUTH);

            root.add(card, BorderLayout.CENTER);
            setContentPane(root);
        }

        private JLabel sectionLabel(String text) {
            JLabel l = new JLabel(text);
            l.setFont(new Font("Segoe UI", Font.BOLD, 12));
            l.setForeground(new Color(0, 0, 0, 170));
            return l;
        }

        private JPanel dateTimeRow(JComponent date, JComponent time) {
            JPanel p = new JPanel(new GridBagLayout());
            p.setOpaque(false);

            GridBagConstraints c = new GridBagConstraints();
            c.fill = GridBagConstraints.HORIZONTAL;

            c.gridx = 0; c.weightx = 0.55;
            c.insets = new Insets(0, 0, 0, 10);
            p.add(date, c);

            c.gridx = 1; c.weightx = 0.45;
            c.insets = new Insets(0, 0, 0, 0);
            p.add(time, c);

            return p;
        }

        private void styleInput(JComponent comp) {
            comp.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            comp.setOpaque(true);
            comp.setBackground(Color.WHITE);
            comp.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(220, 210, 195)),
                    new EmptyBorder(8, 10, 8, 10)
            ));
        }

        private void styleDateTimeField(JComponent fieldPanel) {
            fieldPanel.setBorder(BorderFactory.createEmptyBorder());
            for (Component c : fieldPanel.getComponents()) {
                if (c instanceof JTextField) {
                    JTextField tf = (JTextField) c;
                    tf.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                    tf.setBackground(Color.WHITE);
                    tf.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(220, 210, 195)),
                            new EmptyBorder(8, 10, 8, 10)
                    ));
                } else if (c instanceof JButton) {
                    JButton b = (JButton) c;
                    b.setOpaque(true);
                    b.setBackground(Color.WHITE);
                    b.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(new Color(220, 210, 195)),
                            new EmptyBorder(8, 10, 8, 10)
                    ));
                }
            }
        }

        private void toggleLunchOutEnabled() {
            boolean en = chkHasLunchOut.isSelected();
            lunchOutDate.setEnabled(en);
            lunchOutTime.setEnabled(en);
            setDateTimeRowDim(lunchOutDate, en);
            setDateTimeRowDim(lunchOutTime, en);
        }

        private void toggleLunchInEnabled() {
            boolean en = chkHasLunchOut.isSelected() && chkHasLunchIn.isSelected();
            lunchInDate.setEnabled(en);
            lunchInTime.setEnabled(en);
            setDateTimeRowDim(lunchInDate, en);
            setDateTimeRowDim(lunchInTime, en);
        }

        // ✅ UPDATED: disables and visually dims the entire Time Out row
        private void toggleOutEnabled() {
            boolean en = chkHasOut.isSelected();
            outDate.setEnabled(en);
            outTime.setEnabled(en);

            // Dim look when disabled (textfield + icon button)
            setDateTimeRowDim(outDate, en);
            setDateTimeRowDim(outTime, en);
        }

        private void setDateTimeRowDim(JComponent fieldPanel, boolean enabled) {
            for (Component c : fieldPanel.getComponents()) {
                c.setEnabled(enabled);
                if (c instanceof JTextField) {
                    JTextField tf = (JTextField) c;
                    tf.setBackground(enabled ? Color.WHITE : new Color(245, 245, 245));
                    tf.setForeground(enabled ? new Color(30, 30, 30) : new Color(120, 120, 120));
                } else if (c instanceof JButton) {
                    JButton b = (JButton) c;
                    b.setBackground(enabled ? Color.WHITE : new Color(245, 245, 245));
                    b.setForeground(enabled ? new Color(30, 30, 30) : new Color(120, 120, 120));
                }
            }
        }

        private void loadEmployees() {
            cbEmployee.removeAllItems();
            try {
                java.util.List<Employee> list = EmployeeDAO.listActive();
                for (Employee e : list) cbEmployee.addItem(new EmployeeItem(e));
            } catch (Exception ignored) {}
        }

        private void loadExisting(String logId) {
            try (Connection con = DB.getConnection()) {
                String tlLogId = pickColumn(con, "time_logs", "id", "log_id", "time_log_id");
                String tlEmpFk = pickColumn(con, "time_logs", "emp_id", "employee_id", "empno", "employee_no");
                String tlTimeIn = pickColumn(con, "time_logs", "time_in", "timein", "clock_in");
                String tlLunchOut = pickColumnOptional(con, "time_logs", "lunch_out", "lunchout");
                String tlLunchIn = pickColumnOptional(con, "time_logs", "lunch_in", "lunchin");
                String tlTimeOut = pickColumn(con, "time_logs", "time_out", "timeout", "clock_out");

                String sql = "SELECT " + tlEmpFk + " AS emp_fk, " + tlTimeIn + " AS time_in, " +
                        (tlLunchOut != null ? tlLunchOut + " AS lunch_out, " : "NULL AS lunch_out, ") +
                        (tlLunchIn != null ? tlLunchIn + " AS lunch_in, " : "NULL AS lunch_in, ") +
                        tlTimeOut + " AS time_out FROM time_logs WHERE " + tlLogId + "=? LIMIT 1";

                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, logId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) return;

                        int empId = rs.getInt("emp_fk");
                        Timestamp tin = rs.getTimestamp("time_in");
                        Timestamp tlOut = rs.getTimestamp("lunch_out");
                        Timestamp tlIn = rs.getTimestamp("lunch_in");
                        Timestamp tout = rs.getTimestamp("time_out");

                        if (tin != null) {
                            LocalDateTime ldt = tin.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                            inVal.fromLocalDateTime(ldt);
                        }
                        if (tlOut != null) {
                            LocalDateTime ldt = tlOut.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                            lunchOutVal.fromLocalDateTime(ldt);
                            chkHasLunchOut.setSelected(true);
                        } else {
                            chkHasLunchOut.setSelected(false);
                        }
                        if (tlIn != null) {
                            LocalDateTime ldt = tlIn.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                            lunchInVal.fromLocalDateTime(ldt);
                            chkHasLunchIn.setSelected(true);
                        } else {
                            chkHasLunchIn.setSelected(false);
                        }
                        if (tout != null) {
                            LocalDateTime ldt = tout.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                            outVal.fromLocalDateTime(ldt);
                            chkHasOut.setSelected(true);
                        } else {
                            chkHasOut.setSelected(false);
                        }

                        applyBaseLogDateToMissingOptionalFields(inVal.date);

                        inDate.setValue(inVal);
                        inTime.setValue(inVal);
                        lunchOutDate.setValue(lunchOutVal);
                        lunchOutTime.setValue(lunchOutVal);
                        lunchInDate.setValue(lunchInVal);
                        lunchInTime.setValue(lunchInVal);
                        outDate.setValue(outVal);
                        outTime.setValue(outVal);

                        toggleLunchOutEnabled();
                        toggleLunchInEnabled();
                        toggleOutEnabled();

                        for (int i = 0; i < cbEmployee.getItemCount(); i++) {
                            EmployeeItem it = cbEmployee.getItemAt(i);
                            if (it != null && it.empId == empId) {
                                cbEmployee.setSelectedIndex(i);
                                break;
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Failed to load log record:\n" + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }


        private void syncOptionalDatesToBaseLogDate() {
            LocalDate base = inVal.date;
            if (base == null) return;

            if (!chkHasLunchOut.isSelected()) {
                lunchOutVal.date = base;
                lunchOutDate.setValue(lunchOutVal);
                lunchOutTime.setValue(lunchOutVal);
            }
            if (!chkHasLunchIn.isSelected()) {
                lunchInVal.date = base;
                lunchInDate.setValue(lunchInVal);
                lunchInTime.setValue(lunchInVal);
            }
            if (!chkHasOut.isSelected()) {
                outVal.date = base;
                outDate.setValue(outVal);
                outTime.setValue(outVal);
            }
        }

        private void applyBaseLogDateToMissingOptionalFields(LocalDate base) {
            if (base == null) return;

            if (!chkHasLunchOut.isSelected()) {
                lunchOutVal.date = base;
            }
            if (!chkHasLunchIn.isSelected()) {
                lunchInVal.date = base;
            }
            if (!chkHasOut.isSelected()) {
                outVal.date = base;
            }

            lunchOutDate.setValue(lunchOutVal);
            lunchOutTime.setValue(lunchOutVal);
            lunchInDate.setValue(lunchInVal);
            lunchInTime.setValue(lunchInVal);
            outDate.setValue(outVal);
            outTime.setValue(outVal);
        }

        private void onSave() {
            EmployeeItem empIt = (EmployeeItem) cbEmployee.getSelectedItem();
            if (empIt == null) {
                JOptionPane.showMessageDialog(this, "No employee selected.",
                        "Missing employee", JOptionPane.WARNING_MESSAGE);
                return;
            }

            LocalDateTime ldtIn = inVal.toLocalDateTime();
            LocalDateTime ldtLunchOut = chkHasLunchOut.isSelected() ? lunchOutVal.toLocalDateTime() : null;
            LocalDateTime ldtLunchIn = (chkHasLunchOut.isSelected() && chkHasLunchIn.isSelected()) ? lunchInVal.toLocalDateTime() : null;
            LocalDateTime ldtOut = chkHasOut.isSelected() ? outVal.toLocalDateTime() : null;

            if (ldtLunchOut != null && ldtLunchOut.isBefore(ldtIn)) {
                JOptionPane.showMessageDialog(this,
                        "Lunch Out cannot be earlier than Time In.",
                        "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (ldtLunchIn != null && ldtLunchOut == null) {
                JOptionPane.showMessageDialog(this,
                        "Lunch In cannot exist without Lunch Out.",
                        "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (ldtLunchIn != null && ldtLunchIn.isBefore(ldtLunchOut)) {
                JOptionPane.showMessageDialog(this,
                        "Lunch In cannot be earlier than Lunch Out.",
                        "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (ldtOut != null && ldtOut.isBefore(ldtIn)) {
                JOptionPane.showMessageDialog(this,
                        "Time Out cannot be earlier than Time In.",
                        "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (ldtOut != null && ldtLunchOut != null && ldtOut.isBefore(ldtLunchOut)) {
                JOptionPane.showMessageDialog(this,
                        "Time Out cannot be earlier than Lunch Out.",
                        "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (ldtOut != null && ldtLunchIn != null && ldtOut.isBefore(ldtLunchIn)) {
                JOptionPane.showMessageDialog(this,
                        "Time Out cannot be earlier than Lunch In.",
                        "Validation", JOptionPane.WARNING_MESSAGE);
                return;
            }

            Timestamp tsIn = Timestamp.valueOf(ldtIn);
            Timestamp tsLunchOut = (ldtLunchOut == null) ? null : Timestamp.valueOf(ldtLunchOut);
            Timestamp tsLunchIn = (ldtLunchIn == null) ? null : Timestamp.valueOf(ldtLunchIn);
            Timestamp tsOut = (ldtOut == null) ? null : Timestamp.valueOf(ldtOut);

            try (Connection con = DB.getConnection()) {
                String tlLogId = pickColumn(con, "time_logs", "id", "log_id", "time_log_id");
                String tlEmpFk = pickColumn(con, "time_logs", "emp_id", "employee_id", "empno", "employee_no");
                String tlTimeIn = pickColumn(con, "time_logs", "time_in", "timein", "clock_in");
                String tlLunchOut = pickColumnOptional(con, "time_logs", "lunch_out", "lunchout");
                String tlLunchIn = pickColumnOptional(con, "time_logs", "lunch_in", "lunchin");
                String tlTimeOut = pickColumn(con, "time_logs", "time_out", "timeout", "clock_out");

                if ((chkHasLunchOut.isSelected() || chkHasLunchIn.isSelected()) && (tlLunchOut == null || tlLunchIn == null)) {
                    JOptionPane.showMessageDialog(this,
                            "Your database is missing lunch columns. Add lunch_out and lunch_in first.",
                            "Missing lunch columns", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                if (editingLogId == null) {
                    String sql = "INSERT INTO time_logs (" + tlEmpFk + ", " + tlTimeIn + ", " +
                            (tlLunchOut != null ? tlLunchOut + ", " : "") +
                            (tlLunchIn != null ? tlLunchIn + ", " : "") +
                            tlTimeOut + ") VALUES (?,?," +
                            (tlLunchOut != null ? "?," : "") +
                            (tlLunchIn != null ? "?," : "") +
                            "?)";
                    try (PreparedStatement ps = con.prepareStatement(sql)) {
                        int ix = 1;
                        ps.setInt(ix++, empIt.empId);
                        ps.setTimestamp(ix++, tsIn);
                        if (tlLunchOut != null) {
                            if (tsLunchOut != null) ps.setTimestamp(ix++, tsLunchOut); else ps.setNull(ix++, Types.TIMESTAMP);
                        }
                        if (tlLunchIn != null) {
                            if (tsLunchIn != null) ps.setTimestamp(ix++, tsLunchIn); else ps.setNull(ix++, Types.TIMESTAMP);
                        }
                        if (tsOut != null) ps.setTimestamp(ix++, tsOut);
                        else ps.setNull(ix++, Types.TIMESTAMP);
                        ps.executeUpdate();
                    }
                } else {
                    String sql = "UPDATE time_logs SET " + tlEmpFk + "=?, " + tlTimeIn + "=?, " +
                            (tlLunchOut != null ? tlLunchOut + "=?, " : "") +
                            (tlLunchIn != null ? tlLunchIn + "=?, " : "") +
                            tlTimeOut + "=? WHERE " + tlLogId + "=?";
                    try (PreparedStatement ps = con.prepareStatement(sql)) {
                        int ix = 1;
                        ps.setInt(ix++, empIt.empId);
                        ps.setTimestamp(ix++, tsIn);
                        if (tlLunchOut != null) {
                            if (tsLunchOut != null) ps.setTimestamp(ix++, tsLunchOut); else ps.setNull(ix++, Types.TIMESTAMP);
                        }
                        if (tlLunchIn != null) {
                            if (tsLunchIn != null) ps.setTimestamp(ix++, tsLunchIn); else ps.setNull(ix++, Types.TIMESTAMP);
                        }
                        if (tsOut != null) ps.setTimestamp(ix++, tsOut);
                        else ps.setNull(ix++, Types.TIMESTAMP);
                        ps.setString(ix, editingLogId);
                        ps.executeUpdate();
                    }
                }

                saved = true;
                dispose();

            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        "Save failed:\n" + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static class RoundedCard extends JPanel {
        private static final long serialVersionUID = 1L;

        RoundedCard() {
            setOpaque(false);
            setBackground(Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = 18;

            g2.setColor(new Color(0, 0, 0, 18));
            g2.fillRoundRect(2, 4, w - 4, h - 4, arc, arc);

            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, w - 4, h - 4, arc, arc);

            g2.setColor(new Color(225, 215, 200));
            g2.drawRoundRect(0, 0, w - 4, h - 4, arc, arc);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class EmployeeItem {
        final int empId;
        final String label;

        EmployeeItem(Employee e) {
            this.empId = (e == null) ? 0 : e.empId;
            String name = (e == null) ? null : e.fullName();
            if (name == null || name.trim().isEmpty()) name = "Emp #" + empId;
            this.label = name.trim() + " (" + empId + ")";
        }

        @Override public String toString() { return label; }
    }

    // ===================== COLUMN PICK HELPERS =====================
    private static String pickColumn(Connection con, String table, String... candidates) throws Exception {
        String c = pickColumnOptional(con, table, candidates);
        if (c == null) throw new SQLException("Cannot find required column in " + table + ": " + Arrays.toString(candidates));
        return c;
    }

    private static String pickColumnOptional(Connection con, String table, String... candidates) throws Exception {
        Set<String> cols = getColumnsLower(con, table);
        for (String cand : candidates) {
            if (cols.contains(cand.toLowerCase(Locale.ROOT))) return cand;
        }
        return null;
    }

    private static Set<String> getColumnsLower(Connection con, String table) throws Exception {
        Set<String> cols = new HashSet<>();
        DatabaseMetaData md = con.getMetaData();

        try (ResultSet rs = md.getColumns(con.getCatalog(), null, table, null)) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                if (name != null) cols.add(name.toLowerCase(Locale.ROOT));
            }
        }
        if (cols.isEmpty()) {
            try (ResultSet rs = md.getColumns(con.getCatalog(), null, table.toUpperCase(Locale.ROOT), null)) {
                while (rs.next()) {
                    String name = rs.getString("COLUMN_NAME");
                    if (name != null) cols.add(name.toLowerCase(Locale.ROOT));
                }
            }
        }
        if (cols.isEmpty()) {
            try (ResultSet rs = md.getColumns(con.getCatalog(), null, table.toLowerCase(Locale.ROOT), null)) {
                while (rs.next()) {
                    String name = rs.getString("COLUMN_NAME");
                    if (name != null) cols.add(name.toLowerCase(Locale.ROOT));
                }
            }
        }
        return cols;
    }

    // ===================== PILL UI =====================
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

    private static class PillButton extends RoundedButton {
        private static final long serialVersionUID = 1L;

        PillButton(String text, boolean danger) {
            super(
                    text,
                    danger ? new Color(214, 92, 92) : new Color(245, 245, 245),
                    danger ? Color.WHITE : new Color(35, 35, 35)
            );
            setFocusable(false);
            setFont(new Font("Segoe UI", Font.BOLD, 12));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMargin(new Insets(7, 16, 7, 16));
            setCornerRadius(18);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.max(d.width, 54), 38);
        }
    }
}