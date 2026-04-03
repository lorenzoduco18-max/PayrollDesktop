package ui;

import com.formdev.flatlaf.FlatClientProperties;

import dao.DB;
import dao.EmployeeDAO;
import model.Employee;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class EmployeeAccountDialog extends JDialog {

    private Employee employee;     // null = new
    private Integer empId = null;  // set after save

    private final JTabbedPane tabs = new JTabbedPane();

    // Employee fields
    private final JTextField txtEmpNo = new JTextField();
    private final JTextField txtFirst = new JTextField();
    private final JTextField txtMiddle = new JTextField();
    private final JTextField txtLast = new JTextField();
    private final JTextField txtAddress = new JTextField();
    private final JTextField txtMobile = new JTextField(); // personal contact (legacy column: mobile)
    private final JTextField txtCompanyContact = new JTextField();
    private final JTextField txtPersonalEmail = new JTextField();
    private final JTextField txtCompanyEmail = new JTextField();

    // NEW: HR/Govt info
    private final JTextField txtDob = new JTextField(); // YYYY-MM-DD
    private final JComboBox<String> cmbCivilStatus = new JComboBox<>(new String[]{"", "Single", "Married", "Widowed", "Separated"});
    private final JTextField txtPhilHealth = new JTextField();
    private final JTextField txtSSS = new JTextField();
    private final JTextField txtTIN = new JTextField();
    private final JTextField txtPagIbig = new JTextField();

    private final JTextField txtPosition = new JTextField();
    private final JComboBox<String> cmbPayType = new JComboBox<>(new String[]{"DAILY", "HOURLY", "MONTHLY"});
    private final JTextField txtRate = new JTextField();
    // ✅ Employee-level holiday day rates (PHP/day - 8h). Optional.
    private final JTextField txtRegularHolidayRate = new JTextField();
    private final JTextField txtSpecialHolidayRate = new JTextField();
    private final JLabel lblRate = new JLabel("Rate *");

    private final JButton btnSaveEmployee = new RoundedButton("Save Employee", new Color(24, 130, 90), Color.WHITE);

    // Account fields
    private final JTextField txtUsername = new JTextField();
    private final JPasswordField txtPassword = new JPasswordField();
    private final JPasswordField txtConfirm = new JPasswordField();
    private final JLabel lblAccountHint = new JLabel(" ");
    private final JButton btnCreateAccount = new RoundedButton("Create Account", new Color(24, 130, 90), Color.WHITE);

    private final JButton btnClose = new RoundedButton("Close", new Color(245, 245, 245), new Color(35, 35, 35));

    public EmployeeAccountDialog(Window owner) {
        this(owner, (Employee) null);
    }

    public EmployeeAccountDialog(Window owner, int empId) {
        this(owner, loadEmployeeSafe(empId));
    }

    public EmployeeAccountDialog(Window owner, Employee employee) {
        super(owner, employee == null ? "Create Employee Account" : "Edit Employee", ModalityType.APPLICATION_MODAL);
        this.employee = employee;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(true);

        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(new EmptyBorder(16, 16, 16, 16));
        setContentPane(content);

        content.add(buildHeader(), BorderLayout.NORTH);

        tabs.putClientProperty(FlatClientProperties.STYLE, "tabArc:16; tabHeight:34;");
        tabs.addTab("Employee Info", buildEmployeeTab());
        tabs.addTab("Account", buildAccountTab());
        content.add(tabs, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        styleButton(btnClose, false);
        btnClose.addActionListener(e -> dispose());
        footer.add(btnClose);
        content.add(footer, BorderLayout.SOUTH);

        if (this.employee != null) {
            fillEmployeeFields(this.employee);
            this.empId = this.employee.empId;
        }

        updateAccountTabState();

        getRootPane().setDefaultButton(btnSaveEmployee);
        tabs.addChangeListener(e -> getRootPane().setDefaultButton(tabs.getSelectedIndex() == 0 ? btnSaveEmployee : btnCreateAccount));

        setPreferredSize(new Dimension(920, 640));
        pack();
        setLocationRelativeTo(owner);

        SwingUtilities.invokeLater(() -> txtEmpNo.requestFocusInWindow());
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(10, 6));
        header.setOpaque(false);

        JLabel title = new JLabel(employee == null ? "Create Employee Account" : "Edit Employee");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));

        JLabel subtitle = new JLabel("Employee details and optional login credentials");
        subtitle.setForeground(new Color(0, 0, 0, 140));

        JPanel texts = new JPanel(new GridLayout(0, 1));
        texts.setOpaque(false);
        texts.add(title);
        texts.add(subtitle);

        header.add(texts, BorderLayout.WEST);
        return header;
    }

    private static Employee loadEmployeeSafe(int empId) {
        try {
            return EmployeeDAO.getById(empId);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private JPanel buildEmployeeTab() {
        JPanel outer = new JPanel(new BorderLayout(0, 10));
        outer.setBorder(new EmptyBorder(10, 0, 0, 0));
        outer.setOpaque(false);

        JPanel card = new JPanel(new GridBagLayout());
        card.setBorder(new EmptyBorder(16, 16, 16, 16));
        card.putClientProperty(FlatClientProperties.STYLE,
                "arc:18; background:$Panel.background; border:1,1,1,1,$Component.borderColor");

        prepField(txtEmpNo, "e.g. 2026-001");
        prepField(txtFirst, "e.g. Juan");
        prepField(txtMiddle, "e.g. D");
        prepField(txtLast, "e.g. Dela Cruz");
        prepField(txtAddress, "House/Street/City");
        prepField(txtMobile, "09XXXXXXXXX");
        prepField(txtPosition, "e.g. Driver / Staff");
        // NEW: make the additional contact/email inputs match the same rounded style
        prepField(txtCompanyContact, "e.g. 09XXXXXXXXX");
        prepField(txtPersonalEmail, "e.g. name@gmail.com");
        prepField(txtCompanyEmail, "e.g. name@company.com");

        prepField(txtDob, "YYYY-MM-DD");
        prepCombo(cmbCivilStatus);
        prepField(txtPhilHealth, "e.g. 12-345678901-2");
        prepField(txtSSS, "e.g. 12-3456789-0");
        prepField(txtTIN, "e.g. 123-456-789-000");
        prepField(txtPagIbig, "e.g. 1234-5678-9012");
        prepCombo(cmbPayType);
        prepField(txtRate, "e.g. 1000");
        prepField(txtRegularHolidayRate, "e.g. 1000");
        prepField(txtSpecialHolidayRate, "e.g. 1000");

        cmbPayType.addActionListener(e -> updateRateLabel());
        updateRateLabel();

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(8, 8, 8, 8);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;

        int r = 0;
        addBlock(card, gc, 0, r, block("Employee No *", txtEmpNo));
        addBlock(card, gc, 1, r++, block("Pay Type *", cmbPayType));

        addBlock(card, gc, 0, r, block("First Name *", txtFirst));
        addBlock(card, gc, 1, r++, block("Regular Holiday Rate (PHP/day)", txtRegularHolidayRate));

        addBlock(card, gc, 0, r, block(lblRate, txtRate));
        addBlock(card, gc, 1, r++, block("Special Holiday Rate (PHP/day)", txtSpecialHolidayRate));

        addBlock(card, gc, 0, r, block("Middle Initial", txtMiddle));
        addBlock(card, gc, 1, r++, block("Position", txtPosition));

        addBlock(card, gc, 0, r, block("Last Name *", txtLast));
        addBlock(card, gc, 1, r++, block("Personal Contact No", txtMobile));

        addBlock(card, gc, 0, r, block("Date of Birth", txtDob));
        addBlock(card, gc, 1, r++, block("Civil Status", cmbCivilStatus));

        addBlock(card, gc, 0, r, block("SSS Number", txtSSS));
        addBlock(card, gc, 1, r++, block("PhilHealth Number", txtPhilHealth));

        addBlock(card, gc, 0, r, block("TIN ID Number", txtTIN));
        addBlock(card, gc, 1, r++, block("Pag-IBIG Number", txtPagIbig));

        addBlock(card, gc, 0, r, block("Personal Email", txtPersonalEmail));
        addBlock(card, gc, 1, r++, block("Company Contact No", txtCompanyContact));

        addBlock(card, gc, 0, r, block("Company Email", txtCompanyEmail));
        addBlock(card, gc, 1, r++, spacer());

        gc.gridx = 0;
        gc.gridy = r++;
        gc.gridwidth = 2;
        card.add(block("Address", txtAddress), gc);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        styleButton(btnSaveEmployee, true);
        btnSaveEmployee.addActionListener(e -> onSaveEmployee());
        actions.add(btnSaveEmployee);

        outer.add(wrapScroll(card), BorderLayout.CENTER);
        outer.add(actions, BorderLayout.SOUTH);
        return outer;
    }

    private JPanel buildAccountTab() {
        JPanel outer = new JPanel(new BorderLayout(0, 10));
        outer.setBorder(new EmptyBorder(10, 0, 0, 0));
        outer.setOpaque(false);

        JPanel card = new JPanel(new GridBagLayout());
        card.setBorder(new EmptyBorder(16, 16, 16, 16));
        card.putClientProperty(FlatClientProperties.STYLE,
                "arc:18; background:$Panel.background; border:1,1,1,1,$Component.borderColor");

        prepField(txtUsername, "e.g. empno or juandelacruz");
        prepField(txtPassword, "Password");
        prepField(txtConfirm, "Confirm password");

        lblAccountHint.putClientProperty(FlatClientProperties.STYLE, "foreground:$Label.disabledForeground");

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(8, 8, 8, 8);
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;

        int r = 0;

        gc.gridx = 0;
        gc.gridy = r++;
        gc.gridwidth = 2;
        card.add(block("Username *", txtUsername), gc);

        addBlock(card, gc, 0, r, block("Password *", txtPassword));
        addBlock(card, gc, 1, r++, block("Confirm Password *", txtConfirm));

        final char pwEcho = txtPassword.getEchoChar();
        JCheckBox cbShowPw = new JCheckBox("Show password");
        cbShowPw.setOpaque(false);
        cbShowPw.addActionListener(e -> {
            boolean show = cbShowPw.isSelected();
            txtPassword.setEchoChar(show ? (char) 0 : pwEcho);
            txtConfirm.setEchoChar(show ? (char) 0 : pwEcho);
        });
        gc.gridx = 0;
        gc.gridy = r++;
        gc.gridwidth = 2;
        card.add(cbShowPw, gc);


        gc.gridx = 0;
        gc.gridy = r++;
        gc.gridwidth = 2;
        card.add(lblAccountHint, gc);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        styleButton(btnCreateAccount, true);
        btnCreateAccount.addActionListener(e -> onCreateAccount());
        actions.add(btnCreateAccount);

        outer.add(wrapScroll(card), BorderLayout.CENTER);
        outer.add(actions, BorderLayout.SOUTH);
        return outer;
    }

    private JScrollPane wrapScroll(JComponent c) {
        JScrollPane sp = new JScrollPane(c);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    private void addBlock(JPanel parent, GridBagConstraints gc, int col, int row, JComponent comp) {
        gc.gridx = col;
        gc.gridy = row;
        gc.gridwidth = 1;
        gc.weightx = 1;
        parent.add(comp, gc);
    }

    private JComponent block(JLabel label, JComponent field) {
        label.setFont(label.getFont().deriveFont(12f));

        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        p.add(label, BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    private JComponent block(String labelText, JComponent field) {
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(lbl.getFont().deriveFont(12f));

        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.setOpaque(false);
        p.add(lbl, BorderLayout.NORTH);
        p.add(field, BorderLayout.CENTER);
        return p;
    }

    private JComponent spacer() {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(1, 1));
        return p;
    }

    private void prepField(JComponent field, String placeholder) {
        field.putClientProperty(FlatClientProperties.STYLE,
                "arc:14; margin:8,10,8,10; borderWidth:1");
        if (field instanceof JTextField) {
            ((JTextField) field).putClientProperty("JTextField.placeholderText", placeholder);
        } else if (field instanceof JPasswordField) {
            ((JPasswordField) field).putClientProperty("JTextField.placeholderText", placeholder);
        }
    }

    private void prepCombo(JComboBox<?> combo) {
        combo.putClientProperty(FlatClientProperties.STYLE, "arc:14; minimumWidth:120");
    }

    private void styleButton(JButton b, boolean primary) {
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFocusable(false);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setMargin(new Insets(7, 16, 7, 16));
        b.setPreferredSize(new Dimension(Math.max(b.getPreferredSize().width, 110), 38));
        if (b instanceof RoundedButton rb) {
            rb.setCornerRadius(18);
            if (primary) {
                rb.setBackground(new Color(24, 130, 90));
                rb.setForeground(Color.WHITE);
            } else {
                rb.setBackground(new Color(245, 245, 245));
                rb.setForeground(new Color(35, 35, 35));
            }
        }
    }

    private void updateRateLabel() {
        String pt = (String) cmbPayType.getSelectedItem();
        String payType = pt == null ? "" : pt.trim().toUpperCase();

        if ("MONTHLY".equals(payType)) {
            lblRate.setText("Monthly Rate *");
            txtRate.putClientProperty("JTextField.placeholderText", "e.g. 26000");
        } else if ("HOURLY".equals(payType)) {
            lblRate.setText("Hourly Rate *");
            txtRate.putClientProperty("JTextField.placeholderText", "e.g. 120");
        } else {
            lblRate.setText("Daily Rate *");
            txtRate.putClientProperty("JTextField.placeholderText", "e.g. 1000");
        }
    }

    private void fillEmployeeFields(Employee e) {
        txtEmpNo.setText(nz(e.empNo));
        txtFirst.setText(nz(e.firstName));
        txtMiddle.setText(nz(e.middleName));
        txtLast.setText(nz(e.lastName));
        txtAddress.setText(nz(e.address));
        txtMobile.setText(nz(firstNonEmpty(e.personalContactNo, e.mobile)));
        txtCompanyContact.setText(nz(e.companyContactNo));
        txtPersonalEmail.setText(nz(e.personalEmail));
        txtCompanyEmail.setText(nz(e.companyEmail));
        txtPosition.setText(nz(e.position));
        txtDob.setText(nz(e.dateOfBirth));
        cmbCivilStatus.setSelectedItem(nz(e.civilStatus));
        txtPhilHealth.setText(nz(e.philHealthNo));
        txtSSS.setText(nz(e.sssNo));
        txtTIN.setText(nz(e.tinNo));
        txtPagIbig.setText(nz(e.pagIbigNo));
        if (e.payType != null) cmbPayType.setSelectedItem(e.payType);
        txtRate.setText(String.valueOf(e.rate));
        txtRegularHolidayRate.setText(e.regularHolidayRate > 0 ? String.valueOf(e.regularHolidayRate) : "");
        txtSpecialHolidayRate.setText(e.specialHolidayRate > 0 ? String.valueOf(e.specialHolidayRate) : "");
        updateRateLabel();
    }

    private void onSaveEmployee() {
        String empNo = txtEmpNo.getText().trim();
        String first = txtFirst.getText().trim();
        String middle = txtMiddle.getText().trim();
        String last = txtLast.getText().trim();
        String address = txtAddress.getText().trim();
        String personalContactNo = txtMobile.getText().trim();
        String companyContactNo = txtCompanyContact.getText().trim();
        String personalEmail = txtPersonalEmail.getText().trim();
        String companyEmail = txtCompanyEmail.getText().trim();
        String dob = txtDob.getText().trim();
        String civilStatus = String.valueOf(cmbCivilStatus.getSelectedItem()).trim();
        String philHealthNo = txtPhilHealth.getText().trim();
        String sssNo = txtSSS.getText().trim();
        String tinNo = txtTIN.getText().trim();
        String pagIbigNo = txtPagIbig.getText().trim();
        String position = txtPosition.getText().trim();
        String payType = (String) cmbPayType.getSelectedItem();
        String rateStr = txtRate.getText().trim();
        String regHolRateStr = txtRegularHolidayRate.getText().trim();
        String specHolRateStr = txtSpecialHolidayRate.getText().trim();

        if (empNo.isEmpty() || first.isEmpty() || last.isEmpty() || payType == null || payType.trim().isEmpty() || rateStr.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please fill required fields: Employee No, First Name, Last Name, Pay Type, Rate.",
                    "Missing info", JOptionPane.WARNING_MESSAGE);
            return;
        }

        double rate;
        try {
            rate = Double.parseDouble(rateStr);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Rate must be a valid number.",
                    "Invalid rate", JOptionPane.WARNING_MESSAGE);
            txtRate.requestFocusInWindow();
            return;
        }

        double regHolRate = 0.0;
        double specHolRate = 0.0;
        try {
            if (!regHolRateStr.isEmpty()) regHolRate = Double.parseDouble(regHolRateStr);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Regular Holiday Rate must be a valid number.", "Invalid rate", JOptionPane.WARNING_MESSAGE);
            txtRegularHolidayRate.requestFocusInWindow();
            return;
        }
        try {
            if (!specHolRateStr.isEmpty()) specHolRate = Double.parseDouble(specHolRateStr);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Special Holiday Rate must be a valid number.", "Invalid rate", JOptionPane.WARNING_MESSAGE);
            txtSpecialHolidayRate.requestFocusInWindow();
            return;
        }

        try {
            if (employee == null) {
                if (empNoExistsCompat(empNo)) {
                    JOptionPane.showMessageDialog(this,
                            "Employee No already exists. Please use a different Employee No.",
                            "Duplicate Employee No", JOptionPane.WARNING_MESSAGE);
                    txtEmpNo.requestFocusInWindow();
                    txtEmpNo.selectAll();
                    return;
                }

                int newId = EmployeeDAO.insertFull(empNo, first, middle, last,
                        address,
                        personalContactNo,
                        companyContactNo,
                        personalEmail,
                        companyEmail,
                        position,
                        payType,
                        rate,
                        dob,
                        civilStatus,
                        philHealthNo,
                        sssNo,
                        tinNo,
                        pagIbigNo,
                        regHolRate,
                        specHolRate);
                if (newId <= 0) throw new RuntimeException("Insert returned invalid ID.");

                employee = new Employee();
                employee.empId = newId;
                employee.empNo = empNo;
                employee.firstName = first;
                employee.middleName = middle;
                employee.lastName = last;
                employee.address = address;
                employee.mobile = personalContactNo;
                employee.personalContactNo = personalContactNo;
                employee.companyContactNo = companyContactNo;
                employee.personalEmail = personalEmail;
                employee.companyEmail = companyEmail;
                employee.dateOfBirth = dob;
                employee.civilStatus = civilStatus;
                employee.philHealthNo = philHealthNo;
                employee.sssNo = sssNo;
                employee.tinNo = tinNo;
                employee.pagIbigNo = pagIbigNo;
                employee.dateOfBirth = dob;
                employee.civilStatus = civilStatus;
                employee.philHealthNo = philHealthNo;
                employee.sssNo = sssNo;
                employee.tinNo = tinNo;
                employee.pagIbigNo = pagIbigNo;
                employee.position = position;
                employee.payType = payType;
                employee.rate = rate;
                employee.status = "ACTIVE";

                empId = newId;

                JOptionPane.showMessageDialog(this, "Employee saved. You can now create the account.",
                        "Saved", JOptionPane.INFORMATION_MESSAGE);

                suggestUsername();
                updateAccountTabState();
                tabs.setSelectedIndex(1);
            } else {
                if (EmployeeDAO.empNoExists(empNo, employee.empId)) {
                    JOptionPane.showMessageDialog(this,
                            "Employee No already exists. Please use a different Employee No.",
                            "Duplicate Employee No", JOptionPane.WARNING_MESSAGE);
                    txtEmpNo.requestFocusInWindow();
                    txtEmpNo.selectAll();
                    return;
                }

                EmployeeDAO.updateFull(employee.empId, empNo, first, middle, last,
                        address,
                        personalContactNo,
                        companyContactNo,
                        personalEmail,
                        companyEmail,
                        position,
                        payType,
                        rate,
                        dob,
                        civilStatus,
                        philHealthNo,
                        sssNo,
                        tinNo,
                        pagIbigNo,
                    regHolRate,
                    specHolRate);

                employee.empNo = empNo;
                employee.firstName = first;
                employee.middleName = middle;
                employee.lastName = last;
                employee.address = address;
                employee.mobile = personalContactNo;
                employee.personalContactNo = personalContactNo;
                employee.companyContactNo = companyContactNo;
                employee.personalEmail = personalEmail;
                employee.companyEmail = companyEmail;
                employee.position = position;
                employee.payType = payType;
                employee.rate = rate;

                empId = employee.empId;

                JOptionPane.showMessageDialog(this, "Employee updated.",
                        "Updated", JOptionPane.INFORMATION_MESSAGE);

                updateAccountTabState();
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error saving employee:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ✅ Creates or links a users row WITH emp_id (never NULL)
    private void onCreateAccount() {

        if (empId == null) {
            JOptionPane.showMessageDialog(this,
                    "Please save the employee info first.",
                    "Save Employee First", JOptionPane.WARNING_MESSAGE);
            tabs.setSelectedIndex(0);
            return;
        }

        // If account already exists, this button acts as "Change Password"
        try {
            Integer linkedUserId = getUserIdLinkedToEmp(empId);
            if (linkedUserId != null) {
                onUpdateAccount(linkedUserId);
                updateAccountTabState();
                return;
            }
        } catch (Exception ignore) {
            // fall through to normal create flow
        }

        if (empId == null) {
            JOptionPane.showMessageDialog(this,
                    "Save the employee first before creating an account.",
                    "Not ready", JOptionPane.WARNING_MESSAGE);
            tabs.setSelectedIndex(0);
            return;
        }

        String username = txtUsername.getText().trim();
        String pass = new String(txtPassword.getPassword());
        String conf = new String(txtConfirm.getPassword());

        if (username.isEmpty() || pass.isEmpty() || conf.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please complete Username / Password / Confirm Password.",
                    "Missing info", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!pass.equals(conf)) {
            JOptionPane.showMessageDialog(this, "Passwords do not match.",
                    "Mismatch", JOptionPane.WARNING_MESSAGE);
            txtConfirm.requestFocusInWindow();
            return;
        }

        try {
            Integer already = getUserIdLinkedToEmp(empId);
            if (already != null) {
                JOptionPane.showMessageDialog(this,
                        "This employee already has an account.",
                        "Already has account", JOptionPane.WARNING_MESSAGE);
                updateAccountTabState();
                return;
            }

            Integer userId = getUserIdByUsername(username);

            if (userId == null) {
                // brand new
                insertUser(empId, username, pass, "EMPLOYEE");
            } else {
                Integer existingEmp = getEmpIdForUserId(userId);
                if (existingEmp != null && existingEmp != empId) {
                    JOptionPane.showMessageDialog(this,
                            "That username is already linked to another employee.\nChoose a different username.",
                            "Username already linked", JOptionPane.WARNING_MESSAGE);
                    txtUsername.requestFocusInWindow();
                    txtUsername.selectAll();
                    return;
                }
                // link existing (old accounts with emp_id NULL)
                linkExistingUser(userId, empId, pass, "EMPLOYEE");
            }

            JOptionPane.showMessageDialog(this,
                    "Employee login saved successfully.",
                    "Success", JOptionPane.INFORMATION_MESSAGE);

            txtPassword.setText("");
            txtConfirm.setText("");

            updateAccountTabState();

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error creating account:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    
    private void onUpdateAccount(int linkedUserId) {
        String username = txtUsername.getText().trim();
        String pass = new String(txtPassword.getPassword());
        String conf = new String(txtConfirm.getPassword());

        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username is required.",
                    "Missing username", JOptionPane.WARNING_MESSAGE);
            txtUsername.requestFocusInWindow();
            return;
        }

        boolean wantsPasswordChange = !pass.isEmpty() || !conf.isEmpty();
        if (wantsPasswordChange) {
            if (pass.isEmpty() || conf.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Fill both password fields to change password.",
                        "Incomplete password", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!pass.equals(conf)) {
                JOptionPane.showMessageDialog(this, "Passwords do not match.",
                        "Mismatch", JOptionPane.WARNING_MESSAGE);
                txtConfirm.requestFocusInWindow();
                return;
            }
        }

        try (Connection con = DB.getConnection()) {
            // Username uniqueness check
            Integer existing = getUserIdByUsername(username);
            if (existing != null && existing != linkedUserId) {
                JOptionPane.showMessageDialog(this,
                        "That username is already used by another account.\nChoose a different username.",
                        "Username taken", JOptionPane.WARNING_MESSAGE);
                txtUsername.requestFocusInWindow();
                return;
            }

            con.setAutoCommit(false);
            try {
                // update username
                try (PreparedStatement ps = con.prepareStatement("UPDATE users SET username=? WHERE user_id=?")) {
                    ps.setString(1, username);
                    ps.setInt(2, linkedUserId);
                    ps.executeUpdate();
                }

                // update password (optional)
                if (wantsPasswordChange) {
                    try (PreparedStatement ps = con.prepareStatement("UPDATE users SET password=? WHERE user_id=?")) {
                        ps.setString(1, pass);
                        ps.setInt(2, linkedUserId);
                        ps.executeUpdate();
                    }
                }

                con.commit();
            } catch (Exception ex) {
                con.rollback();
                throw ex;
            } finally {
                con.setAutoCommit(true);
            }

            JOptionPane.showMessageDialog(this,
                    "Employee login updated successfully.",
                    "Success", JOptionPane.INFORMATION_MESSAGE);

            txtPassword.setText("");
            txtConfirm.setText("");
            updateAccountTabState();

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Error updating account:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

private void onChangePassword(int userId) {
        String pass = new String(txtPassword.getPassword()).trim();
        String conf = new String(txtConfirm.getPassword()).trim();
        if (pass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a new password.", "Required", JOptionPane.WARNING_MESSAGE);
            txtPassword.requestFocusInWindow();
            return;
        }
        if (!pass.equals(conf)) {
            JOptionPane.showMessageDialog(this, "Passwords do not match.", "Mismatch", JOptionPane.WARNING_MESSAGE);
            txtConfirm.requestFocusInWindow();
            return;
        }
        if (pass.length() < 4) {
            JOptionPane.showMessageDialog(this, "Password must be at least 4 characters.", "Too short", JOptionPane.WARNING_MESSAGE);
            txtPassword.requestFocusInWindow();
            return;
        }
        try {
            String sql = "UPDATE users SET password=? WHERE user_id=?";
            try (Connection con = DB.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, pass);
                ps.setInt(2, userId);
                int updated = ps.executeUpdate();
                if (updated > 0) {
                    JOptionPane.showMessageDialog(this, "Password updated.", "Success", JOptionPane.INFORMATION_MESSAGE);
                    txtPassword.setText("");
                    txtConfirm.setText("");
                } else {
                    JOptionPane.showMessageDialog(this, "No account updated.", "Not updated", JOptionPane.WARNING_MESSAGE);
                }
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Update failed:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateAccountTabState() {
        boolean saved = (empId != null);
        tabs.setEnabledAt(1, saved);

        if (!saved) {
            lblAccountHint.setText("Save the employee first to enable account creation.");
            setAccountControlsEnabled(false);
            return;
        }

        setAccountControlsEnabled(true);

        try {
            Integer linkedUserId = getUserIdLinkedToEmp(empId);
            if (linkedUserId != null) {
                lblAccountHint.setText("Account already exists (linked user_id = " + linkedUserId + "). You can update the username and/or password below.");
                btnCreateAccount.setText("Update Account");
                btnCreateAccount.setEnabled(true);
                txtUsername.setEnabled(true);
                txtPassword.setEnabled(true);
                txtConfirm.setEnabled(true);
            } else {
                lblAccountHint.setText("Create or link the employee's login credentials.");
                btnCreateAccount.setText("Create Account");
                btnCreateAccount.setEnabled(true);
                txtUsername.setEnabled(true);
                if (txtUsername.getText().trim().isEmpty()) suggestUsername();
            }
        } catch (Exception ex) {
            lblAccountHint.setText("Create or link the employee's login credentials.");
            btnCreateAccount.setText("Create Account");
                btnCreateAccount.setEnabled(true);
                txtUsername.setEnabled(true);
        }
    }

    private void setAccountControlsEnabled(boolean enabled) {
        txtUsername.setEnabled(enabled);
        txtPassword.setEnabled(enabled);
        txtConfirm.setEnabled(enabled);
        btnCreateAccount.setEnabled(enabled);
    }

    private void suggestUsername() {
        String empNo = txtEmpNo.getText().trim();
        if (!empNo.isEmpty()) {
            txtUsername.setText(empNo);
            return;
        }
        String first = txtFirst.getText().trim().toLowerCase();
        String last = txtLast.getText().trim().toLowerCase();
        if (!first.isEmpty() && !last.isEmpty()) {
            txtUsername.setText(first.charAt(0) + last);
        }
    }

    // ---- users table helpers (NO EmployeeDAO dependency) ----

    private Integer getUserIdLinkedToEmp(int empId) throws Exception {
        String sql = "SELECT user_id FROM users WHERE emp_id=? LIMIT 1";
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, empId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("user_id") : null;
            }
        }
    }

    private Integer getUserIdByUsername(String username) throws Exception {
        String sql = "SELECT user_id FROM users WHERE username=? LIMIT 1";
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("user_id") : null;
            }
        }
    }

    private Integer getEmpIdForUserId(int userId) throws Exception {
        String sql = "SELECT emp_id FROM users WHERE user_id=? LIMIT 1";
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                int v = rs.getInt("emp_id");
                return rs.wasNull() ? null : v;
            }
        }
    }

    private void insertUser(int empId, String username, String password, String role) throws Exception {
        String sql = "INSERT INTO users (emp_id, username, password, role) VALUES (?, ?, ?, ?)";
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, empId);
            ps.setString(2, username);
            ps.setString(3, password);
            ps.setString(4, role);
            ps.executeUpdate();
        }
    }

    private void linkExistingUser(int userId, int empId, String password, String role) throws Exception {
        String sql = "UPDATE users SET emp_id=?, password=?, role=? WHERE user_id=?";
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, empId);
            ps.setString(2, password);
            ps.setString(3, role);
            ps.setInt(4, userId);
            ps.executeUpdate();
        }
    }


    /**
     * Compatibility helper: supports older EmployeeDAO.empNoExists(String)
     * and newer EmployeeDAO.empNoExists(String, Integer).
     */
    private boolean empNoExistsCompat(String empNo) {
        try {
            // Prefer: empNoExists(String, Integer)
            try {
                java.lang.reflect.Method m = EmployeeDAO.class.getMethod("empNoExists", String.class, Integer.class);
                Object r = m.invoke(null, empNo, null);
                return (r instanceof Boolean) && (Boolean) r;
            } catch (NoSuchMethodException ignore) {
                // Fallback: empNoExists(String)
                java.lang.reflect.Method m = EmployeeDAO.class.getMethod("empNoExists", String.class);
                Object r = m.invoke(null, empNo);
                return (r instanceof Boolean) && (Boolean) r;
            }
        } catch (Exception e) {
            // If DAO can't be called, assume not existing to avoid blocking save
            return false;
        }
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return "";
    }
}
