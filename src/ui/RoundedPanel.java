package ui;

import java.awt.*;
import javax.swing.*;

public class RoundedPanel extends JPanel {

    private int radius = 24;

    private Color backgroundColor = Color.WHITE;

    // ✅ new: border support (fixes DashboardFrame errors)
    private Color borderColor = null;
    private int borderThickness = 0;

    public RoundedPanel() {
        setOpaque(false);
    }

    public RoundedPanel(int radius) {
        this.radius = radius;
        setOpaque(false);
    }

    // ===== Background =====
    public void setBackgroundColor(Color c) {
        this.backgroundColor = c;
        repaint();
    }

    public Color getBackgroundColor() {
        return backgroundColor;
    }

    @Override
    public void setBackground(Color bg) {
        super.setBackground(bg);
        this.backgroundColor = bg;
        repaint();
    }

    // ===== Radius =====
    public void setRadius(int radius) {
        this.radius = radius;
        repaint();
    }

    public int getRadius() {
        return radius;
    }

    // ===== ✅ Border (DashboardFrame uses these) =====
    public void setBorderColor(Color c) {
        this.borderColor = c;
        repaint();
    }

    public void setBorderThickness(int t) {
        this.borderThickness = Math.max(0, t);
        repaint();
    }

    public Color getBorderColor() {
        return borderColor;
    }

    public int getBorderThickness() {
        return borderThickness;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // background
        g2.setColor(backgroundColor);
        g2.fillRoundRect(0, 0, w, h, radius, radius);

        // border (optional)
        if (borderColor != null && borderThickness > 0) {
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(borderThickness));
            int inset = borderThickness / 2;
            g2.drawRoundRect(inset, inset, w - borderThickness, h - borderThickness, radius, radius);
        }

        g2.dispose();
        super.paintComponent(g);
    }
}
