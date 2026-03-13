package ui;

import model.Employee;
import model.User;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Employee Portal window wrapper.
 * - Content is EmployeeTimePanel (time in/out + history)
 * - On dispose/close, returns to LoginFrame unless closeWithoutReturningToLogin() is called.
 */
public class EmployeePortalFrame extends JFrame {

    private final User user;
    private final Employee employee;
    private boolean logoutToLogin = true; // default: go back to login when closed

    public EmployeePortalFrame(User user, Employee employee) {
        this.user = user;
        this.employee = employee;

        setTitle("Exploring Bearcat Travel & Tours Services");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(1260, 780));
        setSize(1260, 780);
        setLocationRelativeTo(null);

        setContentPane(new EmployeeTimePanel(employee));

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (logoutToLogin) {
                    SwingUtilities.invokeLater(() -> {
                        try {
                            new LoginFrame().setVisible(true);
                        } catch (Throwable ignore) {
                            // If LoginFrame isn't available (or fails), do nothing.
                        }
                    });
                }
            }
        });

        setVisible(true);
    }

    /**
     * If you ever need to close this portal WITHOUT returning to login,
     * call this before dispose().
     */
    public void closeWithoutReturningToLogin() {
        logoutToLogin = false;
        dispose();
    }
}
