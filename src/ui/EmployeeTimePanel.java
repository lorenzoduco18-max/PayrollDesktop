package ui;

import com.formdev.flatlaf.FlatClientProperties;

import dao.TimeLogDAO;
import dao.UserDAO;
import dao.PayslipPublishDAO;
import model.Employee;
import util.EmailUtil;
import util.PhotoStorageUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Date;
import java.util.Locale;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Method;

public class EmployeeTimePanel extends JPanel {

    // ===== palette =====
    private static final Color BG = new Color(0xF6F0E4);
    private static final Color CARD = new Color(0xFFFFFF);
    private static final Color TEXT = new Color(0x111827);
    private static final Color SUB = new Color(0x6B7280);
    private static final Color LINE = new Color(0xE5E7EB);

    private static final Color TEAL = new Color(0x0F766E);
    private static final Color ORANGE = new Color(0xB45309);

    private static final Color OK_BG = new Color(0xECFDF5);
    private static final Color OK_FG = new Color(0x065F46);

    private static final Color OUT_BG = new Color(0xEEF2FF);
    private static final Color OUT_FG = new Color(0x1D4ED8);

    private static final ZoneId PH_ZONE = ZoneId.of("Asia/Manila");

    // Used by DAO display, and also used here to parse back for filters
    private static final DateTimeFormatter HISTORY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");
    private static final DateTimeFormatter CLOCK_FMT = DateTimeFormatter.ofPattern("hh:mm:ss a");
    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ofPattern("EEEE, MMM dd, yyyy");
    private static final DateTimeFormatter LAST_ACTION_FMT = DateTimeFormatter.ofPattern("hh:mm a");
    private static final DateTimeFormatter TIME_CELL_FMT = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);


    private final Employee emp;
    private final int empId;         // DB PK (employees.emp_id)
    private final String empNoText;  // display only (e.g., 505)

    // header
    private final JLabel lblClock = new JLabel("-");
    // pay type
    private final boolean isMonthly;

    private final JLabel lblDate = new JLabel("-");
    private final JLabel lblStatus = new JLabel("-");
    private final RoundedButton btnLogout = createIosButton("Logout", new Color(0x0F766E), Color.WHITE);

    // right card
    private final JLabel lblName = new JLabel("-");
    private final JLabel lblEmpNo = new JLabel("-");
    private final JLabel badge = new JLabel("-", SwingConstants.CENTER);
    private final JLabel lblLastAction = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel lblStateHint = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel lblTodaySum = new JLabel("Today: -", SwingConstants.CENTER);
    private final JLabel lblWeekSum = new JLabel("This week: -", SwingConstants.CENTER);
    private final JLabel lblPayPeriod = new JLabel(" ", SwingConstants.LEFT);
    private final RoundedButton btnToggle = createIosButton("-", TEAL, Color.WHITE);
    // change password (employee self-service)
    private final RoundedButton btnChangePassword = createIosButton("Change Password", new Color(0xE5E7EB), new Color(0x111827));

    // payslips
    @SuppressWarnings("rawtypes")
    private final JComboBox<PayPeriodItem> cbPayslipCutoff = new JComboBox<>();
    private final RoundedButton btnDownloadPayslip = createIosButton("Download", new Color(0x1D4ED8), Color.WHITE);
    private final JLabel lblPayslipStatus = new JLabel(" ", SwingConstants.CENTER);

    private final JLabel lblPhotoReq = new JLabel("Photo required", SwingConstants.CENTER);

    // recent activity pay period filter
    @SuppressWarnings("rawtypes")
    private final JComboBox<LogPeriodItem> cbLogPeriod = new JComboBox<>();

// activity table (NO Log ID column)
    private final DefaultTableModel historyModel = new DefaultTableModel(
            new Object[]{"Time In", "Lunch Out", "Lunch In", "Time Out", "Total Hours"}, 0
    ) {
        @Override public boolean isCellEditable(int row, int col) { return false; }
    };
    private final JTable historyTable = new JTable(historyModel);

    // keep latest fetched rows in memory for filtering
    private List<TimeLogDAO.ActivityRow> cachedRows = new ArrayList<>();
    // current status cache (used for UI + fallback when DAO call fails)
    private boolean isTimedIn = false;

    // timers
    private javax.swing.Timer clockTimer;
    private javax.swing.Timer refreshTimer;

    public EmployeeTimePanel(Employee emp) {
        this.emp = emp;
        this.empId = resolveDbEmpId(emp);
        this.empNoText = safe(emp != null ? emp.getEmpNo() : null);
        this.isMonthly = detectMonthlyPayType(emp);

        buildUI();
        initLogPeriodFilter();
        wireActions();
        initPayslipUi();
        startTimers();
        service.CameraWarmup.warmAsync();


        refreshAll();
    }

    // ================= UI =================

    private void buildUI() {
        setOpaque(true);
        setBackground(BG);
        setLayout(new GridBagLayout());
        setBorder(new EmptyBorder(18, 18, 18, 18));

        JPanel header = buildHeader();
        ModernCard historyCard = buildHistoryCard();
        ModernCard actionCard = buildActionCard();

        GridBagConstraints root = new GridBagConstraints();
        root.gridx = 0; root.gridy = 0;
        root.weightx = 1;
        root.fill = GridBagConstraints.HORIZONTAL;
        add(header, root);

        root.gridy = 1;
        root.insets = new Insets(14, 0, 0, 0);
        root.fill = GridBagConstraints.BOTH;
        root.weighty = 1;

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();

        c.gridx = 0; c.gridy = 0;
        c.weightx = 1; c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(0, 0, 0, 14);
        center.add(historyCard, c);

        c.gridx = 1;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 0, 0);
        center.add(actionCard, c);

        add(center, root);
    }
    private JPanel buildHeader() {
        JPanel header = new JPanel(new GridBagLayout());
        header.setOpaque(false);

        // --- Left: EBCT logo + title + pay period (left aligned) ---
        JLabel logo = new JLabel();
        logo.setOpaque(false);
        try {
            java.net.URL u = getClass().getResource("/EBCTLOGO.png");
            if (u != null) {
                ImageIcon ic = new ImageIcon(u);
                // Bigger logo to fill header space
                int targetH = 72;
                int w = ic.getIconWidth();
                int h = ic.getIconHeight();
                if (w > 0 && h > 0) {
                    int targetW = (int) Math.round(targetH * (w / (double) h));
                    Image scaled = ic.getImage().getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
                    logo.setIcon(new ImageIcon(scaled));
                } else {
                    logo.setIcon(ic);
                }
            }
        } catch (Throwable ignore) {}

        JLabel title = new JLabel("Employee Portal");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(TEXT);

        lblPayPeriod.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lblPayPeriod.setForeground(SUB);

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblPayPeriod.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleBox.add(title);
        titleBox.add(Box.createVerticalStrut(2));
        titleBox.add(lblPayPeriod);

        JPanel left = new JPanel(new GridBagLayout());
        left.setOpaque(false);
        GridBagConstraints lc = new GridBagConstraints();
        lc.gridx = 0; lc.gridy = 0;
        lc.anchor = GridBagConstraints.WEST;
        left.add(logo, lc);

        lc.gridx = 1;
        lc.insets = new Insets(0, 12, 0, 0);
        lc.anchor = GridBagConstraints.WEST;
        left.add(titleBox, lc);

        // --- Right: clock + logout + single status pill ---
        styleClockLabels();

        // pill style (colors are set in refreshStatusAndButton)
        lblStatus.setOpaque(true);
        lblStatus.setFont(new Font("SansSerif", Font.BOLD, 11));
        lblStatus.setHorizontalAlignment(SwingConstants.CENTER);
        lblStatus.setBorder(new EmptyBorder(8, 14, 8, 14));

        btnLogout.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnLogout.setPreferredSize(new Dimension(110, 36));

        JPanel clockBox = new JPanel();
        clockBox.setOpaque(false);
        clockBox.setLayout(new BoxLayout(clockBox, BoxLayout.Y_AXIS));
        lblClock.setAlignmentX(Component.RIGHT_ALIGNMENT);
        lblDate.setAlignmentX(Component.RIGHT_ALIGNMENT);
        clockBox.add(lblClock);
        clockBox.add(Box.createVerticalStrut(2));
        clockBox.add(lblDate);

        JPanel right = new JPanel(new GridBagLayout());
        right.setOpaque(false);
        GridBagConstraints rh = new GridBagConstraints();
        rh.gridx = 0; rh.gridy = 0; rh.anchor = GridBagConstraints.EAST;
        right.add(clockBox, rh);

        rh.gridx = 1; rh.insets = new Insets(0, 12, 0, 0);
        right.add(btnLogout, rh);

        rh.gridx = 2; rh.insets = new Insets(0, 12, 0, 0);
        right.add(lblStatus, rh);

        GridBagConstraints hc = new GridBagConstraints();
        hc.gridx = 0; hc.gridy = 0;
        hc.weightx = 1;
        hc.fill = GridBagConstraints.HORIZONTAL;
        hc.anchor = GridBagConstraints.WEST;
        header.add(left, hc);

        hc.gridx = 1;
        hc.weightx = 0;
        hc.fill = GridBagConstraints.NONE;
        hc.anchor = GridBagConstraints.EAST;
        header.add(right, hc);

        return header;
    }

    private ModernCard buildHistoryCard() {
        ModernCard card = new ModernCard(22);
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(new EmptyBorder(16, 16, 16, 16));

        // Slightly smaller but bold title (requested)
        JLabel h = new JLabel("Recent Activity");
        h.setFont(new Font("SansSerif", Font.BOLD, 13));
        h.setForeground(TEXT);

        // Pay period dropdown (filters recent activity)
        JPanel filters = buildLogPeriodDropdown();
JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(h, BorderLayout.WEST);
        top.add(filters, BorderLayout.EAST);

        // Table styling
        historyTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        historyTable.setRowHeight(44);
        historyTable.setFillsViewportHeight(true);
        historyTable.setShowHorizontalLines(false);
        historyTable.setShowVerticalLines(false);
        historyTable.setIntercellSpacing(new Dimension(0, 0));
        historyTable.getTableHeader().setReorderingAllowed(false);
        historyTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        historyTable.getTableHeader().setForeground(TEXT);
        historyTable.getTableHeader().setBackground(CARD);
        historyTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        historyTable.setForeground(TEXT);
        historyTable.setBackground(CARD);
        historyTable.setSelectionBackground(new Color(0xCDEDEA));
        historyTable.setSelectionForeground(new Color(0x0B3B3A));
        // Zebra rows + padding + centered text (modern, non-default look)
        historyTable.setDefaultRenderer(Object.class, new ZebraCellRenderer(SwingConstants.CENTER));
        historyTable.getColumnModel().getColumn(0).setCellRenderer(new DateTimeCellRenderer(new Color(0x0F766E)));
        historyTable.getColumnModel().getColumn(1).setCellRenderer(new DateTimeCellRenderer(new Color(0xB45309)));
        historyTable.getColumnModel().getColumn(2).setCellRenderer(new DateTimeCellRenderer(new Color(0x0F766E)));
        historyTable.getColumnModel().getColumn(3).setCellRenderer(new DateTimeCellRenderer(new Color(0x1D4ED8)));
        historyTable.getColumnModel().getColumn(4).setCellRenderer(new TotalHoursRenderer());

        // Wider column sizing so full timestamps stay visible
        historyTable.getColumnModel().getColumn(0).setPreferredWidth(190);
        historyTable.getColumnModel().getColumn(1).setPreferredWidth(230);
        historyTable.getColumnModel().getColumn(2).setPreferredWidth(230);
        historyTable.getColumnModel().getColumn(3).setPreferredWidth(190);
        historyTable.getColumnModel().getColumn(4).setPreferredWidth(120);
        historyTable.getColumnModel().getColumn(4).setMinWidth(120);
        historyTable.getColumnModel().getColumn(4).setMaxWidth(140);

        JScrollPane sp = new JScrollPane(historyTable);

        sp.setBorder(new LineBorder(LINE));
        sp.getViewport().setBackground(CARD);
        sp.setOpaque(false);

        card.add(top, BorderLayout.NORTH);
        card.add(sp, BorderLayout.CENTER);

        // Removed tip text + removed double-click copy logic (requested)
        return card;
    }

    private ModernCard buildActionCard() {
        ModernCard card = new ModernCard(22);
        card.setLayout(new GridBagLayout());
        card.setBorder(new EmptyBorder(18, 18, 18, 18));
        // Make the right side feel less "tight".
        // (UI only – no logic changes)
        card.setPreferredSize(new Dimension(320, 0));
        card.setMinimumSize(new Dimension(300, 0));

        // Name + #employeeNo (no labels)
        lblName.setText(fullName(emp));
        lblName.setFont(new Font("SansSerif", Font.BOLD, 15));
        lblName.setForeground(TEXT);

        lblEmpNo.setText(empNoText.isEmpty() ? "-" : ("#" + empNoText));
        lblEmpNo.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lblEmpNo.setForeground(SUB);

        lblLastAction.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lblLastAction.setForeground(SUB);

        lblTodaySum.setFont(new Font("SansSerif", Font.BOLD, 12));
        lblTodaySum.setForeground(TEXT);

        lblWeekSum.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lblWeekSum.setForeground(SUB);

        btnToggle.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnToggle.setPreferredSize(new Dimension(135, 44));

        // Change Password button must stay visible (text was white-on-white before)
        btnChangePassword.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnChangePassword.setPreferredSize(new Dimension(180, 38));
        btnChangePassword.setBackground(new Color(0xE5E7EB));
        btnChangePassword.setForeground(new Color(0x111827));

        lblStateHint.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lblStateHint.setForeground(SUB);
        lblStateHint.setHorizontalAlignment(SwingConstants.CENTER);

        lblPhotoReq.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lblPhotoReq.setForeground(SUB);
        lblPhotoReq.setHorizontalAlignment(SwingConstants.CENTER);

        // Payslip UI (kept)
        cbPayslipCutoff.setFont(new Font("SansSerif", Font.PLAIN, 12));
        cbPayslipCutoff.setMaximumSize(new Dimension(170, 34));
        cbPayslipCutoff.setPreferredSize(new Dimension(170, 34));

        btnDownloadPayslip.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnDownloadPayslip.setPreferredSize(new Dimension(140, 34));

        lblPayslipStatus.setFont(new Font("SansSerif", Font.PLAIN, 10));
        lblPayslipStatus.setForeground(SUB);
        lblPayslipStatus.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel payslipTitle = new JLabel("Payslips");
        payslipTitle.setFont(new Font("SansSerif", Font.BOLD, 12));
        payslipTitle.setForeground(TEXT);
        payslipTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel payslipRow = new JPanel();
        payslipRow.setOpaque(false);
        payslipRow.setLayout(new BoxLayout(payslipRow, BoxLayout.X_AXIS));
        payslipRow.add(cbPayslipCutoff);
        payslipRow.add(Box.createHorizontalStrut(8));
        payslipRow.add(btnDownloadPayslip);
        payslipRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel payslipPanel = new JPanel();
        payslipPanel.setOpaque(false);
        payslipPanel.setLayout(new BoxLayout(payslipPanel, BoxLayout.Y_AXIS));
        payslipPanel.add(payslipTitle);
        payslipPanel.add(Box.createVerticalStrut(6));
        payslipPanel.add(payslipRow);
        payslipPanel.add(Box.createVerticalStrut(6));
        payslipPanel.add(lblPayslipStatus);

        // Remove the "boxed" sub-sections (user said it looks worse).
        // Keep a clean stacked layout with spacing only.

        JPanel infoSection = new JPanel();
        infoSection.setOpaque(false);
        infoSection.setLayout(new BoxLayout(infoSection, BoxLayout.Y_AXIS));
        lblName.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblEmpNo.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoSection.add(lblName);
        infoSection.add(Box.createVerticalStrut(2));
        infoSection.add(lblEmpNo);

        JPanel statsSection = new JPanel();
        statsSection.setOpaque(false);
        statsSection.setLayout(new BoxLayout(statsSection, BoxLayout.Y_AXIS));
        lblLastAction.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblTodaySum.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblWeekSum.setAlignmentX(Component.CENTER_ALIGNMENT);
        statsSection.add(lblLastAction);
        statsSection.add(Box.createVerticalStrut(10));
        statsSection.add(lblTodaySum);
        statsSection.add(Box.createVerticalStrut(2));
        statsSection.add(lblWeekSum);

        JPanel actionSection = new JPanel();
        actionSection.setOpaque(false);
        actionSection.setLayout(new BoxLayout(actionSection, BoxLayout.Y_AXIS));
        btnToggle.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblStateHint.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnChangePassword.setAlignmentX(Component.CENTER_ALIGNMENT);
        actionSection.add(btnToggle);
        actionSection.add(Box.createVerticalStrut(8));
        actionSection.add(lblStateHint);
        actionSection.add(Box.createVerticalStrut(12));
        actionSection.add(btnChangePassword);

        JPanel payslipSection = new JPanel();
        payslipSection.setOpaque(false);
        payslipSection.setLayout(new BoxLayout(payslipSection, BoxLayout.Y_AXIS));
        payslipPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        payslipSection.add(payslipPanel);

        // ---------- Layout ----------
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;

        card.add(infoSection, c);

        c.gridy++;
        c.insets = new Insets(10, 0, 0, 0);
        card.add(statsSection, c);

        c.gridy++;
        c.insets = new Insets(12, 0, 0, 0);
        card.add(actionSection, c);

        c.gridy++;
        c.insets = new Insets(14, 0, 0, 0);
        card.add(payslipSection, c);

        c.gridy++;
        c.insets = new Insets(12, 0, 0, 0);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        card.add(lblPhotoReq, c);

        // push everything to top
        c.gridy++;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        card.add(Box.createVerticalGlue(), c);

        return card;
    }

    
    
    private JPanel buildLogPeriodDropdown() {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);

        cbLogPeriod.setFocusable(false);
        cbLogPeriod.setPreferredSize(new Dimension(240, 30));
        cbLogPeriod.putClientProperty(FlatClientProperties.STYLE,
                "arc:999; borderWidth:1; borderColor:#E5E7EB; background:#ECFDF5; foreground:#0F766E; padding:4,10,4,10");
        cbLogPeriod.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                lbl.setBorder(new EmptyBorder(6, 10, 6, 10));
                if (value instanceof LogPeriodItem) {
                    lbl.setText(((LogPeriodItem) value).label);
                }
                return lbl;
            }
        });

        cbLogPeriod.addActionListener(e -> applyFilterAndRender());

        wrap.add(cbLogPeriod, BorderLayout.CENTER);
        return wrap;
    }

    private void initLogPeriodFilter() {
        // Build list of previous + future periods around "now"
        LocalDate now = LocalDate.now(PH_ZONE);

        List<LogPeriodItem> items = new ArrayList<>();
        if (isMonthly) {
            LocalDate cursor = now.withDayOfMonth(1).minusMonths(12);
            for (int i = 0; i < 18; i++) { // 12 past + current + 5 future
                LocalDate start = cursor;
                LocalDate end = cursor.withDayOfMonth(cursor.lengthOfMonth());
                items.add(LogPeriodItem.monthly(start));
                cursor = cursor.plusMonths(1);
            }
        } else {
            // bi-monthly: 1–15 and 16–end
            LocalDate cursor = now.withDayOfMonth(1).minusMonths(6);
            for (int i = 0; i < 24; i++) { // 6 past months *2 + current + future
                items.add(LogPeriodItem.biMonthly(cursor.withDayOfMonth(1), cursor.withDayOfMonth(15)));
                items.add(LogPeriodItem.biMonthly(cursor.withDayOfMonth(16), cursor.withDayOfMonth(cursor.lengthOfMonth())));
                cursor = cursor.plusMonths(1);
            }
        }

        DefaultComboBoxModel<LogPeriodItem> model = new DefaultComboBoxModel<>();
        for (LogPeriodItem it : items) model.addElement(it);
        cbLogPeriod.setModel(model);

        // select current period
        LogPeriodItem current = null;
        for (int i = 0; i < model.getSize(); i++) {
            LogPeriodItem it = model.getElementAt(i);
            if (it != null && !now.isBefore(it.start) && !now.isAfter(it.end)) {
                current = it;
                break;
            }
        }
        if (current != null) cbLogPeriod.setSelectedItem(current);
    }

    private static boolean detectMonthlyPayType(Employee e) {
        if (e == null) return false;
        try {
            Method m = e.getClass().getMethod("getPayType");
            Object v = m.invoke(e);
            String s = (v == null) ? "" : v.toString().trim();
            return s.equalsIgnoreCase("MONTHLY") || s.equalsIgnoreCase("Monthly");
        } catch (Exception ignore) {
        }
        try {
            // some models store as field
            java.lang.reflect.Field f = e.getClass().getDeclaredField("payType");
            f.setAccessible(true);
            Object v = f.get(e);
            String s = (v == null) ? "" : v.toString().trim();
            return s.equalsIgnoreCase("MONTHLY") || s.equalsIgnoreCase("Monthly");
        } catch (Exception ignore) {
        }
        return false;
    }

    private static class LogPeriodItem {
        final LocalDate start;
        final LocalDate end;
        final String label;

        private LogPeriodItem(LocalDate start, LocalDate end, String label) {
            this.start = start;
            this.end = end;
            this.label = label;
        }

        static LogPeriodItem monthly(LocalDate firstOfMonth) {
            LocalDate start = firstOfMonth.withDayOfMonth(1);
            LocalDate end = firstOfMonth.withDayOfMonth(firstOfMonth.lengthOfMonth());
            DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy MMM", Locale.ENGLISH);
            return new LogPeriodItem(start, end, f.format(start));
        }

        static LogPeriodItem biMonthly(LocalDate start, LocalDate end) {
            DateTimeFormatter f1 = DateTimeFormatter.ofPattern("yyyy MMM", Locale.ENGLISH);
            return new LogPeriodItem(start, end, f1.format(start) + " " + start.getDayOfMonth() + "-" + end.getDayOfMonth());
        }

        @Override public String toString() { return label; }
    }


    private RoundedButton createIosButton(String text, Color background, Color foreground) {
        RoundedButton button = new RoundedButton(text, background, foreground);
        button.setCornerRadius(18);
        button.setFocusPainted(false);
        return button;
    }

    private void styleClockLabels() {
        lblClock.setFont(new Font("SansSerif", Font.BOLD, 15));
        lblClock.setForeground(TEXT);
        lblDate.setFont(new Font("SansSerif", Font.PLAIN, 10));
        lblDate.setForeground(SUB);
    }

    // ================= Actions =================

    private void wireActions() {

        // Logout: just close the portal frame; EmployeePortalFrame will reopen Login (from your latest fix)
        btnLogout.addActionListener(e -> {
            Window w = SwingUtilities.getWindowAncestor(EmployeeTimePanel.this);
            if (w != null) w.dispose();
        });

        btnToggle.addActionListener(e -> runToggleAction());

        btnChangePassword.addActionListener(e -> openChangePasswordDialog());
// payslips
        cbPayslipCutoff.addActionListener(e -> updatePayslipAvailability());
        btnDownloadPayslip.addActionListener(e -> downloadSelectedPayslip());
    }


    // ================= Payslip download (UI only; DB read via reflection) =================

    private static class PayPeriodItem {
        final LocalDate start;
        final LocalDate end;
        final String label;
        PayPeriodItem(LocalDate start, LocalDate end, String label) {
            this.start = start;
            this.end = end;
            this.label = label;
        }
        @Override public String toString() { return label; }
    }

    private void initPayslipUi() {
        // populate 15-year range: 5 years back, 10 years forward (accurate month lengths)
        populatePayslipCutoffs();
        updatePayslipAvailability();
    }

    private void populatePayslipCutoffs() {
        cbPayslipCutoff.removeAllItems();

        LocalDate todayPH = ZonedDateTime.now(PH_ZONE).toLocalDate();
        LocalDate from = todayPH.minusYears(5).withDayOfMonth(1);
        LocalDate to = todayPH.plusYears(10).withDayOfMonth(1);

        YearMonth startYm = YearMonth.of(from.getYear(), from.getMonth());
        YearMonth endYm = YearMonth.of(to.getYear(), to.getMonth());

        DateTimeFormatter ymFmt = DateTimeFormatter.ofPattern("yyyy MMM", Locale.ENGLISH);

        YearMonth cur = startYm;
        while (!cur.isAfter(endYm)) {
            LocalDate d1 = cur.atDay(1);
            LocalDate d15 = cur.atDay(15);
            LocalDate d16 = cur.atDay(16);
            LocalDate dLast = cur.atEndOfMonth();

            cbPayslipCutoff.addItem(new PayPeriodItem(d1, d15,
                    ymFmt.format(d1) + " 1-15"));
            cbPayslipCutoff.addItem(new PayPeriodItem(d16, dLast,
                    ymFmt.format(d1) + " 16-" + dLast.getDayOfMonth()));

            cur = cur.plusMonths(1);
        }

        // default select: current 1-15 or 16-end depending on today
        int day = todayPH.getDayOfMonth();
        String want = DateTimeFormatter.ofPattern("yyyy MMM", Locale.ENGLISH).format(todayPH.withDayOfMonth(1))
                + (day <= 15 ? " 1-15" : " 16-" + YearMonth.from(todayPH).atEndOfMonth().getDayOfMonth());
        for (int i = 0; i < cbPayslipCutoff.getItemCount(); i++) {
            PayPeriodItem it = cbPayslipCutoff.getItemAt(i);
            if (it != null && it.label.equals(want)) {
                cbPayslipCutoff.setSelectedIndex(i);
                break;
            }
        }
    }

    private void updatePayslipAvailability() {
        PayPeriodItem it = (PayPeriodItem) cbPayslipCutoff.getSelectedItem();
        if (it == null || empId <= 0) {
            btnDownloadPayslip.setEnabled(false);
            lblPayslipStatus.setText("Not published yet");
            return;
        }

        try {
            boolean ok = PayslipPublishDAO.exists(empId, it.start, it.end);
            btnDownloadPayslip.setEnabled(ok);
            lblPayslipStatus.setText(ok ? "Available (published)" : "Not published yet");
        } catch (Exception ex) {
            // fail-safe: don't crash UI
            btnDownloadPayslip.setEnabled(false);
            lblPayslipStatus.setText("Not published yet");
        }
    }

    private void downloadSelectedPayslip() {
    PayPeriodItem it = (PayPeriodItem) cbPayslipCutoff.getSelectedItem();
    if (it == null || empId <= 0) return;

    try {
        PayslipPublishDAO.PublishedPdf p = PayslipPublishDAO.fetch(empId, it.start, it.end);
        if (p == null || p.pdfBytes == null || p.pdfBytes.length == 0) {
            JOptionPane.showMessageDialog(this, "Payslip is not published yet for this cutoff.", "Not Available",
                    JOptionPane.INFORMATION_MESSAGE);
            updatePayslipAvailability();
            return;
        }

        // Auto-open in browser (no Save dialog)
        String baseName = (p.fileName != null && !p.fileName.trim().isEmpty())
                ? p.fileName.trim()
                : buildFallbackPayslipFileName(it.start, it.end);

        if (!baseName.toLowerCase(Locale.ROOT).endsWith(".pdf")) baseName += ".pdf";

        // Write to a temporary file and open with the system browser (file:// URL).
        File tmp = File.createTempFile("payslip_", "_" + baseName.replaceAll("[\\/:*?\"<>|]", "_"));
        tmp.deleteOnExit();

        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write(p.pdfBytes);
        }

        if (Desktop.isDesktopSupported()) {
            Desktop d = Desktop.getDesktop();
            try {
                d.browse(tmp.toURI());
            } catch (Exception ignore) {
                // Fallback: open with default PDF app if browser open is not available
                try { d.open(tmp); } catch (Exception ignore2) { /* handled below */ }
            }
        }

        lblPayslipStatus.setText("Opened: " + baseName);

    } catch (Exception ex) {
        JOptionPane.showMessageDialog(this, "Failed to open payslip.\n" + ex.getMessage(),
                "Error", JOptionPane.ERROR_MESSAGE);
    }
}

    private String buildFallbackPayslipFileName(LocalDate start, LocalDate end) {
        // Only used if DB didn't provide the filename. Keep it simple and predictable.
        String ln = safe(emp != null ? emp.getLastName() : null).toLowerCase();
        if (ln.isEmpty()) ln = "employee";
        String empNo = empNoText.isEmpty() ? String.valueOf(empId) : empNoText;
        return "payslip_" + ln + "_" + empNo + "_" + start.getDayOfMonth() + "-" + end.getDayOfMonth() + ".pdf";
    }

    

    /**
     * Fetch payslip meta only (no bytes) so we can enable/disable button cheaply.
     * Uses reflection to adapt to your existing dao.PayslipPublishDAO implementation without changing logic.
     */
    

    /**
     * Fetch published payslip from the existing PayslipPublishDAO by discovering compatible methods at runtime.
     * We intentionally avoid hard-coding method names to prevent breaking your existing logic.
     */
    

    private Object[] buildDateArgs(Method m, int empId, LocalDate start, LocalDate end) {
        if (m == null) return new Object[]{empId, start, end};
        Class<?>[] p = m.getParameterTypes();
        if (p[1] == Date.class && p[2] == Date.class) {
            return new Object[]{empId, Date.valueOf(start), Date.valueOf(end)};
        }
        return new Object[]{empId, start, end};
    }

    private String tryGetString(Object obj, String... names) {
        for (String n : names) {
            try {
                // method
                Method m = obj.getClass().getMethod(n);
                Object v = m.invoke(obj);
                if (v != null) return String.valueOf(v);
            } catch (Exception ignore) {}
            try {
                // field
                var f = obj.getClass().getDeclaredField(n);
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v != null) return String.valueOf(v);
            } catch (Exception ignore) {}
        }
        return null;
    }

    private byte[] tryGetBytes(Object obj, String... names) {
        for (String n : names) {
            try {
                Method m = obj.getClass().getMethod(n);
                Object v = m.invoke(obj);
                if (v instanceof byte[]) return (byte[]) v;
            } catch (Exception ignore) {}
            try {
                var f = obj.getClass().getDeclaredField(n);
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v instanceof byte[]) return (byte[]) v;
            } catch (Exception ignore) {}
        }
        return null;
    }


        private JComponent makeFieldBlock(String label, JComponent field) {
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        p.add(l, BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

private void openChangePasswordDialog() {
        if (empId <= 0) {
            JOptionPane.showMessageDialog(this,
                    "Employee DB ID is missing.\nPlease re-login.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        final JPasswordField pfCurrent = new JPasswordField();
        final JPasswordField pfNew = new JPasswordField();
        final JPasswordField pfConfirm = new JPasswordField();

        // Rounded + consistent styling (FlatLaf)
        pfCurrent.putClientProperty(FlatClientProperties.STYLE, "arc: 12; minimumWidth: 220");
        pfNew.putClientProperty(FlatClientProperties.STYLE, "arc: 12; minimumWidth: 220");
        pfConfirm.putClientProperty(FlatClientProperties.STYLE, "arc: 12; minimumWidth: 220");

        final char echo = pfCurrent.getEchoChar();

        JLabel title = new JLabel("Change Password");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 2f));

        JLabel hint = new JLabel("<html><span style='color:#666666'>Use a strong password you can remember.</span></html>");

        JCheckBox cbShow = new JCheckBox("Show passwords");
        cbShow.setOpaque(false);
        cbShow.addActionListener(e -> {
            boolean show = cbShow.isSelected();
            pfCurrent.setEchoChar(show ? (char) 0 : echo);
            pfNew.setEchoChar(show ? (char) 0 : echo);
            pfConfirm.setEchoChar(show ? (char) 0 : echo);
        });

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(new EmptyBorder(14, 14, 14, 14));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.weightx = 1;

        int r = 0;

        g.gridx = 0; g.gridy = r++; g.gridwidth = 2;
        content.add(title, g);

        g.gridx = 0; g.gridy = r++; g.gridwidth = 2;
        content.add(hint, g);

        g.gridx = 0; g.gridy = r++; g.gridwidth = 2;
        content.add(new JSeparator(), g);

        g.gridx = 0; g.gridy = r++; g.gridwidth = 2;
        content.add(makeFieldBlock("Current Password", pfCurrent), g);

        g.gridx = 0; g.gridy = r++; g.gridwidth = 2;
        content.add(makeFieldBlock("New Password", pfNew), g);

        g.gridx = 0; g.gridy = r++; g.gridwidth = 2;
        content.add(makeFieldBlock("Confirm New Password", pfConfirm), g);

        g.gridx = 0; g.gridy = r++; g.gridwidth = 2;
        content.add(cbShow, g);

        Object[] options = {"Update Password", "Cancel"};
        int res = JOptionPane.showOptionDialog(
                SwingUtilities.getWindowAncestor(this),
                content,
                "Change Password",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]
        );
        if (res != JOptionPane.OK_OPTION) return;

        String cur = new String(pfCurrent.getPassword()).trim();
        String nw = new String(pfNew.getPassword()).trim();
        String cf = new String(pfConfirm.getPassword()).trim();

        if (cur.isEmpty() || nw.isEmpty() || cf.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill out all password fields.", "Missing", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!nw.equals(cf)) {
            JOptionPane.showMessageDialog(this, "New passwords do not match.", "Mismatch", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (nw.length() < 4) {
            JOptionPane.showMessageDialog(this, "Password must be at least 4 characters.", "Too short", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (nw.equals(cur)) {
            JOptionPane.showMessageDialog(this, "New password must be different from current password.", "Invalid", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            UserDAO dao = new UserDAO();
            if (!dao.verifyPasswordByEmpId(empId, cur)) {
                JOptionPane.showMessageDialog(this, "Current password is incorrect.", "Invalid", JOptionPane.ERROR_MESSAGE);
                return;
            }
            boolean ok = dao.updatePasswordByEmpId(empId, nw);
            if (ok) {
                JOptionPane.showMessageDialog(this, "Password updated successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Password update failed.", "Failed", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error updating password:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }



    private void runToggleAction() {
        if (empId <= 0) {
            JOptionPane.showMessageDialog(this,
                    "Employee DB ID is missing.\nPlease re-login or check EmployeeDAO mapping.",
                    "Cannot Time In/Out", JOptionPane.ERROR_MESSAGE);
            return;
        }

        btnToggle.setEnabled(false);
        String oldText = btnToggle.getText();
        btnToggle.setText("PROCESSING...");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override protected Void doInBackground() throws Exception {
                TimeLogDAO.PortalState state = TimeLogDAO.getPortalState(empId);
                if (state == TimeLogDAO.PortalState.TIMED_OUT) {
                    doTimeInInternal();
                } else if (state == TimeLogDAO.PortalState.ON_LUNCH) {
                    int ans = ModernConfirmDialog.showYesNo(
                            EmployeeTimePanel.this,
                            "Return from Lunch",
                            "You are about to return from lunch.",
                            "Continue",
                            "Cancel"
                    );
                    if (ans == ModernConfirmDialog.YES_OPTION) {
                        doEndLunchInternal();
                    }
                } else {
                    String[] options = {"Lunch Time Out", "Time Out for the Day", "Cancel"};
                    int choice = ModernConfirmDialog.showOptions(
                            EmployeeTimePanel.this,
                            "Confirm Time Out",
                            "Choose your time out action.",
                            options,
                            0
                    );
                    if (choice == 0) {
                        doStartLunchInternal();
                    } else if (choice == 1) {
                        int ans = ModernConfirmDialog.showYesNo(
                                EmployeeTimePanel.this,
                                "Time Out for the Day",
                                "You are about to time out for the day.",
                                "Continue",
                                "Cancel"
                        );
                        if (ans == ModernConfirmDialog.YES_OPTION) {
                            doTimeOutInternal();
                        }
                    }
                }
                return null;
            }

            @Override protected void done() {
                try {
                    get();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(EmployeeTimePanel.this,
                            cause.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    btnToggle.setText(oldText);
                    btnToggle.setEnabled(true);
                    refreshAll();
                }
            }
        };
        worker.execute();
    }

    private void doTimeInInternal() throws Exception {
        ZonedDateTime when = ZonedDateTime.now(PH_ZONE);

        CameraCaptureDialog dlg = new CameraCaptureDialog(SwingUtilities.getWindowAncestor(this));
        dlg.setVisible(true);
        BufferedImage img = dlg.getCapturedImage();
        if (img == null) {
            // If camera fails, DO NOT change status (must-do)
            throw new Exception("Photo is required for Time In.");
        }

        File photoFile = PhotoStorageUtil.saveJpg(img, String.valueOf(empId), "TIME_IN", when);
        TimeLogDAO.timeIn(empId);

        // Email failure should not block success
        try {
            String empNameText = fullName(emp);
            String subject = "TIME IN - " + empNameText + " (ID " + empId + ")";
            String body =
                    "Employee: " + empNameText + "\n" +
                    "Employee No: " + (empNoText.isEmpty() ? "-" : empNoText) + "\n" +
                    "Employee ID: " + empId + "\n" +
                    "Action: TIME IN\n" +
                    "Date/Time: " + when.format(HISTORY_FMT) + "\n";
            EmailUtil.sendProofEmail(subject, body, photoFile);
        } catch (Exception ignored) {}
    }

    private void doStartLunchInternal() throws Exception {
        ZonedDateTime when = ZonedDateTime.now(PH_ZONE);

        CameraCaptureDialog dlg = new CameraCaptureDialog(SwingUtilities.getWindowAncestor(this));
        dlg.setVisible(true);
        BufferedImage img = dlg.getCapturedImage();
        if (img == null) {
            throw new Exception("Photo is required for Lunch Time Out.");
        }

        File photoFile = PhotoStorageUtil.saveJpg(img, String.valueOf(empId), "LUNCH_OUT", when);
        TimeLogDAO.startLunch(empId);

        try {
            String empNameText = fullName(emp);
            String subject = "LUNCH OUT - " + empNameText + " (ID " + empId + ")";
            String body =
                    "Employee: " + empNameText + "\n" +
                    "Employee No: " + (empNoText.isEmpty() ? "-" : empNoText) + "\n" +
                    "Employee ID: " + empId + "\n" +
                    "Action: LUNCH OUT\n" +
                    "Date/Time: " + when.format(HISTORY_FMT) + "\n";
            EmailUtil.sendProofEmail(subject, body, photoFile);
        } catch (Exception ignored) {}
    }

    private void doEndLunchInternal() throws Exception {
        ZonedDateTime when = ZonedDateTime.now(PH_ZONE);

        CameraCaptureDialog dlg = new CameraCaptureDialog(SwingUtilities.getWindowAncestor(this));
        dlg.setVisible(true);
        BufferedImage img = dlg.getCapturedImage();
        if (img == null) {
            throw new Exception("Photo is required for Return From Lunch.");
        }

        File photoFile = PhotoStorageUtil.saveJpg(img, String.valueOf(empId), "LUNCH_IN", when);
        TimeLogDAO.endLunch(empId);

        try {
            String empNameText = fullName(emp);
            String subject = "LUNCH IN - " + empNameText + " (ID " + empId + ")";
            String body =
                    "Employee: " + empNameText + "\n" +
                    "Employee No: " + (empNoText.isEmpty() ? "-" : empNoText) + "\n" +
                    "Employee ID: " + empId + "\n" +
                    "Action: LUNCH IN\n" +
                    "Date/Time: " + when.format(HISTORY_FMT) + "\n";
            EmailUtil.sendProofEmail(subject, body, photoFile);
        } catch (Exception ignored) {}
    }

    private void doTimeOutInternal() throws Exception {
        ZonedDateTime when = ZonedDateTime.now(PH_ZONE);

        CameraCaptureDialog dlg = new CameraCaptureDialog(SwingUtilities.getWindowAncestor(this));
        dlg.setVisible(true);
        BufferedImage img = dlg.getCapturedImage();
        if (img == null) {
            // If camera fails, DO NOT change status (must-do)
            throw new Exception("Photo is required for Time Out.");
        }

        File photoFile = PhotoStorageUtil.saveJpg(img, String.valueOf(empId), "TIME_OUT", when);
        TimeLogDAO.timeOut(empId);

        try {
            String empNameText = fullName(emp);
            String subject = "TIME OUT - " + empNameText + " (ID " + empId + ")";
            String body =
                    "Employee: " + empNameText + "\n" +
                    "Employee No: " + (empNoText.isEmpty() ? "-" : empNoText) + "\n" +
                    "Employee ID: " + empId + "\n" +
                    "Action: TIME OUT\n" +
                    "Date/Time: " + when.format(HISTORY_FMT) + "\n";
            EmailUtil.sendProofEmail(subject, body, photoFile);
        } catch (Exception ignored) {}
    }

    private void refreshAll() {
        refreshStatusAndButton();
        refreshHistory(); // fills cache then filters
    }

    
    private void refreshStatusAndButton() {
        try {
            TimeLogDAO.PortalState state = TimeLogDAO.getPortalState(empId);
            isTimedIn = state != TimeLogDAO.PortalState.TIMED_OUT;

            if (state == TimeLogDAO.PortalState.ON_LUNCH) {
                lblStatus.setText("●  ON LUNCH");
                lblStatus.setBackground(new Color(0xFFF7ED));
                lblStatus.setForeground(new Color(0xC2410C));

                badge.setText("●  ON LUNCH");
                badge.setBackground(new Color(0xFFF7ED));
                badge.setForeground(new Color(0xC2410C));

                btnToggle.setText("RETURN FROM LUNCH");
                btnToggle.setBackground(new Color(0x0F766E));
                lblStateHint.setText("You're currently on lunch break.");
            } else if (state == TimeLogDAO.PortalState.TIMED_IN) {
                lblStatus.setText("●  TIMED IN");
                lblStatus.setBackground(OK_BG);
                lblStatus.setForeground(OK_FG);

                badge.setText("●  TIMED IN");
                badge.setBackground(OK_BG);
                badge.setForeground(OK_FG);

                btnToggle.setText("TIME OUT");
                btnToggle.setBackground(ORANGE);
                lblStateHint.setText("You're currently timed in.");
            } else {
                lblStatus.setText("●  TIMED OUT");
                lblStatus.setBackground(OUT_BG);
                lblStatus.setForeground(OUT_FG);

                badge.setText("●  TIMED OUT");
                badge.setBackground(OUT_BG);
                badge.setForeground(OUT_FG);

                btnToggle.setText("TIME IN");
                btnToggle.setBackground(TEAL);
                lblStateHint.setText("You're currently timed out.");
            }
        } catch (Exception ex) {
            isTimedIn = false;
            lblStatus.setText("●  STATUS ERROR");
            lblStatus.setBackground(new Color(0xFEF2F2));
            lblStatus.setForeground(new Color(0xB91C1C));
            badge.setText("●  STATUS ERROR");
            badge.setBackground(new Color(0xFEF2F2));
            badge.setForeground(new Color(0xB91C1C));
            btnToggle.setText("RETRY");
            btnToggle.setBackground(new Color(0x9CA3AF));
            lblStateHint.setText(" ");
        }
    }


    
    private void refreshHistory() {
        if (empId <= 0) {
            historyModel.setRowCount(0);
            cachedRows = new ArrayList<>();
            lblTodaySum.setText("Today: -");
            lblWeekSum.setText("This week: -");
            lblLastAction.setText(" ");
            return;
        }

        try {
            cachedRows = TimeLogDAO.getRecentActivity(empId, 200);
        } catch (Exception ex) {
            cachedRows = new ArrayList<>();
        }

        LocalDate today = LocalDate.now(PH_ZONE);
        int todayMin = computeWorkedMinutes(today, today);
        int weekMin  = computeWorkedMinutes(today.minusDays(6), today);

        lblTodaySum.setText("Today: " + formatHm(todayMin));
        lblWeekSum.setText("This week: " + formatHm(weekMin));

        applyFilterAndRender();
    }


    
    private void applyFilterAndRender() {
        historyModel.setRowCount(0);

        LogPeriodItem period = (LogPeriodItem) cbLogPeriod.getSelectedItem();
        LocalDate from = (period != null) ? period.start : null;
        LocalDate to   = (period != null) ? period.end   : null;

        List<PortalHistoryRow> rows = buildPortalRows(cachedRows, from, to);
        int limit = 200;
        for (int i = 0; i < rows.size() && i < limit; i++) {
            PortalHistoryRow pr = rows.get(i);
            historyModel.addRow(new Object[]{
                    pr.inDt == null ? "-" : pr.inDt.format(ADMIN_LOG_DTF).toLowerCase(Locale.ENGLISH),
                    pr.lunchOutDt == null ? "-" : pr.lunchOutDt.format(ADMIN_LOG_DTF).toLowerCase(Locale.ENGLISH),
                    pr.lunchInDt == null ? "-" : pr.lunchInDt.format(ADMIN_LOG_DTF).toLowerCase(Locale.ENGLISH),
                    pr.outDt == null ? "-" : pr.outDt.format(ADMIN_LOG_DTF).toLowerCase(Locale.ENGLISH),
                    formatHm(pr.workedMinutes())
            });
        }

        if (cachedRows != null && !cachedRows.isEmpty()) updateLastAction(cachedRows.get(0));
        else updateLastAction(null);
    }


    private void updateLastAction(TimeLogDAO.ActivityRow mostRecentShown) {
        if (mostRecentShown == null) {
            lblLastAction.setText(" ");
            return;
        }

        LocalDateTime dt = parseHistoryDateTime(mostRecentShown.whenText);
        if (dt == null) {
            lblLastAction.setText(" ");
            return;
        }

        DateTimeFormatter md = DateTimeFormatter.ofPattern("MMM d");
        lblLastAction.setText("Last action: " + dt.format(LAST_ACTION_FMT) + " • " + dt.format(md));
        lblStateHint.setText(isTimedIn ? "You're currently timed in." : "You're currently timed out.");
    }


    
    
    private static class PortalHistoryRow {
        final int logId;
        LocalDateTime inDt;
        LocalDateTime lunchOutDt;
        LocalDateTime lunchInDt;
        LocalDateTime outDt;

        PortalHistoryRow(int logId) { this.logId = logId; }

        LocalDateTime sortKey() {
            if (outDt != null) return outDt;
            if (lunchInDt != null) return lunchInDt;
            if (lunchOutDt != null) return lunchOutDt;
            return inDt;
        }

        int workedMinutes() {
            if (inDt == null) return 0;
            LocalDateTime end = (outDt != null) ? outDt : LocalDateTime.now(PH_ZONE);
            long mins = Duration.between(inDt, end).toMinutes();
            if (lunchOutDt != null && lunchInDt != null && !lunchInDt.isBefore(lunchOutDt)) {
                mins -= Duration.between(lunchOutDt, lunchInDt).toMinutes();
            }
            if (mins < 0) mins = 0;
            if (mins > 24L * 60L) mins = 24L * 60L;
            return (int) mins;
        }
    }

    private List<PortalHistoryRow> buildPortalRows(List<TimeLogDAO.ActivityRow> events, LocalDate from, LocalDate to) {
        java.util.Map<Integer, PortalHistoryRow> map = new java.util.LinkedHashMap<>();
        if (events == null) return new ArrayList<>();
        for (TimeLogDAO.ActivityRow r : events) {
            if (r == null) continue;
            LocalDateTime dt = parseHistoryDateTime(r.whenText);
            if (dt == null) continue;
            LocalDate d = dt.toLocalDate();
            if (from != null && d.isBefore(from)) continue;
            if (to != null && d.isAfter(to)) continue;
            int logId = r.logId > 0 ? r.logId : (dt.toString() + "|" + safe(r.action)).hashCode();
            PortalHistoryRow row = map.computeIfAbsent(logId, PortalHistoryRow::new);
            String action = safe(r.action).toUpperCase(Locale.ENGLISH);
            if (action.equals("TIME IN")) row.inDt = dt;
            else if (action.equals("LUNCH OUT")) row.lunchOutDt = dt;
            else if (action.equals("LUNCH IN")) row.lunchInDt = dt;
            else if (action.equals("TIME OUT")) row.outDt = dt;
        }
        List<PortalHistoryRow> rows = new ArrayList<>(map.values());
        rows.sort((a,b) -> {
            LocalDateTime da = a.sortKey();
            LocalDateTime db = b.sortKey();
            if (da == null && db == null) return 0;
            if (da == null) return 1;
            if (db == null) return -1;
            return db.compareTo(da);
        });
        return rows;
    }

    private static class DayAgg {
        final LocalDate date;
        final java.util.List<LocalTime> ins = new java.util.ArrayList<>();
        final java.util.List<LocalTime> outs = new java.util.ArrayList<>();

        DayAgg(LocalDate date) { this.date = date; }

        void accept(LocalDateTime dt, String action) {
            if (dt == null || action == null) return;
            String a = action.trim().toUpperCase(Locale.ENGLISH);
            LocalTime t = dt.toLocalTime();
            if (a.contains("IN")) ins.add(t);
            else if (a.contains("OUT")) outs.add(t);
        }

        void sort() {
            ins.sort(java.util.Comparator.naturalOrder());
            outs.sort(java.util.Comparator.naturalOrder());
        }
    }

private int computeWorkedMinutes(LocalDate from, LocalDate to) {
        if (from == null || to == null) return 0;
        List<PortalHistoryRow> rows = buildPortalRows(cachedRows, from, to);
        int total = 0;
        for (PortalHistoryRow r : rows) {
            if (r.inDt == null) continue;
            LocalDate d = r.inDt.toLocalDate();
            if (d.isBefore(from) || d.isAfter(to)) continue;
            total += r.workedMinutes();
        }
        return total;
    }

    private static String formatHm(int minutes) {
        if (minutes <= 0) return "0h 00m";
        int h = minutes / 60;
        int m = minutes % 60;
        return h + "h " + String.format("%02dm", m);
    }

    // DAO date strings may vary depending on earlier revisions.
    // Keep timezone logic intact (PH_ZONE); we only make parsing more tolerant
    // so "Today" / "This week" summaries don't show 0 when logs exist.
    private LocalDateTime parseHistoryDateTime(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;

        // 1) Primary (your original internal format)
        try {
            return LocalDateTime.parse(t, HISTORY_FMT);
        } catch (DateTimeParseException ignore) {}

        // 2) Human-readable formats often returned by older DAO versions
        DateTimeFormatter f1 = new java.time.format.DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern("MMM d, yyyy h:mm a")
                .toFormatter(Locale.ENGLISH);
        try {
            return LocalDateTime.parse(t, f1);
        } catch (DateTimeParseException ignore) {}

        DateTimeFormatter f2 = new java.time.format.DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern("MMM dd, yyyy h:mm a")
                .toFormatter(Locale.ENGLISH);
        try {
            return LocalDateTime.parse(t, f2);
        } catch (DateTimeParseException ignore) {}

        // 3) Common SQL timestamp string (fallback)
        try {
            return LocalDateTime.parse(t, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (DateTimeParseException ignore) {}

        return null;
    }

    // ================= Recent Activity: Pair IN/OUT like Admin Logbook =================

    private static final DateTimeFormatter ADMIN_LOG_DTF =
            new java.time.format.DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("yyyy-MM-dd hh:mm:ss a")
                    .toFormatter(Locale.ENGLISH);

    private static class PairRow {
        final LocalDateTime inDt;
        final LocalDateTime outDt;
        final String inText;
        final String outText;
        final String totalText;

        PairRow(LocalDateTime inDt, LocalDateTime outDt, String inText, String outText, String totalText) {
            this.inDt = inDt;
            this.outDt = outDt;
            this.inText = inText;
            this.outText = outText;
            this.totalText = totalText;
        }

        LocalDateTime sortKey() {
            return (outDt != null) ? outDt : inDt;
        }
    }

    private List<PairRow> buildPairedRows(List<TimeLogDAO.ActivityRow> events, LocalDate from, LocalDate to) {
        List<PairRow> out = new ArrayList<>();
        if (events == null || events.isEmpty()) return out;

        // IMPORTANT:
        // Admin-added "custom logs" are stored as a SINGLE DB row with BOTH time_in and time_out.
        // TimeLogDAO.getRecentActivity expands that single DB row into two "events" (IN + OUT) that share the same logId.
        // If we "pair" by chronological alternation, a long custom log (e.g., 9am-6pm) can be broken by other
        // IN/OUT events on the same day, producing OUT-without-IN and IN-without-OUT (your 24h bug).
        // So we pair strictly by logId.

        class PairAcc {
            LocalDateTime in;
            LocalDateTime out;
        }

        java.util.Map<Integer, PairAcc> byLog = new java.util.LinkedHashMap<>();

        for (TimeLogDAO.ActivityRow r : events) {
            if (r == null) continue;

            LocalDateTime dt = parseHistoryDateTime(r.whenText);
            if (dt == null) continue;

            LocalDate d = dt.toLocalDate();
            if (from != null && d.isBefore(from)) continue;
            if (to != null && d.isAfter(to)) continue;

            int logId = r.logId;
            // Some older rows may not include logId. Fallback to hashing datetime+action, but keep stable.
            if (logId <= 0) {
                logId = (dt.toLocalDate().toString() + "|" + dt.toLocalTime().toString() + "|" + safe(r.action)).hashCode();
            }

            PairAcc acc = byLog.computeIfAbsent(logId, k -> new PairAcc());

            String act = (r.action == null ? "" : r.action.trim().toUpperCase(Locale.ENGLISH));
            boolean isIn = act.contains("IN");
            boolean isOut = act.contains("OUT");

            if (isIn) {
                // Keep earliest IN for this logId (defensive)
                if (acc.in == null || dt.isBefore(acc.in)) acc.in = dt;
            } else if (isOut) {
                // Keep latest OUT for this logId (defensive)
                if (acc.out == null || dt.isAfter(acc.out)) acc.out = dt;
            }
        }

        // Build output list
        for (PairAcc acc : byLog.values()) {
            out.add(pair(acc.in, acc.out));
        }

        // Sort newest first by the best available timestamp (OUT if present else IN)
        out.sort((a, b) -> {
            LocalDateTime ta = a.sortKey();
            LocalDateTime tb = b.sortKey();
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            return tb.compareTo(ta);
        });

        return out;
    }

    private PairRow pair(LocalDateTime inDt, LocalDateTime outDt) {
        String inTxt = (inDt == null) ? "-" : inDt.format(ADMIN_LOG_DTF).toLowerCase(Locale.ENGLISH);
        String outTxt = (outDt == null) ? "-" : outDt.format(ADMIN_LOG_DTF).toLowerCase(Locale.ENGLISH);

        String total;
        if (inDt != null && outDt != null && !outDt.isBefore(inDt)) {
            long mins = Duration.between(inDt, outDt).toMinutes();
            total = formatHm((int) mins);
        } else if (inDt != null && outDt == null) {
            // open log -> show time from IN until now (same logic as admin view "Total Hours")
            long mins = Duration.between(inDt.atZone(PH_ZONE), ZonedDateTime.now(PH_ZONE)).toMinutes();
            if (mins < 0) mins = 0;
            // cap at 24h to avoid weird display if a log is left open for days
            if (mins > 24L * 60L) mins = 24L * 60L;
            total = formatHm((int) mins);
        } else {
            total = "0h 0m";
        }

        return new PairRow(inDt, outDt, inTxt, outTxt, total);
    }

    private static String formatHoursMinutes(int minutes) {
        if (minutes < 0) minutes = 0;
        int h = minutes / 60;
        int m = minutes % 60;
        return h + "h " + m + "m";
    }



    private static String formatTimesMultiline(java.util.List<LocalTime> times) {
        if (times == null || times.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(times.get(i).format(TIME_CELL_FMT).toLowerCase(Locale.ENGLISH));
        }
        return sb.toString();
    }

    private static int countLines(String s) {
        if (s == null || s.isEmpty()) return 1;
        int n = 1;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == '\n') n++;
        return n;
    }

    // ================= timers =================

    private void startTimers() {
        clockTimer = new javax.swing.Timer(1000, e -> updateClock());
        clockTimer.start();

        // Auto-refresh (must-do) – keep it
        refreshTimer = new javax.swing.Timer(8000, e -> refreshAll());
        refreshTimer.start();

        updateClock();
    }

    private String computePayPeriodText(LocalDate d) {
        if (d == null) return " ";
        if (isMonthly) {
            DateTimeFormatter m = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);
            return "Pay period: " + d.format(m);
        }
        int day = d.getDayOfMonth();
        LocalDate start;
        LocalDate end;
        if (day <= 15) {
            start = d.withDayOfMonth(1);
            end = d.withDayOfMonth(15);
        } else {
            start = d.withDayOfMonth(16);
            end = d.withDayOfMonth(d.lengthOfMonth());
        }
        DateTimeFormatter m = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);
        return "Pay period: " + start.format(m) + " " + start.getDayOfMonth() + "–" + end.getDayOfMonth();
    }

    private void updateClock() {
        ZonedDateTime now = ZonedDateTime.now(PH_ZONE);
        lblClock.setText(now.format(CLOCK_FMT));
        lblDate.setText(now.format(DATE_FMT));
        lblPayPeriod.setText(computePayPeriodText(now.toLocalDate()));
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        if (clockTimer != null) clockTimer.stop();
        if (refreshTimer != null) refreshTimer.stop();
    }

    // ================= helpers =================

    private static String safe(String s) {
        return (s == null) ? "" : s.trim();
    }

    private static String fullName(Employee e) {
        if (e == null) return "-";
        String fn = safe(e.getFirstName());
        String mn = safe(e.getMiddleName());
        String ln = safe(e.getLastName());
        String name = (fn + " " + (mn.isEmpty() ? "" : (mn + " ")) + ln).trim();
        return name.isEmpty() ? "-" : name;
    }

    private static int resolveDbEmpId(Employee e) {
        if (e == null) return -1;
        try {
            int id = e.getEmpId();
            return id > 0 ? id : -1;
        } catch (Exception ex) {
            return -1;
        }
    }

    // ================= UI components =================

    private static class ModernCard extends JPanel {
        private final int arc;

        ModernCard(int arc) {
            this.arc = arc;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // shadow
            g2.setColor(new Color(0, 0, 0, 18));
            g2.fillRoundRect(3, 4, w - 6, h - 6, arc, arc);

            // card
            g2.setColor(CARD);
            g2.fillRoundRect(0, 0, w - 6, h - 6, arc, arc);

            // border
            g2.setColor(LINE);
            g2.drawRoundRect(0, 0, w - 6, h - 6, arc, arc);

            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        public Insets getInsets() {
            Insets in = super.getInsets();
            return new Insets(in.top, in.left, in.bottom + 6, in.right + 6);
        }
    }

    /**
     * Modern zebra renderer for JTable cells.
     * - Alternating row backgrounds
     * - Center alignment (as in your screenshot layout)
     * - Extra left/right padding
     * - Keeps selection color readable
     */
    private static class ZebraCellRenderer extends DefaultTableCellRenderer {
        private final int alignment;

        ZebraCellRenderer(int alignment) {
            this.alignment = alignment;
            setHorizontalAlignment(alignment);
            setOpaque(true);
            setBorder(new EmptyBorder(0, 10, 0, 10));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                      boolean isSelected, boolean hasFocus,
                                                      int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            // Always keep consistent font & alignment
            setHorizontalAlignment(alignment);

            if (isSelected) {
                // Use the table selection background already set above
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground() != null ? table.getSelectionForeground() : new Color(0x0B3B3A));
            } else {
                // Zebra effect
                Color even = CARD;
                Color odd  = new Color(0xF6FBFA); // soft mint tint
                setBackground((row % 2 == 0) ? even : odd);
                setForeground(TEXT);
            }

            return this;
        }
    }

    
    // Date column renderer (supports LocalDate and LocalDateTime)
    private static class DateTwoLineRenderer extends DefaultTableCellRenderer {
        private final DateTimeFormatter top = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
        private final DateTimeFormatter time = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            JLabel lbl = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            lbl.setHorizontalAlignment(SwingConstants.CENTER);
            lbl.setVerticalAlignment(SwingConstants.CENTER);
            lbl.setBorder(new EmptyBorder(6, 8, 6, 8));
            lbl.setOpaque(true);

            LocalDate d = null;
            LocalTime t = null;

            if (value instanceof LocalDate) {
                d = (LocalDate) value;
            } else if (value instanceof LocalDateTime) {
                LocalDateTime dt = (LocalDateTime) value;
                d = dt.toLocalDate();
                t = dt.toLocalTime();
            }

            if (d == null) {
                lbl.setText(" ");
            } else if (t != null) {
                lbl.setText("<html><div style='text-align:center;'><b>" + d.format(top) + "</b><br/>" + t.format(time).toLowerCase(Locale.ENGLISH) + "</div></html>");
            } else {
                lbl.setText("<html><div style='text-align:center;'><b>" + d.format(top) + "</b><br/>&nbsp;</div></html>");
            }
            return lbl;
        }
    }

    // Time pill renderer for Time In / Time Out columns
    private static class TimePillRenderer implements TableCellRenderer {
        private final boolean isIn;
        private final JLabel pill = new JLabel("", SwingConstants.CENTER);

        private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

        TimePillRenderer(boolean isIn) {
            this.isIn = isIn;
            pill.setOpaque(true);
            pill.setFont(new Font("SansSerif", Font.BOLD, 12));
            pill.setBorder(new EmptyBorder(6, 14, 6, 14));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            if (value == null) {
                pill.setText(" ");
                pill.setBackground(new Color(0,0,0,0));
                return pill;
            }

            LocalTime t = null;
            if (value instanceof LocalTime) t = (LocalTime) value;
            else if (value instanceof String) {
                try { t = LocalTime.parse(value.toString().trim()); } catch (Exception ignore) {}
            }

            String txt = (t == null) ? value.toString() : t.format(fmt).toLowerCase(Locale.ENGLISH);
            pill.setText(txt);

            if (isIn) {
                pill.setBackground(OK_BG);
                pill.setForeground(OK_FG);
            } else {
                pill.setBackground(OUT_BG);
                pill.setForeground(OUT_FG);
            }

            return pill;
        }
    }

private static class DateTimeTwoLineRenderer extends DefaultTableCellRenderer {
        private final DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy");
        private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("h:mm a");

        public DateTimeTwoLineRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setVerticalAlignment(SwingConstants.CENTER);
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            LocalDateTime dt = null;
            if (value instanceof LocalDateTime) {
                dt = (LocalDateTime) value;
            } else if (value != null) {
                dt = tryParse(value.toString());
            }

            if (dt != null) {
                setText("<html><div style='text-align:center;'>" +
                        dt.format(dateFmt) + "<br/>" +
                        dt.format(timeFmt) +
                        "</div></html>");
            } else {
                setText(value == null ? "" : value.toString());
            }

            if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground() != null ? table.getSelectionForeground() : new Color(0x0B3B3A));
            } else {
                setBackground(row % 2 == 0 ? CARD : new Color(0xF5FAFA));
                setForeground(TEXT);
            }
            return this;
        }

        private LocalDateTime tryParse(String s) {
            try {
                return LocalDateTime.parse(s, HISTORY_FMT);
            } catch (Exception ignore) {
                return null;
            }
        }
    }

    
    private static class DateOnlyRenderer extends DefaultTableCellRenderer {
        private final DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

        public DateOnlyRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setVerticalAlignment(SwingConstants.CENTER);
            setOpaque(true);
            setBorder(new EmptyBorder(0, 10, 0, 10));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                      boolean isSelected, boolean hasFocus,
                                                      int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            LocalDate d = null;
            if (value instanceof LocalDate) d = (LocalDate) value;
            else if (value instanceof LocalDateTime) d = ((LocalDateTime) value).toLocalDate();

            if (d != null) {
                setText("<html><div style='text-align:center; font-weight:600;'>" + d.format(dateFmt) + "</div></html>");
            } else {
                setText(value == null ? "" : value.toString());
            }

            if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground() != null ? table.getSelectionForeground() : new Color(0x0B3B3A));
            } else {
                setBackground((row % 2 == 0) ? CARD : new Color(0xF6FBFA));
                setForeground(TEXT);
            }
            return this;
        }
    }

    private static class TimeMultiLineRenderer extends DefaultTableCellRenderer {
        private final boolean isInColumn;

        TimeMultiLineRenderer(boolean isInColumn) {
            this.isInColumn = isInColumn;
            setHorizontalAlignment(SwingConstants.CENTER);
            setVerticalAlignment(SwingConstants.CENTER);
            setOpaque(true);
            setBorder(new EmptyBorder(6, 10, 6, 10));
            setFont(new Font("SansSerif", Font.BOLD, 12));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                      boolean isSelected, boolean hasFocus,
                                                      int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            String txt = value == null ? "" : value.toString().trim();
            if (txt.isEmpty()) {
                setText("");
                if (isSelected) {
                    setBackground(table.getSelectionBackground());
                    setForeground(table.getSelectionForeground() != null ? table.getSelectionForeground() : new Color(0x0B3B3A));
                } else {
                    setBackground((row % 2 == 0) ? CARD : new Color(0xF6FBFA));
                    setForeground(TEXT);
                }
                return this;
            }

            // Convert \n to <br> so multi-times show like a mini schedule
            String html = "<html><div style='text-align:center; line-height:1.3;'>" + txt.replace("\n", "<br/>") + "</div></html>";
            setText(html);

            if (isInColumn) {
                setBackground(OK_BG);
                setForeground(OK_FG);
            } else {
                setBackground(OUT_BG);
                setForeground(OUT_FG);
            }
            return this;
        }
    }

private static class ActionPillRenderer implements TableCellRenderer {
        private final JLabel pill = new JLabel("", SwingConstants.CENTER);

        public ActionPillRenderer() {
            pill.setOpaque(true);
            pill.setFont(new Font("SansSerif", Font.BOLD, 11));
            pill.setBorder(new EmptyBorder(6, 12, 6, 12));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                      boolean isSelected, boolean hasFocus,
                                                      int row, int column) {
            String action = value == null ? "" : value.toString().trim().toUpperCase();
            pill.setText(action);

            boolean isIn = action.contains("IN");
            if (isIn) {
                pill.setBackground(new Color(0xEAF7F6));
                pill.setForeground(new Color(0x0F766E));
            } else {
                pill.setBackground(new Color(0xEEF2FF));
                pill.setForeground(new Color(0x1D4ED8));
            }
            pill.putClientProperty(FlatClientProperties.STYLE, "arc: 999");

            JPanel wrap = new JPanel(new GridBagLayout());
            wrap.setOpaque(true);
            wrap.setBackground(isSelected ? table.getSelectionBackground()
                    : (row % 2 == 0 ? CARD : new Color(0xF5FAFA)));
            wrap.add(pill);
            return wrap;
        }
    }



    // ================= Renderers for Admin Logbook style =================

    private static class DateTimeCellRenderer extends DefaultTableCellRenderer {
        private final Color fg;

        DateTimeCellRenderer(Color fg) {
            this.fg = fg;
            setHorizontalAlignment(SwingConstants.CENTER);
            setOpaque(true);
            setBorder(new EmptyBorder(0, 10, 0, 10));
            setFont(new Font("SansSerif", Font.PLAIN, 12));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                      boolean isSelected, boolean hasFocus,
                                                      int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            String s = value == null ? "" : value.toString();
            setText(s);

            if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground() != null ? table.getSelectionForeground() : new Color(0x0B3B3A));
            } else {
                setBackground(row % 2 == 0 ? CARD : new Color(0xF6FBFA));
                setForeground(s.equals("-") || s.isEmpty() ? SUB : fg);
            }
            return this;
        }
    }

    private static class TotalHoursRenderer extends DefaultTableCellRenderer {
        TotalHoursRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setOpaque(true);
            setBorder(new EmptyBorder(0, 10, 0, 10));
            setFont(new Font("SansSerif", Font.BOLD, 12));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                      boolean isSelected, boolean hasFocus,
                                                      int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            String s = value == null ? "" : value.toString();
            setText(s);

            if (isSelected) {
                setBackground(table.getSelectionBackground());
                setForeground(table.getSelectionForeground() != null ? table.getSelectionForeground() : new Color(0x0B3B3A));
            } else {
                setBackground(row % 2 == 0 ? CARD : new Color(0xF6FBFA));
                setForeground(new Color(0xB45309)); // ORANGE
            }
            return this;
        }
    }


}
