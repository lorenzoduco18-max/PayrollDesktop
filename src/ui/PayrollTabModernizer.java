package ui;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

/**
 * Minimal, SAFE UI polish.
 * ✅ No boxes around panels
 * ✅ No FlatLaf style keys
 * ✅ No layout changes
 */
public final class PayrollTabModernizer {

    private PayrollTabModernizer() {}

    private static final Color PAGE_BG = new Color(0xFAF4E9);
    private static final Color LINE    = new Color(0xE7DCCB);

    public static void apply(JComponent root) {
        if (root == null) return;

        root.setOpaque(true);
        root.setBackground(PAGE_BG);
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        styleTree(root);
    }

    private static void styleTree(Component c) {
        if (c instanceof JComponent jc) {

            // Inputs
            if (jc instanceof JTextField tf) makeField(tf);
            if (jc instanceof JPasswordField pf) makeField(pf);
            if (jc instanceof JComboBox<?> cb) makeCombo(cb);

            // Buttons
            if (jc instanceof JButton b) makeButton(b);

            // Scroll / preview
            if (jc instanceof JScrollPane sp) {
                sp.setBorder(new EmptyBorder(0, 0, 0, 0));
                if (sp.getViewport() != null) {
                    sp.getViewport().setBackground(Color.WHITE);
                }
            }
            if (jc instanceof JEditorPane ep) {
                ep.setOpaque(true);
                ep.setBackground(Color.WHITE);
            }
        }

        if (c instanceof Container ct) {
            for (Component child : ct.getComponents()) {
                styleTree(child);
            }
        }
    }

    private static void makeField(JComponent field) {
        field.setOpaque(true);
        field.setBackground(Color.WHITE);
        field.setBorder(new CompoundBorder(
                new LineBorder(LINE, 1, true),
                new EmptyBorder(6, 10, 6, 10)
        ));

        Dimension d = field.getPreferredSize();
        if (d != null) field.setPreferredSize(
                new Dimension(d.width, Math.max(d.height, 30))
        );
    }

    private static void makeCombo(JComboBox<?> cb) {
        cb.setOpaque(true);
        cb.setBackground(Color.WHITE);
        cb.setBorder(new CompoundBorder(
                new LineBorder(LINE, 1, true),
                new EmptyBorder(5, 8, 5, 8)
        ));
    }

    private static void makeButton(JButton b) {
        b.putClientProperty(
                FlatClientProperties.BUTTON_TYPE,
                FlatClientProperties.BUTTON_TYPE_ROUND_RECT
        );
        b.setMargin(new Insets(7, 12, 7, 12));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
}
