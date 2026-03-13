package ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class RoundedButton extends JButton {

    private final Color baseBg;
    private final Color baseFg;

    private boolean hover = false;
    private boolean press = false;

    // Look like your screenshot: pill buttons
    private final int arc = 22;

    public RoundedButton(String text, Color background, Color foreground) {
        super(text);
        this.baseBg = background;
        this.baseFg = foreground;

        setFont(getFont().deriveFont(Font.BOLD, 13f));
        setForeground(baseFg);

        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);

        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setMargin(new Insets(10, 18, 10, 18));

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            @Override public void mouseExited(MouseEvent e)  { hover = false; press = false; repaint(); }
            @Override public void mousePressed(MouseEvent e) { if (isEnabled()) { press = true; repaint(); } }
            @Override public void mouseReleased(MouseEvent e){ press = false; repaint(); }
        });
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.height = Math.max(d.height, 44); // like your sample
        return d;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        boolean enabled = isEnabled();

        // --- Colors (KEEP your original colors but make hover obvious)
        Color bg = baseBg;

        if (!enabled) {
            // disabled: still visible, just washed out
            bg = mix(baseBg, Color.WHITE, 0.45f);
        } else if (press) {
            // pressed: slightly darker
            bg = darkenSmart(baseBg, 0.12f);
        } else if (hover) {
            // hover: “lights up” (dark colors brighten, light colors slightly darken)
            bg = hoverSmart(baseBg);
        }

        // --- Soft shadow like your sample
        if (enabled) {
            int a = press ? 30 : (hover ? 55 : 40);
            int y = press ? 3 : 4;
            g2.setColor(new Color(0, 0, 0, a));
            g2.fillRoundRect(1, y, w - 2, h - 2, arc + 8, arc + 8);
        }

        // --- Fill
        g2.setColor(bg);
        g2.fillRoundRect(0, 0, w, h, arc, arc);

        // --- Border (subtle, stronger on hover)
        int borderA = enabled ? (hover ? 55 : 30) : 22;
        g2.setColor(new Color(0, 0, 0, borderA));
        g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

        // --- Text color (use your provided fg, but ensure readable on colored buttons)
        Color text = baseFg;
        if (enabled && isDark(bg) && !isWhiteish(baseFg)) {
            // if bg is dark but text isn't light enough, force white for readability
            text = Color.WHITE;
        }
        if (!enabled) {
            text = new Color(text.getRed(), text.getGreen(), text.getBlue(), 170);
        }

        // --- Draw text
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        String s = getText();

        int tx = (w - fm.stringWidth(s)) / 2;
        int ty = (h + fm.getAscent()) / 2 - 2;

        g2.setColor(text);
        g2.drawString(s, tx, ty);

        g2.dispose();
    }

    // ===== Hover behavior that works for ALL colors =====

    private static Color hoverSmart(Color c) {
        if (isVeryLight(c)) {
            // white/near-white buttons: darken slightly so hover is visible
            return darkenSmart(c, 0.06f);
        }
        // colored buttons: brighten slightly so it “lights up”
        return brightenSmart(c, 0.10f);
    }

    private static Color brightenSmart(Color c, float amount) {
        amount = clamp01(amount);
        int r = (int) (c.getRed() + (255 - c.getRed()) * amount);
        int g = (int) (c.getGreen() + (255 - c.getGreen()) * amount);
        int b = (int) (c.getBlue() + (255 - c.getBlue()) * amount);
        return new Color(clamp255(r), clamp255(g), clamp255(b), c.getAlpha());
    }

    private static Color darkenSmart(Color c, float amount) {
        amount = clamp01(amount);
        int r = (int) (c.getRed() * (1f - amount));
        int g = (int) (c.getGreen() * (1f - amount));
        int b = (int) (c.getBlue() * (1f - amount));
        return new Color(clamp255(r), clamp255(g), clamp255(b), c.getAlpha());
    }

    private static Color mix(Color a, Color b, float t) {
        t = clamp01(t);
        int r = (int) (a.getRed() * (1 - t) + b.getRed() * t);
        int g = (int) (a.getGreen() * (1 - t) + b.getGreen() * t);
        int bl = (int) (a.getBlue() * (1 - t) + b.getBlue() * t);
        return new Color(clamp255(r), clamp255(g), clamp255(bl), a.getAlpha());
    }

    private static boolean isVeryLight(Color c) {
        return luminance(c) > 220;
    }

    private static boolean isDark(Color c) {
        return luminance(c) < 140;
    }

    private static boolean isWhiteish(Color c) {
        return c.getRed() > 220 && c.getGreen() > 220 && c.getBlue() > 220;
    }

    private static double luminance(Color c) {
        return 0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue();
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
