package ui;

import com.github.sarxos.webcam.Webcam;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

public class CameraCaptureDialog extends JDialog {

    private BufferedImage captured;

    // Your webcam is mirrored by driver -> FORCE unmirror for live + captured + saved
    private static final boolean FORCE_UNMIRROR = true;

    // No black borders + no stretching -> crop to fill (center crop)
    private static final boolean CROP_TO_FILL = true;

    private Webcam webcam;

    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);

    private LiveView liveView;
    private final PreviewView previewView = new PreviewView();

    private final JLabel status = new JLabel("Live preview", SwingConstants.LEFT);

    // ✅ FINAL: simple rounded + colored buttons
    private final RoundedButton btnCancel  = new RoundedButton("Cancel",
            new Color(239, 68, 68), Color.WHITE);          // red
    private final RoundedButton btnRetake  = new RoundedButton("Retake",
            new Color(243, 244, 246), new Color(17, 24, 39)); // light gray
    private final RoundedButton btnCapture = new RoundedButton("Capture",
            new Color(59, 130, 246), Color.WHITE);         // blue
    private final RoundedButton btnUse     = new RoundedButton("Use Photo",
            new Color(16, 185, 129), Color.WHITE);         // green

    public CameraCaptureDialog(Window owner) {
        super(owner, "Capture Photo", ModalityType.APPLICATION_MODAL);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setPreferredSize(new Dimension(980, 680));
        setMinimumSize(new Dimension(980, 680));

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(14, 14, 14, 14));
        setContentPane(root);

        liveView = new LiveView();

        JPanel liveCard = wrapTitled(liveView, "Live preview");
        JPanel previewCard = wrapTitled(previewView, "Captured");

        center.add(liveCard, "LIVE");
        center.add(previewCard, "PREVIEW");
        root.add(center, BorderLayout.CENTER);

        // Bottom bar (always visible)
        JPanel bottom = new JPanel(new BorderLayout(12, 0));
        bottom.setBorder(new EmptyBorder(8, 0, 0, 0));
        bottom.setOpaque(true);
        bottom.setBackground(root.getBackground());

        status.setOpaque(false);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        actions.setOpaque(false);

        // consistent sizing
        normalizeButton(btnCancel);
        normalizeButton(btnRetake);
        normalizeButton(btnCapture);
        normalizeButton(btnUse);

        actions.add(btnCancel);
        actions.add(btnRetake);
        actions.add(btnCapture);
        actions.add(btnUse);

        bottom.add(status, BorderLayout.WEST);
        bottom.add(actions, BorderLayout.EAST);
        root.add(bottom, BorderLayout.SOUTH);

        // states
        btnRetake.setEnabled(false);
        btnUse.setEnabled(false);
        cards.show(center, "LIVE");

        // actions
        btnCancel.addActionListener(e -> {
            captured = null;
            dispose();
        });

        btnCapture.addActionListener(e -> doCapture());

        btnRetake.addActionListener(e -> {
            captured = null;
            previewView.setImage(null);
            btnRetake.setEnabled(false);
            btnUse.setEnabled(false);
            btnCapture.setEnabled(true);
            status.setText("Live preview");
            cards.show(center, "LIVE");
        });

        btnUse.addActionListener(e -> dispose());

        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) { startCamera(); }
            @Override public void windowClosed(WindowEvent e) { stopCamera(); }
        });

        pack();
        setLocationRelativeTo(owner);
    }

    public BufferedImage getCapturedImage() {
        return captured;
    }

    // ---------------- Plain sizing

    private void normalizeButton(AbstractButton b) {
        b.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        b.setFocusPainted(false);
        b.setPreferredSize(new Dimension(120, 36));
    }

    // ---------------- Camera lifecycle

    private void startCamera() {
        SwingUtilities.invokeLater(() -> {
            try {
                webcam = Webcam.getDefault();
                if (webcam == null) {
                    status.setText("No camera found.");
                    btnCapture.setEnabled(false);
                    return;
                }

                Dimension best = chooseMaxSize(webcam);
                webcam.setViewSize(best);
                webcam.open(true);

                status.setText("Live preview (" + best.width + "x" + best.height + ")");
                liveView.start(webcam);

            } catch (Throwable t) {
                status.setText("Failed to start camera: " + t.getMessage());
                btnCapture.setEnabled(false);
            }
        });
    }

    private Dimension chooseMaxSize(Webcam cam) {
        try {
            Dimension[] sizes = cam.getViewSizes();
            if (sizes == null || sizes.length == 0) return cam.getViewSize();

            Dimension best = sizes[0];
            long bestArea = (long) best.width * best.height;

            for (Dimension d : sizes) {
                long area = (long) d.width * d.height;
                if (area > bestArea) {
                    best = d;
                    bestArea = area;
                }
            }
            return best != null ? best : cam.getViewSize();
        } catch (Throwable ignored) {
            return new Dimension(640, 480);
        }
    }

    private void stopCamera() {
        try { if (liveView != null) liveView.stop(); } catch (Throwable ignored) {}
        try { if (webcam != null) webcam.close(); } catch (Throwable ignored) {}
        webcam = null;
    }

    // ---------------- Capture

    private void doCapture() {
        if (webcam == null || !webcam.isOpen()) {
            status.setText("Camera not ready.");
            return;
        }

        BufferedImage img = webcam.getImage();
        if (img == null) {
            status.setText("Failed to capture frame.");
            return;
        }

        if (FORCE_UNMIRROR) img = flipHorizontal(img);

        captured = img;
        previewView.setImage(captured);

        btnCapture.setEnabled(false);
        btnRetake.setEnabled(true);
        btnUse.setEnabled(true);

        status.setText("Captured!");
        cards.show(center, "PREVIEW");
    }

    // ---------------- UI helpers

    private static JPanel wrapTitled(JComponent comp, String title) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(comp, BorderLayout.CENTER);
        return p;
    }

    private static BufferedImage flipHorizontal(BufferedImage src) {
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g2 = dst.createGraphics();
        g2.drawImage(src, src.getWidth(), 0, -src.getWidth(), src.getHeight(), null);
        g2.dispose();
        return dst;
    }

    // ---------------- Live preview panel (crop-to-fill + HQ scaling)

    private class LiveView extends JPanel {
        private volatile boolean running = false;
        private Thread thread;
        private BufferedImage current;

        LiveView() { setBackground(Color.BLACK); }

        void start(Webcam cam) {
            stop();
            running = true;

            thread = new Thread(() -> {
                while (running && cam != null && cam.isOpen()) {
                    try {
                        BufferedImage img = cam.getImage();
                        if (img != null) {
                            if (FORCE_UNMIRROR) img = flipHorizontal(img);
                            current = img;
                            repaint();
                        }
                        Thread.sleep(15);
                    } catch (Throwable ignored) {}
                }
            }, "camera-live-view");

            thread.setDaemon(true);
            thread.start();
        }

        void stop() {
            running = false;
            if (thread != null) {
                try { thread.join(150); } catch (InterruptedException ignored) {}
            }
            thread = null;
            current = null;
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            BufferedImage img = current;
            if (img == null) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            drawCropOrFit(g2, img, getWidth(), getHeight(), CROP_TO_FILL);
            g2.dispose();
        }
    }

    // ---------------- Captured preview panel

    private static class PreviewView extends JPanel {
        private BufferedImage img;

        PreviewView() { setBackground(Color.BLACK); }

        void setImage(BufferedImage img) {
            this.img = img;
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (img == null) {
                g.setColor(new Color(120, 120, 120));
                g.drawString("No photo yet", 20, 30);
                return;
            }

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            drawCropOrFit(g2, img, getWidth(), getHeight(), true);
            g2.dispose();
        }
    }

    private static void drawCropOrFit(Graphics2D g2, BufferedImage img, int panelW, int panelH, boolean cropFill) {
        double iw = img.getWidth();
        double ih = img.getHeight();

        double scale = cropFill
                ? Math.max(panelW / iw, panelH / ih)
                : Math.min(panelW / iw, panelH / ih);

        int drawW = (int) Math.round(iw * scale);
        int drawH = (int) Math.round(ih * scale);

        int x = (panelW - drawW) / 2;
        int y = (panelH - drawH) / 2;

        g2.drawImage(img, x, y, drawW, drawH, null);
    }

    // ---------------- Rounded colored button (simple + visible)

    private static class RoundedButton extends JButton {
        private final Color baseBg;
        private final Color baseFg;
        private boolean hover = false;

        private final int arc = 16;

        RoundedButton(String text, Color bg, Color fg) {
            super(text);
            this.baseBg = bg;
            this.baseFg = fg;

            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);

            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setMargin(new Insets(8, 16, 8, 16));

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            Color bg = baseBg;
            if (!isEnabled()) {
                bg = mix(baseBg, Color.WHITE, 0.50f);
            } else if (hover) {
                bg = isVeryLight(baseBg) ? darken(baseBg, 0.06f) : brighten(baseBg, 0.08f);
            }

            // shadow (subtle)
            if (isEnabled()) {
                g2.setColor(new Color(0, 0, 0, 35));
                g2.fillRoundRect(1, 3, w - 2, h - 2, arc + 8, arc + 8);
            }

            // body
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, w, h, arc, arc);

            // border
            g2.setColor(new Color(0, 0, 0, isEnabled() ? (hover ? 55 : 35) : 20));
            g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

            // text
            Color fg = baseFg;
            if (!isEnabled()) fg = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 160);

            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            String s = getText();
            int tx = (w - fm.stringWidth(s)) / 2;
            int ty = (h + fm.getAscent()) / 2 - 2;

            g2.setColor(fg);
            g2.drawString(s, tx, ty);

            g2.dispose();
        }

        private static boolean isVeryLight(Color c) { return luminance(c) > 220; }

        private static double luminance(Color c) {
            return 0.2126 * c.getRed() + 0.7152 * c.getGreen() + 0.0722 * c.getBlue();
        }

        private static Color brighten(Color c, float amount) {
            amount = clamp01(amount);
            int r = (int) (c.getRed() + (255 - c.getRed()) * amount);
            int g = (int) (c.getGreen() + (255 - c.getGreen()) * amount);
            int b = (int) (c.getBlue() + (255 - c.getBlue()) * amount);
            return new Color(clamp255(r), clamp255(g), clamp255(b));
        }

        private static Color darken(Color c, float amount) {
            amount = clamp01(amount);
            int r = (int) (c.getRed() * (1f - amount));
            int g = (int) (c.getGreen() * (1f - amount));
            int b = (int) (c.getBlue() * (1f - amount));
            return new Color(clamp255(r), clamp255(g), clamp255(b));
        }

        private static Color mix(Color a, Color b, float t) {
            t = clamp01(t);
            int r = (int) (a.getRed() * (1 - t) + b.getRed() * t);
            int g = (int) (a.getGreen() * (1 - t) + b.getGreen() * t);
            int bl = (int) (a.getBlue() * (1 - t) + b.getBlue() * t);
            return new Color(clamp255(r), clamp255(g), clamp255(bl));
        }

        private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }
        private static int clamp255(int v)    { return Math.max(0, Math.min(255, v)); }
    }
}
