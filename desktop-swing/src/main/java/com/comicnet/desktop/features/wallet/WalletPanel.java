package com.comicnet.desktop.features.wallet;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.client.api.MutationKey;
import com.comicnet.desktop.ui.*;
import java.awt.*;
import java.util.Map;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Account-scoped balances and immutable transaction history from the backend. */
public final class WalletPanel extends JPanel implements Workspace {
  private final ApiClient api;
  private final UiTasks requests = new UiTasks();
  private final JLabel paid = Theme.plainLabel("—"), reward = Theme.plainLabel("—");
  private final JLabel message = Theme.plainLabel("Đăng nhập để xem ví.");
  private final RemoteTable ledger, orders, entitlements;
  private final JButton retryPayment = new JButton("Tiếp tục đơn thử nghiệm");
  private final JButton failPayment = new JButton("Mô phỏng thất bại");
  private final MutationKey paymentKey = new MutationKey();
  private final Runnable onCoinsChanged;
  private long pendingOrderId;

  public WalletPanel(ApiClient api) {
    this(api, null);
  }

  public WalletPanel(ApiClient api, Runnable onCoinsChanged) {
    super(new BorderLayout(0, 18));
    this.api = api;
    this.onCoinsChanged = onCoinsChanged;
    setOpaque(false);
    setBorder(new EmptyBorder(Theme.CONTENT_PADDING, Theme.CONTENT_PADDING, 24, Theme.CONTENT_PADDING));

    // Header Section V1.0
    JPanel header = new JPanel(new BorderLayout(0, 4));
    header.setOpaque(false);
    JLabel eyebrow = Theme.plainLabel("TÀI CHÍNH & VÍ XU");
    eyebrow.setForeground(Theme.PRIMARY);
    eyebrow.setFont(Theme.heading(11));
    JLabel title = Theme.plainLabel("Ví xu của tôi");
    title.setFont(Theme.heading(28));
    title.setForeground(Theme.TEXT);
    JLabel sub =
        Theme.plainLabel("Theo dõi số dư, nạp xu và lịch sử giao dịch minh bạch.");
    sub.setFont(Theme.label(13));
    sub.setForeground(Theme.MUTED);

    JPanel titleBox = new JPanel(new GridLayout(0, 1, 0, 2));
    titleBox.setOpaque(false);
    titleBox.add(eyebrow);
    titleBox.add(title);
    titleBox.add(sub);
    header.add(titleBox, BorderLayout.WEST);

    // 2 Balance Cards
    JPanel balances = new JPanel(new GridLayout(1, 2, 18, 0));
    balances.setOpaque(false);
    balances.add(balanceCard("XU MUA (PAID COIN)", paid, "Dùng mua chương truyện vĩnh viễn", true));
    balances.add(balanceCard("XU THƯỞNG (REWARD COIN)", reward, "Tích lũy từ điểm danh, đọc truyện & vòng quay", false));

    JPanel top = new JPanel(new BorderLayout(0, 16));
    top.setOpaque(false);
    top.add(header, BorderLayout.NORTH);
    top.add(balances, BorderLayout.CENTER);
    add(top, BorderLayout.NORTH);

    // History Remote Tables
    ledger =
        new RemoteTable(
            api,
            new String[] {"Thời gian", "Nguồn", "Loại xu", "Thay đổi", "Số dư sau", "Trạng thái"},
            page -> "/me/wallet/transactions?page=" + page + "&size=20",
            row ->
                new Object[] {
                  row.path("createdAt").asText(),
                  row.path("source").asText(),
                  row.path("tokenType").asText(),
                  row.path("delta").asLong(),
                  row.path("balanceAfter").asLong(),
                  row.path("status").asText()
                });
    orders =
        new RemoteTable(
            api,
            new String[] {"Mã đơn", "Ngày tạo", "Gói", "Xu mua", "Số tiền VND", "Trạng thái"},
            page -> "/me/payment-orders?page=" + page + "&size=20",
            row ->
                new Object[] {
                  row.path("id").asLong(),
                  row.path("createdAt").asText(),
                  row.path("packageCode").asText(),
                  row.path("paidCoin").asLong(),
                  row.path("amountVnd").asLong(),
                  row.path("status").asText()
                });
    orders.onSelection(
        row -> {
          if (row != null && "PENDING".equals(row.path("status").asText())) {
            pendingOrderId = row.path("id").asLong();
            retryPayment.setEnabled(true);
            failPayment.setEnabled(true);
          } else {
            pendingOrderId = 0;
            retryPayment.setEnabled(false);
            failPayment.setEnabled(false);
          }
        });
    entitlements =
        new RemoteTable(
            api,
            new String[] {
              "Truyện", "Chương", "Nguồn quyền", "Trạng thái", "Có hiệu lực", "Hết hạn"
            },
            page -> "/me/entitlements?page=" + page + "&size=20",
            row ->
                new Object[] {
                  row.path("comicTitle").asText(),
                  row.path("chapterTitle").asText(),
                  row.path("kind").asText(),
                  row.path("state").asText(),
                  row.path("validFrom").asText(),
                  row.path("validTo").isNull() ? "Vĩnh viễn" : row.path("validTo").asText()
                });

    JTabbedPane history = new JTabbedPane();
    history.putClientProperty("JTabbedPane.tabType", "card");
    history.setFont(Theme.brand(13));
    history.addTab("Giao dịch xu", UiIcons.coin(15), ledger);
    history.addTab("Đơn nạp", UiIcons.creditCard(15, Theme.MUTED), orders);
    history.addTab("Quyền mở khóa", UiIcons.lock(15, Theme.MUTED), entitlements);
    add(history, BorderLayout.CENTER);

    // Footer actions
    JPanel footer = new JPanel(new BorderLayout(14, 0));
    footer.setOpaque(true);
    footer.setBackground(Theme.SURFACE);
    footer.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER, 1),
            new EmptyBorder(10, 16, 10, 16)));

    JButton refresh = new JButton("Làm mới ví");
    refresh.setFont(Theme.label(13));
    refresh.setIcon(UiIcons.refresh(14, Theme.TEXT));
    refresh.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    refresh.addActionListener(event -> onShown());

    PrimaryButton topUp = new PrimaryButton("Nạp Paid Coin · Sandbox");
    topUp.setIcon(UiIcons.plus(13, Color.WHITE));
    topUp.addActionListener(event -> showSandboxOffers());

    retryPayment.setFont(Theme.label(13));
    retryPayment.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    retryPayment.setEnabled(false);
    retryPayment.addActionListener(event -> settlePending());

    failPayment.setFont(Theme.label(13));
    failPayment.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    failPayment.setForeground(Theme.DANGER);
    failPayment.setEnabled(false);
    failPayment.addActionListener(event -> failPending());

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
    actions.setOpaque(false);
    actions.add(topUp);
    actions.add(refresh);
    actions.add(retryPayment);
    actions.add(failPayment);

    message.setFont(Theme.label(12));
    message.setForeground(Theme.MUTED);

    footer.add(actions, BorderLayout.WEST);
    footer.add(message, BorderLayout.CENTER);
    add(footer, BorderLayout.SOUTH);
  }

  private void showSandboxOffers() {
    if (!api.signedIn()) {
      message.setText("Đăng nhập trước khi tạo đơn nạp xu thử nghiệm.");
      return;
    }
    requests.run(
        this,
        message,
        api.get("/me/coin-packages"),
        reply -> {
          var offers = reply.body();
          if (!offers.isArray() || offers.isEmpty()) {
            message.setText("Sandbox local chưa có gói xu khả dụng.");
            return;
          }
          String[] labels = new String[offers.size()];
          for (int i = 0; i < offers.size(); i++) {
            var offer = offers.get(i);
            labels[i] =
                offer.path("paidCoin").asText()
                    + " xu · "
                    + offer.path("amountVnd").asText()
                    + " VND thử nghiệm";
          }
          EditDialog dialog = new EditDialog(this, "Nạp xu Sandbox · không dùng tiền thật");
          JComboBox<String> packages = new JComboBox<>(labels);
          dialog.form.field("Gói xu", packages);
          dialog.submit(
              () -> {
                int selected = packages.getSelectedIndex();
                if (selected < 0) throw new IllegalArgumentException("Chọn một gói xu.");
                Map<String, Object> body =
                    Map.of("packageCode", offers.get(selected).path("code").asText());
                return api.create(
                    "/me/payment-orders", body, paymentKey.forInput("sandbox-payment", body));
              },
              order -> {
                paymentKey.confirmed();
                pendingOrderId = order.body().path("id").asLong();
                retryPayment.setEnabled(true);
                settlePending();
              });
          dialog.setSize(500, 270);
          dialog.setVisible(true);
        },
        error -> {
          Throwable cause = ApiClient.unwrap(error);
          message.setText(
              cause instanceof ApiClient.ApiFailure failure && failure.status() == 404
                  ? "Sandbox nạp xu chỉ hoạt động khi backend chạy profile local."
                  : "Không tải được gói xu: " + cause.getMessage());
        });
  }

  private void settlePending() {
    if (pendingOrderId <= 0) return;
    long orderId = pendingOrderId;
    requests.run(
        this,
        message,
        api.request("POST", "/me/payment-orders/" + orderId + "/sandbox-settle", null, null),
        reply -> {
          if ("COMPLETED".equals(reply.body().path("status").asText())) {
            pendingOrderId = 0;
            retryPayment.setEnabled(false);
            onShown();
            orders.reload();
            if (onCoinsChanged != null) onCoinsChanged.run();
            Container topAncestor = getTopLevelAncestor();
            if (topAncestor instanceof MainFrame mf) mf.updateTopBarUser();
          } else message.setText("Đơn #" + orderId + " chưa hoàn tất; thử lại sau.");
        },
        error -> {
          Throwable cause = ApiClient.unwrap(error);
          if (cause instanceof ApiClient.ApiFailure failure && failure.status() == 409) {
            pendingOrderId = 0;
            retryPayment.setEnabled(false);
            failPayment.setEnabled(false);
            orders.reload();
            message.setText("Đơn #" + orderId + " đã thất bại hoặc hết hạn. Tạo đơn nạp mới.");
          } else message.setText("Không xử lý được đơn #" + orderId + ": " + cause.getMessage());
        });
  }

  private void failPending() {
    if (pendingOrderId <= 0) return;
    long id = pendingOrderId;
    requests.run(
        this,
        message,
        api.request("POST", "/me/payment-orders/" + id + "/sandbox-fail", null, null),
        reply -> {
          pendingOrderId = 0;
          retryPayment.setEnabled(false);
          failPayment.setEnabled(false);
          orders.reload();
          message.setText("Đơn #" + id + " đã được đánh dấu thất bại trong sandbox.");
        });
  }

  private JPanel balanceCard(String caption, JLabel amount, String subtitle, boolean isPaid) {
    JPanel card = new JPanel(new BorderLayout(0, 10));
    card.setBackground(Theme.SURFACE);
    card.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER, 1),
            new EmptyBorder(18, 22, 18, 22)));

    JPanel cardTop = new JPanel(new BorderLayout(8, 0));
    cardTop.setOpaque(false);
    JLabel icon = new JLabel(UiIcons.coin(22));
    JLabel label = Theme.plainLabel(caption);
    label.setFont(Theme.heading(12));
    label.setForeground(isPaid ? Theme.PRIMARY : new Color(0xD97706));

    JPanel labelBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    labelBox.setOpaque(false);
    labelBox.add(icon);
    labelBox.add(label);
    cardTop.add(labelBox, BorderLayout.WEST);

    if (isPaid) {
      JButton miniTopUp = new JButton("+ Nạp nhanh");
      miniTopUp.setFont(Theme.label(11));
      miniTopUp.setForeground(Theme.PRIMARY);
      miniTopUp.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      miniTopUp.addActionListener(e -> showSandboxOffers());
      cardTop.add(miniTopUp, BorderLayout.EAST);
    }

    amount.setFont(Theme.heading(32));
    amount.setForeground(Theme.TEXT);

    JLabel subLabel = Theme.plainLabel(subtitle);
    subLabel.setFont(Theme.label(12));
    subLabel.setForeground(Theme.MUTED);

    JPanel centerBox = new JPanel(new GridLayout(0, 1, 0, 4));
    centerBox.setOpaque(false);
    centerBox.add(amount);
    centerBox.add(subLabel);

    card.add(cardTop, BorderLayout.NORTH);
    card.add(centerBox, BorderLayout.CENTER);
    return card;
  }

  @Override
  public void onShown() {
    if (!api.signedIn()) {
      paid.setText("—");
      reward.setText("—");
      message.setText("Đăng nhập để xem ví.");
      ledger.clear();
      orders.clear();
      entitlements.clear();
      return;
    }
    requests.run(
        this,
        message,
        api.get("/me/wallet"),
        reply -> {
          long pCoin = reply.body().path("paidCoin").asLong();
          long rCoin = reply.body().path("rewardCoin").asLong();
          paid.setText(Long.toString(pCoin) + " xu");
          reward.setText(Long.toString(rCoin) + " xu");
          if (onCoinsChanged != null) onCoinsChanged.run();
          Container topAncestor = getTopLevelAncestor();
          if (topAncestor instanceof MainFrame mf) mf.refreshCoins(pCoin + rCoin);
          ledger.first();
          orders.first();
          entitlements.first();
        });
  }

  @Override
  public void sessionChanged() {
    requests.close();
    pendingOrderId = 0;
    retryPayment.setEnabled(false);
    failPayment.setEnabled(false);
    paymentKey.confirmed();
    ledger.clear();
    orders.clear();
    entitlements.clear();
    onShown();
  }

  @Override
  public void close() {
    requests.close();
    ledger.close();
    orders.close();
    entitlements.close();
  }
}
