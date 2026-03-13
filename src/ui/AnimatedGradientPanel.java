package ui;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Random;

/**
 * Smooth “mixing” gradient background (green/orange/yellow) that drifts organically
 * (not just spinning). Soft, non-eye-hurting palette.
 */
public class AnimatedGradientPanel extends JPanel {

    private final Timer timer;
    private final Random rnd = new Random();

    // three moving points (they wander)
    private float ax = 0.15f, ay = 0.25f;
    private float bx = 0.75f, by = 0.20f;
    private float cx = 0.55f, cy = 0.85f;

    // velocities
    private float avx = 0.0018f, avy = 0.0012f;
    private float bvx = -0.0014f, bvy = 0.0016f;
    private float cvx = 0.0012f, cvy = -0.0017f;

    // slow hue shift factor
    private float t = 0f;

    public AnimatedGradientPanel() {
        setOpaque(true);

        // ~60 FPS feel
        timer = new Timer(16, e -> {
            t += 0.012f; // speed (increase to go faster)
            wander();
            repaint();
        });
        timer.start();
    }

    private void wander() {
        // slight random acceleration = “mixing” behavior
        avx += (rnd.nextFloat() - 0.5f) * 0.00025f;
        avy += (rnd.nextFloat() - 0.5f) * 0.00025f;

        bvx += (rnd.nextFloat() - 0.5f) * 0.00025f;
        bvy += (rnd.nextFloat() - 0.5f) * 0.00025f;

        cvx += (rnd.nextFloat() - 0.5f) * 0.00025f;
        cvy += (rnd.nextFloat() - 0.5f) * 0.00025f;

        // dampen so it doesn't go crazy
        avx *= 0.985f; avy *= 0.985f;
        bvx *= 0.985f; bvy *= 0.985f;
        cvx *= 0.985f; cvy *= 0.985f;

        ax += avx; ay += avy;
        bx += bvx; by += bvy;
        cx += cvx; cy += cvy;

        // bounce inside bounds [0..1]
        if (ax < 0) { ax = 0; avx = Math.abs(avx); }
        if (ax > 1) { ax = 1; avx = -Math.abs(avx); }
        if (ay < 0) { ay = 0; avy = Math.abs(avy); }
        if (ay > 1) { ay = 1; avy = -Math.abs(avy); }

        if (bx < 0) { bx = 0; bvx = Math.abs(bvx); }
        if (bx > 1) { bx = 1; bvx = -Math.abs(bvx); }
        if (by < 0) { by = 0; bvy = Math.abs(bvy); }
        if (by > 1) { by = 1; bvy = -Math.abs(bvy); }

        if (cx < 0) { cx = 0; cvx = Math.abs(cvx); }
        if (cx > 1) { cx = 1; cvx = -Math.abs(cvx); }
        if (cy < 0) { cy = 0; cvy = Math.abs(cvy); }
        if (cy > 1) { cy = 1; cvy = -Math.abs(cvy); }
    }

    // Soft palette, shifted slightly over time
    private Color softPalette(float phase) {
        // base hues: green, orange, yellow (softened)
        float hue = (phase % 1f);
        // keep it warm-green range (avoid neon)
        float baseHue = 0.24f + 0.10f * (float)Math.sin(phase * 2.0);
        float mix = 0.50f + 0.50f * (float)Math.sin(phase * 1.7);

        // build two soft colors and blend them
        Color c1 = Color.getHSBColor(baseHue, 0.35f, 0.95f);       // soft green
        Color c2 = Color.getHSBColor(0.10f + 0.05f*hue, 0.38f, 0.98f); // soft orange/yellow

        return lerp(c1, c2, mix * 0.55f);
    }

    private static Color lerp(Color a, Color b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = (int)(a.getRed()   + (b.getRed()   - a.getRed())   * t);
        int g = (int)(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl= (int)(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t);
        return new Color(r, g, bl);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Three drifting colors
        Color ca = softPalette(t * 0.15f + 0.12f);
        Color cb = softPalette(t * 0.15f + 0.42f);
        Color cc = softPalette(t * 0.15f + 0.72f);

        Point2D pa = new Point2D.Float(ax * w, ay * h);
        Point2D pb = new Point2D.Float(bx * w, by * h);
        Point2D pc = new Point2D.Float(cx * w, cy * h);

        // Paint layered radial gradients to create “mixing”
        paintBlob(g2, w, h, pa, ca, 0.92f);
        paintBlob(g2, w, h, pb, cb, 0.88f);
        paintBlob(g2, w, h, pc, cc, 0.84f);

        // subtle soft overlay to reduce eye strain
        g2.setComposite(AlphaComposite.SrcOver.derive(0.10f));
        g2.setPaint(new GradientPaint(0, 0, Color.WHITE, 0, h, Color.BLACK));
        g2.fillRect(0, 0, w, h);

        g2.dispose();
    }

    private void paintBlob(Graphics2D g2, int w, int h, Point2D center, Color c, float alpha) {
        float radius = Math.max(w, h) * 0.85f;

        Color inner = new Color(c.getRed(), c.getGreen(), c.getBlue(), (int)(255 * alpha));
        Color outer = new Color(c.getRed(), c.getGreen(), c.getBlue(), 0);

        RadialGradientPaint rg = new RadialGradientPaint(
                center,
                radius,
                new float[]{0f, 1f},
                new Color[]{inner, outer}
        );

        g2.setComposite(AlphaComposite.SrcOver);
        g2.setPaint(rg);
        g2.fillRect(0, 0, w, h);
    }
}
