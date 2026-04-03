package ui;

import util.AppConstants;
import model.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class DashboardFrame extends JFrame {
    private static final long serialVersionUID = 1L;
    private final User user;
    private CardLayout cardLayout;
    private JPanel contentCards;
    private static final String EMPLOYEES = "EMPLOYEES";
    private static final String LOGBOOK = "LOGBOOK";
    private static final String PAYROLL = "PAYROLL";
    private final Color BG = new Color(250, 244, 233);
    private final Color SURFACE = Color.WHITE;
    private final Color BORDER = new Color(231, 221, 205);
    private final Color TEXT_MUTED = new Color(120, 120, 120);
    private final Color TEXT_STRONG = new Color(35, 35, 35);
    private final Color GREEN = new Color(39, 164, 118);
    private final Color GREEN_DARK = new Color(31, 141, 101);
    private final Color PILL_BG = Color.WHITE;
    private final Color PILL_HOVER = new Color(238, 231, 218);
    private final Color PILL_BORDER = new Color(220, 206, 186);
    private final ZoneId MANILA = ZoneId.of("Asia/Manila");
    private final DateTimeFormatter CLOCK_FMT = DateTimeFormatter.ofPattern("EEE, MMM dd yyyy • hh:mm:ss a");
    private JLabel lblClock;
    private EmployeesPanel employeesPanel;
    private PayrollRunDialog payrollRunDialog;

    public DashboardFrame(User user) {
        super(AppConstants.APP_TITLE);
        this.user = user;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setMinimumSize(new Dimension(1200, 700));
        setLocationRelativeTo(null);
        setContentPane(buildRoot());
        startClock();
    }

    private JPanel buildRoot() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBackground(BG);
        root.setBorder(new EmptyBorder(8, 10, 10, 10));
        root.add(buildTopBar(), BorderLayout.NORTH);
        root.add(buildContent(), BorderLayout.CENTER);
        return root;
    }

    private JComponent buildTopBar() {
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        RoundedPanel bar = new RoundedPanel(18);
        bar.setLayout(new BorderLayout());
        bar.setBackground(new Color(252, 247, 237));
        bar.setBorderColor(BORDER);
        bar.setBorderThickness(1);
        bar.setBorder(new EmptyBorder(8, 10, 8, 10));

        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tabs.setOpaque(false);
        ButtonGroup g = new ButtonGroup();
        PillTab tEmp = new PillTab("Employees");
        PillTab tLog = new PillTab("Logbook");
        PillTab tPay = new PillTab("Payroll");
        for (JToggleButton b : new JToggleButton[]{tEmp, tLog, tPay}) { g.add(b); tabs.add(b); }

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        lblClock = new JLabel(" ");
        lblClock.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblClock.setForeground(TEXT_MUTED);
        JLabel lblUser = new JLabel("Logged in: " + safeUserText());
        lblUser.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblUser.setForeground(TEXT_MUTED);
        PillActionButton btnLogout = new PillActionButton("Logout");
        btnLogout.addActionListener(e -> logout());
        right.add(lblClock); right.add(lblUser); right.add(btnLogout);
        bar.add(tabs, BorderLayout.WEST); bar.add(right, BorderLayout.EAST);
        top.add(bar, BorderLayout.CENTER);

        tEmp.addActionListener(e -> showCard(EMPLOYEES));
        tLog.addActionListener(e -> showCard(LOGBOOK));
        tPay.addActionListener(e -> showCard(PAYROLL));
        tPay.setSelected(true);
        return top;
    }

    private JPanel buildContent() {
        cardLayout = new CardLayout();
        contentCards = new JPanel(cardLayout);
        contentCards.setOpaque(false);
        employeesPanel = new EmployeesPanel();
        payrollRunDialog = new PayrollRunDialog(this);
        contentCards.add(wrapPanel(employeesPanel), EMPLOYEES);
        contentCards.add(wrapPanel(new LogbookPanel()), LOGBOOK);
        contentCards.add(wrapDialog(payrollRunDialog), PAYROLL);
        cardLayout.show(contentCards, PAYROLL);
        return contentCards;
    }

    public void refreshPayrollEmployeeList() { if (payrollRunDialog != null) payrollRunDialog.refreshEmployeesKeepSelection(); }

    private JPanel wrapDialog(JDialog dialog) {
        JPanel panel = new JPanel(new BorderLayout()); panel.setOpaque(false);
        Container c = dialog.getContentPane(); dialog.setContentPane(new JPanel()); dialog.dispose();
        RoundedPanel surface = new RoundedPanel(18); surface.setLayout(new BorderLayout()); surface.setBackground(SURFACE); surface.setBorderColor(BORDER); surface.setBorderThickness(1); surface.setBorder(new EmptyBorder(8, 8, 8, 8)); surface.add(c, BorderLayout.CENTER);
        panel.add(surface, BorderLayout.CENTER); return panel;
    }
    private JPanel wrapPanel(JComponent content) {
        JPanel panel = new JPanel(new BorderLayout()); panel.setOpaque(false);
        RoundedPanel surface = new RoundedPanel(18); surface.setLayout(new BorderLayout()); surface.setBackground(SURFACE); surface.setBorderColor(BORDER); surface.setBorderThickness(1); surface.setBorder(new EmptyBorder(8, 8, 8, 8)); surface.add(content, BorderLayout.CENTER);
        panel.add(surface, BorderLayout.CENTER); return panel;
    }
    private void showCard(String name) { cardLayout.show(contentCards, name); }
    private void logout() { dispose(); new LoginFrame().setVisible(true); }
    private void startClock() { Timer t = new Timer(1000, e -> lblClock.setText(ZonedDateTime.now(MANILA).format(CLOCK_FMT))); t.setInitialDelay(0); t.start(); }
    private String safeUserText() { if (user == null) return "unknown"; try { Object v = user.getClass().getMethod("getUsername").invoke(user); if (v != null) { String s = String.valueOf(v).trim(); if (!s.isEmpty()) return s; } } catch (Exception ignored) {} String s = String.valueOf(user).trim(); return s.isEmpty() ? "user" : s; }

    private class PillTab extends JToggleButton {
        private boolean hover = false;
        private boolean pressed = false;
        private float hoverAnim = 0f;
        private float pressAnim = 0f;
        private Timer animTimer;

        PillTab(String text) {
            super(text);
            setFocusable(false);
            setFont(new Font("Segoe UI", Font.BOLD, 12));
            setMargin(new Insets(10, 20, 10, 20));
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            animTimer = new Timer(16, e -> animateStep());
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; startAnim(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; pressed = false; startAnim(); }
                @Override public void mousePressed(MouseEvent e) { pressed = true; startAnim(); }
                @Override public void mouseReleased(MouseEvent e) { pressed = false; startAnim(); }
            });
        }

        private void startAnim() {
            if (!animTimer.isRunning()) animTimer.start();
            repaint();
        }

        private void animateStep() {
            float hoverTarget = hover ? 1f : 0f;
            float pressTarget = pressed ? 1f : 0f;
            hoverAnim += (hoverTarget - hoverAnim) * 0.18f;
            pressAnim += (pressTarget - pressAnim) * 0.24f;
            boolean done = Math.abs(hoverTarget - hoverAnim) < 0.02f && Math.abs(pressTarget - pressAnim) < 0.02f;
            if (done) {
                hoverAnim = hoverTarget;
                pressAnim = pressTarget;
                animTimer.stop();
            }
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            d.height = Math.max(46, d.height);
            d.width += 6;
            return d;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = h - 2;
            int topInset = 1;
            int bottomInset = isSelected() ? 4 : 5;
            int lift = Math.round(pressAnim);
            int bodyH = Math.max(1, h - topInset - bottomInset + lift);

            Color base = isSelected() ? new Color(24, 130, 90) : new Color(249, 248, 244);
            if (!isSelected() && hoverAnim > 0.01f) {
                base = mix(base, Color.WHITE, 0.28f + hoverAnim * 0.12f);
            }
            if (isSelected() && hoverAnim > 0.01f) {
                base = mix(base, Color.WHITE, 0.06f + hoverAnim * 0.08f);
            }
            if (pressAnim > 0.01f) {
                base = darken(base, 0.05f + pressAnim * 0.07f);
            }

            int shadowAlpha = isSelected() ? (int) (28 + hoverAnim * 12 - pressAnim * 8) : (int) (15 + hoverAnim * 8 - pressAnim * 5);
            g2.setColor(new Color(0, 0, 0, Math.max(8, shadowAlpha)));
            g2.fillRoundRect(0, topInset + 4 - lift, w - 1, bodyH, arc, arc);
            g2.setColor(new Color(0, 0, 0, isSelected() ? 12 : 8));
            g2.fillRoundRect(1, topInset + 2 - lift, w - 3, bodyH, arc - 2, arc - 2);

            GradientPaint gp = new GradientPaint(
                    0, topInset,
                    mix(base, Color.WHITE, isSelected() ? 0.22f : 0.10f + hoverAnim * 0.08f),
                    0, topInset + bodyH,
                    darken(base, isSelected() ? 0.14f + pressAnim * 0.03f : 0.06f + pressAnim * 0.02f)
            );
            g2.setPaint(gp);
            g2.fillRoundRect(0, topInset - lift, w - 1, bodyH, arc, arc);

            g2.setClip(new java.awt.geom.RoundRectangle2D.Float(0, topInset - lift, w - 1, bodyH, arc, arc));
            GradientPaint gloss = new GradientPaint(
                    0, topInset,
                    new Color(255, 255, 255, isSelected() ? 105 : 95),
                    0, topInset + Math.max(12, bodyH / 2),
                    new Color(255, 255, 255, 0)
            );
            g2.setPaint(gloss);
            g2.fillRoundRect(1, topInset - lift + 1, w - 3, Math.max(12, bodyH / 2), arc - 2, arc - 2);
            g2.setClip(null);

            g2.setColor(new Color(255, 255, 255, isSelected() ? 78 : 72));
            g2.drawRoundRect(1, topInset - lift + 1, w - 3, bodyH - 3, arc - 3, arc - 3);
            g2.setColor(isSelected() ? new Color(18, 103, 73, 155) : new Color(220, 206, 186, 185));
            g2.drawRoundRect(0, topInset - lift, w - 2, bodyH - 1, arc, arc);

            g2.setFont(getFont());
            g2.setColor(isSelected() ? Color.WHITE : TEXT_STRONG);
            FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(getText())) / 2;
            int ty = (h - bottomInset - fm.getHeight()) / 2 + fm.getAscent() + Math.round(pressAnim);
            if (isSelected()) {
                g2.setColor(new Color(255, 255, 255, 70));
                g2.drawString(getText(), tx, ty - 1);
                g2.setColor(Color.WHITE);
            }
            g2.drawString(getText(), tx, ty);
            g2.dispose();
        }

        private Color mix(Color a, Color b, float t) {
            t = Math.max(0f, Math.min(1f, t));
            int r = (int) (a.getRed() * (1 - t) + b.getRed() * t);
            int g = (int) (a.getGreen() * (1 - t) + b.getGreen() * t);
            int bl = (int) (a.getBlue() * (1 - t) + b.getBlue() * t);
            return new Color(r, g, bl);
        }

        private Color darken(Color c, float t) {
            t = Math.max(0f, Math.min(1f, t));
            return new Color((int) (c.getRed() * (1f - t)), (int) (c.getGreen() * (1f - t)), (int) (c.getBlue() * (1f - t)));
        }
    }

    private class PillActionButton extends JButton {
        private boolean hover = false;
        private boolean pressed = false;
        private float hoverAnim = 0f;
        private float pressAnim = 0f;
        private final Timer animTimer;

        PillActionButton(String text) {
            super(text);
            setFocusable(false);
            setFont(new Font("Segoe UI", Font.BOLD, 12));
            setMargin(new Insets(10, 20, 10, 20));
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(110, 46));
            animTimer = new Timer(16, e -> animateStep());
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; startAnim(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; pressed = false; startAnim(); }
                @Override public void mousePressed(MouseEvent e) { pressed = true; startAnim(); }
                @Override public void mouseReleased(MouseEvent e) { pressed = false; startAnim(); }
            });
        }

        private void startAnim() {
            if (!animTimer.isRunning()) animTimer.start();
            repaint();
        }

        private void animateStep() {
            float hoverTarget = hover ? 1f : 0f;
            float pressTarget = pressed ? 1f : 0f;
            hoverAnim += (hoverTarget - hoverAnim) * 0.18f;
            pressAnim += (pressTarget - pressAnim) * 0.24f;
            boolean done = Math.abs(hoverTarget - hoverAnim) < 0.02f && Math.abs(pressTarget - pressAnim) < 0.02f;
            if (done) {
                hoverAnim = hoverTarget;
                pressAnim = pressTarget;
                animTimer.stop();
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = h - 2;
            int topInset = 1;
            int bottomInset = pressed ? 4 : 5;
            int lift = Math.round(pressAnim);
            int bodyH = Math.max(1, h - topInset - bottomInset + lift);
            boolean active = pressed || (getModel() != null && (getModel().isArmed() || getModel().isPressed()));

            Color base = active ? new Color(24, 130, 90) : new Color(249, 248, 244);
            if (!active && hoverAnim > 0.01f) {
                base = mix(base, Color.WHITE, 0.28f + hoverAnim * 0.12f);
            }
            if (active && hoverAnim > 0.01f) {
                base = mix(base, Color.WHITE, 0.06f + hoverAnim * 0.08f);
            }
            if (pressAnim > 0.01f) {
                base = darken(base, 0.05f + pressAnim * 0.07f);
            }

            int shadowAlpha = active ? (int) (28 + hoverAnim * 12 - pressAnim * 8) : (int) (15 + hoverAnim * 8 - pressAnim * 5);
            g2.setColor(new Color(0, 0, 0, Math.max(8, shadowAlpha)));
            g2.fillRoundRect(0, topInset + 4 - lift, w - 1, bodyH, arc, arc);
            g2.setColor(new Color(0, 0, 0, active ? 12 : 8));
            g2.fillRoundRect(1, topInset + 2 - lift, w - 3, bodyH, arc - 2, arc - 2);

            GradientPaint gp = new GradientPaint(
                    0, topInset,
                    mix(base, Color.WHITE, active ? 0.22f : 0.10f + hoverAnim * 0.08f),
                    0, topInset + bodyH,
                    darken(base, active ? 0.14f + pressAnim * 0.03f : 0.06f + pressAnim * 0.02f)
            );
            g2.setPaint(gp);
            g2.fillRoundRect(0, topInset - lift, w - 1, bodyH, arc, arc);

            g2.setClip(new java.awt.geom.RoundRectangle2D.Float(0, topInset - lift, w - 1, bodyH, arc, arc));
            GradientPaint gloss = new GradientPaint(
                    0, topInset,
                    new Color(255, 255, 255, active ? 105 : 95),
                    0, topInset + Math.max(12, bodyH / 2),
                    new Color(255, 255, 255, 0)
            );
            g2.setPaint(gloss);
            g2.fillRoundRect(1, topInset - lift + 1, w - 3, Math.max(12, bodyH / 2), arc - 2, arc - 2);
            g2.setClip(null);

            g2.setColor(new Color(255, 255, 255, active ? 78 : 72));
            g2.drawRoundRect(1, topInset - lift + 1, w - 3, bodyH - 3, arc - 3, arc - 3);
            g2.setColor(active ? new Color(18, 103, 73, 155) : new Color(220, 206, 186, 185));
            g2.drawRoundRect(0, topInset - lift, w - 2, bodyH - 1, arc, arc);

            g2.setFont(getFont());
            g2.setColor(active ? Color.WHITE : TEXT_STRONG);
            FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(getText())) / 2;
            int ty = (h - bottomInset - fm.getHeight()) / 2 + fm.getAscent() + Math.round(pressAnim);
            if (active) {
                g2.setColor(new Color(255, 255, 255, 70));
                g2.drawString(getText(), tx, ty - 1);
                g2.setColor(Color.WHITE);
            }
            g2.drawString(getText(), tx, ty);
            g2.dispose();
        }

        private Color mix(Color a, Color b, float t) {
            t = Math.max(0f, Math.min(1f, t));
            int r = (int) (a.getRed() * (1 - t) + b.getRed() * t);
            int g = (int) (a.getGreen() * (1 - t) + b.getGreen() * t);
            int bl = (int) (a.getBlue() * (1 - t) + b.getBlue() * t);
            return new Color(r, g, bl);
        }

        private Color darken(Color c, float t) {
            t = Math.max(0f, Math.min(1f, t));
            return new Color((int) (c.getRed() * (1f - t)), (int) (c.getGreen() * (1f - t)), (int) (c.getBlue() * (1f - t)));
        }
    }
}
