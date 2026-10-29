package com.comicnet.desktop.features.wallet;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.client.api.MutationKey;
import com.comicnet.desktop.ui.*;
import java.awt.*;
import java.util.Map;
import javax.swing.table.DefaultTableModel;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Administrator-only wallet adjustment screen; the backend enforces the role again. */
public final class AdminWalletPanel extends JPanel implements Workspace {
  private final ApiClient api;
  private final UiTasks tasks = new UiTasks();
  private final RemoteTable users, transactions, adjustments, orders;
  private final DefaultTableModel packageModel = new DefaultTableModel(
      new Object[] {"Mã gói", "Xu mua", "Giá VND", "Đang bán"}, 0) {
    @Override public boolean isCellEditable(int row, int column) { return false; }
  };
  private final JTable packages = new JTable(packageModel);
  private long selectedOrder;
  private final JTextField search = new JTextField(20);
  private final JLabel balance = Theme.plainLabel("Chọn một tài khoản."),
      message = Theme.plainLabel(" ");
  private long selectedUser;
  private long paid, reward;
  private final MutationKey mutation = new MutationKey();

  public AdminWalletPanel(ApiClient api) {
    super(new BorderLayout(0, 12));
    this.api = api;
    setOpaque(false);
    setBorder(new EmptyBorder(24, 30, 24, 30));
    JLabel title = Theme.plainLabel("Quản lý ví xu");
    title.setFont(Theme.heading(28));
    JPanel header = new JPanel(new BorderLayout(12, 8));
    header.setOpaque(false);
    header.add(title, BorderLayout.NORTH);
    JPanel find = new JPanel(new BorderLayout(8, 0));
    find.setOpaque(false);
    find.add(search, BorderLayout.CENTER);
    JButton findButton = new JButton("Tìm tài khoản");
    find.add(findButton, BorderLayout.EAST);
    header.add(find, BorderLayout.SOUTH);
    add(header, BorderLayout.NORTH);
    users = new RemoteTable(api,
        new String[] {"ID", "Tài khoản", "Tên", "Xu mua", "Xu thưởng", "Tổng xu", "Trạng thái"},
        page -> "/admin/wallets?page=" + page + "&size=20&q="
            + java.net.URLEncoder.encode(search.getText().strip(), java.nio.charset.StandardCharsets.UTF_8),
        row -> new Object[] {row.path("id").asText(), row.path("username").asText(),
            row.path("displayName").asText(), row.path("paidCoin").asLong(),
            row.path("rewardCoin").asLong(), row.path("totalCoin").asLong(),
            row.path("status").asText()});
    findButton.addActionListener(e -> users.first());
    search.addActionListener(e -> users.first());
    transactions = new RemoteTable(api,
        new String[] {"Thời gian", "Nguồn", "Loại xu", "Thay đổi", "Số dư sau"},
        page -> selectedUser == 0 ? null : "/admin/wallets/" + selectedUser
            + "/transactions?page=" + page + "&size=20",
        row -> new Object[] {row.path("createdAt").asText(), row.path("source").asText(),
            row.path("tokenType").asText(), row.path("delta").asLong(),
            row.path("balanceAfter").asLong()});
    adjustments = new RemoteTable(api,
        new String[] {"Thời gian", "Admin ID", "Thao tác", "Chi tiết"},
        page -> selectedUser == 0 ? null : "/admin/wallets/" + selectedUser
            + "/adjustments?page=" + page + "&size=20",
        row -> new Object[] {row.path("createdAt").asText(), row.path("actorUserId").asLong(),
            row.path("action").asText(), row.path("metadata").asText()});
    orders = new RemoteTable(api,
        new String[] {"Mã đơn", "Tài khoản", "Gói", "Xu mua", "Giá VND", "Trạng thái", "Ngày tạo"},
        page -> "/admin/payment-orders?page=" + page + "&size=20",
        row -> new Object[] {row.path("id").asLong(), row.path("username").asText(),
            row.path("packageCode").asText(), row.path("paidCoin").asLong(),
            row.path("amountVnd").asLong(), row.path("status").asText(),
            row.path("createdAt").asText()});
    orders.onSelection(row -> selectedOrder = row == null ? 0 : row.path("id").asLong());
    users.onSelection(row -> {
      selectedUser = row == null ? 0 : row.path("id").asLong();
      transactions.clear();
      adjustments.clear();
      if (selectedUser != 0) loadBalance();
      else balance.setText("Chọn một tài khoản.");
    });
    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Tài khoản", users);
    tabs.addTab("Giao dịch", transactions);
    tabs.addTab("Điều chỉnh", adjustments);
    JPanel offersPanel = new JPanel(new BorderLayout(0, 8));
    JPanel offerActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
    JButton addPackage = new JButton("Tạo gói"), editPackage = new JButton("Sửa gói"),
        reloadPackages = new JButton("Tải lại gói");
    addPackage.addActionListener(e -> editPackage(null));
    editPackage.addActionListener(e -> {
      int row = packages.getSelectedRow();
      if (row < 0) { message.setText("Chọn gói trước."); return; }
      editPackage(packages.convertRowIndexToModel(row));
    });
    reloadPackages.addActionListener(e -> loadPackages());
    offerActions.add(addPackage); offerActions.add(editPackage); offerActions.add(reloadPackages);
    offersPanel.add(offerActions, BorderLayout.NORTH);
    offersPanel.add(new JScrollPane(packages), BorderLayout.CENTER);
    tabs.addTab("Gói nạp", offersPanel);
    JPanel orderPanel = new JPanel(new BorderLayout(0, 8));
    JButton refund = new JButton("Hoàn xu đơn đã chọn");
    refund.addActionListener(e -> refundOrder());
    JPanel orderActions = new JPanel(new FlowLayout(FlowLayout.LEFT));
    orderActions.add(refund);
    orderPanel.add(orders, BorderLayout.CENTER);
    orderPanel.add(orderActions, BorderLayout.SOUTH);
    tabs.addTab("Đơn nạp", orderPanel);
    tabs.addChangeListener(e -> {
      if (tabs.getSelectedIndex() == 3) loadPackages();
      if (tabs.getSelectedIndex() == 4) orders.first();
      if (selectedUser == 0) return;
      if (tabs.getSelectedIndex() == 1) transactions.first();
      if (tabs.getSelectedIndex() == 2) adjustments.first();
    });
    add(tabs, BorderLayout.CENTER);
    JPanel footer = new JPanel(new GridLayout(0, 1, 0, 8));
    footer.setOpaque(false);
    footer.add(balance);
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
    buttons.setOpaque(false);
    JButton grant = new JButton("Cấp xu"), revoke = new JButton("Thu hồi xu");
    grant.addActionListener(e -> edit("GRANT"));
    revoke.addActionListener(e -> edit("REVOKE"));
    buttons.add(grant);
    buttons.add(revoke);
    footer.add(buttons);
    footer.add(message);
    add(footer, BorderLayout.SOUTH);
  }

  private void loadPackages() {
    tasks.run(this, message, api.get("/admin/coin-packages"), reply -> {
      packageModel.setRowCount(0);
      for (var row : reply.body()) packageModel.addRow(new Object[] {
          row.path("code").asText(), row.path("paidCoin").asLong(),
          row.path("amountVnd").asLong(), row.path("active").asBoolean()});
    });
  }

  private void editPackage(Integer row) {
    String code = row == null ? "" : packageModel.getValueAt(row, 0).toString();
    EditDialog dialog = new EditDialog(this, row == null ? "Tạo gói nạp" : "Sửa gói nạp");
    JTextField codeField = EditDialog.text(code);
    codeField.setEditable(row == null);
    JTextField coins = EditDialog.text(row == null ? "" : packageModel.getValueAt(row, 1).toString());
    JTextField price = EditDialog.text(row == null ? "" : packageModel.getValueAt(row, 2).toString());
    JCheckBox active = new JCheckBox("Đang bán", row == null || Boolean.TRUE.equals(packageModel.getValueAt(row, 3)));
    dialog.form.field("Mã gói (A-Z, 0-9, _)", codeField);
    dialog.form.field("Xu mua", coins);
    dialog.form.field("Giá VND", price);
    dialog.form.field("Trạng thái", active);
    dialog.submit(() -> {
      String key = codeField.getText().strip();
      if (!key.matches("[A-Z0-9_]{2,30}")) throw new IllegalArgumentException("Mã gói phải có 2–30 ký tự A-Z, 0-9 hoặc _.");
      long coinValue, priceValue;
      try { coinValue = Long.parseLong(coins.getText().strip()); priceValue = Long.parseLong(price.getText().strip()); }
      catch (NumberFormatException error) { throw new IllegalArgumentException("Số xu và giá phải là số nguyên dương."); }
      if (coinValue <= 0 || priceValue <= 0) throw new IllegalArgumentException("Số xu và giá phải lớn hơn 0.");
      return api.request("PUT", "/admin/coin-packages/" + key,
          Map.of("paidCoin", coinValue, "amountVnd", priceValue, "active", active.isSelected()), null);
    }, reply -> loadPackages());
    dialog.setSize(520, 380);
    dialog.setVisible(true);
  }

  private void refundOrder() {
    if (selectedOrder <= 0) { message.setText("Chọn đơn nạp trước."); return; }
    long id = selectedOrder;
    String reason = JOptionPane.showInputDialog(this, "Lý do hoàn xu đơn #" + id + ":");
    if (reason == null) return;
    if (reason.isBlank()) { message.setText("Nhập lý do hoàn xu."); return; }
    if (JOptionPane.showConfirmDialog(this, "Thu hồi số xu đã nạp từ đơn #" + id + "?",
        "Xác nhận hoàn xu", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
    tasks.run(this, message, api.request("POST", "/admin/payment-orders/" + id + "/refund",
        Map.of("reason", reason.strip()), null), reply -> { orders.reload(); users.reload(); });
  }

  private void loadBalance() {
    long id = selectedUser;
    tasks.run(this, message, api.get("/admin/wallets/" + id), reply -> {
      if (selectedUser != id) return;
      paid = reply.body().path("paidCoin").asLong();
      reward = reply.body().path("rewardCoin").asLong();
      balance.setText("Tài khoản #" + id + " · Xu mua: " + paid
          + " · Xu thưởng: " + reward + " · Tổng: " + (paid + reward));
    });
  }

  private void edit(String direction) {
    if (selectedUser == 0) { message.setText("Chọn tài khoản trước."); return; }
    long id = selectedUser;
    EditDialog dialog = new EditDialog(this,
        "GRANT".equals(direction) ? "Cấp xu" : "Thu hồi xu");
    JComboBox<String> token = EditDialog.options("REWARD", "REWARD", "PAID");
    JTextField amount = EditDialog.text("100"), reason = EditDialog.text("");
    dialog.form.field("Loại xu", token);
    dialog.form.field("Số xu", amount);
    dialog.form.field("Lý do", reason);
    dialog.submit(() -> {
      long value;
      try { value = Long.parseLong(amount.getText().strip()); }
      catch (NumberFormatException error) { throw new IllegalArgumentException("Số xu phải là số nguyên dương."); }
      if (value <= 0 || reason.getText().isBlank())
        throw new IllegalArgumentException("Nhập số xu dương và lý do.");
      String currency = (String) token.getSelectedItem();
      long current = "PAID".equals(currency) ? paid : reward;
      long result;
      try { result = Math.addExact(current, "GRANT".equals(direction) ? value : -value); }
      catch (ArithmeticException error) { throw new IllegalArgumentException("Số dư vượt giới hạn."); }
      if (result < 0) throw new IllegalArgumentException("Số dư không đủ để thu hồi.");
      int choice = JOptionPane.showConfirmDialog(dialog,
          "Tài khoản #" + id + ": " + current + " → " + result + " " + currency
              + "\nLý do: " + reason.getText().strip(),
          "Xác nhận điều chỉnh ví", JOptionPane.OK_CANCEL_OPTION);
      if (choice != JOptionPane.OK_OPTION) throw new IllegalArgumentException("Đã hủy thao tác.");
      Map<String, Object> body = Map.of("tokenType", currency, "amount", value,
          "direction", direction, "reason", reason.getText().strip());
      return api.create("/admin/wallets/" + id + "/adjustments", body,
          mutation.forInput("wallet:" + id, body));
    }, reply -> {
      mutation.confirmed();
      loadBalance();
      users.reloadSelecting(Long.toString(id));
      transactions.first();
      adjustments.first();
    });
    dialog.setSize(500, 360);
    dialog.setVisible(true);
  }

  @Override public void onShown() { if (api.signedIn()) { users.first(); orders.first(); } }
  @Override public void sessionChanged() {
    tasks.close(); mutation.confirmed(); selectedUser = 0;
    users.clear(); transactions.clear(); adjustments.clear(); orders.clear();
    packageModel.setRowCount(0); selectedOrder = 0;
    balance.setText("Chọn một tài khoản.");
  }
  @Override public void close() {
    tasks.close(); users.close(); transactions.close(); adjustments.close(); orders.close();
  }
}
