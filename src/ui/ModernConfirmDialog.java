package ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.RoundRectangle2D;
import java.lang.reflect.InvocationTargetException;

public final class ModernConfirmDialog extends JDialog {

    public static final int YES_OPTION = 0;
    public static final int NO_OPTION = 1;
    public static final int CLOSED_OPTION = -1;

    private static final Color BG = new Color(0xFFFDF8);
    private static final Color TEXT = new Color(0x111827);
    private static final Color SUB = new Color(0x6B7280);
    private static final Color BORDER = new Color(0xD6D3D1);
    private static final Color PRIMARY = new Color(0x0F766E);
    private static final Color PRIMARY_DARK = new Color(0x0B5F59);
    private static final Color SECONDARY_BG = new Color(0xF3F4F6);
    private static final Color SECONDARY_FG = new Color(0x111827);

    private int selectedIndex = CLOSED_OPTION;

    private ModernConfirmDialog(Window owner, String title, String message, String[] options, int defaultOption) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        setContentPane(buildContent(title, message, options, defaultOption));
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private JComponent buildContent(String title, String message, String[] options, int defaultOption) {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setOpaque(false);
        outer.setBorder(new EmptyBorder(14, 14, 14, 14));

        RoundedPanel card = new RoundedPanel(24);
        card.setLayout(new BorderLayout(0, 18));
        card.setBackground(BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                new EmptyBorder(20, 22, 18, 22)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 21));
        titleLabel.setForeground(TEXT);

        JLabel messageLabel = new JLabel(toHtml(message));
        messageLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        messageLabel.setForeground(SUB);

        JPanel textWrap = new JPanel();
        textWrap.setOpaque(false);
        textWrap.setLayout(new BoxLayout(textWrap, BoxLayout.Y_AXIS));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textWrap.add(titleLabel);
        textWrap.add(Box.createVerticalStrut(8));
        textWrap.add(messageLabel);

        JPanel buttonRow = new JPanel(new GridLayout(1, options.length, 12, 0));
        buttonRow.setOpaque(false);

        for (int i = 0; i < options.length; i++) {
            JButton button = createButton(options[i], i == defaultOption);
            final int index = i;
            button.addActionListener(e -> {
                selectedIndex = index;
                dispose();
            });
            buttonRow.add(button);
            if (i == defaultOption) {
                getRootPane().setDefaultButton(button);
            }
        }

        card.add(textWrap, BorderLayout.CENTER);
        card.add(buttonRow, BorderLayout.SOUTH);

        outer.add(card, BorderLayout.CENTER);
        return outer;
    }

    private JButton createButton(String text, boolean primary) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(12, 18, 12, 18));
        button.setFont(new Font("SansSerif", Font.BOLD, 14));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setContentAreaFilled(false);
        button.setOpaque(false);

        Color fill = primary ? PRIMARY : SECONDARY_BG;
        Color hover = primary ? PRIMARY_DARK : new Color(0xE5E7EB);
        Color fg = primary ? Color.WHITE : SECONDARY_FG;

        button.setForeground(fg);
        button.setUI(new RoundedButtonUI(fill, hover, fg, primary ? PRIMARY_DARK : BORDER));
        return button;
    }

    public static int showYesNo(Component parent, String title, String message, String yesText, String noText) {
        return showOptions(parent, title, message, new String[]{yesText, noText}, 0);
    }

    public static int showOptions(Component parent, String title, String message, String[] options, int defaultOption) {
        if (options == null || options.length == 0) return CLOSED_OPTION;

        final int[] result = new int[]{CLOSED_OPTION};
        Runnable task = () -> {
            Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
            ModernConfirmDialog dialog = new ModernConfirmDialog(owner, title, message, options, Math.max(0, Math.min(defaultOption, options.length - 1)));
            dialog.setVisible(true);
            result[0] = dialog.selectedIndex;
        };

        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(task);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (InvocationTargetException ex) {
                throw new RuntimeException(ex.getCause());
            }
        }
        return result[0];
    }

    private static String toHtml(String text) {
        String escaped = text == null ? "" : text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>");
        return "<html><div style='width: 360px;'>" + escaped + "</div></html>";
    }

    private static final class RoundedPanel extends JPanel {
        private final int arc;

        private RoundedPanel(int arc) {
            this.arc = arc;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        protected void paintBorder(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g2.dispose();
        }

        @Override
        public boolean contains(int x, int y) {
            Shape shape = new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), arc, arc);
            return shape.contains(x, y);
        }
    }

    private static final class RoundedButtonUI extends javax.swing.plaf.basic.BasicButtonUI implements ActionListener {
        private final Color fill;
        private final Color hover;
        private final Color fg;
        private final Color border;
        private AbstractButton button;

        private RoundedButtonUI(Color fill, Color hover, Color fg, Color border) {
            this.fill = fill;
            this.hover = hover;
            this.fg = fg;
            this.border = border;
        }

        @Override
        public void installUI(JComponent c) {
            super.installUI(c);
            button = (AbstractButton) c;
            button.setForeground(fg);
            button.setBorderPainted(false);
            button.setOpaque(false);
            button.addActionListener(this);
        }

        @Override
        public void uninstallUI(JComponent c) {
            if (button != null) {
                button.removeActionListener(this);
            }
            super.uninstallUI(c);
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean hovered = b.getModel().isRollover();
            boolean pressed = b.getModel().isPressed();
            g2.setColor(pressed ? hover.darker() : hovered ? hover : fill);
            g2.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), 18, 18);
            g2.setColor(border);
            g2.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 18, 18);
            g2.dispose();
            super.paint(g, c);
        }

        @Override
        public void actionPerformed(ActionEvent e) { }
    }
}
