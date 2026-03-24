package ui;
import util.AppConstants;

import dao.EmployeeDAO;
import dao.HolidayDAO;
import dao.PayrollDAO;
import dao.PayslipPublishDAO;
import dao.TimeLogDAO;
import model.Employee;
import model.Payslip;
import model.WorkSummary;
import service.PayrollCalculator;
import util.HoursUtil;
import util.HolidayPresetsPH;
import util.Money;
import util.PDFUtil;
import util.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.ItemEvent;
import java.io.File;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public class PayrollRunDialog extends JDialog {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DecimalFormat DF2 = new DecimalFormat("0.##");

    private final JComboBox<EmployeeItem> cbEmp = new JComboBox<>();

    // ===== NEW SECTION FIELDS =====
    // Regular day
    private final JTextField tfRegDayHours = new JTextField("00:00");
    private final JTextField tfRegDayOT = new JTextField("00:00");

    // Holidays
    private final JTextField tfRegHolHours = new JTextField("00:00");  // NON-OT
    private final JTextField tfRegHolOT = new JTextField("00:00");

    private final JTextField tfSpecHolHours = new JTextField("00:00"); // NON-OT
    private final JTextField tfSpecHolOT = new JTextField("00:00");

    // Totals (as you requested)
    // Total Hours = ALL non-OT hours (regular + holidays), EXCLUDING OT
    private final JTextField tfTotalHours = new JTextField("00:00");
    // Total OT Hours = ALL OT (regular + holidays)
    private final JTextField tfTotalOT = new JTextField("00:00");

    // ✅ auto-fill controls
    private final JCheckBox chkAutoFromLogs = new JCheckBox("Auto-fill hours from Time Logs");
    private final JButton btnRecalcFromLogs = new JButton("Recalculate");

    // If user edits fields manually while auto is ON, we stop auto-overwriting until Recalculate.
    private boolean manualOverride = false;
    private boolean suppressManualFlag = false;

    // Background worker to prevent UI freeze on Azure when auto-filling from time logs
    private transient javax.swing.SwingWorker<WorkSummary, Void> autoFillWorker;

    private final JLabel lblHolidayCount = new JLabel("Holidays in this period: -");
    private final JButton btnReviewHolidays = new JButton("Holidays");
    private final HolidayDAO holidayDAO = new HolidayDAO();

    // Keep references so we can enable/disable holiday UI for MONTHLY employees (holidays excluded)
    private JComponent dpRegHolHours;
    private JComponent dpRegHolOT;
    private JComponent dpSpecHolHours;
    private JComponent dpSpecHolOT;

    private final JTextField tfStart = new JTextField();
    private final JTextField tfEnd = new JTextField();
    private final JComboBox<PeriodItem> cbPeriod = new JComboBox<>();
    private final JLabel lblPayPeriod = new JLabel("Pay Period:");

    // ✅ Admin toggle: enable/disable OT pay computation
    // When OFF: OT hours are forced to 0 and NOT paid.
    private final JCheckBox chkEnableOT = new JCheckBox("Enable OT pay");

    private final JTextField tfOTMultiplier = new JTextField("1.25");
    private final JTextField tfOTRateOverride = new JTextField("");
    private final JTextField tfDeductions = new JTextField("0");
    private final JTextField tfAdditionalEarnings = new JTextField("0");

    // ✅ Deductions Options UI
    private final JButton btnDeductionOptions = new JButton("Options");
    private final JButton btnEarningsOptions = new JButton("Earnings Options");
    private JPopupMenu deductionsPopup;
    private JPanel deductionsPanel;
    private JPopupMenu earningsPopup;
    private JPanel earningsPanel;

    // ✅ Deductions state (used for preview breakdown)
    private boolean useCash = false, useSmart = false, useOthers = false;
    private String cashAmt = "0", smartAmt = "0", othersAmt = "0";
    private String cashNote = "";
    private String othersNote = "";

    // ✅ Statutory deductions (PH) state
    private boolean useSSS = false, usePagibig = false, usePhilHealth = false;
    private String sssAmt = "0", pagibigAmt = "0", philhealthAmt = "0";

    // ✅ Additional earnings state
    private boolean useIncentives = false, useThirteenthMonth = false, useBonus = false;
    private String incentivesAmt = "0", thirteenthMonthAmt = "0", bonusAmt = "0";

    // ✅ PREVIEW NOW = HTML (modern)
    private final JEditorPane preview = new JEditorPane();
    private Payslip lastPayslip;
    private int runId = 0;

    public PayrollRunDialog(Frame owner) {
        super(owner, AppConstants.APP_TITLE, true);

        setResizable(true);
        setPreferredSize(new Dimension(1200, 720));

        loadEmployees();

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(Theme.BG);
        root.setBorder(new EmptyBorder(10, 10, 10, 10));
        setContentPane(root);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(buildSummaryCard());
        left.add(Box.createVerticalStrut(10));
        left.add(buildRunPayrollCard());

        JPanel right = buildPreviewCard();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setBorder(null);
        split.setDividerSize(8);
        split.setResizeWeight(0.62);
        split.setContinuousLayout(true);

        root.add(split, BorderLayout.CENTER);

        initPeriodCombo();
        setPeriodToToday();
        syncDatesFromPeriod();
        tfStart.setEditable(false);
        tfEnd.setEditable(false);

        applyThemeStyles();
        forceComboPillLook();
        applyOtEnabledState();
        // Ensure holiday controls reflect the initially-selected employee.
        setHolidayUiEnabled(!isSelectedEmployeeMonthly());
        refreshHolidayCount();

        btnReviewHolidays.addActionListener(e -> openHolidayReview());

        // ✅ Auto-fill wiring
        chkAutoFromLogs.setOpaque(false);
        chkAutoFromLogs.setSelected(true);
        chkAutoFromLogs.setForeground(Theme.TEXT);
        chkAutoFromLogs.setFont(chkAutoFromLogs.getFont().deriveFont(Font.PLAIN, 12f));

        // ✅ OT enable/disable (admin control)
        chkEnableOT.setOpaque(false);
        chkEnableOT.setSelected(true);
        chkEnableOT.setForeground(Theme.TEXT);
        chkEnableOT.setFont(chkEnableOT.getFont().deriveFont(Font.PLAIN, 12f));
        chkEnableOT.addActionListener(e -> applyOtEnabledState());

        // manual override listeners
        for (JTextField f : new JTextField[]{
                tfRegDayHours, tfRegDayOT,
                tfRegHolHours, tfRegHolOT,
                tfSpecHolHours, tfSpecHolOT,
                tfTotalHours, tfTotalOT
        }) {
            attachManualOverride(f);
            attachDurationFormatter(f);
        }

        btnRecalcFromLogs.addActionListener(e -> {
            manualOverride = false;
            autoFillFromTimeLogs(true);
        });

        chkAutoFromLogs.addActionListener(e -> {
            if (chkAutoFromLogs.isSelected()) {
                if (!manualOverride) autoFillFromTimeLogs(false);
            }
        });

        cbEmp.addActionListener(e -> {
            resetPayrollInputsForSelectedEmployee();

            // Rebuild pay periods based on selected employee's pay type.
            initPeriodCombo();
            setPeriodToToday();
            syncDatesFromPeriod();

            // MONTHLY employees: holidays excluded -> disable holiday UI
            setHolidayUiEnabled(!isSelectedEmployeeMonthly());

            refreshHolidayCount();
            manualOverride = false;
            if (chkAutoFromLogs.isSelected()) autoFillFromTimeLogs(false);
        });

        cbPeriod.addActionListener(e -> {
            syncDatesFromPeriod();
            refreshHolidayCount();
            if (chkAutoFromLogs.isSelected() && !manualOverride) autoFillFromTimeLogs(false);
        });

        // ✅ Deductions popup wiring
        buildDeductionsPopup();
        buildEarningsPopup();
        btnDeductionOptions.addActionListener(e -> showDeductionsPopup());
        btnEarningsOptions.addActionListener(e -> showEarningsPopup());

        tfDeductions.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { showDeductionsPopup(); }
        });
        tfAdditionalEarnings.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { showEarningsPopup(); }
        });
        
        PayrollTabModernizer.apply(root);
        pack();
        setLocationRelativeTo(owner);

        SwingUtilities.invokeLater(() -> {
            split.setDividerLocation(0.62);
            if (chkAutoFromLogs.isSelected()) autoFillFromTimeLogs(false);
            applyOtEnabledState();
        });
    }

    /**
     * When OT is disabled, we force all OT values to 0 and disable OT-related inputs.
     * This ensures hours beyond 8/day are NOT computed/paid as overtime.
     */
    private void applyOtEnabledState() {
        boolean enabled = chkEnableOT.isSelected();

        // Disable OT configuration fields
        tfOTMultiplier.setEnabled(enabled);
        tfOTRateOverride.setEnabled(enabled);

        // Disable manual editing for OT hour fields (still show values)
        tfRegDayOT.setEditable(enabled);
        tfRegHolOT.setEditable(enabled);
        tfSpecHolOT.setEditable(enabled);
        tfTotalOT.setEditable(enabled);

        if (!enabled) {
            // Force OT values to 0 visually (but keep non-OT hours intact)
            suppressManualFlag = true;
            tfRegDayOT.setText("00:00");
            tfRegHolOT.setText("00:00");
            tfSpecHolOT.setText("00:00");
            tfTotalOT.setText("00:00");
            suppressManualFlag = false;
        }
    }

    // ================= SUMMARY CARD =================
    private JPanel buildSummaryCard() {
        JPanel card = cardPanel();
        card.setLayout(new BorderLayout(8, 8));
        card.add(sectionTitle("Work Summary (whole pay period)"), BorderLayout.NORTH);

        JPanel wrap = new JPanel(new GridBagLayout());
        wrap.setOpaque(false);

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.NORTHWEST;

        int r = 0;

        // Employee row
        g.gridx = 0; g.gridy = r; g.weightx = 0;
        wrap.add(label("Employee:"), g);
        g.gridx = 1; g.gridwidth = 3; g.weightx = 1;
        wrap.add(cbEmp, g);
        g.gridwidth = 1;
        r++;
        // Auto-fill + Recalculate moved beside Review/Add Holiday (to maximize space)
        Theme.styleSecondaryButton(btnRecalcFromLogs);
        makeButton(btnRecalcFromLogs, 34);
// ===== Two-column compact layout (fits all your sections) =====
        // Left col = Regular Day + Total
        // Right col = Holiday (Regular + Special)
        g.gridx = 0; g.gridy = r; g.gridwidth = 4; g.weightx = 1;
        wrap.add(buildHoursSectionsPanel(), g);
        g.gridwidth = 1;
        r++;

        // Holidays line
        lblHolidayCount.setForeground(Theme.MUTED);
        lblPayPeriod.setForeground(Theme.TEXT);
        lblPayPeriod.setFont(lblPayPeriod.getFont().deriveFont(Font.PLAIN, 12f));
        lblHolidayCount.setFont(lblHolidayCount.getFont().deriveFont(Font.PLAIN, 11.2f));
        Theme.styleSecondaryButton(btnReviewHolidays);
        makeButton(btnReviewHolidays, 34);

        JPanel holLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        holLine.setOpaque(false);
        holLine.add(lblHolidayCount);
        holLine.add(btnReviewHolidays);

        
        holLine.add(Box.createHorizontalStrut(12));
        holLine.add(chkAutoFromLogs);
        holLine.add(btnRecalcFromLogs);
g.gridx = 0; g.gridy = r;
        wrap.add(new JLabel(""), g);
        g.gridx = 1; g.gridwidth = 3;
        wrap.add(holLine, g);
        g.gridwidth = 1;

        card.add(wrap, BorderLayout.CENTER);

        JButton clear = new JButton("Clear Preview");
        Theme.styleOrangeButton(clear);
        makeButton(clear, 34);
        clear.addActionListener(e -> {
            preview.setText("");
            lastPayslip = null;
        });

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        bottom.setOpaque(false);
        bottom.add(chkEnableOT);
        bottom.add(clear);

        card.add(bottom, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildHoursSectionsPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(2, 2, 2, 2);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.NORTHWEST;

        // Two halves (your request: "top left part half so we can fit all")
        JPanel left = miniCard("Regular Day + Totals");
        left.setLayout(new GridBagLayout());

        JPanel right = miniCard("Holiday Breakdown");
        right.setLayout(new GridBagLayout());

        // ---- LEFT (regular day + totals) ----
        addMiniRow(left, 0, "Regular Day Hours:", makeDurationPicker(tfRegDayHours));
        addMiniRow(left, 1, "Regular Day OT:", makeDurationPicker(tfRegDayOT));

        addMiniDivider(left, 2);

        addMiniRow(left, 3, "Total Hours:", makeDurationPicker(tfTotalHours));
        addMiniRow(left, 4, "Total OT Hours:", makeDurationPicker(tfTotalOT));

        // ---- RIGHT (holidays) ----
        dpRegHolHours = makeDurationPicker(tfRegHolHours);
        addMiniRow(right, 0, "Regular Holiday Hours:", dpRegHolHours);
        dpRegHolOT = makeDurationPicker(tfRegHolOT);
        addMiniRow(right, 1, "Regular Holiday OT:", dpRegHolOT);

        addMiniDivider(right, 2);

        dpSpecHolHours = makeDurationPicker(tfSpecHolHours);
        addMiniRow(right, 3, "Special Holiday Hours:", dpSpecHolHours);
        dpSpecHolOT = makeDurationPicker(tfSpecHolOT);
        addMiniRow(right, 4, "Special Holiday OT:", dpSpecHolOT);

        // Place halves
        g.gridx = 0; g.gridy = 0; g.weightx = 0.5; g.gridwidth = 1;
        p.add(left, g);

        g.gridx = 1; g.gridy = 0; g.weightx = 0.5;
        p.add(right, g);

        return p;
    }

    private JPanel miniCard(String title) {
        JPanel c = new JPanel();
        c.setOpaque(true);
        c.setBackground(new Color(0xFFFFFF));
        c.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true),
                new EmptyBorder(8, 8, 8, 8)
        ));
        c.setLayout(new BorderLayout(6, 6));

        JLabel t = new JLabel(title);
        t.setForeground(Theme.PRIMARY);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 12f));
        c.add(t, BorderLayout.NORTH);

        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);
        c.add(body, BorderLayout.CENTER);

        return c;
    }

    private void addMiniRow(JPanel miniCard, int row, String label, JComponent comp) {
        JPanel body = (JPanel) miniCard.getComponent(1);

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(3, 0, 3, 0);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.WEST;

        g.gridx = 0; g.gridy = row; g.weightx = 0;
        JLabel l = new JLabel(label);
        l.setForeground(Theme.TEXT);
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 12f));
        body.add(l, g);

        g.gridx = 1; g.weightx = 1;
        body.add(comp, g);

        // Make textfields “exact size”: slightly smaller width, consistent height
        if (comp instanceof JTextField) {
            ((JTextField) comp).setColumns(10);
        }
    }

    private void addMiniDivider(JPanel miniCard, int row) {
        JPanel body = (JPanel) miniCard.getComponent(1);
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.gridy = row; g.gridwidth = 2;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(6, 0, 6, 0);
        JSeparator sep = new JSeparator();
        sep.setForeground(Theme.BORDER);
        body.add(sep, g);
    }

    private JPanel buildRunPayrollCard() {
        JPanel card = cardPanel();
        card.setLayout(new BorderLayout(8, 8));
        // card title removed to save vertical space

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        // More bottom padding so buttons never clip (especially on 125%/150% scaling)
        form.setBorder(new EmptyBorder(4, 6, 22, 6));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 10, 6, 10);
        g.gridy = 0;

        java.util.function.Consumer<GridBagConstraints> asLabel = (c) -> {
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            c.anchor = GridBagConstraints.EAST;
        };

        java.util.function.Consumer<GridBagConstraints> asField = (c) -> {
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.anchor = GridBagConstraints.WEST;
        };

        // ---- Row 0
        g.gridx = 0; asLabel.accept(g);
        form.add(lblPayPeriod, g);

        g.gridx = 1; asField.accept(g);
        g.gridwidth = 3;
        form.add(cbPeriod, g);
        g.gridwidth = 1;

        // ---- Row 1
        g.gridy++;
        g.gridx = 0; asLabel.accept(g);
        form.add(label("OT Multiplier:"), g);

        g.gridx = 1; asField.accept(g);
        form.add(tfOTMultiplier, g);

        g.gridx = 2; asLabel.accept(g);
        form.add(label("OT Rate Override:"), g);

        g.gridx = 3; asField.accept(g);
        form.add(tfOTRateOverride, g);

        // ---- Row 2
        g.gridy++;

        g.gridx = 0; asLabel.accept(g);
        form.add(label("Deductions (₱):"), g);

        g.gridx = 1; asField.accept(g);
        form.add(tfDeductions, g);

        g.gridx = 2; asLabel.accept(g);
        form.add(label("Additional Earnings (₱):"), g);

        g.gridx = 3; asField.accept(g);
        form.add(tfAdditionalEarnings, g);

        JButton btnCompute = new JButton("Compute Payslip");
        JButton btnPDF = new JButton("Preview PDF");
        JButton btnSend = new JButton("Upload Payslip");

        Theme.stylePrimaryButton(btnCompute);
        Theme.styleSecondaryButton(btnPDF);
        Theme.styleSecondaryButton(btnSend);

        // Slightly shorter so it won't clip anywhere
        makeButton(btnCompute, 36);
        makeButton(btnPDF, 36);
        makeButton(btnSend, 36);

        btnCompute.addActionListener(e -> computePayslip());
        btnPDF.addActionListener(e -> generatePDF());
        btnSend.addActionListener(e -> publishPayslip());

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnRow.setOpaque(false);
        // Tiny padding inside the row so the bottom of buttons never touches the edge
        btnRow.setBorder(new EmptyBorder(2, 0, 4, 0));
        btnRow.add(btnCompute);
        btnRow.add(btnPDF);
        btnRow.add(btnSend);

        // ---- Row 3 (Options + Buttons)
        g.gridy++;
        g.insets = new Insets(6, 10, 16, 10);

        // Options buttons under the Deductions / Additional Earnings row
        JPanel optionsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        optionsRow.setOpaque(false);
        optionsRow.add(btnDeductionOptions);
        optionsRow.add(btnEarningsOptions);

        g.gridx = 1;
        g.gridwidth = 2;
        g.weightx = 0;
        g.fill = GridBagConstraints.NONE;
        g.anchor = GridBagConstraints.WEST;
        form.add(optionsRow, g);
        g.gridwidth = 1;

        // Buttons aligned to the right
        g.gridx = 3;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.EAST;
        form.add(btnRow, g);

        // ---- Spacer absorber
        g.gridy++;
        g.gridx = 0;
        g.gridwidth = 4;
        g.weightx = 1;
        g.weighty = 1;
        g.fill = GridBagConstraints.BOTH;
        g.insets = new Insets(0, 0, 0, 0);
        form.add(Box.createGlue(), g);

        card.add(form, BorderLayout.CENTER);
        return card;
    }


    // ================= PREVIEW =================
    private JPanel buildPreviewCard() {
        JPanel card = cardPanel();
        card.setLayout(new BorderLayout(8, 8));
        card.add(sectionTitle("Computed Payslip Preview"), BorderLayout.NORTH);

        preview.setContentType("text/html");
        preview.setEditable(false);
        preview.setOpaque(true);
        preview.setBackground(Color.WHITE);
        preview.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);

        JScrollPane sp = new JScrollPane(preview);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(16);

        card.add(sp, BorderLayout.CENTER);
        return card;
    }

    // ================= AUTO-FILL FROM LOGS =================
    private void autoFillFromTimeLogs(boolean force) {
        if (!chkAutoFromLogs.isSelected()) return;
        if (!force && manualOverride) return;

        EmployeeItem item = (EmployeeItem) cbEmp.getSelectedItem();
        if (item == null) return;

        if (tfStart.getText().trim().isEmpty() || tfEnd.getText().trim().isEmpty()) {
            syncDatesFromPeriod();
        }

        // Cancel previous run if user changes employee/period quickly
        if (autoFillWorker != null && !autoFillWorker.isDone()) {
            autoFillWorker.cancel(true);
        }

        final LocalDate s;
        final LocalDate e;
        try {
            s = LocalDate.parse(tfStart.getText().trim(), ISO);
            e = LocalDate.parse(tfEnd.getText().trim(), ISO);
        } catch (Exception parseEx) {
            if (force) {
                JOptionPane.showMessageDialog(this,
                        "Invalid period dates.",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
            return;
        }

        // Small UX: show busy cursor while computing
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        btnRecalcFromLogs.setEnabled(false);
        cbEmp.setEnabled(false);
        cbPeriod.setEnabled(false);

        autoFillWorker = new javax.swing.SwingWorker<WorkSummary, Void>() {
            @Override
            protected WorkSummary doInBackground() throws Exception {
                return TimeLogDAO.computeWorkSummary(item.empId, s, e);
            }

            @Override
            protected void done() {
                try {
                    if (isCancelled()) return;
                    WorkSummary ws = get();
                    if (ws == null) return;
                    // ✅ Apply holiday rules for auto-filled hours
                    // 1) If employee is MONTHLY: holidays are excluded (no holiday premium pay)
                    //    -> move ALL holiday buckets into Regular Day so hours are not lost.
                    // 2) If holiday type is not enabled in this period: treat its bucket as Regular Day (hours not lost).
                    try {
                        Employee emp = EmployeeDAO.getById(item.empId);
                        boolean isMonthly = (emp != null && emp.payType != null
                                && emp.payType.trim().equalsIgnoreCase("MONTHLY"));

                        boolean hasRegEnabled = false;
                        boolean hasSpecEnabled = false;

                        if (!isMonthly) {
                            List<model.Holiday> enabledHols = holidayDAO.listEnabledBetween(s, e);
                            for (model.Holiday h : enabledHols) {
                                if (h == null || h.type == null) continue;
                                String t = h.type.trim().toUpperCase();
                                if ("REGULAR".equals(t)) hasRegEnabled = true;
                                else if (t.startsWith("SPECIAL")) hasSpecEnabled = true;
                            }
                        }

                        if (isMonthly || !hasRegEnabled) {
                            ws.regularDayHours += ws.regularHolidayHours;
                            ws.regularDayOtHours += ws.regularHolidayOtHours;
                            ws.regularHolidayHours = 0;
                            ws.regularHolidayOtHours = 0;
                        }
                        if (isMonthly || !hasSpecEnabled) {
                            ws.regularDayHours += ws.specialHolidayHours;
                            ws.regularDayOtHours += ws.specialHolidayOtHours;
                            ws.specialHolidayHours = 0;
                            ws.specialHolidayOtHours = 0;
                        }

                        // Recompute totals (non-OT hours includes regular+holidays; OT includes all OT)
                        ws.totalHours = ws.regularDayHours + ws.regularHolidayHours + ws.specialHolidayHours;
                        ws.otHours = ws.regularDayOtHours + ws.regularHolidayOtHours + ws.specialHolidayOtHours;
                    } catch (Exception ignore) {
                        // keep auto-fill resilient; if holiday lookup fails, we still fill hours
                    }

                    suppressManualFlag = true;

                    // set breakdown
                    tfRegDayHours.setText(formatColon(ws.regularDayHours));
                    tfRegDayOT.setText(formatColon(ws.regularDayOtHours));

                    tfRegHolHours.setText(formatColon(ws.regularHolidayHours));
                    tfRegHolOT.setText(formatColon(ws.regularHolidayOtHours));

                    tfSpecHolHours.setText(formatColon(ws.specialHolidayHours));
                    tfSpecHolOT.setText(formatColon(ws.specialHolidayOtHours));

                    // totals follow your rule
                    tfTotalHours.setText(formatColon(ws.totalHours));
                    if (chkEnableOT.isSelected()) {
                        tfTotalOT.setText(formatColon(ws.otHours));
                    } else {
                        // OT disabled => force OT to zero
                        tfRegDayOT.setText("00:00");
                        tfRegHolOT.setText("00:00");
                        tfSpecHolOT.setText("00:00");
                        tfTotalOT.setText("00:00");
                    }

                } catch (Exception ex) {
                    ex.printStackTrace();
                    if (force) {
                        JOptionPane.showMessageDialog(PayrollRunDialog.this,
                                "Auto-fill error:\n" + ex.getMessage(),
                                "Error",
                                JOptionPane.ERROR_MESSAGE);
                    }
                } finally {
                    suppressManualFlag = false;
                    btnRecalcFromLogs.setEnabled(true);
                    cbEmp.setEnabled(true);
                    cbPeriod.setEnabled(true);
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        };

        autoFillWorker.execute();
    }

    private void attachManualOverride(JTextField field) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            private void mark() {
                if (suppressManualFlag) return;
                if (chkAutoFromLogs.isSelected()) manualOverride = true;
            }
            @Override public void insertUpdate(DocumentEvent e) { mark(); }
            @Override public void removeUpdate(DocumentEvent e) { mark(); }
            @Override public void changedUpdate(DocumentEvent e) { mark(); }
        });
    }

    private void attachDurationFormatter(JTextField field) {
    // Restrict typing to digits/colon and keep it duration-like.
    if (field.getDocument() instanceof AbstractDocument) {
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DurationDocumentFilter());
    }

    // Enter => next field (auto-enter feel)
    field.addActionListener(e -> field.transferFocus());

    field.addFocusListener(new java.awt.event.FocusAdapter() {
        @Override public void focusGained(java.awt.event.FocusEvent e) {
            SwingUtilities.invokeLater(field::selectAll);
        }
        @Override public void focusLost(java.awt.event.FocusEvent e) {
            suppressManualFlag = true;
            try {
                field.setText(normalizeDuration(field.getText())); // normalize to HH:MM
                recomputeTotalsFromBreakdown();
            } finally {
                suppressManualFlag = false;
            }
        }
    });
}

/**
 * Converts any supported input ("8h 30m", "8:30", "8.5") into "HH:MM".
 */
private static String normalizeDuration(String raw) {
    double h = HoursUtil.parseHours(raw);
    long mins = Math.round(h * 60.0);
    if (mins < 0) mins = 0;
    mins = (mins / 30) * 30; // floor to 30-minute blocks
    return String.format("%02d:%02d", (mins / 60), (mins % 60));
}

/**
 * Formats decimal hours into HH:MM (00:00 .. 999:59).
 */
private static String formatColon(double hours) {
    if (Double.isNaN(hours) || Double.isInfinite(hours) || hours < 0) hours = 0;
    long totalMinutes = Math.round(hours * 60.0);
    long hh = totalMinutes / 60;
    long mm = totalMinutes % 60;

    if (hh > 999) hh = 999;
    if (mm < 0) mm = 0;
    if (mm > 59) mm = 59;

    return String.format("%02d:%02d", hh, mm);
}

/**
 * Allows only digits and ":" while typing, and auto-shapes digits into HH:MM.
 * Examples:
 *  - typing 7    -> "7"      (focus lost => "07:00")
 *  - typing 730  -> "7:30"
 *  - typing 1230 -> "12:30"
 */
private static class DurationDocumentFilter extends DocumentFilter {
    @Override
    public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
            throws BadLocationException {
        replace(fb, offset, 0, string, attr);
    }

    @Override
    public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
            throws BadLocationException {

        if (text == null) text = "";
        Document doc = fb.getDocument();
        String current = doc.getText(0, doc.getLength());
        String next = new StringBuilder(current).replace(offset, offset + length, text).toString();

        // keep only digits and colon
        next = next.replaceAll("[^0-9:]", "");

        // If user typed only digits, auto-split last 2 as minutes
        String digits = next.replace(":", "");
        if (digits.length() > 5) digits = digits.substring(0, 5); // up to 999 + 2 mins

        String shaped;
        if (digits.isEmpty()) {
            shaped = "";
        } else if (digits.length() <= 2) {
            shaped = digits;
        } else {
            String hh = digits.substring(0, digits.length() - 2);
            String mm = digits.substring(digits.length() - 2);
            shaped = hh + ":" + mm;
        }

        // Clamp minutes if we have HH:MM
        if (shaped.contains(":")) {
            String[] p = shaped.split(":");
            String hh = (p.length > 0 && !p[0].isEmpty()) ? p[0] : "0";
            String mm = (p.length > 1 && !p[1].isEmpty()) ? p[1] : "0";

            int mmInt;
            try { mmInt = Integer.parseInt(mm); } catch (Exception e) { mmInt = 0; }
            if (mmInt > 59) mmInt = 59;

            shaped = hh + ":" + String.format("%02d", mmInt);
        }

        if (shaped.length() > 6) shaped = shaped.substring(0, 6);

        fb.replace(0, doc.getLength(), shaped, attrs);
    }
}

    /**
     * Keeps totals consistent with the breakdown (your rules).
     * If user edits breakdown fields, totals update.
     */
    private void recomputeTotalsFromBreakdown() {
        try {
            double rdH = HoursUtil.parseHours(tfRegDayHours.getText());
            double rhH = HoursUtil.parseHours(tfRegHolHours.getText());
            double shH = HoursUtil.parseHours(tfSpecHolHours.getText());

            double rdOT = HoursUtil.parseHours(tfRegDayOT.getText());
            double rhOT = HoursUtil.parseHours(tfRegHolOT.getText());
            double shOT = HoursUtil.parseHours(tfSpecHolOT.getText());

            double totalH = rdH + rhH + shH;         // ✅ excludes OT
            double totalOT = rdOT + rhOT + shOT;     // ✅ includes all OT
            if (!chkEnableOT.isSelected()) {
                // OT is disabled => do not compute/pay OT
                totalOT = 0;
            }

            tfTotalHours.setText(formatColon(totalH));
            tfTotalOT.setText(formatColon(totalOT));
        } catch (Exception ignore) {
            // keep silent; user may be typing
        }
    }




    // ================= DEDUCTIONS POPUP =================
    private void showDeductionsPopup() {
        if (deductionsPopup == null) buildDeductionsPopup();
        if (deductionsPanel == null) return;

        Dimension pref = deductionsPanel.getPreferredSize();
        if (pref == null || pref.height < 200)
            pref = new Dimension(520, 430);



        Container root = getContentPane();
        Point p = SwingUtilities.convertPoint(btnDeductionOptions, 0, 0, root);

        int spaceBelow = root.getHeight() - (p.y + btnDeductionOptions.getHeight());
        int spaceAbove = p.y;

        int x = 0;
        int y = btnDeductionOptions.getHeight();
        if (spaceBelow < pref.height && spaceAbove >= pref.height) {
            y = -pref.height;
        }

        deductionsPopup.show(btnDeductionOptions, x, y);
    }

    private void buildDeductionsPopup() {
        deductionsPopup = new JPopupMenu();
        deductionsPopup.setLightWeightPopupEnabled(false);

        deductionsPopup.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true),
                new EmptyBorder(2, 2, 2, 2)
        ));

        deductionsPanel = new JPanel();
        deductionsPanel.setLayout(new BoxLayout(deductionsPanel, BoxLayout.Y_AXIS));
        deductionsPanel.setBackground(Theme.CARD);
        deductionsPanel.setBorder(new EmptyBorder(10, 12, 10, 12));

        deductionsPanel.setPreferredSize(new Dimension(520, 430));


        JLabel title = new JLabel("Deductions Options");
        title.setForeground(Theme.PRIMARY);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        deductionsPanel.add(title);
        deductionsPanel.add(Box.createVerticalStrut(8));

        JCheckBox cbCash = new JCheckBox("Cash Advance");
        JCheckBox cbSmart = new JCheckBox("Smart Billing");
        JCheckBox cbOthers = new JCheckBox("Others");

        // ✅ Statutory deductions
        JCheckBox cbSSS = new JCheckBox("SSS");
        JCheckBox cbPagibig = new JCheckBox("Pag-IBIG");
        JCheckBox cbPhilHealth = new JCheckBox("PhilHealth");

        for (JCheckBox cb : new JCheckBox[]{cbCash, cbSmart, cbOthers, cbSSS, cbPagibig, cbPhilHealth}) {
            cb.setOpaque(false);
            cb.setForeground(Theme.TEXT);
        }

        JTextField tfCash   = new JTextField(cashAmt);
        JTextField tfSmart  = new JTextField(smartAmt);
        JTextField tfOthers = new JTextField(othersAmt);

        // Statutory fields (amounts can be typed; if left 0, system auto-computes on Compute Payslip)
        JTextField tfSSS = new JTextField(sssAmt);
        JTextField tfPagibig = new JTextField(pagibigAmt);
        JTextField tfPhilHealth = new JTextField(philhealthAmt);

        JTextField tfCashNote   = new JTextField(cashNote);
        JTextField tfOthersNote = new JTextField(othersNote);

        for (JTextField tf : new JTextField[]{tfCash, tfSmart, tfOthers, tfSSS, tfPagibig, tfPhilHealth, tfCashNote, tfOthersNote}) {
            Theme.styleInput(tf);
            tf.setPreferredSize(new Dimension(0, 34));
            tf.setMinimumSize(new Dimension(0, 34));
            tf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            tf.setMargin(new Insets(6, 8, 6, 8));
            tf.setColumns(22);
        }

        cbCash.setSelected(useCash);
        cbSmart.setSelected(useSmart);
        cbOthers.setSelected(useOthers);

        cbSSS.setSelected(useSSS);
        cbPagibig.setSelected(usePagibig);
        cbPhilHealth.setSelected(usePhilHealth);

        tfCash.setEnabled(useCash);
        tfSmart.setEnabled(useSmart);
        tfOthers.setEnabled(useOthers);

        tfSSS.setEnabled(useSSS);
        tfPagibig.setEnabled(usePagibig);
        tfPhilHealth.setEnabled(usePhilHealth);

        tfCashNote.setEnabled(useCash);
        tfOthersNote.setEnabled(useOthers);

        JLabel peso1 = new JLabel("₱");
        JLabel peso2 = new JLabel("₱");
        JLabel peso3 = new JLabel("₱");
        JLabel peso4 = new JLabel("₱");
        JLabel peso5 = new JLabel("₱");
        JLabel peso6 = new JLabel("₱");
        for (JLabel pp : new JLabel[]{peso1, peso2, peso3, peso4, peso5, peso6}) pp.setForeground(Theme.MUTED);

        JLabel totalLbl = new JLabel();
        totalLbl.setForeground(Theme.MUTED);
        totalLbl.setFont(totalLbl.getFont().deriveFont(Font.PLAIN, 11.5f));
        totalLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        java.util.function.Function<Object[], JPanel> row = (arr) -> {
            JCheckBox cb = (JCheckBox) arr[0];
            JTextField tf = (JTextField) arr[1];
            JLabel peso = (JLabel) arr[2];

            JPanel r = new JPanel(new BorderLayout(10, 0));
            r.setOpaque(false);
            r.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel left = new JPanel(new BorderLayout());
            left.setOpaque(false);
            left.setPreferredSize(new Dimension(130, 34));
            left.add(cb, BorderLayout.WEST);

            JPanel right = new JPanel(new BorderLayout());
            right.setOpaque(false);
            right.setPreferredSize(new Dimension(18, 34));
            right.add(peso, BorderLayout.CENTER);

            r.add(left, BorderLayout.WEST);
            r.add(tf, BorderLayout.CENTER);
            r.add(right, BorderLayout.EAST);
            return r;
        };

        Runnable recalc = () -> {
            useCash = cbCash.isSelected();
            useSmart = cbSmart.isSelected();
            useOthers = cbOthers.isSelected();

            useSSS = cbSSS.isSelected();
            usePagibig = cbPagibig.isSelected();
            usePhilHealth = cbPhilHealth.isSelected();

            tfCash.setEnabled(useCash);
            tfSmart.setEnabled(useSmart);
            tfOthers.setEnabled(useOthers);

            tfSSS.setEnabled(useSSS);
            tfPagibig.setEnabled(usePagibig);
            tfPhilHealth.setEnabled(usePhilHealth);

            tfCashNote.setEnabled(useCash);
            tfOthersNote.setEnabled(useOthers);
            if (!useCash) tfCashNote.setText("");
            if (!useOthers) tfOthersNote.setText("");

            cashAmt = tfCash.getText();
            smartAmt = tfSmart.getText();
            othersAmt = tfOthers.getText();

            sssAmt = tfSSS.getText();
            pagibigAmt = tfPagibig.getText();
            philhealthAmt = tfPhilHealth.getText();

            cashNote = useCash ? tfCashNote.getText() : "";
            othersNote = useOthers ? tfOthersNote.getText() : "";

            // Try to auto-compute statutory deductions using the latest computed gross (if available).
            double grossForAuto = (lastPayslip != null ? lastPayslip.grossPay : 0.0);

            double sssAuto = 0.0, pagibigAuto = 0.0, philhealthAuto = 0.0;
            if (grossForAuto > 0) {
                sssAuto = PayrollCalculator.computeSSSEmployeeSharePerPeriod(grossForAuto);
                pagibigAuto = PayrollCalculator.computePagIbigEmployeeSharePerPeriod(grossForAuto);
                philhealthAuto = PayrollCalculator.computePhilHealthEmployeeSharePerPeriod(grossForAuto);
            }

            double sssVal = parseMoney(sssAmt);
            double pagibigVal = parseMoney(pagibigAmt);
            double philhealthVal = parseMoney(philhealthAmt);

            // If checkbox is on and field is 0, use auto value (when gross is known).
            if (useSSS && sssVal == 0 && sssAuto > 0) sssVal = sssAuto;
            if (usePagibig && pagibigVal == 0 && pagibigAuto > 0) pagibigVal = pagibigAuto;
            if (usePhilHealth && philhealthVal == 0 && philhealthAuto > 0) philhealthVal = philhealthAuto;

            double total = 0;
            if (useCash)  total += parseMoney(cashAmt);
            if (useSmart) total += parseMoney(smartAmt);
            if (useOthers) total += parseMoney(othersAmt);

            if (useSSS) total += sssVal;
            if (usePagibig) total += pagibigVal;
            if (usePhilHealth) total += philhealthVal;

            tfDeductions.setText(formatMoney(total));
            tfDeductions.setCaretPosition(0);
            totalLbl.setText("Total: ₱" + formatMoney(total));
        };

        DocumentListener dl = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { recalc.run(); }
            @Override public void removeUpdate(DocumentEvent e) { recalc.run(); }
            @Override public void changedUpdate(DocumentEvent e) { recalc.run(); }
        };

        tfCash.getDocument().addDocumentListener(dl);
        tfSmart.getDocument().addDocumentListener(dl);
        tfOthers.getDocument().addDocumentListener(dl);
        tfSSS.getDocument().addDocumentListener(dl);
        tfPagibig.getDocument().addDocumentListener(dl);
        tfPhilHealth.getDocument().addDocumentListener(dl);
        tfCashNote.getDocument().addDocumentListener(dl);
        tfOthersNote.getDocument().addDocumentListener(dl);

        // ✅ If user manually types an amount, auto-enable the checkbox (so it is counted)
        DocumentListener autoCheck = new DocumentListener() {
            private void run() {
                SwingUtilities.invokeLater(() -> {
                    if (parseMoney(tfSSS.getText()) > 0) cbSSS.setSelected(true);
                    if (parseMoney(tfPagibig.getText()) > 0) cbPagibig.setSelected(true);
                    if (parseMoney(tfPhilHealth.getText()) > 0) cbPhilHealth.setSelected(true);
                });
            }
            @Override public void insertUpdate(DocumentEvent e) { run(); }
            @Override public void removeUpdate(DocumentEvent e) { run(); }
            @Override public void changedUpdate(DocumentEvent e) { run(); }
        };
        tfSSS.getDocument().addDocumentListener(autoCheck);
        tfPagibig.getDocument().addDocumentListener(autoCheck);
        tfPhilHealth.getDocument().addDocumentListener(autoCheck);


        cbCash.addActionListener(e -> recalc.run());
        cbSmart.addActionListener(e -> recalc.run());
        cbOthers.addActionListener(e -> recalc.run());
        cbSSS.addActionListener(e -> recalc.run());
        cbPagibig.addActionListener(e -> recalc.run());
        cbPhilHealth.addActionListener(e -> recalc.run());
        
     /// ✅ Enter-to-next behavior (form-like)
     // Cash -> Smart -> Others -> SSS -> Pag-IBIG -> PhilHealth -> Note -> Done

        tfCash.addActionListener(e -> {
            recalc.run();
            if (tfSmart.isEnabled()) tfSmart.requestFocusInWindow();
            else if (tfOthers.isEnabled()) tfOthers.requestFocusInWindow();
            else if (tfSSS.isEnabled()) tfSSS.requestFocusInWindow();
            else if (tfPagibig.isEnabled()) tfPagibig.requestFocusInWindow();
            else if (tfPhilHealth.isEnabled()) tfPhilHealth.requestFocusInWindow();
            else if (tfCashNote.isEnabled()) tfCashNote.requestFocusInWindow();
            else if (tfOthersNote.isEnabled()) tfOthersNote.requestFocusInWindow();
        });

        tfSmart.addActionListener(e -> {
            recalc.run();
            if (tfOthers.isEnabled()) tfOthers.requestFocusInWindow();
            else if (tfSSS.isEnabled()) tfSSS.requestFocusInWindow();
            else if (tfPagibig.isEnabled()) tfPagibig.requestFocusInWindow();
            else if (tfPhilHealth.isEnabled()) tfPhilHealth.requestFocusInWindow();
            else if (tfCashNote.isEnabled()) tfCashNote.requestFocusInWindow();
            else if (tfOthersNote.isEnabled()) tfOthersNote.requestFocusInWindow();
        });

        tfOthers.addActionListener(e -> {
            recalc.run();
            if (tfSSS.isEnabled()) tfSSS.requestFocusInWindow();
            else if (tfPagibig.isEnabled()) tfPagibig.requestFocusInWindow();
            else if (tfPhilHealth.isEnabled()) tfPhilHealth.requestFocusInWindow();
            else if (tfCashNote.isEnabled()) tfCashNote.requestFocusInWindow();
            else if (tfOthersNote.isEnabled()) tfOthersNote.requestFocusInWindow();
        });

        // ✅ Statutory fields
        tfSSS.addActionListener(e -> {
            recalc.run();
            if (tfPagibig.isEnabled()) tfPagibig.requestFocusInWindow();
            else if (tfPhilHealth.isEnabled()) tfPhilHealth.requestFocusInWindow();
            else if (tfCashNote.isEnabled()) tfCashNote.requestFocusInWindow();
            else if (tfOthersNote.isEnabled()) tfOthersNote.requestFocusInWindow();
        });

        tfPagibig.addActionListener(e -> {
            recalc.run();
            if (tfPhilHealth.isEnabled()) tfPhilHealth.requestFocusInWindow();
            else if (tfCashNote.isEnabled()) tfCashNote.requestFocusInWindow();
            else if (tfOthersNote.isEnabled()) tfOthersNote.requestFocusInWindow();
        });

        tfPhilHealth.addActionListener(e -> {
            recalc.run();
            if (tfCashNote.isEnabled()) tfCashNote.requestFocusInWindow();
            else if (tfOthersNote.isEnabled()) tfOthersNote.requestFocusInWindow();
        });



        

     // ✅ Enter-to-next for statutory fields
     tfSSS.addActionListener(e -> {
         recalc.run();
         if (tfPagibig.isEnabled()) tfPagibig.requestFocusInWindow();
         else if (tfPhilHealth.isEnabled()) tfPhilHealth.requestFocusInWindow();
         else if (tfCashNote.isEnabled()) tfCashNote.requestFocusInWindow();
         else if (tfOthersNote.isEnabled()) tfOthersNote.requestFocusInWindow();
     });

     tfPagibig.addActionListener(e -> {
         recalc.run();
         if (tfPhilHealth.isEnabled()) tfPhilHealth.requestFocusInWindow();
         else if (tfCashNote.isEnabled()) tfCashNote.requestFocusInWindow();
         else if (tfOthersNote.isEnabled()) tfOthersNote.requestFocusInWindow();
     });

     tfPhilHealth.addActionListener(e -> {
         recalc.run();
         if (tfCashNote.isEnabled()) tfCashNote.requestFocusInWindow();
         else if (tfOthersNote.isEnabled()) tfOthersNote.requestFocusInWindow();
     });

     deductionsPanel.add(row.apply(new Object[]{cbCash, tfCash, peso1}));
        deductionsPanel.add(Box.createVerticalStrut(6));

        JPanel cashNoteRow = new JPanel(new BorderLayout(10, 0));
        cashNoteRow.setOpaque(false);
        cashNoteRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel cashNoteLbl = label("Cash Advance Note:");
        cashNoteLbl.setPreferredSize(new Dimension(130, 34));

        cashNoteRow.add(cashNoteLbl, BorderLayout.WEST);
        cashNoteRow.add(tfCashNote, BorderLayout.CENTER);

        deductionsPanel.add(cashNoteRow);
        deductionsPanel.add(Box.createVerticalStrut(10));
        deductionsPanel.add(row.apply(new Object[]{cbSmart, tfSmart, peso2}));
        deductionsPanel.add(Box.createVerticalStrut(6));
        deductionsPanel.add(row.apply(new Object[]{cbOthers, tfOthers, peso3}));
        deductionsPanel.add(Box.createVerticalStrut(6));

        JPanel othersNoteRow = new JPanel(new BorderLayout(10, 0));
        othersNoteRow.setOpaque(false);
        othersNoteRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel othersNoteLbl = label("Others Note:");
        othersNoteLbl.setPreferredSize(new Dimension(130, 34));

        othersNoteRow.add(othersNoteLbl, BorderLayout.WEST);
        othersNoteRow.add(tfOthersNote, BorderLayout.CENTER);

        deductionsPanel.add(othersNoteRow);
        deductionsPanel.add(Box.createVerticalStrut(10));

        // ✅ Statutory deductions rows
        deductionsPanel.add(row.apply(new Object[]{cbSSS, tfSSS, peso4}));
        deductionsPanel.add(Box.createVerticalStrut(6));
        deductionsPanel.add(row.apply(new Object[]{cbPagibig, tfPagibig, peso5}));
        deductionsPanel.add(Box.createVerticalStrut(6));
        deductionsPanel.add(row.apply(new Object[]{cbPhilHealth, tfPhilHealth, peso6}));
        deductionsPanel.add(Box.createVerticalStrut(8));
        deductionsPanel.add(totalLbl);
        deductionsPanel.add(Box.createVerticalStrut(8));

        JButton btnClear = new JButton("Clear");
        JButton btnDone = new JButton("Done");
        tfCashNote.addActionListener(e -> {
            recalc.run();
            if (tfOthersNote.isEnabled()) tfOthersNote.requestFocusInWindow();
            else btnDone.doClick();
        });
        tfOthersNote.addActionListener(e -> {
            recalc.run();
            btnDone.doClick();
        });
        Theme.styleSecondaryButton(btnClear);
        Theme.stylePrimaryButton(btnDone);
        makeButton(btnClear, 36);
        makeButton(btnDone, 36);

        btnClear.addActionListener(e -> {
            cbCash.setSelected(false);
            cbSmart.setSelected(false);
            cbOthers.setSelected(false);
            cbSSS.setSelected(false);
            cbPagibig.setSelected(false);
            cbPhilHealth.setSelected(false);
            tfCash.setText("0");
            tfSmart.setText("0");
            tfOthers.setText("0");
            tfSSS.setText("0");
            tfPagibig.setText("0");
            tfPhilHealth.setText("0");
            tfCashNote.setText("");
            tfOthersNote.setText("");
            recalc.run();
        });

        btnDone.addActionListener(e -> deductionsPopup.setVisible(false));
     // ✅ Enter on Note = Done
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.setOpaque(false);
        btnRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnRow.add(btnClear);
        btnRow.add(btnDone);

        deductionsPanel.add(btnRow);

        deductionsPopup.removeAll();
        deductionsPopup.add(deductionsPanel);

        recalc.run();
    }

    private void showEarningsPopup() {
        if (earningsPopup == null) buildEarningsPopup();

        JRootPane root = getRootPane();
        if (root == null) {
            earningsPopup.show(btnEarningsOptions, 0, btnEarningsOptions.getHeight());
            return;
        }

        Dimension pref = earningsPanel.getPreferredSize();
        int popupW = pref.width;
        int popupH = pref.height;

        Point p = SwingUtilities.convertPoint(btnEarningsOptions, 0, 0, root);
        int spaceBelow = root.getHeight() - (p.y + btnEarningsOptions.getHeight());
        int spaceAbove = p.y;

        int x = 0;
        int y = btnEarningsOptions.getHeight();
        if (spaceBelow < popupH && spaceAbove > spaceBelow) {
            y = -popupH;
        }
        int rightOverflow = (p.x + popupW) - root.getWidth();
        if (rightOverflow > 0) x = -rightOverflow - 4;

        earningsPopup.show(btnEarningsOptions, x, y);
    }

    private void buildEarningsPopup() {
        earningsPopup = new JPopupMenu();
        earningsPopup.setLightWeightPopupEnabled(false);
        earningsPopup.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true),
                new EmptyBorder(2, 2, 2, 2)
        ));

        earningsPanel = new JPanel();
        earningsPanel.setLayout(new BoxLayout(earningsPanel, BoxLayout.Y_AXIS));
        earningsPanel.setBackground(Theme.CARD);
        earningsPanel.setBorder(new EmptyBorder(10, 12, 10, 12));
        earningsPanel.setPreferredSize(new Dimension(520, 280));

        JLabel title = new JLabel("Additional Earnings Options");
        title.setForeground(Theme.PRIMARY);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        earningsPanel.add(title);
        earningsPanel.add(Box.createVerticalStrut(8));

        JCheckBox cbIncentives = new JCheckBox("Incentives");
        JCheckBox cbThirteenth = new JCheckBox("13th Month Pay");
        JCheckBox cbBonus = new JCheckBox("Bonus");
        for (JCheckBox cb : new JCheckBox[]{cbIncentives, cbThirteenth, cbBonus}) {
            cb.setOpaque(false);
            cb.setForeground(Theme.TEXT);
        }

        JTextField tfIncentivesPopup = new JTextField(incentivesAmt);
        JTextField tfThirteenth = new JTextField(thirteenthMonthAmt);
        JTextField tfBonus = new JTextField(bonusAmt);
        for (JTextField tf : new JTextField[]{tfIncentivesPopup, tfThirteenth, tfBonus}) {
            Theme.styleInput(tf);
            tf.setPreferredSize(new Dimension(0, 34));
            tf.setMinimumSize(new Dimension(0, 34));
            tf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            tf.setMargin(new Insets(6, 8, 6, 8));
            tf.setColumns(22);
        }

        cbIncentives.setSelected(useIncentives);
        cbThirteenth.setSelected(useThirteenthMonth);
        cbBonus.setSelected(useBonus);
        tfIncentivesPopup.setEnabled(useIncentives);
        tfThirteenth.setEnabled(useThirteenthMonth);
        tfBonus.setEnabled(useBonus);

        JLabel peso1 = new JLabel("₱");
        JLabel peso2 = new JLabel("₱");
        JLabel peso3 = new JLabel("₱");
        for (JLabel pp : new JLabel[]{peso1, peso2, peso3}) pp.setForeground(Theme.MUTED);

        JLabel totalLbl = new JLabel();
        totalLbl.setForeground(Theme.MUTED);
        totalLbl.setFont(totalLbl.getFont().deriveFont(Font.PLAIN, 11.5f));
        totalLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        java.util.function.Function<Object[], JPanel> row = (arr) -> {
            JCheckBox cb = (JCheckBox) arr[0];
            JTextField tf = (JTextField) arr[1];
            JLabel peso = (JLabel) arr[2];

            JPanel r = new JPanel(new BorderLayout(10, 0));
            r.setOpaque(false);
            r.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel left = new JPanel(new BorderLayout());
            left.setOpaque(false);
            left.setPreferredSize(new Dimension(150, 34));
            left.add(cb, BorderLayout.WEST);

            JPanel right = new JPanel(new BorderLayout());
            right.setOpaque(false);
            right.setPreferredSize(new Dimension(18, 34));
            right.add(peso, BorderLayout.CENTER);

            r.add(left, BorderLayout.WEST);
            r.add(tf, BorderLayout.CENTER);
            r.add(right, BorderLayout.EAST);
            return r;
        };

        Runnable recalc = () -> {
            useIncentives = cbIncentives.isSelected();
            useThirteenthMonth = cbThirteenth.isSelected();
            useBonus = cbBonus.isSelected();

            tfIncentivesPopup.setEnabled(useIncentives);
            tfThirteenth.setEnabled(useThirteenthMonth);
            tfBonus.setEnabled(useBonus);

            incentivesAmt = tfIncentivesPopup.getText();
            thirteenthMonthAmt = tfThirteenth.getText();
            bonusAmt = tfBonus.getText();

            double total = (useIncentives ? parseMoney(incentivesAmt) : 0.0)
                    + (useThirteenthMonth ? parseMoney(thirteenthMonthAmt) : 0.0)
                    + (useBonus ? parseMoney(bonusAmt) : 0.0);
            tfAdditionalEarnings.setText(formatMoney(total));
            tfAdditionalEarnings.setCaretPosition(0);
            totalLbl.setText("Total: ₱" + formatMoney(total));
        };

        java.awt.event.ItemListener il = e -> recalc.run();
        cbIncentives.addItemListener(il);
        cbThirteenth.addItemListener(il);
        cbBonus.addItemListener(il);

        javax.swing.event.DocumentListener dl = new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { recalc.run(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { recalc.run(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { recalc.run(); }
        };
        tfIncentivesPopup.getDocument().addDocumentListener(dl);
        tfThirteenth.getDocument().addDocumentListener(dl);
        tfBonus.getDocument().addDocumentListener(dl);

        javax.swing.event.DocumentListener autoCheck = new javax.swing.event.DocumentListener() {
            private void run() {
                SwingUtilities.invokeLater(() -> {
                    if (parseMoney(tfIncentivesPopup.getText()) > 0) cbIncentives.setSelected(true);
                    if (parseMoney(tfThirteenth.getText()) > 0) cbThirteenth.setSelected(true);
                    if (parseMoney(tfBonus.getText()) > 0) cbBonus.setSelected(true);
                });
            }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { run(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { run(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { run(); }
        };
        tfIncentivesPopup.getDocument().addDocumentListener(autoCheck);
        tfThirteenth.getDocument().addDocumentListener(autoCheck);
        tfBonus.getDocument().addDocumentListener(autoCheck);

        earningsPanel.add(row.apply(new Object[]{cbIncentives, tfIncentivesPopup, peso1}));
        earningsPanel.add(Box.createVerticalStrut(8));
        earningsPanel.add(row.apply(new Object[]{cbThirteenth, tfThirteenth, peso2}));
        earningsPanel.add(Box.createVerticalStrut(8));
        earningsPanel.add(row.apply(new Object[]{cbBonus, tfBonus, peso3}));
        earningsPanel.add(Box.createVerticalStrut(12));
        earningsPanel.add(totalLbl);
        earningsPanel.add(Box.createVerticalStrut(12));

        JButton btnClear = new JButton("Clear");
        JButton btnDone = new JButton("Done");
        Theme.styleSecondaryButton(btnClear);
        Theme.stylePrimaryButton(btnDone);
        makeButton(btnClear, 36);
        makeButton(btnDone, 36);

        btnClear.addActionListener(e -> {
            cbIncentives.setSelected(false);
            cbThirteenth.setSelected(false);
            cbBonus.setSelected(false);
            tfIncentivesPopup.setText("0");
            tfThirteenth.setText("0");
            tfBonus.setText("0");
            recalc.run();
        });

        // Enter-to-next behavior for Additional Earnings:
        // move to the next enabled field, and close when there is no next field left.
        tfIncentivesPopup.addActionListener(e -> {
            recalc.run();
            if (tfThirteenth.isEnabled()) tfThirteenth.requestFocusInWindow();
            else if (tfBonus.isEnabled()) tfBonus.requestFocusInWindow();
            else btnDone.doClick();
        });

        tfThirteenth.addActionListener(e -> {
            recalc.run();
            if (tfBonus.isEnabled()) tfBonus.requestFocusInWindow();
            else btnDone.doClick();
        });

        tfBonus.addActionListener(e -> {
            recalc.run();
            btnDone.doClick();
        });

        btnDone.addActionListener(e -> {
            recalc.run();
            earningsPopup.setVisible(false);
        });

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        actionRow.setOpaque(false);
        actionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        actionRow.add(btnClear);
        actionRow.add(btnDone);
        earningsPanel.add(actionRow);

        earningsPopup.add(earningsPanel);
        recalc.run();
    }

    // ================= STYLES =================
    private void applyThemeStyles() {
        Theme.styleCombo(cbEmp);
        Theme.styleCombo(cbPeriod);

        // new fields
        for (JTextField tf : new JTextField[]{
                tfRegDayHours, tfRegDayOT,
                tfRegHolHours, tfRegHolOT,
                tfSpecHolHours, tfSpecHolOT,
                tfTotalHours, tfTotalOT
        }) Theme.styleInput(tf);

        Theme.styleInput(tfStart);
        Theme.styleInput(tfEnd);
        Theme.styleInput(tfOTMultiplier);
        Theme.styleInput(tfOTRateOverride);
        Theme.styleInput(tfDeductions);
        Theme.styleInput(tfAdditionalEarnings);

        Theme.styleSecondaryButton(btnDeductionOptions);
        Theme.styleSecondaryButton(btnEarningsOptions);

        setFixedHeight(cbEmp, 32);

        for (JTextField tf : new JTextField[]{
                tfRegDayHours, tfRegDayOT,
                tfRegHolHours, tfRegHolOT,
                tfSpecHolHours, tfSpecHolOT,
                tfTotalHours, tfTotalOT
        }) setFixedHeight(tf, 32);

        setFixedHeight(tfStart, 32);
        setFixedHeight(tfEnd, 32);
        setFixedHeight(cbPeriod, 32);

        setFixedHeight(tfOTMultiplier, 32);
        setFixedHeight(tfOTRateOverride, 32);
        setFixedHeight(tfDeductions, 32);
        setFixedHeight(tfAdditionalEarnings, 32);

        makeButton(btnDeductionOptions, 34);
        makeButton(btnEarningsOptions, 34);
        btnDeductionOptions.setPreferredSize(new Dimension(92, 34));
        btnEarningsOptions.setPreferredSize(new Dimension(150, 34));
    }

    private void setFixedHeight(JComponent c, int h) {
        Dimension pref = c.getPreferredSize();
        c.setPreferredSize(new Dimension(pref.width, h));
        c.setMinimumSize(new Dimension(120, h));
    }

    private void forceComboPillLook() {
        cbEmp.setOpaque(true);
        cbPeriod.setOpaque(true);
        cbEmp.setBackground(Theme.INPUT_BG);
        cbPeriod.setBackground(Theme.INPUT_BG);
    }

    private JPanel cardPanel() {
        JPanel p = new JPanel();
        p.setBackground(Theme.CARD);
        p.setBorder(BorderFactory.createCompoundBorder(
                new javax.swing.border.LineBorder(Theme.BORDER, 1, true),
                new EmptyBorder(10, 10, 10, 10)
        ));
        p.setOpaque(true);
        return p;
    }

    private void makeButton(JButton b, int height) {
        b.setFont(b.getFont().deriveFont(Font.BOLD, 12.0f));
        b.setMargin(new Insets(6, 14, 6, 14));
        Dimension pref = b.getPreferredSize();
        b.setPreferredSize(new Dimension(pref.width, height));
    }

    private JLabel sectionTitle(String text) {
        JLabel t = new JLabel(text);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 13.2f));
        t.setForeground(Theme.PRIMARY);
        return t;
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Theme.TEXT);
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 12f));
        return l;
    }


    private void resetPayrollInputsForSelectedEmployee() {
        suppressManualFlag = true;
        try {
            tfDeductions.setText("0");
            tfDeductions.setCaretPosition(0);
            tfAdditionalEarnings.setText("0");
            tfAdditionalEarnings.setCaretPosition(0);

            useCash = false;
            useSmart = false;
            useOthers = false;
            cashAmt = "0";
            smartAmt = "0";
            othersAmt = "0";
            cashNote = "";
            othersNote = "";

            useSSS = false;
            usePagibig = false;
            usePhilHealth = false;
            sssAmt = "0";
            pagibigAmt = "0";
            philhealthAmt = "0";

            useIncentives = false;
            useThirteenthMonth = false;
            useBonus = false;
            incentivesAmt = "0";
            thirteenthMonthAmt = "0";
            bonusAmt = "0";

            deductionsPopup = null;
            deductionsPanel = null;
            earningsPopup = null;
            earningsPanel = null;

            lastPayslip = null;
            preview.setText("");
        } finally {
            suppressManualFlag = false;
        }
    }

    // ================= PERIODS =================
    private String getSelectedEmployeePayTypeSafe() {
        try {
            Object sel = cbEmp.getSelectedItem();
            if (sel instanceof EmployeeItem it) {
                Employee emp = EmployeeDAO.getById(it.empId);
                if (emp != null && emp.payType != null) {
                    return emp.payType.trim();
                }
            }
        } catch (Exception ignored) { }
        return "";
    }

void initPeriodCombo() {
        cbPeriod.removeAllItems();

        // Only MONTHLY employees can use Monthly periods.
        // DAILY / HOURLY / BI-MONTHLY employees get BI-MONTHLY (semi-monthly) periods only.
        String payType = getSelectedEmployeePayTypeSafe();
        boolean isMonthly = payType != null && payType.trim().equalsIgnoreCase("MONTHLY");

        // Dynamic label
        lblPayPeriod.setText(isMonthly ? "MONTHLY Period:" : "BI-MONTHLY Period:");

        int currentYear = LocalDate.now().getYear();
        int minYear = currentYear - 1;
        int maxYear = currentYear + 50;

        for (int year = minYear; year <= maxYear; year++) {
            for (Month m : Month.values()) {
                YearMonth ym = YearMonth.of(year, m);
                if (isMonthly) {
                    cbPeriod.addItem(new PeriodItem(ym.atDay(1), ym.atEndOfMonth()));
                } else {
                    cbPeriod.addItem(new PeriodItem(ym.atDay(1), ym.atDay(15)));
                    cbPeriod.addItem(new PeriodItem(ym.atDay(16), ym.atEndOfMonth()));
                }
            }
        }
    }

    private void setPeriodToToday() {
        LocalDate today = LocalDate.now();
        ComboBoxModel<PeriodItem> model = cbPeriod.getModel();
        for (int i = 0; i < model.getSize(); i++) {
            PeriodItem it = model.getElementAt(i);
            if (!today.isBefore(it.start) && !today.isAfter(it.end)) {
                cbPeriod.setSelectedIndex(i);
                return;
            }
        }
        if (model.getSize() > 0) cbPeriod.setSelectedIndex(0);
    }

    private void syncDatesFromPeriod() {
        PeriodItem p = (PeriodItem) cbPeriod.getSelectedItem();
        if (p == null) return;
        tfStart.setText(ISO.format(p.start));
        tfEnd.setText(ISO.format(p.end));
        tfStart.setCaretPosition(0);
        tfEnd.setCaretPosition(0);
    }

    // ================= HOLIDAYS =================

    /**
     * IMPORTANT: Do NOT hit the database here.
     * When switching employees, DB fetch can lag and/or momentarily return stale/empty data,
     * which caused the MONTHLY warning + holiday UI disabling to trigger for non-monthly employees.
     *
     * We cache the payType in the combo item on load and rely on that.
     */
    private boolean isSelectedEmployeeMonthly() {
        EmployeeItem item = (EmployeeItem) cbEmp.getSelectedItem();
        if (item == null) return false;
        return item.payType != null && item.payType.trim().equalsIgnoreCase("MONTHLY");
    }

    private void setHolidayUiEnabled(boolean enabled) {
        btnReviewHolidays.setEnabled(enabled);

        // Disable the duration pickers (the panel + its children) if present
        setComponentTreeEnabled(dpRegHolHours, enabled);
        setComponentTreeEnabled(dpRegHolOT, enabled);
        setComponentTreeEnabled(dpSpecHolHours, enabled);
        setComponentTreeEnabled(dpSpecHolOT, enabled);

        if (!enabled) {
            // clear holiday inputs so they don't accidentally compute
            tfRegHolHours.setText("00:00");
            tfRegHolOT.setText("00:00");
            tfSpecHolHours.setText("00:00");
            tfSpecHolOT.setText("00:00");
        }
    }

    private void setComponentTreeEnabled(Component c, boolean enabled) {
        if (c == null) return;
        c.setEnabled(enabled);
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                setComponentTreeEnabled(child, enabled);
            }
        }
    }

    private void refreshHolidayCount() {
        if (isSelectedEmployeeMonthly()) {
            lblHolidayCount.setText("Holidays in this period: (excluded for MONTHLY)");
            return;
        }
        try {
            LocalDate s = LocalDate.parse(tfStart.getText().trim(), ISO);
            LocalDate e = LocalDate.parse(tfEnd.getText().trim(), ISO);

            HolidayPresetsPH.seedYearIfEmpty(s.getYear());
            int c = holidayDAO.countBetween(s, e);

            lblHolidayCount.setText("Holidays in this period: " + c);
        } catch (Exception ex) {
            lblHolidayCount.setText("Holidays in this period: -");
        }
    }

    private void openHolidayReview() {
        if (isSelectedEmployeeMonthly()) {
            JOptionPane.showMessageDialog(this,
                    "Holiday settings are disabled for MONTHLY employees (holidays excluded).",
                    "Holidays Disabled", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        try {
            LocalDate s = LocalDate.parse(tfStart.getText().trim(), ISO);
            LocalDate e = LocalDate.parse(tfEnd.getText().trim(), ISO);

            HolidayPresetsPH.seedYearIfEmpty(s.getYear());

            Window owner = SwingUtilities.getWindowAncestor(this);
            HolidayReviewDialog dlg = new HolidayReviewDialog(owner, s, e);
            dlg.setVisible(true);

            refreshHolidayCount();

            if (chkAutoFromLogs.isSelected() && !manualOverride) {
                autoFillFromTimeLogs(false);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this
                    , "Holiday review error:\n" + ex.getMessage()
                    , "Error"
                    , JOptionPane.ERROR_MESSAGE);
        }
    }
 // ================= EMPLOYEES =================
    private void loadEmployees() {
        cbEmp.removeAllItems();
        try {
            List<Employee> list = EmployeeDAO.listActive(); // ✅ active only
            for (Employee e : list) {
                cbEmp.addItem(new EmployeeItem(e.empId, e.empNo, e.fullName(), e.payType));
            }
            if (cbEmp.getItemCount() > 0 && cbEmp.getSelectedIndex() < 0) {
                cbEmp.setSelectedIndex(0);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            cbEmp.revalidate();
            cbEmp.repaint();
        }
    }


    // ================= PAYROLL =================
    private void computePayslip() {
        if (chkAutoFromLogs.isSelected() && !manualOverride) {
            autoFillFromTimeLogs(false);
        }

        EmployeeItem item = (EmployeeItem) cbEmp.getSelectedItem();
        if (item == null) {
            JOptionPane.showMessageDialog(this, "Please select an employee.");
            return;
        }

        if (tfStart.getText().trim().isEmpty() || tfEnd.getText().trim().isEmpty()) {
            syncDatesFromPeriod();
            refreshHolidayCount();
        }

        String start = tfStart.getText().trim();
        String end = tfEnd.getText().trim();

        try {
            LocalDate.parse(start, ISO);
            LocalDate.parse(end, ISO);
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this, "Invalid Period Start/End. Please select a Pay Period again.");
            return;
        }

        try {
            // Read breakdown
            double rdH = HoursUtil.parseHours(tfRegDayHours.getText());
            double rdOT = HoursUtil.parseHours(tfRegDayOT.getText());

            double rhH = HoursUtil.parseHours(tfRegHolHours.getText());
            double rhOT = HoursUtil.parseHours(tfRegHolOT.getText());

            double shH = HoursUtil.parseHours(tfSpecHolHours.getText());
            double shOT = HoursUtil.parseHours(tfSpecHolOT.getText());

            // ✅ If admin disabled OT, force OT hours to 0 regardless of what is typed
            if (!chkEnableOT.isSelected()) {
                rdOT = 0;
                rhOT = 0;
                shOT = 0;
            }

            // Keep totals consistent
            double totalHours = rdH + rhH + shH;         // ✅ non-OT only
            double totalOT = rdOT + rhOT + shOT;         // ✅ all OT (or 0 if disabled)

            // Update totals boxes visually (so user sees correct totals)
            suppressManualFlag = true;
            tfTotalHours.setText(formatColon(totalHours));
            tfTotalOT.setText(formatColon(totalOT));
            if (!chkEnableOT.isSelected()) {
                // keep OT fields consistent in UI
                tfRegDayOT.setText("00:00");
                tfRegHolOT.setText("00:00");
                tfSpecHolOT.setText("00:00");
            }
            suppressManualFlag = false;

            double otMult = parseDouble(tfOTMultiplier.getText());
            Double otOverride = parseNullableDouble(tfOTRateOverride.getText());
            if (!chkEnableOT.isSelected()) {
                // multiplier/override irrelevant when OT is disabled
                otMult = 1.0;
                otOverride = null;
            }
            double deductions = parseDouble(tfDeductions.getText());

            Employee emp = EmployeeDAO.getById(item.empId);

            if (runId == 0) runId = PayrollDAO.createRun(start, end);

            Payslip p = PayrollCalculator.computeFromBreakdown(
                    runId, emp, start, end,
                    rdH, rdOT,
                    rhH, rhOT,
                    shH, shOT,
                    otMult, otOverride, 0
            );

            // ✅ Additional earnings
            double incentives = useIncentives ? parseMoney(incentivesAmt) : 0.0;
            double thirteenthMonthPay = useThirteenthMonth ? parseMoney(thirteenthMonthAmt) : 0.0;
            double bonusPay = useBonus ? parseMoney(bonusAmt) : 0.0;
            p.incentives = incentives;
            p.thirteenthMonthPay = thirteenthMonthPay;
            p.bonusPay = bonusPay;
            p.additionalEarnings = incentives + thirteenthMonthPay + bonusPay;
            // Add additional earnings to gross before deductions/statutory computations
            p.grossPay = p.grossPay + p.additionalEarnings;


            // ✅ store OT toggle state for PDF rendering
            p.otEnabled = chkEnableOT.isSelected();


            // ✅ store deduction breakdown + note into payslip (preview + PDF uses these)
            p.cashAdvanceDeduction = (useCash ? parseMoney(cashAmt) : 0.0);
            p.smartBillingDeduction = (useSmart ? parseMoney(smartAmt) : 0.0);
            p.otherDeduction = (useOthers ? parseMoney(othersAmt) : 0.0);

            // ✅ statutory deductions (auto-compute if enabled and amount is 0)
            double sssVal = (useSSS ? parseMoney(sssAmt) : 0.0);
            double pagibigVal = (usePagibig ? parseMoney(pagibigAmt) : 0.0);
            double philhealthVal = (usePhilHealth ? parseMoney(philhealthAmt) : 0.0);

            if (useSSS && sssVal == 0) sssVal = PayrollCalculator.computeSSSEmployeeSharePerPeriod(p.grossPay);
            if (usePagibig && pagibigVal == 0) pagibigVal = PayrollCalculator.computePagIbigEmployeeSharePerPeriod(p.grossPay);
            if (usePhilHealth && philhealthVal == 0) philhealthVal = PayrollCalculator.computePhilHealthEmployeeSharePerPeriod(p.grossPay);

            p.sssDeduction = (useSSS ? sssVal : 0.0);
            p.pagibigDeduction = (usePagibig ? pagibigVal : 0.0);
            p.philhealthDeduction = (usePhilHealth ? philhealthVal : 0.0);

            p.cashAdvanceNote = (useCash && cashNote != null) ? cashNote.trim() : "";
            p.otherDeductionNote = (useOthers && othersNote != null) ? othersNote.trim() : "";
            p.deductionNote = "";

            // ✅ finalize total deductions + net pay (ensures statutory deductions affect Net Pay)
            p.deductions = p.cashAdvanceDeduction + p.smartBillingDeduction + p.otherDeduction
                    + p.sssDeduction + p.pagibigDeduction + p.philhealthDeduction;
            p.netPay = p.grossPay - p.deductions;

            // keep the UI fields in sync
            tfDeductions.setText(formatMoney(p.deductions));
            tfDeductions.setCaretPosition(0);
            tfAdditionalEarnings.setText(formatMoney(p.additionalEarnings));
            tfAdditionalEarnings.setCaretPosition(0);

            // store totals as well (your rule)
            p.totalHours = totalHours;
            p.totalOtHours = totalOT;

            int payslipId = PayrollDAO.savePayslip(p);
            p.payslipId = payslipId;

            lastPayslip = p;

            preview.setText(buildPreviewHtml(p));
            preview.setCaretPosition(0);

            // Removed popup for smoother click-by-click flow (payslip still computed & saved)
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Compute error: " + ex.getMessage());
        }
    }

    private void generatePDF() {
        try {
            if (lastPayslip == null) {
                JOptionPane.showMessageDialog(this, "Compute a payslip first.");
                return;
            }

            // ✅ Auto-save folder (same behavior as before)
            // 1) If you already have a folder name you want, change it here:
            File outDir = new File("payrollpdf");

            // 2) Create if missing
            if (!outDir.exists()) outDir.mkdirs();

            // 3) If still not created (rare permission issue), fallback to user home
            if (!outDir.exists()) {
                outDir = new File(System.getProperty("user.home"), "payrollpdf");
                if (!outDir.exists()) outDir.mkdirs();
            }

            // ✅ File name (same pattern you already use)
            String empNo = "";
            try {
                Object emp = lastPayslip.emp;
                if (emp != null) {
                    // common field names
                    try { empNo = String.valueOf(emp.getClass().getField("empNo").get(emp)); } catch (Exception ignored) {}
                    if (empNo == null || empNo.trim().isEmpty()) {
                        try { empNo = String.valueOf(emp.getClass().getField("employeeNo").get(emp)); } catch (Exception ignored) {}
                    }
                    if (empNo == null || empNo.trim().isEmpty()) {
                        try { empNo = String.valueOf(emp.getClass().getField("id").get(emp)); } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}

            if (empNo == null || empNo.trim().isEmpty()) empNo = "EMP";

            String end = (lastPayslip.periodEnd != null && !lastPayslip.periodEnd.trim().isEmpty())
                    ? lastPayslip.periodEnd.trim()
                    : "period";

            String fileName = buildPayslipFileName(lastPayslip.emp, empNo, tfStart.getText(), tfEnd.getText());
            File outFile = new File(outDir, fileName);

            // ✅ Generate PDF (NO chooser)
            util.PDFUtil.generatePayslipPDF(lastPayslip, outFile);


            // 🔥 Try auto-open immediately

            boolean opened = tryOpenGeneratedPDF(outFile);


            if (!opened) {

                
                JOptionPane.showMessageDialog(this,

                        "Payslip PDF saved ✅\n" + outFile.getAbsolutePath() + "\n\nAuto-open failed (no default PDF app).\nI opened the folder instead.",

                        "PDF Generated",

                        JOptionPane.INFORMATION_MESSAGE);

            } else {

                JOptionPane.showMessageDialog(this,

                        "Payslip PDF saved ✅\n" + outFile.getAbsolutePath(),

                        "PDF Generated",

                        JOptionPane.INFORMATION_MESSAGE);

            }
} catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "PDF error: " + ex.getMessage());
        }
    }

    /**
     * Upload/Send = publish the payslip into the ONLINE database (Azure MySQL) so employees
     * on other devices can download it.
     *
     * Also keeps a local copy in payrollpdf/published for admin verification.
     *
     * IMPORTANT: Does NOT touch any timezone / payroll computation logic.
     */
    private void publishPayslip() {
        try {
            if (lastPayslip == null) {
                JOptionPane.showMessageDialog(this, "Compute a payslip first.");
                return;
            }

            // Published folder lives beside your normal generated folder
            File publishedDir = new File("payrollpdf", "published");
            if (!publishedDir.exists()) publishedDir.mkdirs();

            if (!publishedDir.exists()) {
                // fallback to user home if relative folder can't be created
                publishedDir = new File(System.getProperty("user.home"), "payrollpdf/published");
                if (!publishedDir.exists()) publishedDir.mkdirs();
            }

            // File name uses the SAME logic as Generate PDF
            String empNo = "";
            try {
                Object emp = lastPayslip.emp;
                if (emp != null) {
                    try { empNo = String.valueOf(emp.getClass().getField("empNo").get(emp)); } catch (Exception ignored) {}
                    if (empNo == null || empNo.trim().isEmpty()) {
                        try { empNo = String.valueOf(emp.getClass().getField("employeeNo").get(emp)); } catch (Exception ignored) {}
                    }
                    if (empNo == null || empNo.trim().isEmpty()) {
                        try { empNo = String.valueOf(emp.getClass().getField("id").get(emp)); } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}

            if (empNo == null || empNo.trim().isEmpty()) empNo = "EMP";

            String fileName = buildPayslipFileName(lastPayslip.emp, empNo, tfStart.getText(), tfEnd.getText());
            File outFile = new File(publishedDir, fileName);

            // Generate PDF directly to published folder
            PDFUtil.generatePayslipPDF(lastPayslip, outFile);

            // ---- Upload to ONLINE DB (BLOB) ----
            // Dates are stored as DATE (no timezone), so it won't affect your PH timestamps.
            LocalDate s = LocalDate.parse(tfStart.getText().trim(), ISO);
            LocalDate e = LocalDate.parse(tfEnd.getText().trim(), ISO);
            int empId = (lastPayslip.emp != null) ? lastPayslip.emp.empId : 0;
            if (empId <= 0) {
                throw new IllegalStateException("Employee ID is missing. Cannot publish payslip.");
            }
            byte[] pdfBytes = Files.readAllBytes(outFile.toPath());
            PayslipPublishDAO.upsert(empId, s, e, fileName, pdfBytes);

            // Open folder for quick verification (optional)
            
            JOptionPane.showMessageDialog(this,
                    "Payslip published ✅\n" + outFile.getAbsolutePath() +
                            "\n\nEmployees can download this cutoff from their portal.",
                    "Payslip Published",
                    JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Publish error: " + ex.getMessage());
        }
    }



    private double readDoubleProperty(Object obj, String... names) {
        if (obj == null || names == null) return 0.0;
        for (String n : names) {
            if (n == null || n.isEmpty()) continue;
            // try getter first (getX / isX)
            try {
                String cap = n.substring(0, 1).toUpperCase() + n.substring(1);
                try {
                    java.lang.reflect.Method m = obj.getClass().getMethod("get" + cap);
                    Object v = m.invoke(obj);
                    if (v instanceof Number) return ((Number) v).doubleValue();
                } catch (NoSuchMethodException ignore) {
                    // try exact method name
                    try {
                        java.lang.reflect.Method m2 = obj.getClass().getMethod(n);
                        Object v2 = m2.invoke(obj);
                        if (v2 instanceof Number) return ((Number) v2).doubleValue();
                    } catch (NoSuchMethodException ignore2) { /* fallthrough */ }
                }
            } catch (Exception ignore) { /* fallthrough */ }

            // try field
            try {
                java.lang.reflect.Field f = obj.getClass().getDeclaredField(n);
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v instanceof Number) return ((Number) v).doubleValue();
            } catch (Exception ignore) { /* ignore */ }
        }
        return 0.0;
    }

    private double getEmployeeRegularHolidayRate(Employee emp) {
        return readDoubleProperty(emp,
                // common names
                "regularHolidayRate",
                "regularHolidayDayRate",
                "regHolidayRate",
                "regHolidayDayRate",
                "regularHolidayRatePerDay",
                // PH-style / UI label variants
                "regularHolidayRatePhp",
                "regularHolidayRatePHP",
                "regularHolidayRatePhpPerDay",
                "regularHolidayRatePHPPerDay",
                "regularHolidayRatePerDayPhp",
                "regularHolidayRatePerDayPHP",
                "regHolidayRatePhp",
                "regHolidayRatePHP",
                "regHolidayRatePhpPerDay",
                "regHolidayRatePHPPerDay",
                // very generic fallbacks (some codebases use these)
                "holidayRateRegular",
                "holidayRateRegularDay",
                "holidayRegularRate");
    }

    private double getEmployeeSpecialHolidayRate(Employee emp) {
        return readDoubleProperty(emp,
                // common names
                "specialHolidayRate",
                "specialHolidayDayRate",
                "specHolidayRate",
                "specHolidayDayRate",
                "specialHolidayRatePerDay",
                // PH-style / UI label variants
                "specialHolidayRatePhp",
                "specialHolidayRatePHP",
                "specialHolidayRatePhpPerDay",
                "specialHolidayRatePHPPerDay",
                "specialHolidayRatePerDayPhp",
                "specialHolidayRatePerDayPHP",
                "specHolidayRatePhp",
                "specHolidayRatePHP",
                "specHolidayRatePhpPerDay",
                "specHolidayRatePHPPerDay",
                // very generic fallbacks
                "holidayRateSpecial",
                "holidayRateSpecialDay",
                "holidaySpecialRate");
    }

    private String buildPreviewHtml(Payslip p) {
        String primary = toHex(Theme.PRIMARY);
        String text = "#1f2937";
        String muted = "#6b7280";
        String line = "#e5e7eb";
        String bg = "#ffffff";

        Employee emp = null;
        try {
            EmployeeItem item = (EmployeeItem) cbEmp.getSelectedItem();
            if (item != null) emp = new EmployeeDAO().getById(item.empId);
        } catch (Exception ignore) {
            emp = null;
        }
        boolean isMonthly = (emp != null && emp.payType != null && emp.payType.trim().equalsIgnoreCase("MONTHLY"));

        // Some older computations only fill holidayPremiumPay (total) and leave the split
        // fields (regularHolidayPay/specialHolidayPay and day rates) as 0.00.
        // For preview purposes, derive a reasonable split & implied rates from hours.
        double regHolPayDisp = p.regularHolidayPay;
        double specHolPayDisp = p.specialHolidayPay;
        if ((regHolPayDisp == 0.0 && specHolPayDisp == 0.0) && p.holidayPremiumPay > 0.0) {
            double rh = Math.max(0.0, p.regularHolidayHours);
            double sh = Math.max(0.0, p.specialHolidayHours);
            double denom = rh + sh;
            if (denom > 0.0) {
                regHolPayDisp = p.holidayPremiumPay * (rh / denom);
                specHolPayDisp = p.holidayPremiumPay - regHolPayDisp;
            } else {
                // no holiday hours recorded; show everything as total
                regHolPayDisp = p.holidayPremiumPay;
                specHolPayDisp = 0.0;
            }
        }

        double regHolRateDisp = isMonthly ? 0.0 : (getEmployeeRegularHolidayRate(emp) > 0.0 ? getEmployeeRegularHolidayRate(emp) : p.regularHolidayDayRate);
        if (!isMonthly && regHolRateDisp == 0.0 && regHolPayDisp > 0.0 && p.regularHolidayHours > 0.0) {
            regHolRateDisp = regHolPayDisp / (p.regularHolidayHours / 8.0);
        }

        double specHolRateDisp = isMonthly ? 0.0 : (getEmployeeSpecialHolidayRate(emp) > 0.0 ? getEmployeeSpecialHolidayRate(emp) : p.specialHolidayDayRate);
        if (!isMonthly && specHolRateDisp == 0.0 && specHolPayDisp > 0.0 && p.specialHolidayHours > 0.0) {
            specHolRateDisp = specHolPayDisp / (p.specialHolidayHours / 8.0);
        }

        String net = Money.php(p.netPay);

        return "<html><head>"
                + "<style>"
                + "body{font-family:Segoe UI,Arial,sans-serif;font-size:12.5px;color:" + text + ";background:" + bg + ";}"
                + ".wrap{padding:14px;}"
                + ".title{font-size:14px;font-weight:700;color:" + primary + ";margin-bottom:6px;}"
                + ".sub{color:" + muted + ";margin-bottom:12px;}"
                + ".card{border:1px solid " + line + ";border-radius:14px;padding:12px;margin-bottom:10px;}"
                + ".row{display:flex;justify-content:space-between;gap:16px;}"
                + ".col{flex:1;}"
                + ".h{font-size:11px;color:" + muted + ";text-transform:uppercase;letter-spacing:.06em;margin-bottom:8px;}"
                + "table{width:100%;border-collapse:collapse;}"
                + "td{padding:6px 0;border-bottom:1px dashed " + line + ";}"
                + "td.k{color:" + muted + ";width:65%;}"
                + "td.v{font-weight:600;text-align:right;}"
                + ".big{font-size:18px;font-weight:800;color:" + primary + ";}"
                + ".pill{display:inline-block;padding:3px 8px;border-radius:999px;border:1px solid " + line + ";color:" + muted + ";font-size:11px;}"
                + ".dedBreak{margin-top:6px;font-size:11.5px;color:" + muted + ";line-height:1.45;}"
                + "</style>"
                + "</head><body>"
                + "<div class='wrap'>"
                + "<div class='title'>Computed Payslip</div>"
                + "<div class='sub'>Pay period: <span class='pill'>" + esc(p.periodStart) + " → " + esc(p.periodEnd) + "</span></div>"

                + "<div class='card'>"
                + "<div class='row'>"
                + "<div class='col'>"
                + "<div class='h'>Hours Breakdown</div>"
                + "<table>"
                + tr("Regular Day Hours", fmtHMDec(p.regularDayHours))
                + tr("Regular Day OT", fmtHMDec(p.regularDayOtHours))
                + tr("Regular Holiday Hours", fmtHMDec(p.regularHolidayHours))
                + tr("Regular Holiday OT", fmtHMDec(p.regularHolidayOtHours))
                + tr("Special Holiday Hours", fmtHMDec(p.specialHolidayHours))
                + tr("Special Holiday OT", fmtHMDec(p.specialHolidayOtHours))
                + tr("Total Hours", fmtHMDec(p.totalHours))
                + tr("Total OT Hours", fmtHMDec(p.totalOtHours))
                + "</table>"
                + "</div>"
                + "<div class='col'>"
                + "<div class='h'>Rates</div>"
                + "<table>"
                + tr("Daily Rate", Money.php(p.dailyRate))
                + tr("Hourly Rate", Money.php(p.hourlyRate))
                + tr("OT Rate", Money.php(p.otRateUsed))
                + tr("Reg Holiday Rate", Money.php(regHolRateDisp) + "/day")
                + tr("Spec Holiday Rate", Money.php(specHolRateDisp) + "/day")
                + "</table>"
                + "</div>"
                + "</div>"
                + "</div>"

                + "<div class='card'>"
                + "<div class='row'>"
                + "<div class='col'>"
                + "<div class='h'>Earnings</div>"
                + "<table>"
                + tr("Basic Pay", Money.php(p.basicPay))
                + tr("OT Pay", Money.php(p.overtimePay))
                + tr("Regular Holiday Pay", Money.php(regHolPayDisp))
                + tr("Special Holiday Pay", Money.php(specHolPayDisp))
                + tr("Holiday Pay (Total)", Money.php(p.holidayPremiumPay))
                + (p.incentives != 0 ? tr("Incentives", Money.php(p.incentives)) : "")
                + (p.thirteenthMonthPay != 0 ? tr("13th Month Pay", Money.php(p.thirteenthMonthPay)) : "")
                + (p.bonusPay != 0 ? tr("Bonus", Money.php(p.bonusPay)) : "")
                + "</table>"
                + "</div>"
                + "<div class='col'>"
                + "<div class='h'>Summary</div>"
                + "<table>"
                + tr("Gross Pay", Money.php(p.grossPay))
                + tr("Deduction", Money.php(p.deductions))
                + "</table>"
                + buildDeductionBreakdownHtml(p)
                + "<div style='margin-top:10px;border-top:1px solid " + line + ";padding-top:10px;display:flex;justify-content:space-between;align-items:end;'>"
                + "<div class='h' style='margin:0;'>Net Pay</div>"
                + "<div class='big'>" + esc(net) + "</div>"
                + "</div>"
                + "</div>"
                + "</div>"
                + "</div>"

                + "</div></body></html>";
    }

    private String buildDeductionBreakdownHtml(Payslip p) {
        String cashNote = (p.cashAdvanceNote == null) ? "" : p.cashAdvanceNote.trim();
        String otherNote = (p.otherDeductionNote == null) ? "" : p.otherDeductionNote.trim();

        boolean any = (p.cashAdvanceDeduction > 0)
                || (p.smartBillingDeduction > 0)
                || (p.otherDeduction > 0)
                || (p.sssDeduction > 0)
                || (p.pagibigDeduction > 0)
                || (p.philhealthDeduction > 0)
                || !cashNote.isEmpty()
                || !otherNote.isEmpty();

        if (!any) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<div class='dedBreak'>");

        if (p.cashAdvanceDeduction > 0 || !cashNote.isEmpty()) {
            sb.append("• Cash Advance: <b>").append(esc(Money.php(p.cashAdvanceDeduction))).append("</b>");
            if (!cashNote.isEmpty()) {
                sb.append(" <span style='color:#9ca3af'>[").append(esc(cashNote)).append("]</span>");
            }
            sb.append("<br>");
        }
        if (p.smartBillingDeduction > 0) {
            sb.append("• Smart Billing: <b>").append(esc(Money.php(p.smartBillingDeduction))).append("</b><br>");
        }
        if (p.otherDeduction > 0 || !otherNote.isEmpty()) {
            sb.append("• Others: <b>").append(esc(Money.php(p.otherDeduction))).append("</b>");
            if (!otherNote.isEmpty()) {
                sb.append(" <span style='color:#9ca3af'>[").append(esc(otherNote)).append("]</span>");
            }
            sb.append("<br>");
        }

        if (p.sssDeduction > 0) {
            sb.append("• SSS: <b>").append(esc(Money.php(p.sssDeduction))).append("</b><br>");
        }
        if (p.pagibigDeduction > 0) {
            sb.append("• Pag-IBIG: <b>").append(esc(Money.php(p.pagibigDeduction))).append("</b><br>");
        }
        if (p.philhealthDeduction > 0) {
            sb.append("• PhilHealth: <b>").append(esc(Money.php(p.philhealthDeduction))).append("</b><br>");
        }

        sb.append("</div>");
        return sb.toString();
    }

    // ================= HELPERS (kept from your file style) =================
    private static String tr(String k, String v) {
        return "<tr><td class='k'>" + esc(k) + "</td><td class='v'>" + esc(v) + "</td></tr>";
    }

    private static String fmtHMDec(double hours) {
        return HoursUtil.formatHM(hours) + " (" + DF2.format(hours) + ")";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String toHex(Color c) {
        String h = Integer.toHexString(c.getRGB() & 0xFFFFFF);
        while (h.length() < 6) h = "0" + h;
        return "#" + h;
    }

    private static double parseDouble(String s) {
        try {
            if (s == null) return 0;
            String x = s.trim();
            if (x.isEmpty()) return 0;
            return Double.parseDouble(x);
        } catch (Exception e) {
            return 0;
        }
    }

    private static Double parseNullableDouble(String s) {
        try {
            if (s == null) return null;
            String x = s.trim();
            if (x.isEmpty()) return null;
            return Double.parseDouble(x);
        } catch (Exception e) {
            return null;
        }
    }

    private static double parseMoney(String s) {
        try {
            if (s == null) return 0;
            String x = s.trim().replace(",", "");
            if (x.isEmpty()) return 0;
            return Double.parseDouble(x);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String formatMoney(double v) {
        return new java.text.DecimalFormat("#,##0.00").format(v);
    }

    // ========= small classes =========
    private static class EmployeeItem {
        final int empId;
        final String empNo;
        final String label;
        final String payType;

        EmployeeItem(int empId, String empNo, String label, String payType) {
            this.empId = empId;
            this.empNo = empNo;
            this.label = label;
            this.payType = payType;
        }

        @Override public String toString() {
            return (empNo == null ? "" : empNo) + " - " + (label == null ? "" : label);
        }
    }

    private static class PeriodItem {
        final LocalDate start;
        final LocalDate end;
        PeriodItem(LocalDate s, LocalDate e) { start = s; end = e; }
        @Override public String toString() {
            String sm = start.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH);
            String em = end.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH);
            if (start.getYear() != end.getYear()) {
                return start.getYear() + " " + sm + " " + start.getDayOfMonth() + " – " + end.getYear() + " " + em + " " + end.getDayOfMonth();
            }
            if (start.getMonth() != end.getMonth()) {
                return start.getYear() + " " + sm + " " + start.getDayOfMonth() + " – " + em + " " + end.getDayOfMonth();
            }
            return start.getYear() + " " + sm + " " + start.getDayOfMonth() + "–" + end.getDayOfMonth();
        }
    }
    // ✅ Called by DashboardFrame when Employees list changes (deactivate/reactivate/delete)
 // ✅ Called by DashboardFrame when Employees list changes (deactivate/reactivate/delete)
    public void refreshEmployeesKeepSelection() {
        Integer keepId = null;
        Object sel = cbEmp.getSelectedItem();
        if (sel instanceof EmployeeItem it) {
            keepId = it.empId;
        }

        loadEmployees();

        if (keepId != null) {
            for (int i = 0; i < cbEmp.getItemCount(); i++) {
                EmployeeItem it = cbEmp.getItemAt(i);
                if (it != null && it.empId == keepId) {
                    cbEmp.setSelectedIndex(i);
                    break;
                }
            }
        }

        cbEmp.revalidate();
        cbEmp.repaint();
    }

    

    
    

    // ===== Duration dropdown picker (Hrs/Mins) bound to an existing HH:MM JTextField =====
    private JComponent makeDurationPicker(JTextField backingField) {
        // Panel with top labels "Hrs" and "Mins", and two JComboBoxes.
        // IMPORTANT: We keep the combos NON-EDITABLE to avoid FlatLaf editor text bugs
        // (where the selected value is stored but the box looks blank).
        // We still allow "typing numbers" by implementing a small key buffer that jumps to the typed value.
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);

        DefaultComboBoxModel<Integer> hourModel = new DefaultComboBoxModel<>();
        for (int i = 0; i <= 999; i++) hourModel.addElement(i);

        DefaultComboBoxModel<Integer> minModel = new DefaultComboBoxModel<>();
        for (int i = 0; i < 60; i++) minModel.addElement(i);

        final JComboBox<Integer> cbH = new JComboBox<>(hourModel);
        final JComboBox<Integer> cbM = new JComboBox<>(minModel);

        cbH.setEditable(false);
        cbM.setEditable(false);

        Dimension boxSize = new Dimension(76, 28);
        cbH.setPreferredSize(boxSize);
        cbM.setPreferredSize(boxSize);
        cbH.setMinimumSize(boxSize);
        cbM.setMinimumSize(boxSize);

        // Renderer for both dropdown list and "selected item" display
        ListCellRenderer<? super Integer> numRenderer = (list, value, index, isSelected, cellHasFocus) -> {
            JLabel lbl = new JLabel(value == null ? "" : String.valueOf(value));
            lbl.setOpaque(true);
            lbl.setBorder(new EmptyBorder(2, 6, 2, 6));
            if (isSelected) {
                lbl.setBackground(list.getSelectionBackground());
                lbl.setForeground(list.getSelectionForeground());
            } else {
                lbl.setBackground(list.getBackground());
                lbl.setForeground(list.getForeground());
            }
            return lbl;
        };
        cbH.setRenderer(numRenderer);
        cbM.setRenderer(numRenderer);

        // Top labels
        JLabel lH = new JLabel("Hrs");
        JLabel lM = new JLabel("Mins");
        lH.setFont(lH.getFont().deriveFont(Font.PLAIN, 11f));
        lM.setFont(lM.getFont().deriveFont(Font.PLAIN, 11f));
        lH.setForeground(new Color(0x666666));
        lM.setForeground(new Color(0x666666));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(0, 0, 0, 10);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.NONE;

        // Row 0 labels
        g.gridy = 0; g.gridx = 0;
        p.add(lH, g);
        g.gridx = 1;
        p.add(lM, g);

        // Row 1 combos
        g.gridy = 1; g.gridx = 0; g.fill = GridBagConstraints.HORIZONTAL;
        p.add(cbH, g);
        g.gridx = 1;
        p.add(cbM, g);

        final boolean[] updating = new boolean[] { false };

        Runnable syncToField = () -> {
            if (updating[0]) return;
            updating[0] = true;

            Integer hObj = (Integer) cbH.getSelectedItem();
            Integer mObj = (Integer) cbM.getSelectedItem();
            int h = hObj == null ? 0 : hObj;
            int m = mObj == null ? 0 : mObj;

            if (h < 0) h = 0;
            if (h > 999) h = 999;
            if (m < 0) m = 0;
            if (m > 59) m = 59;

            // Ensure clamped values are reflected in UI
            cbH.setSelectedItem(h);
            cbM.setSelectedItem(m);

            backingField.setText(String.format("%02d:%02d", h, m));
            updating[0] = false;
        };

        Runnable syncFromField = () -> {
            if (updating[0]) return;
            updating[0] = true;

            int[] hm = parseHHMM(backingField.getText());
            cbH.setSelectedItem(hm[0]);
            cbM.setSelectedItem(hm[1]);

            // Force repaint (some LAFs can be lazy after programmatic change)
            cbH.revalidate(); cbH.repaint();
            cbM.revalidate(); cbM.repaint();

            updating[0] = false;
        };

        // Initial sync
        syncFromField.run();

        cbH.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) syncToField.run();
        });
        cbM.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) syncToField.run();
        });

        // When backing field changes programmatically, reflect it in combo display
        backingField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { syncFromField.run(); }
            @Override public void removeUpdate(DocumentEvent e) { syncFromField.run(); }
            @Override public void changedUpdate(DocumentEvent e) { syncFromField.run(); }
        });

        // Allow typing numbers like your Logbook picker:
        // Type "6" => selects 6, type "12" quickly => selects 12, etc.
        installNumericTypeSelect(cbH, 0, 999);
        installNumericTypeSelect(cbM, 0, 59);

        return p;
    }

    private void installNumericTypeSelect(JComboBox<Integer> combo, int min, int max) {
        final StringBuilder buf = new StringBuilder();
        final Timer clearTimer = new Timer(650, e -> buf.setLength(0));
        clearTimer.setRepeats(false);

        combo.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyTyped(java.awt.event.KeyEvent e) {
                char c = e.getKeyChar();
                if (!Character.isDigit(c)) return;

                buf.append(c);
                clearTimer.restart();

                try {
                    int v = Integer.parseInt(buf.toString());
                    if (v < min) v = min;
                    if (v > max) v = max;
                    combo.setSelectedItem(v);
                } catch (Exception ignored) { }
            }
        });
    }

    private void tweakComboEditor(JComboBox<Integer> combo) {
        try {
            // Force a plain text editor so selected values actually appear in the box (FlatLaf can be picky here).
            combo.setEditor(new javax.swing.plaf.basic.BasicComboBoxEditor() {
                @Override
                public void setItem(Object anObject) {
                    super.setItem(anObject == null ? "" : String.valueOf(anObject));
                }
            });

            Component ec = combo.getEditor().getEditorComponent();
            if (ec instanceof JTextField tf) {
                tf.setEditable(true);
                tf.setFocusable(true);
                tf.setForeground(UIManager.getColor("TextField.foreground"));
                tf.setBackground(UIManager.getColor("TextField.background"));
                tf.setCaretColor(UIManager.getColor("TextField.foreground"));
                tf.setBorder(new EmptyBorder(2, 6, 2, 6));
            }
            combo.setFocusable(true);
        } catch (Exception ignored) {}
    }

    private int getComboInt(JComboBox<Integer> combo, int min, int max) {
        Object v = combo.getEditor().getItem();
        int n;
        try {
            n = Integer.parseInt(String.valueOf(v).trim());
        } catch (Exception e) {
            n = min;
        }
        if (n < min) n = min;
        if (n > max) n = max;
        // Keep combo selection in sync with clamped value
        combo.setSelectedItem(n);
        return n;
    }

    // returns [hours, minutes]
    private int[] parseHHMM(String s) {
        if (s == null) return new int[] {0, 0};
        s = s.trim();
        if (s.isEmpty()) return new int[] {0, 0};

        // Accept "HH:MM" or "H:MM"
        if (s.contains(":")) {
            String[] p = s.split(":");
            int h = safeInt(p.length > 0 ? p[0] : "0");
            int m = safeInt(p.length > 1 ? p[1] : "0");
            if (h < 0) h = 0;
            if (h > 999) h = 999;
            if (m < 0) m = 0;
            if (m > 59) m = 59;
            return new int[] {h, m};
        }

        // Accept legacy "0h 00m"
        if (s.contains("h") || s.contains("m")) {
            String onlyNums = s.replaceAll("[^0-9 ]", " ").trim();
            String[] parts = onlyNums.split("\s+");
            int h = parts.length > 0 ? safeInt(parts[0]) : 0;
            int m = parts.length > 1 ? safeInt(parts[1]) : 0;
            if (h < 0) h = 0;
            if (h > 999) h = 999;
            if (m < 0) m = 0;
            if (m > 59) m = 59;
            return new int[] {h, m};
        }

        // Just a number -> treat as hours
        int h = safeInt(s);
        if (h < 0) h = 0;
        if (h > 999) h = 999;
        return new int[] {h, 0};
    }

    private int safeInt(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }



    // --- Auto-open helpers (PDF) ---
    

    // =========================
    // Payslip PDF filename format
    // payslip_<lastname>_<employeenumber>_<1-15|16-30>.pdf
    // =========================
    private String buildPayslipFileName(Object emp, String empNo, String startDateStr, String endDateStr) {
        String lastName = extractLastName(emp);
        String periodLabel = computePayPeriodLabel(startDateStr, endDateStr);

        String safeLast = sanitizeFilePart(lastName).toLowerCase();
        String safeEmpNo = sanitizeFilePart(empNo);
        String safePeriod = sanitizeFilePart(periodLabel);

        return "payslip_" + safeLast + "_" + safeEmpNo + "_" + safePeriod + ".pdf";
    }

    private String computePayPeriodLabel(String startDateStr, String endDateStr) {
        int startDay = parseDayOfMonth(startDateStr);
        int endDay = parseDayOfMonth(endDateStr);

        // If we can't parse, fall back to end date string (keeps it unique & readable)
        if (startDay <= 0 || endDay <= 0) {
            if (startDateStr != null && !startDateStr.trim().isEmpty()
                    && endDateStr != null && !endDateStr.trim().isEmpty()) {
                return startDateStr.trim() + "_to_" + endDateStr.trim();
            }
            return "period";
        }

        // Your requested labels:
        // 1-15 for first half, 16-30 for second half (even if month has 31)
        if (startDay <= 15 && endDay <= 15) return "1-15";
        return "16-30";
    }

    private int parseDayOfMonth(String isoDate) {
        try {
            if (isoDate == null) return -1;
            String s = isoDate.trim();
            if (s.isEmpty()) return -1;
            // expected: yyyy-MM-dd
            java.time.LocalDate d = java.time.LocalDate.parse(s);
            return d.getDayOfMonth();
        } catch (Exception ignored) {
            return -1;
        }
    }

    private String extractLastName(Object emp) {
        // Try common getters/fields first (safe reflection)
        String ln = reflectString(emp, "getLastName");
        if (isNonEmpty(ln)) return ln;

        ln = reflectString(emp, "getLastname");
        if (isNonEmpty(ln)) return ln;

        ln = reflectString(emp, "getSurname");
        if (isNonEmpty(ln)) return ln;

        // Try full name / name
        String full = reflectString(emp, "getFullName");
        if (!isNonEmpty(full)) full = reflectString(emp, "getName");
        if (!isNonEmpty(full)) full = reflectString(emp, "toString");

        if (!isNonEmpty(full)) return "unknown";

        // last token as last name
        String[] parts = full.trim().split("\\s+");
        return parts.length > 0 ? parts[parts.length - 1].trim() : "unknown";
    }

    private String reflectString(Object obj, String methodName) {
        try {
            if (obj == null) return null;
            java.lang.reflect.Method m = obj.getClass().getMethod(methodName);
            Object v = m.invoke(obj);
            return v == null ? null : String.valueOf(v);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isNonEmpty(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private String sanitizeFilePart(String s) {
        if (s == null) return "unknown";
        String out = s.trim();
        if (out.isEmpty()) return "unknown";
        // remove characters invalid in Windows filenames
        out = out.replaceAll("[\\\\/:*?\"<>|]", "");
        // collapse spaces to underscores
        out = out.replaceAll("\\s+", "_");
        // avoid double underscores
        out = out.replaceAll("_+", "_");
        return out;
    }

private boolean tryOpenGeneratedPDF(java.io.File pdfFile) {
        if (pdfFile == null || !pdfFile.exists()) return false;

        // 1) Try default PDF app association
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop d = java.awt.Desktop.getDesktop();
                if (d.isSupported(java.awt.Desktop.Action.OPEN)) {
                    d.open(pdfFile);
                    return true;
                }
            }
        } catch (Exception ignored) {}

        // 2) Try opening in default browser
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop d = java.awt.Desktop.getDesktop();
                if (d.isSupported(java.awt.Desktop.Action.BROWSE)) {
                    d.browse(pdfFile.toURI());
                    return true;
                }
            }
        } catch (Exception ignored) {}

        // 3) Windows fallback: shell "start"
        try {
            new ProcessBuilder("cmd", "/c", "start", "", pdfFile.getAbsolutePath()).start();
            return true;
        } catch (Exception ignored) {}

        return false;
    }

    private void openPDFLocation(java.io.File pdfFile) {
        try {
            if (pdfFile == null) return;
            java.io.File folder = pdfFile.getParentFile();
            if (folder != null && folder.exists()) {
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().open(folder);
                    return;
                }
                new ProcessBuilder("explorer.exe", folder.getAbsolutePath()).start();
            }
        } catch (Exception ignored) {}
    }

}