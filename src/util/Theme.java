package util;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class Theme {

    // ===== Palette (green / orange / warm neutral) =====
    public static final Color BG      = new Color(0xFBF4E7);   // warm cream
    public static final Color CARD    = new Color(0xFFFFFF);
    public static final Color TEXT    = new Color(0x1F2937);   // slate
    public static final Color MUTED   = new Color(0x6B7280);   // gray

    public static final Color PRIMARY   = new Color(0x0B6B3A);  // deep green
    public static final Color PRIMARY_2 = new Color(0x0F7A46);  // hover green

    public static final Color ACCENT   = new Color(0xF59E0B);   // orange
    public static final Color ACCENT_2 = new Color(0xF7B733);   // hover orange

    public static final Color BORDER   = new Color(0xE6D7C3);
    public static final Color INPUT_BG = new Color(0xFFFFFF);

    // ===== Global LAF =====
    public static void applyLookAndFeel() {
        try {
            FlatLightLaf.setup();

            // Global roundness
            UIManager.put("Component.arc", 18);
            UIManager.put("Button.arc", 18);
            UIManager.put("TextComponent.arc", 14);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.trackArc", 999);

            // Slightly nicer defaults
            UIManager.put("Component.focusWidth", 1);
            UIManager.put("Component.innerFocusWidth", 0);

            // Table defaults (helps LogbookDialog look cleaner)
            UIManager.put("Table.rowHeight", 28);
            UIManager.put("Table.showHorizontalLines", true);
            UIManager.put("Table.showVerticalLines", false);
            UIManager.put("Table.intercellSpacing", new Dimension(0, 1));
            UIManager.put("Table.selectionBackground", new Color(0xEAF4EE));
            UIManager.put("Table.selectionForeground", TEXT);

        } catch (Exception ignored) {}
    }

    // ===== Card Panel =====
    public static JPanel cardPanel() {
        JPanel p = new JPanel();
        p.setBackground(CARD);
        p.setBorder(new CompoundBorder(
                new LineBorder(BORDER, 1, true),
                new EmptyBorder(16, 16, 16, 16)
        ));
        p.setOpaque(true);
        return p;
    }

    // ===== Inputs =====
    public static void styleInput(JTextField tf) {
        tf.setBackground(INPUT_BG);
        tf.setForeground(TEXT);
        tf.setCaretColor(TEXT);
        tf.setBorder(new CompoundBorder(
                new LineBorder(new Color(0xEADDCB), 1, true),
                new EmptyBorder(10, 12, 10, 12)
        ));
    }

    public static void styleCombo(JComboBox<?> cb) {
        cb.setBackground(INPUT_BG);
        cb.setForeground(TEXT);
        cb.setBorder(new CompoundBorder(
                new LineBorder(new Color(0xEADDCB), 1, true),
                new EmptyBorder(6, 10, 6, 10)
        ));
        cb.setOpaque(false);
        cb.setUI(new BasicComboBoxUI());
    }

    // Optional: style a JSpinner to match inputs (useful for LogbookDialog date pickers)
    public static void styleSpinner(JSpinner sp) {
        JComponent editor = sp.getEditor();
        if (editor instanceof JSpinner.DefaultEditor de) {
            JTextField tf = de.getTextField();
            styleInput(tf);
            tf.setBorder(new CompoundBorder(
                    new LineBorder(new Color(0xEADDCB), 1, true),
                    new EmptyBorder(8, 12, 8, 12)
            ));
        }
        sp.setOpaque(false);
    }

    // ===== Bubble Buttons (Animated Hover) =====
    public static void stylePrimaryButton(JButton b) {
        makeBubble(b, PRIMARY, PRIMARY_2, darker(PRIMARY), Color.WHITE, 18);
    }

    public static void styleSecondaryButton(JButton b) {
        // teal-ish secondary
        Color base = new Color(0x0B7C7C);
        makeBubble(b, base, lighten(base, 0.10f), darker(base), Color.WHITE, 18);
    }

    public static void styleOrangeButton(JButton b) {
        makeBubble(b, ACCENT, ACCENT_2, darker(ACCENT), new Color(0x111827), 18);
    }

    public static void styleNeutralButton(JButton b) {
        // White bubble with subtle border + hover tint
        Color base = Color.WHITE;
        Color hover = new Color(0xF3F4F6);
        Color press = new Color(0xE5E7EB);
        makeBubble(b, base, hover, press, new Color(0x111827), 18);
        // subtle outline
        b.putClientProperty("bubbleBorder", new Color(0xEADDCB));
    }

    private static void makeBubble(JButton b,
                                   Color base, Color hover, Color press,
                                   Color textColor, int radius) {

        BubbleButtonUI ui = new BubbleButtonUI(base, hover, press, textColor, radius);
        b.setUI(ui);

        // remove the “box”
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setOpaque(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        b.setForeground(textColor);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 12.5f));
        b.setMargin(new Insets(12, 18, 12, 18));

        // Smooth hover animation
        b.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { ui.setTarget(BubbleButtonUI.State.HOVER); }
            @Override public void mouseExited(MouseEvent e)  { ui.setTarget(BubbleButtonUI.State.NORMAL); }
            @Override public void mousePressed(MouseEvent e) { ui.setTarget(BubbleButtonUI.State.PRESSED); }
            @Override public void mouseReleased(MouseEvent e) {
                if (b.contains(e.getPoint())) ui.setTarget(BubbleButtonUI.State.HOVER);
                else ui.setTarget(BubbleButtonUI.State.NORMAL);
            }
        });
    }

    // ===== Custom UI with color tweening =====
    private static class BubbleButtonUI extends javax.swing.plaf.basic.BasicButtonUI {

        enum State { NORMAL, HOVER, PRESSED }

        private final Color base;
        private final Color hover;
        private final Color press;
        private final Color text;
        private final int radius;

        private State target = State.NORMAL;
        private Color current;

        private final Timer tween;

        BubbleButtonUI(Color base, Color hover, Color press, Color text, int radius) {
            this.base = base;
            this.hover = hover;
            this.press = press;
            this.text = text;
            this.radius = radius;

            this.current = base;

            // 60fps-ish small steps
            tween = new Timer(12, e -> step());
            tween.setRepeats(true);
        }

        void setTarget(State s) {
            target = s;
            if (!tween.isRunning()) tween.start();
        }

        private void step() {
            Color goal = switch (target) {
                case HOVER -> hover;
                case PRESSED -> press;
                default -> base;
            };

            if (distance(current, goal) < 3) {
                current = goal;
                tween.stop();
            } else {
                current = lerp(current, goal, 0.18f);
            }
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = c.getWidth();
            int h = c.getHeight();

            // Shadow (subtle)
            g2.setColor(new Color(0, 0, 0, 20));
            g2.fillRoundRect(2, 3, w - 4, h - 4, radius, radius);

            // Main bubble
            g2.setColor(current);
            g2.fillRoundRect(0, 0, w - 1, h - 1, radius, radius);

            // Optional border for neutral button
            Object borderColor = b.getClientProperty("bubbleBorder");
            if (borderColor instanceof Color bc) {
                g2.setColor(bc);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, radius, radius);
            }

            // Text (centered)
            g2.setFont(b.getFont());
            FontMetrics fm = g2.getFontMetrics();

            String txt = b.getText() == null ? "" : b.getText();
            int textW = fm.stringWidth(txt);
            int textH = fm.getAscent();

            int tx = (w - textW) / 2;
            int ty = (h + textH) / 2 - 2;

            g2.setColor(text);
            g2.drawString(txt, tx, ty);

            g2.dispose();
        }

        private static float distance(Color a, Color b) {
            int dr = a.getRed() - b.getRed();
            int dg = a.getGreen() - b.getGreen();
            int db = a.getBlue() - b.getBlue();
            return (float) Math.sqrt(dr * dr + dg * dg + db * db);
        }

        private static Color lerp(Color a, Color b, float t) {
            int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
            int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
            int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
            return new Color(clamp(r), clamp(g), clamp(bl));
        }

        private static int clamp(int v) {
            return Math.max(0, Math.min(255, v));
        }
    }

    // ===== Color helpers =====
    private static Color darker(Color c) {
        return new Color(
                Math.max(0, (int) (c.getRed() * 0.85)),
                Math.max(0, (int) (c.getGreen() * 0.85)),
                Math.max(0, (int) (c.getBlue() * 0.85))
        );
    }

    private static Color lighten(Color c, float amount) {
        int r = (int) (c.getRed() + (255 - c.getRed()) * amount);
        int g = (int) (c.getGreen() + (255 - c.getGreen()) * amount);
        int b = (int) (c.getBlue() + (255 - c.getBlue()) * amount);
        return new Color(Math.min(255, r), Math.min(255, g), Math.min(255, b));
    }
}
