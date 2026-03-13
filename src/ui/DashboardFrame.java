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

	private final Color GREEN = new Color(0, 110, 60);
	private final Color GREEN_DARK = new Color(0, 95, 52);

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
		JPanel root = new JPanel(new BorderLayout(0, 10));
		root.setBackground(BG);
		root.setBorder(new EmptyBorder(10, 14, 14, 14));

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
		bar.setBorder(new EmptyBorder(10, 12, 10, 12));

		JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		tabs.setOpaque(false);

		ButtonGroup g = new ButtonGroup();
		PillTab tEmp = new PillTab("Employees");
		PillTab tLog = new PillTab("Logbook");
		PillTab tPay = new PillTab("Payroll");

		g.add(tEmp);
		g.add(tLog);
		g.add(tPay);
		tabs.add(tEmp);
		tabs.add(tLog);
		tabs.add(tPay);

		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
		right.setOpaque(false);

		lblClock = new JLabel(" ");
		lblClock.setFont(new Font("Segoe UI", Font.PLAIN, 12));
		lblClock.setForeground(TEXT_MUTED);

		JLabel lblUser = new JLabel("Logged in: " + safeUserText());
		lblUser.setFont(new Font("Segoe UI", Font.PLAIN, 12));
		lblUser.setForeground(TEXT_MUTED);

		// ✅ ONLY LOGOUT HERE (View Employee Details moved inside EmployeesPanel)
		PillActionButton btnLogout = new PillActionButton("Logout");
		btnLogout.addActionListener(e -> logout());

		right.add(lblClock);
		right.add(lblUser);
		right.add(btnLogout);

		bar.add(tabs, BorderLayout.WEST);
		bar.add(right, BorderLayout.EAST);
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

	public void refreshPayrollEmployeeList() {
		if (payrollRunDialog != null) {
			payrollRunDialog.refreshEmployeesKeepSelection();
		}
	}

	private JPanel wrapDialog(JDialog dialog) {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setOpaque(false);

		Container c = dialog.getContentPane();
		dialog.setContentPane(new JPanel());
		dialog.dispose();

		RoundedPanel surface = new RoundedPanel(18);
		surface.setLayout(new BorderLayout());
		surface.setBackground(SURFACE);
		surface.setBorderColor(BORDER);
		surface.setBorderThickness(1);
		surface.setBorder(new EmptyBorder(12, 12, 12, 12));
		surface.add(c, BorderLayout.CENTER);

		panel.add(surface, BorderLayout.CENTER);
		return panel;
	}

	private JPanel wrapPanel(JComponent content) {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setOpaque(false);

		RoundedPanel surface = new RoundedPanel(18);
		surface.setLayout(new BorderLayout());
		surface.setBackground(SURFACE);
		surface.setBorderColor(BORDER);
		surface.setBorderThickness(1);
		surface.setBorder(new EmptyBorder(12, 12, 12, 12));
		surface.add(content, BorderLayout.CENTER);

		panel.add(surface, BorderLayout.CENTER);
		return panel;
	}

	private void showCard(String name) {
		cardLayout.show(contentCards, name);
	}

	private void logout() {
		dispose();
		new LoginFrame().setVisible(true);
	}

	private void startClock() {
		Timer t = new Timer(1000, e -> lblClock.setText(ZonedDateTime.now(MANILA).format(CLOCK_FMT)));
		t.setInitialDelay(0);
		t.start();
	}

	private String safeUserText() {
		if (user == null)
			return "unknown";
		try {
			Object v = user.getClass().getMethod("getUsername").invoke(user);
			if (v != null) {
				String s = String.valueOf(v).trim();
				if (!s.isEmpty())
					return s;
			}
		} catch (Exception ignored) {
		}
		String s = String.valueOf(user).trim();
		return s.isEmpty() ? "user" : s;
	}

	private class PillTab extends JToggleButton {
		private boolean hover = false;

		PillTab(String text) {
			super(text);
			setFocusable(false);
			setFont(new Font("Segoe UI", Font.BOLD, 12));
			setMargin(new Insets(10, 16, 10, 16));
			setOpaque(false);
			setContentAreaFilled(false);
			setBorderPainted(false);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseEntered(MouseEvent e) {
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e) {
					hover = false;
					repaint();
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			int w = getWidth(), h = getHeight(), arc = h;

			if (isSelected())
				g2.setColor(GREEN);
			else if (hover)
				g2.setColor(PILL_HOVER);
			else
				g2.setColor(PILL_BG);

			g2.fillRoundRect(0, 0, w, h, arc, arc);

			g2.setColor(isSelected() ? GREEN_DARK : PILL_BORDER);
			g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

			g2.setFont(getFont());
			g2.setColor(isSelected() ? Color.WHITE : TEXT_STRONG);

			FontMetrics fm = g2.getFontMetrics();
			int tx = (w - fm.stringWidth(getText())) / 2;
			int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
			g2.drawString(getText(), tx, ty);

			g2.dispose();
		}
	}

	private static class PillActionButton extends JButton {
		private boolean hover = false;

		PillActionButton(String text) {
			super(text);
			setFocusable(false);
			setFont(new Font("Segoe UI", Font.BOLD, 12));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setContentAreaFilled(false);
			setBorderPainted(false);
			setOpaque(false);
			setMargin(new Insets(10, 18, 10, 18));

			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseEntered(MouseEvent e) {
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e) {
					hover = false;
					repaint();
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			int w = getWidth(), h = getHeight(), arc = h;

			g2.setColor(Color.WHITE);
			g2.fillRoundRect(0, 0, w, h, arc, arc);

			g2.setColor(hover ? new Color(180, 160, 135) : new Color(220, 206, 186));
			g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

			g2.setFont(getFont());
			g2.setColor(new Color(30, 30, 30));

			FontMetrics fm = g2.getFontMetrics();
			int tx = (w - fm.stringWidth(getText())) / 2;
			int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
			g2.drawString(getText(), tx, ty);

			g2.dispose();
		}
	}

	private static class RoundedPanel extends JPanel {
		private final int arc;
		private Color borderColor = new Color(231, 221, 205);
		private int borderThickness = 1;

		RoundedPanel(int arc) {
			this.arc = arc;
			setOpaque(false);
		}

		public void setBorderColor(Color c) {
			borderColor = c;
			repaint();
		}

		public void setBorderThickness(int t) {
			borderThickness = t;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			int w = getWidth(), h = getHeight();
			g2.setColor(getBackground());
			g2.fillRoundRect(0, 0, w, h, arc, arc);

			g2.setColor(borderColor);
			for (int i = 0; i < borderThickness; i++) {
				g2.drawRoundRect(i, i, w - 1 - (2 * i), h - 1 - (2 * i), arc, arc);
			}
			g2.dispose();
			super.paintComponent(g);
		}
	}
}