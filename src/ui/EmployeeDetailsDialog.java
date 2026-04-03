package ui;

import com.formdev.flatlaf.FlatClientProperties;

import dao.DB;
import dao.EmployeeDAO;
import model.Employee;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EmployeeDetailsDialog extends JDialog {

    private static final ZoneId PH_ZONE = ZoneId.of("Asia/Manila");

    // Soft palette
    private static final Color BG = new Color(0xF6F4EE);
    private static final Color CARD = new Color(0xFFFFFF);
    private static final Color CARD_ALT = new Color(0xFBFBF8);
    private static final Color LINE = new Color(0xE6E1D6);
    private static final Color TEXT = new Color(0x1F2328);
    private static final Color MUTED = new Color(0x6B7280);

    private static final Color CHIP_BLUE_BG = new Color(0xEAF2FF);
    private static final Color CHIP_BLUE_FG = new Color(0x1D4ED8);
    private static final Color CHIP_GREEN_BG = new Color(0xEAF7EE);
    private static final Color CHIP_GREEN_FG = new Color(0x166534);
    private static final Color CHIP_RED_BG = new Color(0xFEE2E2);
    private static final Color CHIP_RED_FG = new Color(0x991B1B);
    private static final Color CHIP_GRAY_BG = new Color(0xF1F5F9);
    private static final Color CHIP_GRAY_FG = new Color(0x334155);

    private final int empId;

    // If this is set, dialog is "wired to payroll" and period is locked
    private final LocalDate lockedStart;
    private final LocalDate lockedEnd;

    // If not locked, we use 15-day toggle
    private LocalDate periodAnchor = LocalDate.now(PH_ZONE);

    // UI fields
    private JLabel lblName;
    private JLabel chipPayType;
    private JLabel chipStatus;

    private InfoRow rowEmpNo, rowPosition, rowRate, rowPayType, rowStatus,
            rowDob, rowCivilStatus, rowSss, rowPhilHealth, rowTin, rowPagIbig,
            rowPersonalContact, rowCompanyContact, rowPersonalEmail, rowCompanyEmail, rowAddress;

    private JLabel lblUsername, lblRole;
    private JPasswordField pfPassword;
    private JButton btnShowHide;

    private JLabel lblPeriod;
    private JButton btnPrev, btnNext;

    private StatTile tileDays, tileTotal, tileOT;

    private WrapPanel workedDatesWrap;
    private JScrollPane workedDatesScroll;

    private char defaultEcho;

    /** Normal mode (15-day toggle) */
    public EmployeeDetailsDialog(Window owner, int empId) {
        this(owner, empId, null, null);
    }

    /** Payroll-wired mode (locked payroll range) */
    public EmployeeDetailsDialog(Window owner, int empId, LocalDate payrollStart, LocalDate payrollEnd) {
        super(owner, "Employee Details", ModalityType.APPLICATION_MODAL);
        this.empId = empId;
        this.lockedStart = payrollStart;
        this.lockedEnd = payrollEnd;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);

        setContentPane(buildUI());
        pack();

        // Use a fixed, stable dialog size (prevents resizing when worked-dates list grows)
        Dimension fixed = new Dimension(1080, 700);
        setSize(fixed);
        setMinimumSize(fixed);
        setMaximumSize(fixed);
        setPreferredSize(fixed);

        setLocationRelativeTo(owner);

        refreshAll();
    }

    private JPanel buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(14, 14, 14, 14));

        root.add(buildTopBar(), BorderLayout.NORTH);

        JPanel body = new JPanel(new GridBagLayout());
        body.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        // IMPORTANT: Make LEFT clearly wider than RIGHT.
        // LEFT gets all extra horizontal space; RIGHT stays near its preferred width.
        gbc.weightx = 1.0;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(10, 0, 0, 10);
        body.add(buildLeftCard(), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.0;
        gbc.insets = new Insets(10, 0, 0, 0);
        body.add(buildRightCard(), gbc);

        root.add(body, BorderLayout.CENTER);

        return root;
    }

    private JComponent buildTopBar() {
        RoundedPanel top = new RoundedPanel(16);
        top.setBackground(CARD);
        top.setBorder(new EmptyBorder(12, 14, 12, 14));
        top.setLayout(new BorderLayout(10, 0));

        JLabel title = new JLabel("Employee Details");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setForeground(TEXT);

        JButton btnClose = pillButton("Close");
        btnClose.addActionListener(e -> dispose());

        top.add(title, BorderLayout.WEST);
        top.add(btnClose, BorderLayout.EAST);

        return top;
    }

    private JComponent buildLeftCard() {
        RoundedPanel card = new RoundedPanel(18);
        card.setBackground(CARD);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(14, 14, 14, 14));

        // Header (left aligned, smaller name)
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblName = new JLabel("-");
        lblName.setForeground(TEXT);
        lblName.setFont(lblName.getFont().deriveFont(Font.BOLD, 18f));
        lblName.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        chips.setOpaque(false);
        chips.setAlignmentX(Component.LEFT_ALIGNMENT);

        chipPayType = chipLabel("-", CHIP_BLUE_BG, CHIP_BLUE_FG);
        chipStatus = chipLabel("-", CHIP_GREEN_BG, CHIP_GREEN_FG);
        chips.add(chipPayType);
        chips.add(chipStatus);

        header.add(lblName);
        header.add(Box.createVerticalStrut(8));
        header.add(chips);

        card.add(header, BorderLayout.NORTH);


        // --- Info rows ---
        rowEmpNo = new InfoRow("Employee No");
        rowPosition = new InfoRow("Position");
        rowRate = new InfoRow("Rate");
        rowPayType = new InfoRow("Pay Type");
        rowStatus = new InfoRow("Status");
        rowDob = new InfoRow("Date of Birth");
        rowCivilStatus = new InfoRow("Civil Status");
        rowSss = new InfoRow("SSS Number");
        rowPhilHealth = new InfoRow("PhilHealth Number");
        rowTin = new InfoRow("TIN ID Number");
        rowPagIbig = new InfoRow("Pag-IBIG Number");
        rowPersonalContact = new InfoRow("Personal Contact No");
        rowCompanyContact = new InfoRow("Company Contact No");
        rowPersonalEmail = new InfoRow("Personal Email");
        rowCompanyEmail = new InfoRow("Company Email");
        rowAddress = new InfoRow("Address");

        // --- Premium sections container (scrollable) ---
        JPanel sections = new JPanel();
        sections.setOpaque(false);
        sections.setLayout(new BoxLayout(sections, BoxLayout.Y_AXIS));

        // Basic Info
        JPanel basicRows = rowsPanel(
                rowEmpNo,
                rowPosition,
                rowRate,
                rowPayType,
                rowStatus,
                rowDob,
                rowCivilStatus
        );
        sections.add(sectionBox("Basic Info", new DotIcon(new Color(0x2E7D32)), basicRows));
        sections.add(Box.createVerticalStrut(10));

        // Government IDs
        JPanel govRows = rowsPanel(
                rowSss,
                rowPhilHealth,
                rowTin,
                rowPagIbig
        );
        sections.add(sectionBox("Government IDs", new DotIcon(new Color(0x1D4ED8)), govRows));
        sections.add(Box.createVerticalStrut(10));

        // Contacts
        JPanel contactRows = rowsPanel(
                rowPersonalContact,
                rowCompanyContact,
                rowPersonalEmail,
                rowCompanyEmail
        );
        sections.add(sectionBox("Contacts", new DotIcon(new Color(0x7C3AED)), contactRows));
        sections.add(Box.createVerticalStrut(10));

        // Address
        JPanel addressRows = rowsPanel(rowAddress);
        sections.add(sectionBox("Address", new DotIcon(new Color(0xF59E0B)), addressRows));

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.setBorder(new EmptyBorder(12, 0, 0, 0));

        // Make the left info sections scrollable so the dialog never changes size.
        JScrollPane infoScroll = new JScrollPane(sections,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        infoScroll.setBorder(null);
        infoScroll.setOpaque(false);
        infoScroll.getViewport().setOpaque(false);
        infoScroll.getVerticalScrollBar().setUnitIncrement(16);

        center.add(infoScroll, BorderLayout.CENTER);
        card.add(center, BorderLayout.CENTER);

        return card;
    }

    private JComponent buildRightCard() {
        RoundedPanel card = new RoundedPanel(18);
        card.setBackground(CARD);
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(14, 14, 14, 14));

        // Keep the entire right column visually stable across period changes
        // (prevents any subtle reflow that can look like the dialog is resizing)
        // Keep the right column intentionally narrower so employee info gets more room.
        card.setPreferredSize(new Dimension(380, 10));
        card.setMinimumSize(new Dimension(380, 10));

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));

        // Ensure BoxLayout uses the same width for all children
        stack.setAlignmentX(Component.LEFT_ALIGNMENT);

        // =====================
        // Account content (wrapped in a premium section)
        // =====================
        JPanel accountContent = new JPanel(new GridBagLayout());
        accountContent.setOpaque(false);

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 0, 6, 10);
        g.anchor = GridBagConstraints.WEST;

        g.gridx = 0; g.gridy = 1;
        accountContent.add(labelMuted("Username"), g);
        g.gridx = 1; g.gridy = 1; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        lblUsername = new JLabel("-");
        lblUsername.setForeground(TEXT);
        lblUsername.setFont(lblUsername.getFont().deriveFont(Font.BOLD, 12.5f));
        accountContent.add(lblUsername, g);

        g.gridx = 0; g.gridy = 2; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        accountContent.add(labelMuted("Role"), g);
        g.gridx = 1; g.gridy = 2; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        lblRole = chipLabelInline("-");
        accountContent.add(lblRole, g);

        g.gridx = 0; g.gridy = 3; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        accountContent.add(labelMuted("Password"), g);

        g.gridx = 1; g.gridy = 3; g.weightx = 1; g.fill = GridBagConstraints.HORIZONTAL;
        pfPassword = new JPasswordField("-");
        pfPassword.setEditable(false);
        pfPassword.setBorder(new LineBorder(LINE, 1, true));
        pfPassword.setBackground(Color.WHITE);
        pfPassword.setPreferredSize(new Dimension(220, 34));
        pfPassword.setMinimumSize(new Dimension(220, 34));
        defaultEcho = pfPassword.getEchoChar();
        accountContent.add(pfPassword, g);

        g.gridx = 2; g.gridy = 3; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        btnShowHide = pillButton("Show");
        btnShowHide.addActionListener(this::togglePassword);
        accountContent.add(btnShowHide, g);

        JComponent accountBox = sectionBox("Account", new DotIcon(new Color(0x0F766E)), accountContent);
        accountBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));

        // =====================
        // Work summary content (period navigator + stats)
        // =====================
        JPanel workSummaryContent = new JPanel();
        workSummaryContent.setOpaque(false);
        workSummaryContent.setLayout(new BoxLayout(workSummaryContent, BoxLayout.Y_AXIS));

        // Period selector
        RoundedPanel period = new RoundedPanel(14);
        period.setBackground(Color.WHITE);
        period.setBorder(new LineBorder(LINE, 1, true));
        period.setLayout(new BorderLayout(8, 0));
        period.setPreferredSize(new Dimension(10, 44));
        period.setMinimumSize(new Dimension(10, 44));
        period.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        btnPrev = squareButton("<");
        btnNext = squareButton(">");

        lblPeriod = new JLabel("-", SwingConstants.CENTER);
        lblPeriod.setForeground(TEXT);
        lblPeriod.setFont(lblPeriod.getFont().deriveFont(Font.BOLD, 12.8f));
        // Keep the period bar stable even when text length changes
        lblPeriod.setPreferredSize(new Dimension(220, 28));
        lblPeriod.setMinimumSize(new Dimension(220, 28));

        btnPrev.addActionListener(e -> { goPrevPeriod(); refreshSummaryOnly(); });
        btnNext.addActionListener(e -> { goNextPeriod(); refreshSummaryOnly(); });

        period.add(btnPrev, BorderLayout.WEST);
        period.add(lblPeriod, BorderLayout.CENTER);
        period.add(btnNext, BorderLayout.EAST);

        workSummaryContent.add(period);
        workSummaryContent.add(Box.createVerticalStrut(10));

        // Stats row
        JPanel stats = new JPanel(new GridLayout(1, 3, 10, 0));
        stats.setOpaque(false);

        tileDays = new StatTile("Days Worked");
        tileTotal = new StatTile("Total Time");
        tileOT = new StatTile("Overtime");

        stats.add(tileDays);
        stats.add(tileTotal);
        stats.add(tileOT);

        workSummaryContent.add(stats);

        JComponent workSummaryBox = sectionBox("Work Summary", new DotIcon(new Color(0xF59E0B)), workSummaryContent);
        workSummaryBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 230));

        // Worked dates chips (scroll when many)
        JPanel workedDatesContent = new JPanel(new BorderLayout(0, 8));
        workedDatesContent.setOpaque(false);

        workedDatesWrap = new WrapPanel(FlowLayout.LEFT, 8, 8);
        workedDatesWrap.setOpaque(false);

        workedDatesScroll = new JScrollPane(workedDatesWrap,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        workedDatesScroll.setBorder(null);
        workedDatesScroll.getViewport().setOpaque(false);
        workedDatesScroll.setOpaque(false);

        // Fixed height so it NEVER changes when chips wrap differently on different machines
        int workedDatesHeight = 165;
        workedDatesScroll.setPreferredSize(new Dimension(10, workedDatesHeight));
        workedDatesScroll.setMinimumSize(new Dimension(10, workedDatesHeight));
        workedDatesScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, workedDatesHeight));

        workedDatesContent.add(workedDatesScroll, BorderLayout.CENTER);

        JComponent workedBox = sectionBox("Worked Dates", new DotIcon(new Color(0x2563EB)), workedDatesContent);
        workedBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 245));

        stack.add(accountBox);
        stack.add(Box.createVerticalStrut(10));
        stack.add(workSummaryBox);
        stack.add(Box.createVerticalStrut(10));
        stack.add(workedBox);

        card.add(stack, BorderLayout.CENTER);

        //  If payroll wired, lock the arrows
        if (lockedStart != null && lockedEnd != null) {
            btnPrev.setEnabled(false);
            btnNext.setEnabled(false);
        }

        return card;
    }

    private void refreshAll() {
        loadEmployeeAndAccount();
        refreshSummaryOnly();
    }

    private void refreshSummaryOnly() {
        LocalDate start;
        LocalDate end;

        if (lockedStart != null && lockedEnd != null) {
            start = lockedStart;
            end = lockedEnd;
        } else {
            start = periodStart(periodAnchor);
            end = periodEnd(periodAnchor);
        }

        lblPeriod.setText(formatPeriodLabel(start, end));

        WorkSummary summary = loadWorkSummary(empId, start, end);

        tileDays.setValue(summary.daysWorked + (summary.daysWorked == 1 ? " day" : " days"));
        tileTotal.setValue(fmtHM(summary.totalMinutes));
        tileOT.setValue(fmtHM(summary.otMinutes));

        renderWorkedDates(summary.workedDays, start, end);
    }

    private void loadEmployeeAndAccount() {
        try (Connection con = DB.getConnection()) {

            // Load employee core info via EmployeeDAO (safe with optional columns)
            Employee emp = EmployeeDAO.getById(empId);
            if (emp != null) {
                String empNo = nvl(emp.empNo);
                String fullName = buildFullName(emp.firstName, emp.middleName, emp.lastName);
                String pos = nvl(emp.position);
                String pay = nvl(emp.payType);
                String status = nvl(emp.status);
                String addr = nvl(emp.address);

                String personalContact = nvl(firstNonEmpty(emp.personalContactNo, emp.mobile));
                String companyContact = nvl(emp.companyContactNo);
                String personalEmail = nvl(emp.personalEmail);
                String companyEmail = nvl(emp.companyEmail);

                double rateD = emp.rate;

                lblName.setText(fullName);
                chipPayType.setText(pay.isEmpty() ? "-" : pay);
                chipStatus.setText(status.isEmpty() ? "-" : status);

                // Status chip color
                if (status.equalsIgnoreCase("INACTIVE")) {
                    styleChip(chipStatus, CHIP_RED_BG, CHIP_RED_FG);
                } else {
                    styleChip(chipStatus, CHIP_GREEN_BG, CHIP_GREEN_FG);
                }

                rowEmpNo.setValue(empNo);
                rowPosition.setValue(pos);
                rowRate.setValue(String.valueOf(rateD));
                rowPayType.setValue(pay);
                rowStatus.setValue(status);
                rowDob.setValue(nvl(emp.dateOfBirth));
                rowCivilStatus.setValue(nvl(emp.civilStatus));
                rowSss.setValue(nvl(emp.sssNo));
                rowPhilHealth.setValue(nvl(emp.philHealthNo));
                rowTin.setValue(nvl(emp.tinNo));
                rowPagIbig.setValue(nvl(emp.pagIbigNo));
                rowPersonalContact.setValue(personalContact);
                rowCompanyContact.setValue(companyContact);
                rowPersonalEmail.setValueMultiline(personalEmail);
                rowCompanyEmail.setValueMultiline(companyEmail);
                rowAddress.setValueMultiline(addr);
            }

            String sqlAcc = """
                SELECT username, password, role
                FROM users
                WHERE emp_id = ?
                LIMIT 1
            """;
            try (PreparedStatement ps = con.prepareStatement(sqlAcc)) {
                ps.setInt(1, empId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String u = nvl(rs.getString("username"));
                        String p = nvl(rs.getString("password"));
                        String r = nvl(rs.getString("role"));

                        lblUsername.setText(u.isBlank() ? "(no account)" : u);
                        lblRole.setText(r.isBlank() ? "-" : r);

                        pfPassword.setText(p.isBlank() ? "-" : p);
                        pfPassword.setEchoChar(defaultEcho);
                        btnShowHide.setText("Show");
                    } else {
                        lblUsername.setText("(no account)");
                        lblRole.setText("-");
                        pfPassword.setText("-");
                        pfPassword.setEchoChar((char) 0);
                        btnShowHide.setText("Show");
                    }
                }
            }

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to load employee/account:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private WorkSummary loadWorkSummary(int empId, LocalDate start, LocalDate endInclusive) {
        WorkSummary out = new WorkSummary();

        LocalDateTime from = start.atStartOfDay();
        LocalDateTime toExclusive = endInclusive.plusDays(1).atStartOfDay();

        String sql = """
            SELECT DATE(time_in) AS work_day,
                   SUM(TIMESTAMPDIFF(MINUTE, time_in, time_out)) AS mins
            FROM time_logs
            WHERE emp_id = ?
              AND time_out IS NOT NULL
              AND time_in >= ?
              AND time_in < ?
            GROUP BY DATE(time_in)
            ORDER BY work_day
        """;

        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, empId);
            ps.setTimestamp(2, Timestamp.valueOf(from));
            ps.setTimestamp(3, Timestamp.valueOf(toExclusive));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.sql.Date d = rs.getDate("work_day");
                    long mins = rs.getLong("mins");
                    if (d == null || mins <= 0) continue;

                    LocalDate day = d.toLocalDate();

                    out.workedDays.add(day);
                    out.daysWorked++;
                    out.totalMinutes += mins;

                    long reg = 8L * 60L;
                    if (mins > reg) out.otMinutes += (mins - reg);
                }
            }

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to load summary:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }

        return out;
    }

    private void renderWorkedDates(List<LocalDate> dates, LocalDate start, LocalDate end) {
        workedDatesWrap.removeAll();

        if (dates == null || dates.isEmpty()) {
            JLabel none = new JLabel("No logs in this period.");
            none.setForeground(MUTED);
            workedDatesWrap.add(none);
        } else {
            DateTimeFormatter chipFmt = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
            DateTimeFormatter tipFmt = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);

            for (LocalDate d : dates) {
                if (d.isBefore(start) || d.isAfter(end)) continue;

                JLabel chip = chipLabel(d.format(chipFmt), CHIP_GRAY_BG, CHIP_GRAY_FG);
                chip.setToolTipText(d.format(tipFmt));
                workedDatesWrap.add(chip);
            }
        }

        workedDatesWrap.revalidate();
        workedDatesWrap.repaint();
        workedDatesScroll.getViewport().revalidate();
        workedDatesScroll.revalidate();
        workedDatesScroll.repaint();

        SwingUtilities.invokeLater(() -> {
            workedDatesWrap.revalidate();
            workedDatesScroll.getViewport().revalidate();
            workedDatesScroll.getVerticalScrollBar().setValue(0);
        });
    }

    // 15-day toggle logic (only used when not payroll-wired)
    private LocalDate periodStart(LocalDate anchor) {
        int day = anchor.getDayOfMonth();
        if (day <= 15) return anchor.withDayOfMonth(1);
        return anchor.withDayOfMonth(16);
    }

    private LocalDate periodEnd(LocalDate anchor) {
        int day = anchor.getDayOfMonth();
        if (day <= 15) return anchor.withDayOfMonth(15);
        YearMonth ym = YearMonth.from(anchor);
        return anchor.withDayOfMonth(ym.lengthOfMonth());
    }

    private void goPrevPeriod() {
        if (lockedStart != null) return;
        LocalDate start = periodStart(periodAnchor);
        if (start.getDayOfMonth() == 1) {
            LocalDate prevMonth = start.minusMonths(1);
            periodAnchor = prevMonth.withDayOfMonth(16);
        } else {
            periodAnchor = start.withDayOfMonth(1);
        }
    }

    private void goNextPeriod() {
        if (lockedStart != null) return;
        LocalDate start = periodStart(periodAnchor);
        if (start.getDayOfMonth() == 1) {
            periodAnchor = start.withDayOfMonth(16);
        } else {
            LocalDate nextMonth = start.plusMonths(1);
            periodAnchor = nextMonth.withDayOfMonth(1);
        }
    }

    private String formatPeriodLabel(LocalDate start, LocalDate end) {
        DateTimeFormatter mmm = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);
        String month = start.format(mmm);
        return month + " " + start.getDayOfMonth() + "-" + end.getDayOfMonth() + ", " + start.getYear();
    }

    private void togglePassword(ActionEvent e) {
        if (pfPassword.getText().equals("-")) return;

        if (pfPassword.getEchoChar() == (char) 0) {
            pfPassword.setEchoChar(defaultEcho);
            btnShowHide.setText("Show");
        } else {
            pfPassword.setEchoChar((char) 0);
            btnShowHide.setText("Hide");
        }
    }

    private JButton pillButton(String text) {
        RoundedButton b = new RoundedButton(text, new Color(245, 245, 245), new Color(35, 35, 35));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setMargin(new Insets(7, 16, 7, 16));
        b.setPreferredSize(new Dimension(Math.max(92, b.getPreferredSize().width), 38));
        b.setCornerRadius(18);
        return b;
    }

    private JButton squareButton(String text) {
        RoundedButton b = new RoundedButton(text, new Color(245, 245, 245), new Color(35, 35, 35));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setMargin(new Insets(7, 12, 7, 12));
        b.setPreferredSize(new Dimension(44, 38));
        b.setCornerRadius(18);
        return b;
    }

    private JLabel labelMuted(String s) {
        JLabel l = new JLabel(s);
        l.setForeground(MUTED);
        l.setFont(l.getFont().deriveFont(12f));
        return l;
    }

    private JLabel chipLabel(String text, Color bg, Color fg) {
        JLabel l = new JLabel(text);
        l.setOpaque(true);
        l.setBackground(bg);
        l.setForeground(fg);
        l.setBorder(new EmptyBorder(4, 10, 4, 10));
        l.setFont(l.getFont().deriveFont(Font.BOLD, 11.5f));
        return l;
    }

    private JLabel chipLabelInline(String text) {
        return chipLabel(text, CHIP_GRAY_BG, CHIP_GRAY_FG);
    }

    private void setChip(JLabel chip, String text, Color bg, Color fg) {
        chip.setText(text == null || text.isBlank() ? "-" : text);
        chip.setBackground(bg);
        chip.setForeground(fg);
    }

    private void styleChip(JLabel chip, Color bg, Color fg) {
        chip.setOpaque(true);
        chip.setBackground(bg);
        chip.setForeground(fg);
        chip.setBorder(new EmptyBorder(4, 10, 4, 10));
        chip.setFont(chip.getFont().deriveFont(Font.BOLD, 11.5f));
    }


    // ===============================
    // Premium section UI helpers
    // ===============================

    private JPanel rowsPanel(InfoRow... rows) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        if (rows != null) {
            for (int i = 0; i < rows.length; i++) {
                p.add(rows[i]);
                if (i < rows.length - 1) {
                    p.add(Box.createVerticalStrut(6));
                    p.add(thinSeparator());
                    p.add(Box.createVerticalStrut(6));
                }
            }
        }
        return p;
    }

    private JComponent sectionBox(String title, Icon icon, JComponent content) {
        RoundedPanel box = new RoundedPanel(16);
        box.setBackground(CARD_ALT);
        box.setLayout(new BorderLayout());

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(true);
        // Slightly darker than CARD_ALT for the header strip
        header.setBackground(new Color(0xF2EFE6));
        header.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(LINE, 1, true),
                new EmptyBorder(8, 10, 8, 10)
        ));

        JLabel lbl = new JLabel(title);
        lbl.setIcon(icon);
        lbl.setIconTextGap(8);
        lbl.setForeground(new Color(0x374151));
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD, 12.5f));

        header.add(lbl, BorderLayout.WEST);

        JPanel inner = new JPanel(new BorderLayout());
        inner.setOpaque(false);
        inner.setBorder(new EmptyBorder(10, 10, 10, 10));
        inner.add(content, BorderLayout.CENTER);

        box.add(header, BorderLayout.NORTH);
        box.add(inner, BorderLayout.CENTER);

        return box;
    }

    private JSeparator thinSeparator() {
        JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
        sep.setForeground(LINE);
        sep.setOpaque(false);
        return sep;
    }

    private static class DotIcon implements Icon {
        private final Color color;
        private final int size;

        DotIcon(Color color) {
            this(color, 10);
        }

        DotIcon(Color color, int size) {
            this.color = color;
            this.size = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                int d = size;
                int yy = y + (getIconHeight() - d) / 2;
                g2.fillOval(x, yy, d, d);
            } finally {
                g2.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return "";
    }

    private static class WorkSummary {
        int daysWorked = 0;
        long totalMinutes = 0;
        long otMinutes = 0;
        List<LocalDate> workedDays = new ArrayList<>();
    }

    private static class InfoRow extends JPanel {
        private static final int LABEL_COL_WIDTH = 150;

        private final JLabel label;
        private JLabel valueLabel;
        private JTextArea valueArea;
        private JComponent areaHolder;
        private JComponent valueComp;

        InfoRow(String labelText) {
            setOpaque(false);
            setLayout(new BorderLayout(10, 0));

            label = new JLabel(labelText);
            label.setForeground(new Color(0x6B7280));
            label.setFont(label.getFont().deriveFont(12f));
            label.setVerticalAlignment(SwingConstants.TOP);

            label.setHorizontalAlignment(SwingConstants.LEFT);

            Dimension lp = label.getPreferredSize();
            label.setPreferredSize(new Dimension(LABEL_COL_WIDTH, lp.height));
            label.setMinimumSize(new Dimension(LABEL_COL_WIDTH, lp.height));
            label.setMaximumSize(new Dimension(LABEL_COL_WIDTH, Integer.MAX_VALUE));

            // Default: single-line value (right-aligned). We keep value in CENTER (not EAST)
            // so very long strings don't force the dialog to resize.
            valueLabel = new JLabel("-", SwingConstants.RIGHT);
            valueLabel.setForeground(new Color(0x111827));
            valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 12.5f));

            valueComp = valueLabel;

            add(label, BorderLayout.WEST);
            add(valueComp, BorderLayout.CENTER);
        }

        void setValue(String v) {
            ensureLabelValue();
            String text = (v == null || v.isBlank()) ? "-" : v;
            valueLabel.setText(text);
            valueLabel.setToolTipText((v == null || v.isBlank()) ? null : v);
        }

        /** Use this for fields that can be long (e.g., Address) so it wraps instead of overlapping. */
        void setValueMultiline(String v) {
            ensureAreaValue();
            String text = (v == null || v.isBlank()) ? "-" : v;
            valueArea.setText(text);
            valueArea.setToolTipText((v == null || v.isBlank()) ? null : v);
        }

        private void ensureLabelValue() {
            if (valueComp == valueLabel) return;
            remove(valueComp);
            valueComp = valueLabel;
            add(valueComp, BorderLayout.CENTER);
            revalidate();
            repaint();
        }

        private void ensureAreaValue() {
            if (valueArea == null) {
                valueArea = new JTextArea();
                valueArea.setEditable(false);
                valueArea.setFocusable(false);
                valueArea.setOpaque(false);
                valueArea.setBorder(null);
                valueArea.setLineWrap(true);
                valueArea.setWrapStyleWord(true);
                valueArea.setFont(new JLabel().getFont().deriveFont(Font.BOLD, 12.5f));
                valueArea.setForeground(new Color(0x111827));
                valueArea.setRows(2);

                // Ensure short addresses look naturally left-aligned.
                valueArea.setAlignmentX(LEFT_ALIGNMENT);
                valueArea.setMargin(new Insets(0, 0, 0, 0));
            }
            if (areaHolder == null) {
                areaHolder = new JPanel(new BorderLayout());
                areaHolder.setOpaque(false);
                // NORTH anchors the text to the top-left (prevents "centered" feel).
                areaHolder.add(valueArea, BorderLayout.NORTH);
            }

            if (valueComp == areaHolder) return;
            remove(valueComp);
            valueComp = areaHolder;
            add(valueComp, BorderLayout.CENTER);
            revalidate();
            repaint();
        }
    }

    private static class StatTile extends RoundedPanel {
        private final JLabel val;

        StatTile(String title) {
            super(16);
            setBackground(CARD_ALT);
            setBorder(new EmptyBorder(10, 10, 10, 10));
            setLayout(new BorderLayout(0, 6));

            JLabel t = new JLabel(title);
            t.setForeground(MUTED);
            t.setFont(t.getFont().deriveFont(12f));

            val = new JLabel("-");
            val.setForeground(TEXT);
            val.setFont(val.getFont().deriveFont(Font.BOLD, 13f));

            add(t, BorderLayout.NORTH);
            add(val, BorderLayout.CENTER);
        }

        void setValue(String v) {
            val.setText(v == null || v.isBlank() ? "-" : v);
        }
    }

    private static class RoundedPanel extends JPanel {
        private final int arc;

        RoundedPanel(int arc) {
            this.arc = arc;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, w, h, arc, arc);

            g2.setColor(LINE);
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * A FlowLayout panel that correctly WRAPS inside a JScrollPane.
     *
     * Without implementing Scrollable + tracksViewportWidth, the panel keeps its
     * "long single row" preferred width, so the viewport doesn't constrain it and
     * chips can appear to run past the right edge.
     */
    private static class WrapPanel extends JPanel implements Scrollable {

        WrapPanel(int align, int hgap, int vgap) {
            super(new FlowLayout(align, hgap, vgap));
        }

        @Override
        public Dimension getPreferredSize() {
            if (!(getLayout() instanceof FlowLayout flow)) {
                return super.getPreferredSize();
            }

            int targetWidth = 0;
            if (getParent() instanceof JViewport viewport) {
                targetWidth = viewport.getWidth();
            }
            if (targetWidth <= 0) {
                return super.getPreferredSize();
            }

            Insets insets = getInsets();
            int availableWidth = Math.max(1, targetWidth - insets.left - insets.right - (flow.getHgap() * 2));

            int x = 0;
            int y = flow.getVgap();
            int rowHeight = 0;

            for (Component c : getComponents()) {
                if (!c.isVisible()) continue;

                Dimension d = c.getPreferredSize();

                if (x > 0 && x + d.width > availableWidth) {
                    x = 0;
                    y += rowHeight + flow.getVgap();
                    rowHeight = 0;
                }

                if (x == 0) {
                    x = d.width;
                } else {
                    x += flow.getHgap() + d.width;
                }
                rowHeight = Math.max(rowHeight, d.height);
            }

            y += rowHeight + flow.getVgap();

            return new Dimension(targetWidth, y + insets.top + insets.bottom);
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r,int o,int d){ return 20; }
        @Override public int getScrollableBlockIncrement(Rectangle r,int o,int d){ return 60; }
        @Override public boolean getScrollableTracksViewportWidth(){ return true; }
        @Override public boolean getScrollableTracksViewportHeight(){ return false; }
    }

    private static String nvl(String s) { return s == null ? "" : s.trim(); }

    private static String buildFullName(String first, String mid, String last) {
        String m = (mid == null || mid.isBlank()) ? "" : (" " + mid.trim());
        String f = (first == null) ? "" : first.trim();
        String l = (last == null) ? "" : last.trim();
        return (f + m + " " + l).trim().replaceAll("\\s+", " ");
    }

    private static String fmtHM(long totalMinutes) {
        if (totalMinutes <= 0) return "0 hrs 0 mins";
        long hrs = totalMinutes / 60;
        long mins = totalMinutes % 60;
        return hrs + " hrs " + mins + " mins";
    }

    private static String ellipsis(String s, int max) {
        if (s == null) return "-";
        s = s.trim();
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(0, max - 1)) + "...";
    }
}