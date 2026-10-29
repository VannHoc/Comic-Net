package com.comicnet.desktop.features.wallet;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.client.api.MutationKey;
import com.comicnet.desktop.ui.*;
import java.awt.*;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/** Administrator-only wallet adjustment screen; the backend enforces the role again. */
public final class AdminWalletPanel extends JPanel implements Workspace {
  private final ApiClient api;
  private final UiTasks tasks = new UiTasks();
  private final RemoteTable users, transactions, adjustments, orders, purchases;
  private final DefaultTableModel packageModel =
      new DefaultTableModel(
          new Object[] {"Mã gói", "Xu mua", "Giá VND", "Đang bán"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
          return false;
        }
      };
  private final JTable packages = new JTable(packageModel);
  private long selectedOrder;
  private long selectedPurchase;
  private final JTextField search = new JTextField(22);
  private final JLabel balance = Theme.plainLabel("Chọn một tài khoản để xem chi tiết và thao tác."),
      message = Theme.plainLabel(" ");
  private long selectedUser;
  private long paid, reward;
  private final MutationKey mutation = new MutationKey();

  public AdminWalletPanel(ApiClient api) {
    super(new BorderLayout(0, 16));
    this.api = api;
    setOpaque(false);
    setBorder(new EmptyBorder(Theme.CONTENT_PADDING, Theme.CONTENT_PADDING, 24, Theme.CONTENT_PADDING));

    // Header Section V1.0
    JPanel header = new JPanel(new BorderLayout(0, 4));
    header.setOpaque(false);
    JLabel eyebrow = Theme.plainLabel("QUẢN TRỊ HỆ THỐNG");
    eyebrow.setForeground(Theme.PRIMARY);
    eyebrow.setFont(Theme.heading(11));
    JLabel title = Theme.plainLabel("Quản lý ví xu & đối soát");
    title.setFont(Theme.heading(28));
    title.setForeground(Theme.TEXT);
    JLabel sub =
        Theme.plainLabel("Tra cứu tài khoản, đối soát giao dịch, gói nạp và xử lý thu hồi/hoàn tiền.");
    sub.setFont(Theme.label(13));
    sub.setForeground(Theme.MUTED);

    JPanel titleBox = new JPanel(new GridLayout(0, 1, 0, 2));
    titleBox.setOpaque(false);
    titleBox.add(eyebrow);
    titleBox.add(title);
    titleBox.add(sub);

    // Search bar top right
    JPanel find = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    find.setOpaque(false);
    search.putClientProperty("JComponent.roundRect", true);
    PrimaryButton findButton = new PrimaryButton("Tìm tài khoản");
    findButton.setIcon(UiIcons.search(14, Color.WHITE));
    find.add(search);
    find.add(findButton);

    header.add(titleBox, BorderLayout.WEST);
    header.add(find, BorderLayout.EAST);
    add(header, BorderLayout.NORTH);

    users =
        new RemoteTable(
            api,
            new String[] {
              "ID", "Tài khoản", "Tên", "Xu mua", "Xu thưởng", "Tổng xu", "Trạng thái"
            },
            page ->
                "/admin/wallets?page="
                    + page
                    + "&size=20&q="
                    + java.net.URLEncoder.encode(
                        search.getText().strip(), java.nio.charset.StandardCharsets.UTF_8),
            row ->
                new Object[] {
                  row.path("id").asText(),
                  row.path("username").asText(),
                  row.path("displayName").asText(),
                  row.path("paidCoin").asLong(),
                  row.path("rewardCoin").asLong(),
                  row.path("totalCoin").asLong(),
                  row.path("status").asText()
                });
    findButton.addActionListener(e -> users.first());
    search.addActionListener(e -> users.first());

    transactions =
        new RemoteTable(
            api,
            new String[] {"Thời gian", "Nguồn", "Loại xu", "Thay đổi", "Số dư sau"},
            page ->
                selectedUser == 0
                    ? null
                    : "/admin/wallets/" + selectedUser + "/transactions?page=" + page + "&size=20",
            row ->
                new Object[] {
                  row.path("createdAt").asText(),
                  row.path("source").asText(),
                  row.path("tokenType").asText(),
                  row.path("delta").asLong(),
                  row.path("balanceAfter").asLong()
                });

    adjustments =
        new RemoteTable(
            api,
            new String[] {"Thời gian", "Admin ID", "Thao tác", "Chi tiết"},
            page ->
                selectedUser == 0
                    ? null
                    : "/admin/wallets/" + selectedUser + "/adjustments?page=" + page + "&size=20",
            row ->
                new Object[] {
                  row.path("createdAt").asText(),
                  row.path("actorUserId").asLong(),
                  row.path("action").asText(),
                  row.path("metadata").asText()
                });

    orders =
        new RemoteTable(
            api,
            new String[] {
              "Mã đơn", "Tài khoản", "Gói", "Xu mua", "Giá VND", "Trạng thái", "Ngày tạo"
            },
            page -> "/admin/payment-orders?page=" + page + "&size=20",
            row ->
                new Object[] {
                  row.path("id").asLong(),
                  row.path("username").asText(),
                  row.path("packageCode").asText(),
                  row.path("paidCoin").asLong(),
                  row.path("amountVnd").asLong(),
                  row.path("status").asText(),
                  row.path("createdAt").asText()
                });
    orders.onSelection(row -> selectedOrder = row == null ? 0 : row.path("id").asLong());

    purchases =
        new RemoteTable(
            api,
            new String[] {
              "Mã mua",
              "Tài khoản",
              "Truyện",
              "Chương",
              "Số xu",
              "Loại xu",
              "Trạng thái",
              "Ngày mua",
              "Ngày thu hồi",
              "Lý do"
            },
            page -> "/admin/purchases?page=" + page + "&size=20",
            row ->
                new Object[] {
                  row.path("id").asLong(),
                  row.path("username").asText(),
                  row.path("comicTitle").asText(),
                  row.path("chapterTitle").asText(),
                  row.path("amount").asLong(),
                  row.path("currencyCode").asText(),
                  row.path("status").asText(),
                  row.path("createdAt").asText(),
                  row.path("revokedAt").asText(""),
                  row.path("revokeReason").asText("")
                });
    purchases.onSelection(
        row -> selectedPurchase = row == null ? 0 : row.path("id").asLong());

    users.onSelection(
        row -> {
          selectedUser = row == null ? 0 : row.path("id").asLong();
          transactions.clear();
          adjustments.clear();
          if (selectedUser != 0) loadBalance();
          else balance.setText("Chọn một tài khoản để xem chi tiết và thao tác.");
        });

    // Offers Panel
    JPanel offersPanel = new JPanel(new BorderLayout(0, 10));
    offersPanel.setOpaque(false);
    JPanel offerActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    offerActions.setOpaque(true);
    offerActions.setBackground(Theme.SURFACE);
    offerActions.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER, 1),
            new EmptyBorder(8, 12, 8, 12)));

    PrimaryButton addPackage = new PrimaryButton("Tạo gói");
    addPackage.setIcon(UiIcons.plus(13, Color.WHITE));
    JButton editPackage = new JButton("Sửa gói");
    editPackage.setFont(Theme.label(13));
    editPackage.setIcon(UiIcons.edit(14, Theme.TEXT));
    JButton reloadPackages = new JButton("Tải lại gói");
    reloadPackages.setFont(Theme.label(13));
    reloadPackages.setIcon(UiIcons.refresh(14, Theme.TEXT));

    addPackage.addActionListener(e -> editPackage(null));
    editPackage.addActionListener(
        e -> {
          int row = packages.getSelectedRow();
          if (row < 0) {
            message.setText("Chọn gói trước.");
            return;
          }
          editPackage(packages.convertRowIndexToModel(row));
        });
    reloadPackages.addActionListener(e -> loadPackages());
    offerActions.add(addPackage);
    offerActions.add(editPackage);
    offerActions.add(reloadPackages);
    offersPanel.add(offerActions, BorderLayout.NORTH);

    packages.setRowHeight(36);
    packages.setFont(Theme.label(13));
    packages.setSelectionBackground(Theme.ACTION_TINT);
    packages.setSelectionForeground(Theme.TEXT);
    if (packages.getTableHeader() != null) {
      packages.getTableHeader().setFont(Theme.brand(12));
      packages.getTableHeader().setBackground(Theme.ELEVATED);
      packages.getTableHeader().setForeground(Theme.MUTED);
      packages.getTableHeader().setPreferredSize(new Dimension(100, 34));
    }
    JScrollPane packageScroll = new JScrollPane(packages);
    packageScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));
    offersPanel.add(packageScroll, BorderLayout.CENTER);

    // Orders Panel
    JPanel orderPanel = new JPanel(new BorderLayout(0, 10));
    orderPanel.setOpaque(false);
    JButton refund = new JButton("Hoàn xu đơn đã chọn");
    refund.setFont(Theme.label(13));
    refund.setIcon(UiIcons.refresh(14, Theme.DANGER));
    refund.setForeground(Theme.DANGER);
    refund.addActionListener(e -> refundOrder());
    JPanel orderActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    orderActions.setOpaque(true);
    orderActions.setBackground(Theme.SURFACE);
    orderActions.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER, 1),
            new EmptyBorder(8, 12, 8, 12)));
    orderActions.add(refund);
    orderPanel.add(orders, BorderLayout.CENTER);
    orderPanel.add(orderActions, BorderLayout.SOUTH);

    // Purchases Panel
    JPanel purchasePanel = new JPanel(new BorderLayout(0, 10));
    purchasePanel.setOpaque(false);
    JButton refundPurchase = new JButton("Thu hồi quyền & hoàn xu đơn mua đã chọn");
    refundPurchase.setFont(Theme.label(13));
    refundPurchase.setIcon(UiIcons.refresh(14, Theme.DANGER));
    refundPurchase.setForeground(Theme.DANGER);
    refundPurchase.addActionListener(e -> refundPurchase());
    JPanel purchaseActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    purchaseActions.setOpaque(true);
    purchaseActions.setBackground(Theme.SURFACE);
    purchaseActions.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER, 1),
            new EmptyBorder(8, 12, 8, 12)));
    purchaseActions.add(refundPurchase);
    purchasePanel.add(purchases, BorderLayout.CENTER);
    purchasePanel.add(purchaseActions, BorderLayout.SOUTH);

    // Tabs
    JTabbedPane tabs = new JTabbedPane();
    tabs.putClientProperty("JTabbedPane.tabType", "card");
    tabs.setFont(Theme.brand(13));
    tabs.addTab("Tài khoản", UiIcons.user(14, Theme.MUTED), users);
    tabs.addTab("Giao dịch", UiIcons.coin(14), transactions);
    tabs.addTab("Điều chỉnh", UiIcons.settings(14, Theme.MUTED), adjustments);
    tabs.addTab("Gói nạp", UiIcons.gift(14, Theme.MUTED), offersPanel);
    tabs.addTab("Đơn nạp", UiIcons.creditCard(14, Theme.MUTED), orderPanel);
    tabs.addTab("Mua chương", UiIcons.bookOpen(14, Theme.MUTED), purchasePanel);

    tabs.addChangeListener(
        e -> {
          if (tabs.getSelectedIndex() == 3) loadPackages();
          if (tabs.getSelectedIndex() == 4) orders.first();
          if (tabs.getSelectedIndex() == 5) purchases.first();
          if (selectedUser == 0) return;
          if (tabs.getSelectedIndex() == 1) transactions.first();
          if (tabs.getSelectedIndex() == 2) adjustments.first();
        });
    add(tabs, BorderLayout.CENTER);

    // Footer Card
    JPanel footerCard = new JPanel(new BorderLayout(14, 8));
    footerCard.setOpaque(true);
    footerCard.setBackground(Theme.SURFACE);
    footerCard.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER, 1),
            new EmptyBorder(12, 16, 12, 16)));

    balance.setFont(Theme.heading(13));
    balance.setForeground(Theme.TEXT);

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
    buttons.setOpaque(false);
    PrimaryButton grant = new PrimaryButton("Cấp xu");
    grant.setIcon(UiIcons.plus(13, Color.WHITE));
    JButton revoke = new JButton("Thu hồi xu");
    revoke.setFont(Theme.label(13));
    revoke.setIcon(UiIcons.trash(14, Theme.DANGER));
    revoke.setForeground(Theme.DANGER);

    grant.addActionListener(e -> edit("GRANT"));
    revoke.addActionListener(e -> edit("REVOKE"));
    buttons.add(grant);
    buttons.add(revoke);

    message.setFont(Theme.label(12));
    message.setForeground(Theme.MUTED);

    JPanel leftFooter = new JPanel(new GridLayout(0, 1, 0, 4));
    leftFooter.setOpaque(false);
    leftFooter.add(balance);
    leftFooter.add(message);

    footerCard.add(leftFooter, BorderLayout.WEST);
    footerCard.add(buttons, BorderLayout.EAST);
    add(footerCard, BorderLayout.SOUTH);
  }

  private void loadPackages() {
    tasks.run(
        this,
        message,
        api.get("/admin/coin-packages"),
        reply -> {
          packageModel.setRowCount(0);
          for (var row : reply.body())
            packageModel.addRow(
                new Object[] {
                  row.path("code").asText(),
                  row.path("paidCoin").asLong(),
                  row.path("amountVnd").asLong(),
                  row.path("active").asBoolean()
                });
        });
  }

  private void editPackage(Integer row) {
    String code = row == null ? "" : packageModel.getValueAt(row, 0).toString();
    EditDialog dialog = new EditDialog(this, row == null ? "Tạo gói nạp" : "Sửa gói nạp");
    JTextField codeField = EditDialog.text(code);
    codeField.setEditable(row == null);
    JTextField coins =
        EditDialog.text(row == null ? "" : packageModel.getValueAt(row, 1).toString());
    JTextField price =
        EditDialog.text(row == null ? "" : packageModel.getValueAt(row, 2).toString());
    JCheckBox active =
        new JCheckBox(
            "Đang bán",
            row == null || Boolean.TRUE.equals(packageModel.getValueAt(row, 3)));
    dialog.form.field("Mã gói (A-Z, 0-9, _)", codeField);
    dialog.form.field("Xu mua", coins);
    dialog.form.field("Giá VND", price);
    dialog.form.field("Trạng thái", active);
    dialog.submit(
        () -> {
          String key = codeField.getText().strip();
          if (!key.matches("[A-Z0-9_]{2,30}"))
            throw new IllegalArgumentException(
                "Mã gói phải có 2–30 ký tự A-Z, 0-9 hoặc _.");
          long coinValue, priceValue;
          try {
            coinValue = Long.parseLong(coins.getText().strip());
            priceValue = Long.parseLong(price.getText().strip());
          } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Số xu và giá phải là số nguyên dương.");
          }
          if (coinValue <= 0 || priceValue <= 0)
            throw new IllegalArgumentException("Số xu và giá phải lớn hơn 0.");
          return api.request(
              "PUT",
              "/admin/coin-packages/" + key,
              Map.of(
                  "paidCoin",
                  coinValue,
                  "amountVnd",
                  priceValue,
                  "active",
                  active.isSelected()),
              null);
        },
        reply -> loadPackages());
    dialog.setSize(520, 380);
    dialog.setVisible(true);
  }

  private void refundOrder() {
    if (selectedOrder <= 0) {
      message.setText("Chọn đơn nạp trước.");
      return;
    }
    long id = selectedOrder;
    String reason = JOptionPane.showInputDialog(this, "Lý do hoàn xu đơn #" + id + ":");
    if (reason == null) return;
    if (reason.isBlank()) {
      message.setText("Nhập lý do hoàn xu.");
      return;
    }
    if (JOptionPane.showConfirmDialog(
            this,
            "Thu hồi số xu đã nạp từ đơn #" + id + "?",
            "Xác nhận hoàn xu",
            JOptionPane.OK_CANCEL_OPTION)
        != JOptionPane.OK_OPTION) return;
    tasks.run(
        this,
        message,
        api.request(
            "POST",
            "/admin/payment-orders/" + id + "/refund",
            Map.of("reason", reason.strip()),
            null),
        reply -> {
          orders.reload();
          users.reload();
        });
  }

  private void refundPurchase() {
    if (selectedPurchase <= 0) {
      message.setText("Chọn đơn mua chương trước.");
      return;
    }
    long id = selectedPurchase;
    String reason =
        JOptionPane.showInputDialog(this, "Lý do thu hồi & hoàn xu đơn mua #" + id + ":");
    if (reason == null) return;
    if (reason.isBlank()) {
      message.setText("Nhập lý do thu hồi.");
      return;
    }
    if (JOptionPane.showConfirmDialog(
            this,
            "Thu hồi quyền đọc và hoàn xu cho người mua đơn #" + id + "?",
            "Xác nhận thu hồi quyền & hoàn xu",
            JOptionPane.OK_CANCEL_OPTION)
        != JOptionPane.OK_OPTION) return;
    tasks.run(
        this,
        message,
        api.request(
            "POST",
            "/admin/purchases/" + id + "/refund",
            Map.of("reason", reason.strip()),
            null),
        reply -> {
          purchases.reload();
          users.reload();
        });
  }

  private void loadBalance() {
    long id = selectedUser;
    tasks.run(
        this,
        message,
        api.get("/admin/wallets/" + id),
        reply -> {
          if (selectedUser != id) return;
          paid = reply.body().path("paidCoin").asLong();
          reward = reply.body().path("rewardCoin").asLong();
          balance.setText(
              "Tài khoản #"
                  + id
                  + "  ·  Xu mua: "
                  + paid
                  + "  ·  Xu thưởng: "
                  + reward
                  + "  ·  Tổng: "
                  + (paid + reward));
        });
  }

  private void edit(String direction) {
    if (selectedUser == 0) {
      message.setText("Chọn tài khoản trước.");
      return;
    }
    long id = selectedUser;
    EditDialog dialog =
        new EditDialog(this, "GRANT".equals(direction) ? "Cấp xu" : "Thu hồi xu");
    JComboBox<String> token = EditDialog.options("REWARD", "REWARD", "PAID");
    JTextField amount = EditDialog.text("100"), reason = EditDialog.text("");
    dialog.form.field("Loại xu", token);
    dialog.form.field("Số xu", amount);
    dialog.form.field("Lý do", reason);
    dialog.submit(
        () -> {
          long value;
          try {
            value = Long.parseLong(amount.getText().strip());
          } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Số xu phải là số nguyên dương.");
          }
          if (value <= 0 || reason.getText().isBlank())
            throw new IllegalArgumentException("Nhập số xu dương và lý do.");
          String currency = (String) token.getSelectedItem();
          long current = "PAID".equals(currency) ? paid : reward;
          long result;
          try {
            result = Math.addExact(current, "GRANT".equals(direction) ? value : -value);
          } catch (ArithmeticException error) {
            throw new IllegalArgumentException("Số dư vượt giới hạn.");
          }
          if (result < 0) throw new IllegalArgumentException("Số dư không đủ để thu hồi.");
          int choice =
              JOptionPane.showConfirmDialog(
                  dialog,
                  "Tài khoản #"
                      + id
                      + ": "
                      + current
                      + " → "
                      + result
                      + " "
                      + currency
                      + "\nLý do: "
                      + reason.getText().strip(),
                  "Xác nhận điều chỉnh ví",
                  JOptionPane.OK_CANCEL_OPTION);
          if (choice != JOptionPane.OK_OPTION)
            throw new IllegalArgumentException("Đã hủy thao tác.");
          Map<String, Object> body =
              Map.of(
                  "tokenType",
                  currency,
                  "amount",
                  value,
                  "direction",
                  direction,
                  "reason",
                  reason.getText().strip());
          return api.create(
              "/admin/wallets/" + id + "/adjustments",
              body,
              mutation.forInput("wallet:" + id, body));
        },
        reply -> {
          mutation.confirmed();
          loadBalance();
          users.reloadSelecting(Long.toString(id));
          transactions.first();
          adjustments.first();
        });
    dialog.setSize(500, 360);
    dialog.setVisible(true);
  }

  @Override
  public void onShown() {
    if (api.signedIn()) {
      users.first();
      orders.first();
    }
  }

  @Override
  public void sessionChanged() {
    tasks.close();
    mutation.confirmed();
    selectedUser = 0;
    users.clear();
    transactions.clear();
    adjustments.clear();
    orders.clear();
    purchases.clear();
    selectedPurchase = 0;
    packageModel.setRowCount(0);
    selectedOrder = 0;
    balance.setText("Chọn một tài khoản để xem chi tiết và thao tác.");
  }

  @Override
  public void close() {
    tasks.close();
    users.close();
    transactions.close();
    adjustments.close();
    orders.close();
    purchases.close();
  }
}
