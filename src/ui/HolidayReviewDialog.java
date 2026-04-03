package ui;

import com.formdev.flatlaf.FlatClientProperties;
import dao.HolidayDAO;
import model.Holiday;
import util.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Holiday review/editor for a selected pay period.
 *
 * Notes:
 * - "Apply" is stored in DB column: holidays.enabled (TINYINT(1))
 * - Apply is ONLY toggled in the table (click the checkbox)
 */
public class HolidayReviewDialog extends JDialog {

    private final HolidayDAO holidayDAO = new HolidayDAO();
    private final LocalDate periodStart;
    private final LocalDate periodEnd;

    private JTable table;
    private DefaultTableModel model;

    private final JTextField tfDate = new JTextField();
    private final JTextField tfName = new JTextField();
    private final JComboBox<String> cbType = new JComboBox<>(new String[]{
            "REGULAR",
            "SPECIAL_NON_WORKING",
            "SPECIAL_WORKING"
    });
    private final JComboBox<String> cbScope = new JComboBox<>(new String[]{
            "NATIONAL",
            "LOCAL"
    });

    // Stored in DB column "rate_multiplier" but used here as PHP/day (8 hours). 0 = no override.
    private final JTextField tfRateDay = new JTextField();
    private final JTextField tfNotes = new JTextField();

    // Action buttons (need to be fields so we can disable for MONTHLY employees)
    private JButton btnDelete;
    private JButton btnSave;
    private JButton btnClose;

    // If true, holiday editing is disabled (used for MONTHLY employees)
    private boolean holidaysDisabled = false;

    private final Map<LocalDate, Holiday> holidayByDate = new HashMap<>();

    private static final Color HEADER_BG = new Color(0xFFF7ED);
    private static final Color ZEBRA = new Color(0xFCFBF8);
    private static final Color GRID = new Color(0xEFE5D6);

    private static final DecimalFormat MONEY_FMT = new DecimalFormat(
            "#,##0.##",
            DecimalFormatSymbols.getInstance(Locale.ENGLISH)
    );

    public HolidayReviewDialog(Window owner, LocalDate start, LocalDate end) {
        this(owner, start, end, false);
    }

    public HolidayReviewDialog(Window owner, LocalDate start, LocalDate end, boolean holidaysDisabled) {

        super(owner, "Review / Add Holiday", ModalityType.APPLICATION_MODAL);
        this.periodStart = start;
        this.periodEnd = end;
        this.holidaysDisabled = holidaysDisabled;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        setSize(940, 650);
        setMinimumSize(new Dimension(920, 620));
        setLocationRelativeTo(owner);

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(new EmptyBorder(16, 16, 16, 16));
        root.setBackground(Theme.BG);
        root.putClientProperty(FlatClientProperties.STYLE,
                "arc:18; background:" + toHex(Theme.BG) + ";");
        setContentPane(root);

        root.add(buildTopBar(), BorderLayout.NORTH);

        JPanel tableCard = (JPanel) buildTableCard();
        JPanel formCard = (JPanel) buildFormCardCompact();

        // Dates/table is scrollable, so make it smaller
        tableCard.setMinimumSize(new Dimension(0, 170));

        // Details must have MORE room so all fields fit (no scroll)
        formCard.setMinimumSize(new Dimension(0, 380));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableCard, formCard);
        split.setOpaque(false);
        split.setBorder(null);
        split.setContinuousLayout(true);
        split.setDividerSize(10);
        split.putClientProperty(FlatClientProperties.STYLE, "dividerSize:10;");

        // Favor DETAILS space (bottom)
        split.setResizeWeight(0.28);

        root.add(split, BorderLayout.CENTER);

        loadTable();

        if (this.holidaysDisabled) {
            disableHolidayUI();
        }

        // Move divider up to give more space to details
        SwingUtilities.invokeLater(() -> split.setDividerLocation(235));
    }

    private JComponent buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setOpaque(false);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Holidays in selected pay period");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15.5f));
        title.setForeground(Theme.PRIMARY);

        JLabel hint = new JLabel("Add or correct holidays here (for sudden proclamations). Saves instantly.");
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11.5f));
        hint.setForeground(Theme.MUTED);

        left.add(title);
        left.add(Box.createVerticalStrut(2));
        left.add(hint);

        JLabel chip = new JLabel("  " + periodStart + "  —  " + periodEnd + "  ");
        chip.setFont(chip.getFont().deriveFont(Font.PLAIN, 11.5f));
        chip.setForeground(Theme.MUTED);
        chip.setOpaque(true);
        chip.setBackground(Color.WHITE);
        chip.setBorder(new LineBorder(Theme.BORDER, 1, true));

        bar.add(left, BorderLayout.WEST);
        bar.add(chip, BorderLayout.EAST);
        return bar;
    }

    private JComponent buildTableCard() {
        JPanel card = Theme.cardPanel();
        card.setLayout(new BorderLayout(10, 10));
        card.putClientProperty(FlatClientProperties.STYLE, "arc:18;");

        JLabel lbl = new JLabel("Review list (click a row to edit)");
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 12.5f));
        lbl.setForeground(Theme.TEXT);

        model = new DefaultTableModel(
                new Object[]{"Date", "Day", "Holiday Name", "Type", "Scope", "Apply", "Notes", "_ID"},
                0
        ) {
            @Override
            public boolean isCellEditable(int r, int c) {
                // Only Apply is editable, and only if there is a holiday record on that date
                if (holidaysDisabled) return false;
                if (c != 5) return false;
                int id = toInt(getValueAt(r, 7));
                return id > 0;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 5) return Boolean.class;
                return Object.class;
            }

            @Override
            public void setValueAt(Object aValue, int r, int c) {
                super.setValueAt(aValue, r, c);
                if (holidaysDisabled) return;
                if (c != 5) return;

                int id = toInt(getValueAt(r, 7));
                if (id <= 0) return;

                boolean enabled = Boolean.TRUE.equals(aValue);

                // Persist immediately
                try {
                    holidayDAO.setEnabled(id, enabled);
                } catch (Exception ex) {
                    // revert on failure
                    try { super.setValueAt(!enabled, r, c); } catch (Exception ignore) {}
                    return;
                }

                // Keep in-memory map in sync
                try {
                    LocalDate d = LocalDate.parse(String.valueOf(getValueAt(r, 0)));
                    Holiday h = holidayByDate.get(d);
                    if (h != null) h.enabled = enabled;
                } catch (Exception ignore) {}
            }
        };

        table = new JTable(model);

        // Hide internal _ID column
        try { table.removeColumn(table.getColumnModel().getColumn(7)); } catch (Exception ignore) {}

        table.setRowHeight(30);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setGridColor(GRID);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);
        table.getSelectionModel().addListSelectionListener(this::onRowSelected);

        JTableHeader th = table.getTableHeader();
        th.setPreferredSize(new Dimension(th.getPreferredSize().width, 34));
        th.setFont(th.getFont().deriveFont(Font.BOLD, 12f));
        th.setBackground(HEADER_BG);
        th.setForeground(Theme.TEXT);
        th.setOpaque(true);

        table.getColumnModel().getColumn(0).setPreferredWidth(110);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setPreferredWidth(260);
        table.getColumnModel().getColumn(3).setPreferredWidth(160);
        table.getColumnModel().getColumn(4).setPreferredWidth(120);
        table.getColumnModel().getColumn(5).setPreferredWidth(70);
        table.getColumnModel().getColumn(6).setPreferredWidth(220);

        table.setDefaultRenderer(Object.class, new ZebraRenderer());

        DefaultTableCellRenderer center = new ZebraRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        table.getColumnModel().getColumn(0).setCellRenderer(center);
        table.getColumnModel().getColumn(1).setCellRenderer(center);

        // Apply checkbox (centered) + zebra-safe renderer
        table.getColumnModel().getColumn(5).setCellRenderer(new CheckboxZebraRenderer());
        JCheckBox cb = new JCheckBox();
        cb.setHorizontalAlignment(SwingConstants.CENTER);
        cb.setOpaque(true);
        table.getColumnModel().getColumn(5).setCellEditor(new DefaultCellEditor(cb));

        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(new LineBorder(Theme.BORDER, 1, true));
        sp.getViewport().setBackground(Color.WHITE);

        card.add(lbl, BorderLayout.NORTH);
        card.add(sp, BorderLayout.CENTER);
        return card;
    }

    /**
     * Compact details (no scroll). 2 fields per row.
     */
    private JComponent buildFormCardCompact() {
        JPanel card = Theme.cardPanel();
        card.setLayout(new BorderLayout(12, 10));
        card.putClientProperty(FlatClientProperties.STYLE, "arc:18;");

        // breathing room inside details
        card.setBorder(new EmptyBorder(14, 18, 16, 18));

        JLabel lbl = new JLabel("Details");
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 12.5f));
        lbl.setForeground(Theme.TEXT);

        JLabel sub = new JLabel("Edit fields then click Save / Update");
        sub.setFont(sub.getFont().deriveFont(Font.PLAIN, 11.5f));
        sub.setForeground(Theme.MUTED);

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.add(lbl);
        header.add(Box.createVerticalStrut(2));
        header.add(sub);

        Theme.styleInput(tfDate);
        Theme.styleInput(tfName);
        Theme.styleInput(tfRateDay);
        Theme.styleInput(tfNotes);
        Theme.styleCombo(cbType);
        Theme.styleCombo(cbScope);

        tfDate.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "YYYY-MM-DD");
        tfName.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "e.g., New Year's Day");
        tfRateDay.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Optional override");
        tfNotes.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Notes (optional)");

        tfRateDay.setHorizontalAlignment(SwingConstants.RIGHT);

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 8, 6, 8);
        g.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        addRow4(grid, g, row++, "Date", tfDate, "Type", cbType);
        addRow4(grid, g, row++, "Name", tfName, "Scope", cbScope);
        addRow4(grid, g, row++, "Holiday Rate (PHP/day)", tfRateDay, "Notes", tfNotes);

        btnDelete = new RoundedButton("Delete", new Color(214, 92, 92), Color.WHITE);
        btnSave = new RoundedButton("Save / Update", new Color(24, 130, 90), Color.WHITE);
        btnClose = new RoundedButton("Close", new Color(245, 245, 245), new Color(35, 35, 35));

        Theme.styleNeutralButton(btnDelete);
        Theme.stylePrimaryButton(btnSave);
        Theme.styleNeutralButton(btnClose);
        keepOriginalIosButtonColors(btnDelete, "danger");
        keepOriginalIosButtonColors(btnSave, "primary");
        keepOriginalIosButtonColors(btnClose, "neutral");

        btnDelete.addActionListener(e -> deleteHoliday());
        btnSave.addActionListener(e -> saveHoliday());
        btnClose.addActionListener(e -> dispose());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        actions.setBorder(new EmptyBorder(10, 0, 0, 0));
        actions.add(btnDelete);
        actions.add(btnSave);
        actions.add(btnClose);

        card.add(header, BorderLayout.NORTH);
        card.add(grid, BorderLayout.CENTER);
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }


    private void keepOriginalIosButtonColors(JButton b, String role) {
        if (!(b instanceof RoundedButton rb)) return;

        if ("primary".equals(role)) {
            rb.setBackground(new Color(24, 130, 90));
            rb.setForeground(Color.WHITE);
        } else if ("neutral".equals(role)) {
            rb.setBackground(new Color(245, 245, 245));
            rb.setForeground(new Color(35, 35, 35));
        } else if ("danger".equals(role)) {
            rb.setBackground(new Color(214, 92, 92));
            rb.setForeground(Color.WHITE);
        }

        rb.setCornerRadius(18);
        rb.setOpaque(false);
        rb.setContentAreaFilled(false);
        rb.setBorderPainted(false);
        rb.setFocusPainted(false);
    }

    private void addRow4(JPanel p, GridBagConstraints g, int row,
                         String label1, JComponent field1,
                         String label2, JComponent field2) {

        JLabel l1 = new JLabel(label1);
        l1.setForeground(Theme.MUTED);
        l1.setFont(l1.getFont().deriveFont(Font.PLAIN, 12f));

        JLabel l2 = new JLabel(label2);
        l2.setForeground(Theme.MUTED);
        l2.setFont(l2.getFont().deriveFont(Font.PLAIN, 12f));

        g.gridx = 0; g.gridy = row; g.weightx = 0; g.gridwidth = 1;
        p.add(l1, g);

        g.gridx = 1; g.weightx = 0.55;
        p.add(field1, g);

        g.gridx = 2; g.weightx = 0;
        p.add(l2, g);

        g.gridx = 3; g.weightx = 0.45;
        p.add(field2, g);
    }

    private void loadTable() {
        model.setRowCount(0);
        holidayByDate.clear();

        try {
            List<Holiday> holidays = holidayDAO.listBetween(periodStart, periodEnd);
            for (Holiday h : holidays) holidayByDate.put(h.holDate, h);

            LocalDate d = periodStart;
            while (!d.isAfter(periodEnd)) {
                Holiday h = holidayByDate.get(d);

                model.addRow(new Object[]{
                        d.toString(),
                        d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                        h != null ? nz(h.name) : "",
                        h != null ? nz(h.type) : "",
                        h != null ? nz(h.scope) : "",
                        h != null ? Boolean.valueOf(h.enabled) : Boolean.FALSE,
                        h != null ? nz(h.notes) : "",
                        h != null ? Integer.valueOf(h.id) : Integer.valueOf(0)
                });

                d = d.plusDays(1);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to load holidays: " + ex.getMessage());
        }
    }

    private void onRowSelected(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;
        int row = table.getSelectedRow();
        if (row < 0) return;

        // table row index is view index; model is same because no sort, so safe
        LocalDate d = LocalDate.parse(model.getValueAt(row, 0).toString());
        tfDate.setText(d.toString());

        Holiday h = holidayByDate.get(d);
        if (h == null) {
            tfName.setText("");
            cbType.setSelectedIndex(0);
            cbScope.setSelectedIndex(0);
            tfRateDay.setText("");
            tfNotes.setText("");
            return;
        }

        tfName.setText(nz(h.name));
        cbType.setSelectedItem(nz(h.type).isEmpty() ? "REGULAR" : h.type);
        String scope = nz(h.scope).isEmpty() ? "NATIONAL" : h.scope;
        if ("COMPANY".equalsIgnoreCase(scope)) scope = "LOCAL";
        cbScope.setSelectedItem(scope);
        tfRateDay.setText(h.rateMultiplier == 0 ? "" : MONEY_FMT.format(h.rateMultiplier));
        tfNotes.setText(nz(h.notes));
    }

    private void saveHoliday() {
        try {
            Holiday h = new Holiday();
            h.holDate = LocalDate.parse(tfDate.getText().trim());
            h.name = tfName.getText().trim();
            h.type = cbType.getSelectedItem().toString();
            h.scope = cbScope.getSelectedItem().toString();
            h.notes = tfNotes.getText().trim();

            // preserve enabled if exists
            Holiday existing = holidayByDate.get(h.holDate);
            h.enabled = (existing == null) || existing.enabled;

            String rateTxt = tfRateDay.getText().trim();
            if (rateTxt.isEmpty()) h.rateMultiplier = 0.0;
            else h.rateMultiplier = Double.parseDouble(rateTxt.replace(",", ""));

            holidayDAO.upsert(h);
            loadTable();
            selectDateRow(h.holDate);

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Save failed: " + ex.getMessage());
        }
    }

    private void deleteHoliday() {
        try {
            LocalDate d = LocalDate.parse(tfDate.getText().trim());
            holidayDAO.deleteByDate(d);
            loadTable();
            selectDateRow(d);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Delete failed: " + ex.getMessage());
        }
    }

    private void selectDateRow(LocalDate d) {
        for (int i = 0; i < model.getRowCount(); i++) {
            if (model.getValueAt(i, 0).toString().equals(d.toString())) {
                table.getSelectionModel().setSelectionInterval(i, i);
                table.scrollRectToVisible(table.getCellRect(i, 0, true));
                return;
            }
        }
    }

    private static int toInt(Object o) {
        try { return Integer.parseInt(String.valueOf(o)); }
        catch (Exception e) { return 0; }
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    private class ZebraRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {

            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            setBorder(new EmptyBorder(0, 10, 0, 10));
            setForeground(Theme.TEXT);

            if (isSelected) setBackground(new Color(0xEAF4EE));
            else setBackground((row % 2 == 0) ? Color.WHITE : ZEBRA);

            return this;
        }
    }



private void disableHolidayUI() {
    try {
        if (table != null) table.setEnabled(false);
        if (btnSave != null) btnSave.setEnabled(false);
        if (btnDelete != null) btnDelete.setEnabled(false);

        // prevent editing fields
        tfDate.setEnabled(false);
        tfName.setEnabled(false);
        cbType.setEnabled(false);
        cbScope.setEnabled(false);
        tfRateDay.setEnabled(false);
        tfNotes.setEnabled(false);

        JOptionPane.showMessageDialog(
                this,
                "Holiday feature is disabled for MONTHLY employees.",
                "Holiday Disabled",
                JOptionPane.INFORMATION_MESSAGE
        );
    } catch (Exception ignore) {}
}

/** Renders Boolean cells as centered checkboxes while preserving zebra striping. */
private class CheckboxZebraRenderer extends JCheckBox implements javax.swing.table.TableCellRenderer {
    CheckboxZebraRenderer() {
        setHorizontalAlignment(SwingConstants.CENTER);
        setOpaque(true);
    }

    @Override
    public Component getTableCellRendererComponent(
            JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column
    ) {
        setSelected(Boolean.TRUE.equals(value));

        if (isSelected) setBackground(new Color(0xEAF4EE));
        else setBackground((row % 2 == 0) ? Color.WHITE : ZEBRA);

        return this;
    }
}

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
