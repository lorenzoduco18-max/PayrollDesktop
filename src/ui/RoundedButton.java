package ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

public class RoundedButton extends JButton {

    private Color baseBg;
    private Color baseFg;

    private boolean hover = false;
    private boolean press = false;

    private int cornerRadius = 22;

    // micro animation state
    private float hoverAnim = 0f;
    private float pressAnim = 0f;
    private float rippleAnim = 1f;
    private float rippleAlpha = 0f;
    private int rippleX = -1;
    private int rippleY = -1;

    // removed final so Eclipse won't complain about self-reference during initialization
    private Timer animTimer;

    public RoundedButton(String text, Color background, Color foreground) {
        super(text);
        this.baseBg = premiumizeButtonColor(background);
        this.baseFg = foreground;

        setFont(getFont().deriveFont(Font.BOLD, 13f));
        super.setForeground(baseFg);

        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);

        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setMargin(new Insets(10, 18, 10, 18));

        animTimer = new Timer(16, e -> {
            boolean changed = false;

            float hoverTarget = hover && isEnabled() ? 1f : 0f;
            float pressTarget = press && isEnabled() ? 1f : 0f;

            float newHover = approach(hoverAnim, hoverTarget, 0.18f);
            float newPress = approach(pressAnim, pressTarget, 0.22f);

            if (Math.abs(newHover - hoverAnim) > 0.001f) {
                hoverAnim = newHover;
                changed = true;
            }
            if (Math.abs(newPress - pressAnim) > 0.001f) {
                pressAnim = newPress;
                changed = true;
            }

            if (rippleAlpha > 0.001f) {
                rippleAnim = Math.min(1f, rippleAnim + 0.08f);
                rippleAlpha = Math.max(0f, rippleAlpha - 0.045f);
                changed = true;
            } else {
                rippleAlpha = 0f;
            }

            if (changed) {
                repaint();
            } else {
                animTimer.stop();
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                hover = true;
                startAnim();
            }

            @Override public void mouseExited(MouseEvent e) {
                hover = false;
                press = false;
                startAnim();
            }

            @Override public void mousePressed(MouseEvent e) {
                if (isEnabled()) {
                    press = true;
                    rippleX = e.getX();
                    rippleY = e.getY();
                    rippleAnim = 0f;
                    rippleAlpha = 0.24f;
                    startAnim();
                }
            }

            @Override public void mouseReleased(MouseEvent e) {
                press = false;
                startAnim();
            }
        });
    }

    public void setCornerRadius(int radius) {
        this.cornerRadius = Math.max(8, radius);
        repaint();
    }

    public int getCornerRadius() {
        return cornerRadius;
    }

    private void startAnim() {
        if (!animTimer.isRunning()) {
            animTimer.start();
        }
        repaint();
    }

    @Override
    public void setBackground(Color bg) {
        super.setBackground(bg);
        this.baseBg = premiumizeButtonColor(bg);
        repaint();
    }

    @Override
    public void setForeground(Color fg) {
        super.setForeground(fg);
        this.baseFg = fg;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        d.height = Math.max(d.height, 44);
        return d;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            g2.dispose();
            return;
        }

        boolean enabled = isEnabled();
        int topInset = 1;
        int bottomInset = enabled ? (int) Math.round(4 - (2.2f * pressAnim)) : 2;
        int bodyX = 0;
        int bodyY = topInset;
        int bodyW = w - 1;
        int bodyH = Math.max(1, h - topInset - bottomInset);
        int shadowLift = enabled ? (int) Math.round(pressAnim) : 0;

        int arc = Math.min(cornerRadius, Math.max(8, bodyH));
        RoundRectangle2D.Float body = new RoundRectangle2D.Float(bodyX, bodyY + shadowLift, bodyW, bodyH, arc, arc);

        Color bg = effectiveBaseColor();
        float hoverBoost = hoverAnim * 0.10f;
        Color topColor = mix(bg, Color.WHITE, enabled ? 0.20f + hoverBoost : 0.08f);
        Color midColor = mix(bg, Color.WHITE, enabled ? 0.07f + hoverBoost * 0.5f : 0.03f);
        Color bottomColor = darkenSmart(bg, enabled ? (0.13f + pressAnim * 0.05f) : 0.05f);

        paintOuterShadow(g2, bodyX, bodyY + shadowLift, bodyW, bodyH, enabled);
        paintBottomAmbient(g2, bodyX, bodyY + shadowLift, bodyW, bodyH, enabled);

        LinearGradientPaint mainFill = new LinearGradientPaint(
                0, bodyY,
                0, bodyY + bodyH,
                new float[]{0f, 0.48f, 1f},
                new Color[]{topColor, midColor, bottomColor}
        );
        g2.setPaint(mainFill);
        g2.fill(body);

        paintTopGloss(g2, bodyX, bodyY + shadowLift, bodyW, bodyH, enabled);
        paintInnerLight(g2, bodyX, bodyY + shadowLift, bodyW, bodyH, enabled);
        paintEdgeStroke(g2, bodyX, bodyY + shadowLift, bodyW, bodyH, enabled, bg);
        paintHoverGlow(g2, bodyX, bodyY + shadowLift, bodyW, bodyH, bg);
        paintRipple(g2, bodyX, bodyY + shadowLift, bodyW, bodyH);

        Color text = resolveTextColor(bg, enabled);
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        String s = getText();
        int tx = (w - fm.stringWidth(s)) / 2;
        int ty = (h + fm.getAscent()) / 2 - 2 + Math.round(pressAnim);

        g2.setColor(text);
        g2.drawString(s, tx, ty);
        g2.dispose();
    }

    private Color effectiveBaseColor() {
        if (!isEnabled()) {
            return mix(baseBg, Color.WHITE, 0.42f);
        }
        if (pressAnim > 0.01f) {
            return darkenSmart(baseBg, 0.05f + pressAnim * 0.08f);
        }
        if (hoverAnim > 0.01f) {
            return isVeryLight(baseBg)
                    ? darkenSmart(baseBg, 0.02f + hoverAnim * 0.03f)
                    : brightenSmart(baseBg, 0.03f + hoverAnim * 0.08f);
        }
        return baseBg;
    }

    private void paintOuterShadow(Graphics2D g2, int x, int y, int w, int h, boolean enabled) {
        if (!enabled) return;

        int[][] layers;
        if (pressAnim > 0.4f) {
            layers = new int[][]{{3, 18}, {2, 12}};
        } else if (hoverAnim > 0.02f) {
            layers = new int[][]{{7, 42}, {5, 28}, {3, 18}};
        } else {
            layers = new int[][]{{5, 28}, {3, 18}, {2, 10}};
        }

        for (int[] layer : layers) {
            int offset = layer[0];
            int alpha = layer[1];
            g2.setColor(new Color(0, 0, 0, alpha));
            g2.fillRoundRect(x, y + offset, w, h, cornerRadius + offset * 2, cornerRadius + offset * 2);
        }
    }

    private void paintBottomAmbient(Graphics2D g2, int x, int y, int w, int h, boolean enabled) {
        if (!enabled) return;

        int alpha = (int) (20 + hoverAnim * 14 - pressAnim * 4);
        GradientPaint gp = new GradientPaint(
                0, y + h * 0.55f, new Color(255, 255, 255, 0),
                0, y + h + 6, new Color(255, 255, 255, Math.max(0, alpha))
        );
        Shape old = g2.getClip();
        g2.clip(new RoundRectangle2D.Float(x, y, w, h + 8, cornerRadius, cornerRadius));
        g2.setPaint(gp);
        g2.fillRoundRect(x + 1, y + h / 2, w - 2, h / 2 + 8, cornerRadius, cornerRadius);
        g2.setClip(old);
    }

    private void paintTopGloss(Graphics2D g2, int x, int y, int w, int h, boolean enabled) {
        int glossHeight = Math.max(10, (int) (h * (0.42f - pressAnim * 0.08f)));
        Shape oldClip = g2.getClip();
        RoundRectangle2D.Float full = new RoundRectangle2D.Float(x, y, w, h, cornerRadius, cornerRadius);
        g2.clip(full);

        int topAlpha = enabled ? (int) (98 + hoverAnim * 32) : 55;
        int midAlpha = enabled ? (int) (42 + hoverAnim * 20) : 28;

        LinearGradientPaint gloss = new LinearGradientPaint(
                0, y,
                0, y + glossHeight,
                new float[]{0f, 0.58f, 1f},
                new Color[]{
                        new Color(255, 255, 255, topAlpha),
                        new Color(255, 255, 255, midAlpha),
                        new Color(255, 255, 255, 0)
                }
        );
        g2.setPaint(gloss);
        g2.fillRoundRect(x + 1, y + 1, w - 2, glossHeight, Math.max(8, cornerRadius - 2), Math.max(8, cornerRadius - 2));

        RoundRectangle2D.Float lowerCut = new RoundRectangle2D.Float(x + 2, y + glossHeight / 2f, w - 4, h, cornerRadius, cornerRadius);
        Area sheen = new Area(new RoundRectangle2D.Float(x + 2, y + 2, w - 4, glossHeight, Math.max(8, cornerRadius - 3), Math.max(8, cornerRadius - 3)));
        sheen.subtract(new Area(lowerCut));
        g2.setColor(new Color(255, 255, 255, enabled ? (int) (35 + hoverAnim * 16) : 18));
        g2.fill(sheen);

        g2.setClip(oldClip);
    }

    private void paintInnerLight(Graphics2D g2, int x, int y, int w, int h, boolean enabled) {
        g2.setColor(new Color(255, 255, 255, enabled ? (int) (72 + hoverAnim * 18) : 28));
        g2.drawRoundRect(x + 1, y + 1, w - 3, h - 3, Math.max(8, cornerRadius - 2), Math.max(8, cornerRadius - 2));

        g2.setColor(new Color(255, 255, 255, enabled ? (int) (28 + hoverAnim * 10) : 14));
        g2.drawRoundRect(x + 2, y + 2, w - 5, h - 5, Math.max(8, cornerRadius - 4), Math.max(8, cornerRadius - 4));
    }

    private void paintEdgeStroke(Graphics2D g2, int x, int y, int w, int h, boolean enabled, Color bg) {
        Color outer = enabled
                ? darkenSmart(bg, 0.24f + hoverAnim * 0.08f)
                : mix(bg, Color.GRAY, 0.18f);
        g2.setColor(new Color(outer.getRed(), outer.getGreen(), outer.getBlue(), enabled ? 118 : 55));
        g2.drawRoundRect(x, y, w - 1, h - 1, cornerRadius, cornerRadius);
    }

    private void paintHoverGlow(Graphics2D g2, int x, int y, int w, int h, Color bg) {
        if (hoverAnim <= 0.01f || !isEnabled()) return;

        Color glow = mix(bg, Color.WHITE, 0.42f);
        int alpha1 = (int) (18 + hoverAnim * 32);
        int alpha2 = (int) (10 + hoverAnim * 18);

        g2.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), alpha1));
        g2.drawRoundRect(x - 1, y - 1, w + 1, h + 1, cornerRadius + 2, cornerRadius + 2);

        g2.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), alpha2));
        g2.drawRoundRect(x - 2, y - 2, w + 3, h + 3, cornerRadius + 4, cornerRadius + 4);
    }

    private void paintRipple(Graphics2D g2, int x, int y, int w, int h) {
        if (rippleAlpha <= 0.001f || rippleX < 0 || rippleY < 0) return;

        Shape oldClip = g2.getClip();
        g2.clip(new RoundRectangle2D.Float(x, y, w, h, cornerRadius, cornerRadius));

        float maxRadius = (float) Math.hypot(w, h);
        float radius = 10f + rippleAnim * maxRadius;
        int alpha = (int) (255f * rippleAlpha);

        Color rippleColor = new Color(255, 255, 255, Math.max(0, Math.min(255, alpha)));
        g2.setColor(rippleColor);
        g2.fill(new Ellipse2D.Float(rippleX - radius, rippleY - radius, radius * 2, radius * 2));

        g2.setClip(oldClip);
    }

    private Color resolveTextColor(Color bg, boolean enabled) {
        Color text = baseFg;
        if (enabled && isDark(bg) && !isWhiteish(baseFg)) {
            text = Color.WHITE;
        }
        if (!enabled) {
            text = new Color(text.getRed(), text.getGreen(), text.getBlue(), 165);
        }
        return text;
    }

    private static float approach(float current, float target, float speed) {
        return current + (target - current) * speed;
    }

    private static Color premiumizeButtonColor(Color c) {
        if (c == null) {
            return new Color(24, 130, 90);
        }

        boolean greenDominant = c.getGreen() >= c.getRed() + 18 && c.getGreen() >= c.getBlue() + 10;
        boolean notNeutral = saturation(c) > 0.16f;

        if (greenDominant && notNeutral) {
            float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
            float hue = hsb[0];
            if (hue >= 0.28f && hue <= 0.48f) {
                if (hue > 0.41f) {
                    return new Color(20, 124, 98);
                } else if (hue > 0.35f) {
                    return new Color(22, 128, 92);
                } else {
                    return new Color(24, 130, 90);
                }
            }
        }

        return c;
    }

    private static float saturation(Color c) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        return hsb[1];
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
        int al = (int) (a.getAlpha() * (1 - t) + b.getAlpha() * t);
        return new Color(clamp255(r), clamp255(g), clamp255(bl), clamp255(al));
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
