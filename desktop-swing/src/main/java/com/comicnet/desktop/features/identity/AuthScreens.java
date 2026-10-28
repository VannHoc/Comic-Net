package com.comicnet.desktop.features.identity;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.ui.Theme;
import com.comicnet.desktop.ui.UiTasks;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/** Six connected authentication views; all requests are handled off the EDT by UiTasks. */
final class AuthScreens extends JPanel {
  private enum Screen { LOGIN, REGISTER, VERIFY, FORGOT, RESET, SUCCESS }

  private final ApiClient api;
  private final UiTasks tasks;
  private final Runnable loggedIn;
  private final JPanel left = new JPanel();
  private final JPanel shell = new JPanel();
  private final JLabel status = new JLabel(" ");
  private final JTextField address = new JTextField();
  private final JTextField phoneNumber = new JTextField();
  private final JTextField username = new JTextField();
  private final JTextField name = new JTextField();
  private final OtpInput code = new OtpInput();
  private final JPasswordField password = new JPasswordField();
  private final JPasswordField confirm = new JPasswordField();
  private final char passwordEcho = password.getEchoChar();
  private final char confirmEcho = confirm.getEchoChar();
  private final JCheckBox terms = new JCheckBox("Tôi đồng ý với điều khoản dịch vụ");
  private final JCheckBox remember = new JCheckBox("Ghi nhớ đăng nhập");
  private final JLabel artwork = new JLabel();
  private Screen screen = Screen.LOGIN;
  private boolean compact;
  private final JButton resend = new JButton("Gửi lại mã");
  private int resendSeconds;
  private final Timer resendTimer = new Timer(1000, event -> {
    if (resendSeconds > 0) resendSeconds--;
    resend.setEnabled(resendSeconds == 0);
    resend.setText(resendSeconds == 0 ? "Gửi lại mã" : "Gửi lại sau " + resendSeconds + " giây");
    if (resendSeconds == 0) ((Timer) event.getSource()).stop();
  });

  AuthScreens(ApiClient api, UiTasks tasks, Runnable loggedIn) {
    super(new BorderLayout());
    this.api = api;
    this.tasks = tasks;
    this.loggedIn = loggedIn;
    setBackground(Color.WHITE);
    setPreferredSize(new Dimension(850, 570));
    left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
    left.setBackground(Color.WHITE);
    left.setBorder(new EmptyBorder(14, 26, 10, 26));
    shell.setLayout(new GridLayout(1, 2));
    shell.add(left);
    artwork.setOpaque(true);
    artwork.setBackground(new Color(0xE8EAF7));
    shell.add(artwork);
    add(shell, BorderLayout.CENTER);
    status.setForeground(Theme.ACTION);
    status.setBorder(new EmptyBorder(4, 42, 8, 42));
    add(status, BorderLayout.SOUTH);
    show(Screen.LOGIN);
    addComponentListener(new java.awt.event.ComponentAdapter() {
      @Override public void componentResized(java.awt.event.ComponentEvent event) {
        boolean nowCompact = getHeight() < 600;
        if (compact != nowCompact) {
          compact = nowCompact;
          if (screen == Screen.REGISTER) show(screen, false);
        }
        artwork.repaint();
      }
    });
  }

  private void show(Screen next) {
    show(next, true);
  }

  private void show(Screen next, boolean clearSecrets) {
    screen = next;
    password.setEchoChar(passwordEcho);
    confirm.setEchoChar(confirmEcho);
    if (clearSecrets) {
      password.setText("");
      confirm.setText("");
      if (next == Screen.VERIFY || next == Screen.RESET) code.clear();
    }
    left.removeAll();
    left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
    left.setBorder(new EmptyBorder(compact ? 8 : 14, 26, compact ? 4 : 10, 26));
    status.setText(" ");
    if (screen != Screen.SUCCESS) {
      brand();
      left.add(Box.createVerticalStrut(compact ? 4 : 10));
    }
    switch (screen) {
      case LOGIN -> loginView();
      case REGISTER -> registerView();
      case VERIFY -> verifyView();
      case FORGOT -> forgotView();
      case RESET -> resetView();
      case SUCCESS -> successView();
    }
    shell.removeAll();
    if (screen == Screen.SUCCESS) {
      shell.setLayout(new BorderLayout());
      shell.add(left, BorderLayout.CENTER);
    } else {
      shell.setLayout(new GridLayout(1, 2));
      shell.add(left);
      shell.add(artwork);
    }
    updateArtwork();
    shell.revalidate();
    left.revalidate();
    left.repaint();
  }

  private void brand() {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 9, 0));
    row.setOpaque(false);
    row.setAlignmentX(LEFT_ALIGNMENT);
    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
    JComponent mark = new JComponent() {
      @Override public Dimension getPreferredSize() { return new Dimension(31, 29); }
      @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.ACTION);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawPolyline(new int[]{3, 12, 15}, new int[]{5, 8, 24}, 3);
        g.drawPolyline(new int[]{27, 18, 15}, new int[]{5, 8, 24}, 3);
        g.drawLine(15, 24, 15, 5);
        g.dispose();
      }
    };
    row.add(mark);
    JLabel logo = new JLabel("Comic Net");
    logo.setFont(Theme.heading(24));
    logo.setForeground(Theme.TEXT);
    row.add(logo);
    left.add(row);
    JLabel caption = new JLabel("Đọc truyện · Khám phá · Kết nối");
    caption.setForeground(Theme.MUTED);
    caption.setAlignmentX(LEFT_ALIGNMENT);
    if (!compact) left.add(caption);
  }

  private void title(String heading, String subtitle) {
    JLabel h = new JLabel(heading);
    h.setFont(Theme.heading(21));
    h.setForeground(Theme.TEXT);
    h.setAlignmentX(LEFT_ALIGNMENT);
    left.add(h);
    JLabel sub = new JLabel(subtitle);
    sub.setForeground(Theme.MUTED);
    sub.setAlignmentX(LEFT_ALIGNMENT);
    left.add(Box.createVerticalStrut(3));
    left.add(sub);
    left.add(Box.createVerticalStrut(compact ? 6 : 10));
  }

  private void field(String caption, JComponent input) {
    JLabel label = new JLabel(caption);
    label.setForeground(Theme.TEXT);
    label.setAlignmentX(LEFT_ALIGNMENT);
    label.setLabelFor(input);
    left.add(label);
    left.add(Box.createVerticalStrut(3));
    input.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
    input.setPreferredSize(new Dimension(300, 34));
    input.setAlignmentX(LEFT_ALIGNMENT);
    input.getAccessibleContext().setAccessibleName(caption);
    left.add(input);
    left.add(Box.createVerticalStrut(5));
  }

  private JPanel compactField(String caption, JComponent input) {
    JPanel column = new JPanel();
    column.setOpaque(false);
    column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
    JLabel label = new JLabel(caption);
    label.setForeground(Theme.TEXT);
    label.setAlignmentX(LEFT_ALIGNMENT);
    label.setLabelFor(input);
    column.add(label);
    column.add(Box.createVerticalStrut(3));
    input.setAlignmentX(LEFT_ALIGNMENT);
    input.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
    input.getAccessibleContext().setAccessibleName(caption);
    column.add(input);
    return column;
  }

  private void button(String text, Runnable action) {
    JButton button = new JButton(text);
    button.setBackground(Theme.ACTION);
    button.setForeground(Color.WHITE);
    button.setFocusPainted(false);
    button.setMaximumSize(new Dimension(Integer.MAX_VALUE, compact ? 34 : 40));
    button.setAlignmentX(LEFT_ALIGNMENT);
    button.addActionListener(event -> action.run());
    left.add(button);
    left.add(Box.createVerticalStrut(compact ? 3 : 8));
  }

  private void link(String text, Screen target) {
    JButton button = new JButton(text);
    button.setBorderPainted(false);
    button.setContentAreaFilled(false);
    button.setForeground(Theme.ACTION);
    button.setAlignmentX(LEFT_ALIGNMENT);
    button.addActionListener(event -> show(target));
    left.add(button);
  }

  private void passwordField(String caption, JPasswordField input) {
    field(caption, passwordInput(input));
  }

  private JPanel passwordInput(JPasswordField input) {
    JPanel row = new JPanel(new BorderLayout(4, 0));
    row.setOpaque(false);
    row.add(input, BorderLayout.CENTER);
    JButton eye = new JButton("Hiện");
    char echo = input == confirm ? confirmEcho : passwordEcho;
    eye.addActionListener(event -> {
      boolean visible = input.getEchoChar() == 0;
      input.setEchoChar(visible ? echo : (char) 0);
      eye.setText(visible ? "Hiện" : "Ẩn");
    });
    row.add(eye, BorderLayout.EAST);
    return row;
  }

  private void pairedFields(String firstCaption, JComponent first,
      String secondCaption, JComponent second) {
    JPanel pair = new JPanel(new GridLayout(1, 2, 8, 0));
    pair.setOpaque(false);
    pair.setAlignmentX(LEFT_ALIGNMENT);
    pair.setMaximumSize(new Dimension(Integer.MAX_VALUE, 53));
    pair.add(compactField(firstCaption, first));
    pair.add(compactField(secondCaption, second));
    left.add(pair);
    left.add(Box.createVerticalStrut(compact ? 2 : 5));
  }

  private void loginView() {
    title("Đăng nhập", "Chào mừng bạn quay trở lại!");
    field("Tài khoản", address);
    passwordField("Mật khẩu", password);
    remember.setOpaque(false);
    remember.setSelected(api.rememberEnabled());
    left.add(remember);
    link("Quên mật khẩu?", Screen.FORGOT);
    left.add(Box.createVerticalStrut(8));
    button("Đăng nhập", this::submitLogin);
    link("Chưa có tài khoản? Đăng ký ngay", Screen.REGISTER);
  }

  private void registerView() {
    title("Đăng ký tài khoản", "Khám phá thế giới truyện tranh cùng Comic Net.");
    if (compact) pairedFields("Tài khoản", username, "Tên hiển thị", name);
    else {
      field("Tài khoản", username);
      field("Tên hiển thị", name);
    }
    JPanel contacts = new JPanel(new GridLayout(1, 2, 8, 0));
    contacts.setOpaque(false);
    contacts.setAlignmentX(LEFT_ALIGNMENT);
    contacts.setMaximumSize(new Dimension(Integer.MAX_VALUE, 53));
    contacts.add(compactField("Email (tùy chọn)", address));
    contacts.add(compactField("Số điện thoại (tùy chọn)", phoneNumber));
    phoneNumber.setToolTipText("Khôi phục qua SMS chưa khả dụng.");
    left.add(contacts);
    left.add(Box.createVerticalStrut(compact ? 2 : 5));
    if (compact) pairedFields("Mật khẩu", passwordInput(password),
        "Xác nhận mật khẩu", passwordInput(confirm));
    else {
      passwordField("Mật khẩu", password);
      passwordField("Xác nhận mật khẩu", confirm);
    }
    terms.setOpaque(false);
    terms.setAlignmentX(LEFT_ALIGNMENT);
    left.add(terms);
    left.add(Box.createVerticalStrut(compact ? 2 : 5));
    button("Đăng ký", this::submitRegister);
    link("Đã có tài khoản? Đăng nhập", Screen.LOGIN);
  }

  private void verifyView() {
    title("Xác thực email", "Nhập mã 6 chữ số được gửi tới email của bạn.");
    field("Email", address);
    field("Mã xác thực", code);
    button("Xác thực", this::submitVerify);
    for (var listener : resend.getActionListeners()) resend.removeActionListener(listener);
    resend.addActionListener(event -> tasks.run(this, status,
        api.request("POST", "/auth/resend-verification", Map.of("email", address.getText().strip()), null),
        reply -> { status.setText("Nếu thông tin hợp lệ, mã mới đã được gửi qua email."); startResendCooldown(); }));
    resend.setEnabled(resendSeconds == 0);
    left.add(resend);
    link("Quay lại đăng nhập", Screen.LOGIN);
  }

  private void forgotView() {
    title("Quên mật khẩu", "Nhập email đã đăng ký để nhận mã khôi phục.");
    field("Email", address);
    button("Gửi hướng dẫn", () -> tasks.run(this, status,
        api.request("POST", "/auth/forgot-password", Map.of("email", address.getText().strip()), null),
        reply -> { show(Screen.RESET); status.setText("Nếu email hợp lệ, mã khôi phục đã được gửi."); }));
    link("Đã nhớ mật khẩu? Đăng nhập", Screen.LOGIN);
  }

  private void resetView() {
    title("Đặt lại mật khẩu", "Nhập mã nhận qua email và mật khẩu mới.");
    field("Mã khôi phục", code);
    passwordField("Mật khẩu mới (3–12 ký tự)", password);
    passwordField("Xác nhận mật khẩu", confirm);
    button("Đặt lại mật khẩu", this::submitReset);
    link("Quay lại đăng nhập", Screen.LOGIN);
  }

  private void successView() {
    left.setLayout(new GridBagLayout());
    JPanel card = new JPanel();
    card.setOpaque(false);
    card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
    JComponent symbol = new JComponent() {
      @Override public Dimension getPreferredSize() { return new Dimension(98, 98); }
      @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xE4F7E8)); g.fillOval(1, 1, 94, 94);
        g.setColor(new Color(0x74C880)); g.fillOval(14, 14, 68, 68);
        g.setColor(Color.WHITE); g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawPolyline(new int[]{32, 43, 64}, new int[]{48, 60, 38}, 3);
        g.dispose();
      }
    };
    symbol.setAlignmentX(CENTER_ALIGNMENT);
    card.add(symbol);
    card.add(Box.createVerticalStrut(18));
    JLabel heading = new JLabel("Thành công!");
    heading.setFont(Theme.heading(24));
    heading.setForeground(Theme.TEXT);
    heading.setAlignmentX(CENTER_ALIGNMENT);
    card.add(heading);
    card.add(Box.createVerticalStrut(10));
    JLabel description = new JLabel("Tài khoản của bạn đã sẵn sàng. Hãy đăng nhập để đọc truyện!");
    description.setForeground(Theme.MUTED);
    description.setAlignmentX(CENTER_ALIGNMENT);
    card.add(description);
    card.add(Box.createVerticalStrut(28));
    JButton back = new JButton("Đăng nhập ngay");
    back.setBackground(Theme.ACTION);
    back.setForeground(Color.WHITE);
    back.setPreferredSize(new Dimension(220, 40));
    back.setMaximumSize(new Dimension(220, 40));
    back.setAlignmentX(CENTER_ALIGNMENT);
    back.addActionListener(event -> show(Screen.LOGIN));
    card.add(back);
    left.add(card);
  }

  private void startResendCooldown() {
    resendSeconds = 60;
    resend.setEnabled(false);
    resend.setText("Gửi lại sau 60 giây");
    resendTimer.restart();
  }

  private boolean passwordValid() {
    char[] first = password.getPassword();
    char[] second = confirm.getPassword();
    boolean valid = first.length >= 3 && first.length <= 12 && java.util.Arrays.equals(first, second);
    java.util.Arrays.fill(first, '\0');
    java.util.Arrays.fill(second, '\0');
    if (!valid) status.setText("Mật khẩu phải có 3–12 ký tự và hai ô phải trùng nhau.");
    return valid;
  }

  private void submitLogin() {
    if (tasks.busy()) return;
    tasks.run(this, status, api.login(address.getText().strip(), new String(password.getPassword())),
        reply -> {
          password.setText("");
          boolean saved = api.remember(remember.isSelected());
          loggedIn.run();
          if (!saved) JOptionPane.showMessageDialog(this,
              "Đã đăng nhập nhưng không thể lưu phiên trên Windows. Bạn sẽ cần đăng nhập lại khi mở ứng dụng.",
              "Comic Net", JOptionPane.WARNING_MESSAGE);
        });
  }

  private void submitRegister() {
    if (tasks.busy()) return;
    if (!username.getText().strip().matches("[A-Za-z0-9_]{3,12}")) {
      status.setText("Tài khoản cần 3–12 ký tự chữ, số hoặc dấu gạch dưới."); return;
    }
    if (name.getText().strip().length() < 3 || name.getText().strip().length() > 12) {
      status.setText("Tên hiển thị cần 3–12 ký tự."); return;
    }
    if (!terms.isSelected()) { status.setText("Bạn cần đồng ý điều khoản dịch vụ."); return; }
    if (!passwordValid()) return;
    String recoveryEmail = address.getText().strip();
    String recoveryPhone = phoneNumber.getText().strip();
    if (!recoveryPhone.isEmpty() && !recoveryPhone.matches("\\+?[0-9]{9,15}")) {
      status.setText("Số điện thoại cần 9–15 chữ số, có thể bắt đầu bằng +."); return;
    }
    var request = new java.util.HashMap<String, String>();
    request.put("username", username.getText().strip());
    request.put("displayName", name.getText().strip());
    request.put("password", new String(password.getPassword()));
    if (!recoveryEmail.isEmpty()) request.put("email", recoveryEmail);
    if (!recoveryPhone.isEmpty()) request.put("phone", recoveryPhone);
    tasks.run(this, status, api.request("POST", "/auth/register", request, null),
        reply -> {
          password.setText(""); confirm.setText("");
          if (recoveryEmail.isEmpty()) {
            show(Screen.SUCCESS);
            if (!recoveryPhone.isEmpty())
              status.setText("Khôi phục qua SMS chưa khả dụng; hãy bổ sung email khi có hỗ trợ.");
          }
          else { show(Screen.VERIFY); startResendCooldown(); }
        });
  }

  private void submitVerify() {
    if (!code.value().matches("[0-9]{6}")) {
      status.setText("Mã xác thực phải gồm đúng 6 chữ số."); return;
    }
    tasks.run(this, status, api.request("POST", "/auth/verify-email-otp",
        Map.of("email", address.getText().strip(), "code", code.value()), null),
        reply -> show(Screen.SUCCESS));
  }

  private void submitReset() {
    if (!code.value().matches("[0-9]{6}")) {
      status.setText("Mã khôi phục phải gồm đúng 6 chữ số."); return;
    }
    if (!passwordValid()) return;
    tasks.run(this, status, api.request("POST", "/auth/reset-password-otp",
        Map.of("email", address.getText().strip(), "code", code.value(),
            "password", new String(password.getPassword())), null),
        reply -> { password.setText(""); confirm.setText(""); show(Screen.SUCCESS); });
  }

  private static final class OtpInput extends JPanel {
    private final JTextField[] digits = new JTextField[6];

    OtpInput() {
      super(new GridLayout(1, 6, 7, 0));
      setOpaque(false);
      for (int i = 0; i < digits.length; i++) {
        final int index = i;
        JTextField input = new JTextField();
        input.setHorizontalAlignment(JTextField.CENTER);
        input.setFont(Theme.heading(19));
        ((AbstractDocument) input.getDocument()).setDocumentFilter(new DocumentFilter() {
          @Override public void replace(FilterBypass bypass, int offset, int length, String text,
              AttributeSet attributes) throws BadLocationException {
            if (text == null || text.isEmpty()) {
              super.replace(bypass, offset, length, "", attributes);
              return;
            }
            String clean = text.replaceAll("[^0-9]", "");
            if (clean.isEmpty()) return;
            if (clean.length() > 1) {
              SwingUtilities.invokeLater(() -> distribute(index, clean));
              return;
            }
            super.replace(bypass, 0, bypass.getDocument().getLength(), clean, attributes);
            if (index < 5) SwingUtilities.invokeLater(() -> digits[index + 1].requestFocusInWindow());
          }
          @Override public void insertString(FilterBypass bypass, int offset, String text,
              AttributeSet attributes) throws BadLocationException {
            replace(bypass, offset, 0, text, attributes);
          }
        });
        input.addKeyListener(new java.awt.event.KeyAdapter() {
          @Override public void keyPressed(java.awt.event.KeyEvent event) {
            if (event.getKeyCode() == java.awt.event.KeyEvent.VK_BACK_SPACE
                && input.getText().isEmpty() && index > 0) digits[index - 1].requestFocusInWindow();
          }
        });
        digits[i] = input;
        add(input);
      }
    }

    String value() {
      StringBuilder result = new StringBuilder(6);
      for (JTextField digit : digits) result.append(digit.getText());
      return result.toString();
    }

    void clear() { for (JTextField digit : digits) digit.setText(""); }

    private void distribute(int start, String text) {
      for (int i = 0; i < text.length() && start + i < digits.length; i++) {
        digits[start + i].setText(text.substring(i, i + 1));
      }
      digits[Math.min(digits.length - 1, start + text.length())].requestFocusInWindow();
    }
  }

  private void updateArtwork() {
    artwork.setVisible(screen != Screen.SUCCESS);
    if (screen == Screen.SUCCESS) return;
    String path = switch (screen) {
      case REGISTER -> "/auth/register.png";
      case LOGIN -> "/auth/login.png";
      case FORGOT -> "/auth/forgot.png";
      case RESET -> "/auth/reset.png";
      default -> "/auth/verify.png";
    };
    try (var stream = getClass().getResourceAsStream(path)) {
      BufferedImage image = stream == null ? null : ImageIO.read(stream);
      artwork.setIcon(image == null ? null : new ScaledArtwork(image));
    } catch (IOException failure) {
      artwork.setIcon(null);
    }
  }

  private final class ScaledArtwork implements Icon {
    private final BufferedImage image;
    ScaledArtwork(BufferedImage image) { this.image = image; }
    @Override public int getIconWidth() { return artwork.getWidth(); }
    @Override public int getIconHeight() { return artwork.getHeight(); }
    @Override public void paintIcon(Component c, Graphics graphics, int x, int y) {
      int w = c.getWidth(), h = c.getHeight();
      if (w <= 0 || h <= 0) return;
      Graphics2D g = (Graphics2D) graphics.create();
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      double scale = Math.max((double) w / image.getWidth(), (double) h / image.getHeight());
      int dw = (int) Math.ceil(image.getWidth() * scale), dh = (int) Math.ceil(image.getHeight() * scale);
      g.setClip(x, y, w, h);
      g.drawImage(image, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, null);
      g.dispose();
    }
  }
}
