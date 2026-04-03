package ui;
// ✅ Background image overlay for LOGIN.png
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.SwingWorker;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import util.AppVersion;

import com.formdev.flatlaf.FlatClientProperties;

import dao.DB;
import dao.EmployeeDAO;
import dao.UserDAO;
import model.Employee;
import model.User;
import util.AppConstants;

public class LoginFrame extends JFrame {

    private final UserDAO userDAO = new UserDAO();
    private final Preferences prefs = Preferences.userRoot().node("payroll_login");

    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JLabel lblError;
    private JCheckBox chkRemember;

    private PillToggleButton btnAdmin;
    private PillToggleButton btnEmployee;

    private RoundedButton btnLogin;
    private RoundedButton btnShow;

    private char defaultEchoChar;
    private AnimatedGradientPanel bg;

    // ✅ SMALL + CUTE sizing
    private static final int WIN_W = 410;
    private static final int WIN_H = 535;

    private static final int CARD_W = 300;
    private static final int CARD_H = 375;
    private static final int CARD_PAD = 14;

    private static final int LOGO_W = 102;
    private static final int LOGO_H = 76;

    private static final int FIELD_H = 28;
    private static final int FIELD_W = 230;
    private static final int BTN_H = 36;

    private static final int RADIUS = 18;

    

    // ✅ App version (displayed under Login button)
  
public LoginFrame() {
        super(AppConstants.APP_TITLE);

        Object v = UIManager.get("PasswordField.echoChar");
        defaultEchoChar = (v instanceof Character) ? (Character) v : '•';

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setSize(WIN_W, WIN_H);
        setLocationRelativeTo(null);

        bg = new AnimatedGradientPanel();
        bg.setLayout(new GridBagLayout());
        setContentPane(bg);

        bg.add(buildCenterCard(), gbc());

        applyRememberedUsername();
        wireLiveValidation();

        setVisible(true);

        // ✅ do this AFTER visible so root pane exists
        wireEnterToLogin();
    }

    private GridBagConstraints gbc() {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        g.weightx = 1;
        g.weighty = 1;
        g.anchor = GridBagConstraints.CENTER;
        return g;
    }

    private JComponent buildCenterCard() {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(10, 0, 10, 0));
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));

        wrapper.add(buildLogo());
        wrapper.add(Box.createVerticalStrut(8));

        GlassCard card = new GlassCard();
        card.setPreferredSize(new Dimension(CARD_W, CARD_H));
        card.setMaximumSize(new Dimension(CARD_W, CARD_H));
        card.setMinimumSize(new Dimension(CARD_W, CARD_H));
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(CARD_PAD, CARD_PAD, CARD_PAD, CARD_PAD));

        
        ShadowLabel sub = new ShadowLabel("Please login to continue", SwingConstants.CENTER);
        
        
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);
        sub.setShadow(new Color(0,0,0,130), 1, 1);
        sub.setFont(sub.getFont().deriveFont(Font.PLAIN, 11.5f));
        sub.setForeground(Color.BLACK);

                card.add(Box.createVerticalStrut(2));
        card.add(sub);
        card.add(Box.createVerticalStrut(8));

        card.add(labelTinyCenter("Login as"));
        card.add(Box.createVerticalStrut(6));
        card.add(buildRoleToggle());
        card.add(Box.createVerticalStrut(10));

        card.add(labelTinyCenter("Username"));
        card.add(Box.createVerticalStrut(4));

        txtUsername = new RoundedTextField();
        txtUsername.setMaximumSize(new Dimension(FIELD_W, FIELD_H));
        txtUsername.setPreferredSize(new Dimension(FIELD_W, FIELD_H));
        card.add(txtUsername);

        card.add(Box.createVerticalStrut(8));

        card.add(labelTinyCenter("Password"));
        card.add(Box.createVerticalStrut(4));

        JPanel passRow = new JPanel();
        passRow.setOpaque(false);
        passRow.setLayout(new BoxLayout(passRow, BoxLayout.X_AXIS));

        txtPassword = new RoundedPasswordField();
        txtPassword.setEchoChar(defaultEchoChar);
        txtPassword.setMaximumSize(new Dimension(FIELD_W - 56 - 8, FIELD_H));
        txtPassword.setPreferredSize(new Dimension(FIELD_W - 56 - 8, FIELD_H));

        btnShow = new RoundedButton("Show", new Color(249, 248, 244), new Color(21, 39, 71));
        btnShow.setFont(btnShow.getFont().deriveFont(Font.BOLD, 11.5f));
        btnShow.setCornerRadius(16);
        btnShow.setMargin(new Insets(6, 12, 6, 12));
        btnShow.setPreferredSize(new Dimension(60, FIELD_H));
        btnShow.setMaximumSize(new Dimension(60, FIELD_H));
        btnShow.addActionListener(e -> toggleShowPassword());

        passRow.add(txtPassword);
        passRow.add(Box.createHorizontalStrut(8));
        passRow.add(btnShow);

        card.add(passRow);

        card.add(Box.createVerticalStrut(7));

                // Remember me row (checkbox + shadow text for readability on photo background)
        chkRemember = new JCheckBox();
        chkRemember.setOpaque(false);
        chkRemember.setFocusPainted(false);
        chkRemember.putClientProperty(FlatClientProperties.STYLE_CLASS, "small");

        ShadowLabel rememberLabel = new ShadowLabel("Remember me", SwingConstants.LEFT);
        rememberLabel.setForeground(Color.BLACK);
        rememberLabel.setShadow(new Color(0, 0, 0, 220), 2, 2);
        rememberLabel.setFont(rememberLabel.getFont().deriveFont(Font.PLAIN, 9f));

        JPanel rememberRow = new JPanel();
        rememberRow.setOpaque(false);
        rememberRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        rememberRow.setLayout(new BoxLayout(rememberRow, BoxLayout.X_AXIS));
        rememberRow.add(chkRemember);
        rememberRow.add(Box.createHorizontalStrut(6));
        rememberRow.add(rememberLabel);

        card.add(rememberRow);
card.add(Box.createVerticalStrut(6));

        lblError = new JLabel(" ");
        lblError.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblError.setForeground(new Color(170, 40, 40));
        lblError.setFont(lblError.getFont().deriveFont(Font.PLAIN, 11f));
        card.add(lblError);

        card.add(Box.createVerticalStrut(4));

        btnLogin = new RoundedButton("Login", new Color(52, 180, 130), Color.WHITE);
        btnLogin.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnLogin.setFont(btnLogin.getFont().deriveFont(Font.BOLD, 12.5f));
        btnLogin.setCornerRadius(18);
        btnLogin.setMargin(new Insets(8, 18, 8, 18));
        btnLogin.setPreferredSize(new Dimension(90, 20));
        btnLogin.setMaximumSize(new Dimension(90, 20));
        btnLogin.addActionListener(e -> doLogin());
        
card.add(btnLogin);

// ✅ Version label (small, centered, below Login) - visible + spaced
card.add(Box.createVerticalStrut(12));

JLabel versionLabel = new JLabel("v" + AppVersion.getVersion(), SwingConstants.CENTER);
versionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
versionLabel.setFont(versionLabel.getFont().deriveFont(Font.BOLD, 10f));
versionLabel.setForeground(new Color(60, 60, 60));

card.add(versionLabel);wrapper.add(card);
        return wrapper;
    }

    
    private JComponent buildLogo() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

        JLabel logo1 = new JLabel();
        JLabel logo2 = new JLabel();

        ImageIcon icon1 = loadImage("/EBCTLOGO.png");
        ImageIcon icon2 = loadImage("/ETIMELOGO.png");

        if (icon1 != null) {
            Image scaled1 = scaleImageKeepAspect(icon1.getImage(), LOGO_W, LOGO_H);
            logo1.setIcon(new ImageIcon(scaled1));
        }

        if (icon2 != null) {
            Image scaled2 = scaleImageKeepAspect(icon2.getImage(), LOGO_W, LOGO_H);
            logo2.setIcon(new ImageIcon(scaled2));
        }

        panel.add(logo1);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(logo2);

        return panel;
    }

    private ImageIcon loadImage(String path) {
        URL url = getClass().getResource(path);
        if (url != null) return new ImageIcon(url);
        return null;
    }


    private ImageIcon loadLogoIcon() {
    	String[] candidates = {
    		    "/EBCTLOGO.png"
    		};

        for (String p : candidates) {
            URL url = getClass().getResource(p);
            if (url != null) return new ImageIcon(url);
        }
        return null;
    }

    private Image scaleImageKeepAspect(Image img, int maxW, int maxH) {
        int w = img.getWidth(null);
        int h = img.getHeight(null);
        if (w <= 0 || h <= 0) return img;

        double s = Math.min(maxW / (double) w, maxH / (double) h);
        int nw = Math.max(1, (int) Math.round(w * s));
        int nh = Math.max(1, (int) Math.round(h * s));

        return img.getScaledInstance(nw, nh, Image.SCALE_SMOOTH);
    }

    private JComponent buildRoleToggle() {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(Component.CENTER_ALIGNMENT);

        ButtonGroup group = new ButtonGroup();

        btnAdmin = new PillToggleButton("Admin");
        btnEmployee = new PillToggleButton("Employee");

        group.add(btnAdmin);
        group.add(btnEmployee);
        btnAdmin.setSelected(true);

        row.add(btnAdmin);
        row.add(Box.createHorizontalStrut(8));
        row.add(btnEmployee);

        return row;
    }

    private JLabel labelTinyCenter(String s) {
        ShadowLabel l = new ShadowLabel(s, SwingConstants.CENTER);
        l.setAlignmentX(Component.CENTER_ALIGNMENT);
        // White labels + soft halo shadow (no box, no outline stroke)
        l.setForeground(Color.BLACK);
        l.setShadow(new Color(0, 0, 0, 220), 2, 2);
        l.setFont(l.getFont().deriveFont(Font.PLAIN, 11.5f));
        return l;
    }

    private RoundedButton createSmallActionButton(String text, Color background, Color foreground, int width, int height) {
        RoundedButton button = new RoundedButton(text, background, foreground);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 11.5f));
        button.setCornerRadius(16);
        button.setMargin(new Insets(6, 12, 6, 12));
        button.setFocusPainted(false);
        button.setPreferredSize(new Dimension(width, height));
        button.setMaximumSize(new Dimension(width, height));
        button.setMinimumSize(new Dimension(width, height));
        return button;
    }

    private void toggleShowPassword() {
        if (txtPassword.getEchoChar() == 0) {
            txtPassword.setEchoChar(defaultEchoChar);
            btnShow.setText("Show");
        } else {
            txtPassword.setEchoChar((char) 0);
            btnShow.setText("Hide");
        }
    }

    private void applyRememberedUsername() {
        String remembered = prefs.get("remembered_username", "");
        if (remembered != null && !remembered.isBlank()) {
            txtUsername.setText(remembered);
            chkRemember.setSelected(true);
        }
    }

    private void storeRememberedUsernameIfNeeded() {
        if (chkRemember.isSelected()) {
            prefs.put("remembered_username", txtUsername.getText().trim());
        } else {
            prefs.remove("remembered_username");
        }
    }

    private void wireEnterToLogin() {
        // Keep action listeners (press Enter inside fields)
        Action doLoginAction = new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { doLogin(); }
        };
        txtUsername.addActionListener(doLoginAction);
        txtPassword.addActionListener(doLoginAction);

        // ✅ Proper default button (Enter anywhere triggers Login)
        // Must be done AFTER the frame is realized (root pane exists)
        SwingUtilities.invokeLater(() -> {
            JRootPane rp = getRootPane();
            if (rp != null && btnLogin != null) {
                rp.setDefaultButton(btnLogin);
            }
        });
    }

    private void wireLiveValidation() {
        DocumentListener dl = new DocumentListener() {
            private void clearErr() {
                if (lblError != null && !" ".equals(lblError.getText())) {
                    lblError.setText(" ");
                }
            }
            @Override public void insertUpdate(DocumentEvent e) { clearErr(); }
            @Override public void removeUpdate(DocumentEvent e) { clearErr(); }
            @Override public void changedUpdate(DocumentEvent e) { clearErr(); }
        };

        if (txtUsername != null) txtUsername.getDocument().addDocumentListener(dl);
        if (txtPassword != null) txtPassword.getDocument().addDocumentListener(dl);
    }

    // ✅ Role check
    private String fetchRoleByUserId(int userId) throws Exception {
        String sql = "SELECT role FROM users WHERE user_id=? LIMIT 1";
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String role = rs.getString("role");
                    return role == null ? "" : role.trim().toUpperCase();
                }
            }
        }
        return "";
    }

    private static final class LoginOutcome {
        final User user;
        final Employee employee; // only for EMPLOYEE role
        final String role;
        final String error;

        LoginOutcome(User user, Employee employee, String role, String error) {
            this.user = user;
            this.employee = employee;
            this.role = role;
            this.error = error;
        }
    }

    private void doLogin() {
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword());

        if (username.isEmpty()) {
            showError("Please enter your username.");
            txtUsername.requestFocusInWindow();
            return;
        }
        if (password.isEmpty()) {
            showError("Please enter your password.");
            txtPassword.requestFocusInWindow();
            return;
        }

        btnLogin.setEnabled(false);
        lblError.setText(" ");

        final String wantedRole = btnAdmin.isSelected() ? "ADMIN" : "EMPLOYEE";

        // IMPORTANT: run DB work off the EDT so the UI doesn't freeze on Azure
        new SwingWorker<LoginOutcome, Void>() {
            @Override
            protected LoginOutcome doInBackground() {
                try {
                    User user = userDAO.login(username, password);
                    if (user == null) {
                        return new LoginOutcome(null, null, null, "Invalid username or password.");
                    }

                    String realRole = fetchRoleByUserId(user.getId());
                    if (!wantedRole.equalsIgnoreCase(realRole)) {
                        return new LoginOutcome(null, null, null,
                                "This account is not allowed to login as " + wantedRole + ".");
                    }

                    if ("EMPLOYEE".equalsIgnoreCase(realRole)) {
                        Employee emp = EmployeeDAO.getByUserId(user.getId());
                        if (emp == null) {
                            return new LoginOutcome(user, null, realRole,
                                    "This account is not linked to an employee record.");
                        }

                        String st = emp.status == null ? "" : emp.status.trim();
                        if (st.equalsIgnoreCase("INACTIVE")) {
                            return new LoginOutcome(null, null, null,
                                    "Your employee account is INACTIVE. Please contact admin.");
                        }
                        return new LoginOutcome(user, emp, realRole, null);
                    }

                    return new LoginOutcome(user, null, realRole, null);

                } catch (Exception ex) {
                    ex.printStackTrace();
                    return new LoginOutcome(null, null, null, "Login error: " + ex.getMessage());
                }
            }

            @Override
            protected void done() {
                try {
                    LoginOutcome out = get();

                    if (out == null) {
                        showError("Login failed.");
                        return;
                    }

                    // Special message when user exists but not linked
                    if (out.error != null) {
                        if (out.user != null && out.employee == null && "EMPLOYEE".equalsIgnoreCase(out.role)) {
                            JOptionPane.showMessageDialog(
                                    LoginFrame.this,
                                    out.error + "\nAsk the admin to create/link your employee profile.",
                                    "Employee Not Found",
                                    JOptionPane.WARNING_MESSAGE
                            );
                        } else {
                            showError(out.error);
                        }
                        return;
                    }

                    storeRememberedUsernameIfNeeded();
                    dispose();

                    if ("EMPLOYEE".equalsIgnoreCase(out.role)) {
                        new EmployeePortalFrame(out.user, out.employee).setVisible(true);
                    } else {
                        new DashboardFrame(out.user).setVisible(true);
                    }

                } catch (Exception ex) {
                    ex.printStackTrace();
                    showError("Login error: " + ex.getMessage());
                } finally {
                    btnLogin.setEnabled(true);
                }
            }
        }.execute();
    }

    private void showError(String msg) {
        lblError.setText(msg);
        Toolkit.getDefaultToolkit().beep();
    }

    // ===========================
    // ✅ STATIC background (NO moving circles)
    // ✅ LOGIN.png is visible (drawn first, then soft overlay)
    // ===========================
    private static class AnimatedGradientPanel extends JPanel {

        private BufferedImage bgImage;

        // Make it OBVIOUS the photo is there first, then you can reduce later.
        // 1.00f = full photo, 0.50f = half, etc.
        private final float photoOpacity = 1.00f;

        // Overlay to keep UI readable. LOWER = photo more visible.
        private final float overlayOpacity = 0.20f;

        AnimatedGradientPanel() {
            setOpaque(true);
            bgImage = loadBg();
        }

        private BufferedImage loadBg() {
            // Export-safe: load LOGIN.png from RESOURCES (classpath)
            try (InputStream in = getClass().getResourceAsStream("/LOGIN.png")) {
                if (in == null) {
                    System.out.println("[LoginFrame] /LOGIN.png NOT FOUND in RESOURCES (classpath).");
                    return null;
                }
                return ImageIO.read(in);
            } catch (Exception e) {
                System.out.println("[LoginFrame] Failed to load /LOGIN.png: " + e);
                return null;
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                // 1) Draw photo FIRST (full visibility)
                if (bgImage != null) {
                    Composite old = g2.getComposite();
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, photoOpacity));
                    drawCover(g2, bgImage, w, h);
                    g2.setComposite(old);
                } else {
                    // fallback solid background
                    g2.setColor(new Color(235, 245, 238));
                    g2.fillRect(0, 0, w, h);

                    // BIG visual indicator so you immediately know it's not loading
                    g2.setColor(new Color(200, 60, 60));
                    g2.setFont(g2.getFont().deriveFont(Font.BOLD, 14f));
                    g2.drawString("LOGIN.png NOT FOUND", 12, 22);
                }

                // 2) Very light overlay (so photo stays visible)
                Color a = new Color(225, 255, 242);
                Color b = new Color(255, 242, 225);
                GradientPaint gp = new GradientPaint(0, 0, a, w, h, b);

                Composite old2 = g2.getComposite();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, overlayOpacity));
                g2.setPaint(gp);
                g2.fillRect(0, 0, w, h);
                g2.setComposite(old2);

                // subtle border
                g2.setColor(new Color(0, 0, 0, 22));
                g2.drawRoundRect(8, 8, w - 16, h - 16, 30, 30);

            } finally {
                g2.dispose();
            }
        }

        private void drawCover(Graphics2D g2, BufferedImage img, int w, int h) {
            int iw = img.getWidth();
            int ih = img.getHeight();
            if (iw <= 0 || ih <= 0) {
                g2.drawImage(img, 0, 0, w, h, null);
                return;
            }

            double scale = Math.max(w / (double) iw, h / (double) ih);
            int dw = (int) Math.ceil(iw * scale);
            int dh = (int) Math.ceil(ih * scale);
            int dx = (w - dw) / 2;
            int dy = (h - dh) / 2;

            g2.drawImage(img, dx, dy, dw, dh, null);
        }
    }

    // ===========================
    // GLASS CARD
    // ===========================
    private static class GlassCard extends JPanel {
        GlassCard() { setOpaque(false); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                // Removed the low-opacity white background fill so the photo shows through.
                // Keep only a subtle rounded border to preserve structure.
                g2.setColor(new Color(255, 255, 255, 120));
                g2.drawRoundRect(0, 0, w - 1, h - 1, RADIUS, RADIUS);

            } finally {
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    // ===========================
    // Pill toggle styled like updated system buttons
    // ===========================
    private static class PillToggleButton extends JToggleButton {
        private final Color selectedBg = new Color(52, 180, 130);
        private final Color idleBg = new Color(249, 248, 244);
        private final Color selectedFg = Color.WHITE;
        private final Color idleFg = new Color(21, 39, 71);
        private final Color border = new Color(227, 217, 199);
        private boolean hover = false;
        private boolean press = false;
        private float hoverAnim = 0f;
        private float pressAnim = 0f;
        private Timer animTimer;

        PillToggleButton(String text) {
            super(text);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setPreferredSize(new Dimension(92, 32));
            setMaximumSize(new Dimension(92, 32));
            setMinimumSize(new Dimension(92, 32));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(getFont().deriveFont(Font.BOLD, 11.5f));

            animTimer = new Timer(16, e -> {
                boolean changed = false;
                float hoverTarget = hover && isEnabled() ? 1f : 0f;
                float pressTarget = press && isEnabled() ? 1f : 0f;

                float newHover = hoverAnim + (hoverTarget - hoverAnim) * 0.18f;
                float newPress = pressAnim + (pressTarget - pressAnim) * 0.22f;

                if (Math.abs(newHover - hoverAnim) > 0.001f) {
                    hoverAnim = newHover;
                    changed = true;
                }
                if (Math.abs(newPress - pressAnim) > 0.001f) {
                    pressAnim = newPress;
                    changed = true;
                }

                if (changed) repaint(); else animTimer.stop();
            });

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; startAnim(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; press = false; startAnim(); }
                @Override public void mousePressed(MouseEvent e) { if (isEnabled()) { press = true; startAnim(); } }
                @Override public void mouseReleased(MouseEvent e) { press = false; startAnim(); }
            });
        }

        private void startAnim() {
            if (!animTimer.isRunning()) animTimer.start();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();
                boolean selected = isSelected();
                Color bg = selected ? selectedBg : idleBg;
                Color fg = selected ? selectedFg : idleFg;

                if (pressAnim > 0.01f) {
                    bg = darken(bg, selected ? 0.10f : 0.05f);
                } else if (hoverAnim > 0.01f) {
                    bg = selected ? brighten(bg, 0.06f) : darken(idleBg, 0.02f);
                }

                int arc = 18;
                int shadowOffset = selected ? 3 : 2;
                g2.setColor(new Color(0, 0, 0, selected ? 30 : 18));
                g2.fillRoundRect(0, shadowOffset, w - 1, h - shadowOffset, arc, arc);

                GradientPaint fill = new GradientPaint(
                    0, 0,
                    brighten(bg, selected ? 0.18f : 0.10f),
                    0, h,
                    darken(bg, selected ? 0.18f : 0.08f)
                );
                g2.setPaint(fill);
                g2.fillRoundRect(0, 0, w - 1, h - 2, arc, arc);

                g2.setColor(new Color(255, 255, 255, selected ? 90 : 65));
                g2.drawRoundRect(1, 1, w - 3, h - 5, arc - 2, arc - 2);

                Color stroke = selected ? darken(bg, 0.28f) : border;
                g2.setColor(new Color(stroke.getRed(), stroke.getGreen(), stroke.getBlue(), selected ? 135 : 110));
                g2.drawRoundRect(0, 0, w - 2, h - 3, arc, arc);

                FontMetrics fm = g2.getFontMetrics(getFont());
                int tx = (w - fm.stringWidth(getText())) / 2;
                int ty = (h - fm.getHeight()) / 2 + fm.getAscent() - 1 + Math.round(pressAnim);
                g2.setColor(fg);
                g2.setFont(getFont());
                g2.drawString(getText(), tx, ty);
            } finally {
                g2.dispose();
            }
        }

        private static Color brighten(Color c, float amount) {
            int r = (int) (c.getRed() + (255 - c.getRed()) * amount);
            int g = (int) (c.getGreen() + (255 - c.getGreen()) * amount);
            int b = (int) (c.getBlue() + (255 - c.getBlue()) * amount);
            return new Color(Math.min(255, r), Math.min(255, g), Math.min(255, b), c.getAlpha());
        }

        private static Color darken(Color c, float amount) {
            int r = (int) (c.getRed() * (1f - amount));
            int g = (int) (c.getGreen() * (1f - amount));
            int b = (int) (c.getBlue() * (1f - amount));
            return new Color(Math.max(0, r), Math.max(0, g), Math.max(0, b), c.getAlpha());
        }
    }

    // ===========================
    // Rounded fields
    // ===========================
    private static class RoundedTextField extends JTextField {
        RoundedTextField() {
            putClientProperty(FlatClientProperties.STYLE, "arc:16; margin:7,10,7,10; background:rgba(255,255,255,220); borderColor:rgba(255,255,255,170); focusedBorderColor:#1ba060;");
        }
        @Override public Dimension getMaximumSize() {
            Dimension d = super.getMaximumSize();
            d.height = FIELD_H;
            return d;
        }
    }

    private static class RoundedPasswordField extends JPasswordField {
        RoundedPasswordField() {
            putClientProperty(FlatClientProperties.STYLE, "arc:16; margin:7,10,7,10; background:rgba(255,255,255,220); borderColor:rgba(255,255,255,170); focusedBorderColor:#1ba060;");
        }
        @Override public Dimension getMaximumSize() {
            Dimension d = super.getMaximumSize();
            d.height = FIELD_H;
            return d;
        }
    }

    // ===========================
    // Shadow label (for readability on photo backgrounds)
    // ===========================
    private static class RainbowLabel extends JComponent {
        private final String text;
        private final int alignment;
        private Color shadow = new Color(0, 0, 0, 170);
        private int shadowDx = 1;
        private int shadowDy = 2;

        // Vibrant but still readable palette (red→violet)
        private final Color[] palette = new Color[] {
                new Color(255,  71,  87),  // red-pink
                new Color(255, 159,  28),  // orange
                new Color(255, 214,  10),  // yellow
                new Color( 46, 213, 115),  // green
                new Color( 54, 162, 235),  // blue
                new Color( 95,  39, 205),  // indigo
                new Color(199,  0, 255)    // violet
        };

        RainbowLabel(String text, int alignment) {
            this.text = text;
            this.alignment = alignment;
            setOpaque(false);
            setForeground(Color.WHITE);
        }

        void setShadow(Color c, int dx, int dy) {
            if (c != null) shadow = c;
            shadowDx = dx;
            shadowDy = dy;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            Font f = getFont();
            if (f == null) f = UIManager.getFont("Label.font");
            FontMetrics fm = getFontMetrics(f);
            Insets ins = getInsets();
            int w = fm.stringWidth(text) + ins.left + ins.right;
            int h = fm.getHeight() + ins.top + ins.bottom;
            return new Dimension(w, h);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                Insets ins = getInsets();
                int w = getWidth() - ins.left - ins.right;
                int h = getHeight() - ins.top - ins.bottom;

                Font f = getFont();
                if (f == null) f = UIManager.getFont("Label.font");
                g2.setFont(f);
                FontMetrics fm = g2.getFontMetrics();

                int textW = fm.stringWidth(text);
                int x0;
                if (alignment == SwingConstants.CENTER) {
                    x0 = ins.left + (w - textW) / 2;
                } else if (alignment == SwingConstants.RIGHT) {
                    x0 = ins.left + Math.max(0, w - textW);
                } else {
                    x0 = ins.left;
                }
                int y = ins.top + fm.getAscent();

                // 1) soft shadow (no stroke/outline)
                g2.setColor(shadow);
                drawColoredText(g2, text, x0 + shadowDx, y + shadowDy, true);

                // 2) colored text
                drawColoredText(g2, text, x0, y, false);

            } finally {
                g2.dispose();
            }
        }

        private void drawColoredText(Graphics2D g2, String s, int x, int y, boolean shadowPass) {
            FontMetrics fm = g2.getFontMetrics();
            int cx = x;

            // Color per letter for "TRAVEL" vibe. Spaces keep spacing but no color jump.
            int colorIndex = 0;
            for (int i = 0; i < s.length(); i++) {
                String ch = s.substring(i, i + 1);
                int cw = fm.stringWidth(ch);

                if (!shadowPass) {
                    if (!ch.equals(" ")) {
                        g2.setColor(palette[colorIndex % palette.length]);
                        colorIndex++;
                    } else {
                        // keep current color index stable on spaces
                        g2.setColor(new Color(255, 255, 255, 0));
                    }
                } else {
                    // shadow already set outside
                }

                if (!(!shadowPass && ch.equals(" "))) {
                    g2.drawString(ch, cx, y);
                }
                cx += cw;
            }
        }
    }


class ShadowCheckBox extends JCheckBox {
        private Color shadowColor = new Color(0, 0, 0, 200);
        private int shadowDx = 2;
        private int shadowDy = 2;
        private String realText;

        ShadowCheckBox(String text) {
            super("");
            this.realText = text;
            setText(""); // keep LAF from drawing text; we draw it ourselves
            setOpaque(false);
        }

        void setShadow(Color c, int dx, int dy) {
            if (c != null) shadowColor = c;
            shadowDx = dx;
            shadowDy = dy;
            repaint();
        }

        @Override
        public void setText(String text) {
            // store but don't let default painting draw it
            this.realText = text;
            super.setText("");
        }

        @Override
        public String getText() {
            return realText;
        }

        @Override
        protected void paintComponent(Graphics g) {
            // Paint checkbox icon
            super.paintComponent(g);

            if (realText == null || realText.isEmpty()) return;

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                FontMetrics fm = g2.getFontMetrics(getFont());

                int iconW = 16;
                Icon icon = getIcon();
                if (icon != null) iconW = icon.getIconWidth();
                int gap = getIconTextGap();

                int tx = getInsets().left + iconW + gap;
                int ty = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();

                g2.setFont(getFont());
                g2.setColor(Color.BLACK);
                g2.drawString(realText, tx, ty);
            } finally {
                g2.dispose();
            }
        }
    }

class ShadowLabel extends JLabel {
        private Color shadowColor = new Color(0, 0, 0, 200);
        private int shadowDx = 2;
        private int shadowDy = 2;

        ShadowLabel(String text, int alignment) {
            super(text, alignment);
            setOpaque(false);
        }

        void setShadow(Color c, int dx, int dy) {
            if (c != null) shadowColor = c;
            shadowDx = dx;
            shadowDy = dy;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                String t = getText();
                if (t != null && !t.isEmpty()) {
                    FontMetrics fm = g2.getFontMetrics(getFont());
                    Insets ins = getInsets();
                    int w = getWidth() - ins.left - ins.right;

                    int x;
                    int strW = fm.stringWidth(t);
                    if (getHorizontalAlignment() == SwingConstants.CENTER) {
                        x = ins.left + Math.max(0, (w - strW) / 2);
                    } else if (getHorizontalAlignment() == SwingConstants.RIGHT) {
                        x = getWidth() - ins.right - strW;
                    } else {
                        x = ins.left;
                    }

                    int y = ins.top + fm.getAscent();

                    g2.setFont(getFont());
                    g2.setColor(Color.BLACK);
                    g2.drawString(t, x, y);
                }
            } finally {
                g2.dispose();
            }
        }
    }


}