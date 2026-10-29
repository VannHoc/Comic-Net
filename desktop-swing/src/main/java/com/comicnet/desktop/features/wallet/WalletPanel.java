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
  private long pendingOrderId;

  public WalletPanel(ApiClient api) {
    super(new BorderLayout(0, 18));
    this.api = api;
    setOpaque(false);
    setBorder(new EmptyBorder(24, 30, 24, 30));
    JLabel title = Theme.plainLabel("Ví xu của tôi");
    title.setFont(Theme.heading(28));
    JPanel balances = new JPanel(new GridLayout(1, 2, 16, 0));
    balances.setOpaque(false);
    balances.add(balanceCard("PAID COIN", paid));
    balances.add(balanceCard("REWARD COIN", reward));
    JPanel top = new JPanel(new BorderLayout(0, 14));
    top.setOpaque(false);
    top.add(title, BorderLayout.NORTH);
    top.add(balances, BorderLayout.CENTER);
    add(top, BorderLayout.NORTH);
    ledger = new RemoteTable(api,
        new String[] {"Thời gian", "Nguồn", "Loại xu", "Thay đổi", "Số dư sau", "Trạng thái"},
        page -> "/me/wallet/transactions?page=" + page + "&size=20",
        row -> new Object[] {row.path("createdAt").asText(), row.path("source").asText(),
            row.path("tokenType").asText(), row.path("delta").asLong(),
            row.path("balanceAfter").asLong(), row.path("status").asText()});
    orders = new RemoteTable(api,
        new String[] {"Mã đơn", "Ngày tạo", "Gói", "Xu mua", "Số tiền VND", "Trạng thái"},
        page -> "/me/payment-orders?page=" + page + "&size=20",
        row -> new Object[] {row.path("id").asLong(), row.path("createdAt").asText(),
            row.path("packageCode").asText(), row.path("paidCoin").asLong(),
            row.path("amountVnd").asLong(), row.path("status").asText()});
    orders.onSelection(row -> {
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
    entitlements = new RemoteTable(api,
        new String[] {"Truyện", "Chương", "Nguồn quyền", "Trạng thái", "Có hiệu lực", "Hết hạn"},
        page -> "/me/entitlements?page=" + page + "&size=20",
        row -> new Object[] {row.path("comicTitle").asText(), row.path("chapterTitle").asText(),
            row.path("kind").asText(), row.path("state").asText(),
            row.path("validFrom").asText(), row.path("validTo").isNull() ? "Vĩnh viễn" : row.path("validTo").asText()});
    JTabbedPane history = new JTabbedPane();
    history.addTab("Giao dịch xu", ledger);
    history.addTab("Đơn nạp", orders);
    history.addTab("Quyền mở khóa", entitlements);
    add(history, BorderLayout.CENTER);
    JPanel footer = new JPanel(new BorderLayout());
    footer.setOpaque(false);
    JButton refresh = new JButton("Làm mới ví");
    refresh.addActionListener(event -> onShown());
    JButton topUp = new JButton("Nạp Paid Coin · Sandbox local");
    topUp.addActionListener(event -> showSandboxOffers());
    retryPayment.setEnabled(false);
    failPayment.setEnabled(false);
    retryPayment.addActionListener(event -> settlePending());
    failPayment.addActionListener(event -> failPending());
    JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    actions.setOpaque(false);
    actions.add(refresh);
    actions.add(topUp);
    actions.add(retryPayment);
    actions.add(failPayment);
    footer.add(actions, BorderLayout.WEST);
    footer.add(message, BorderLayout.CENTER);
    add(footer, BorderLayout.SOUTH);
  }

  private void showSandboxOffers() {
    if (!api.signedIn()) {
      message.setText("Đăng nhập trước khi tạo đơn nạp xu thử nghiệm.");
      return;
    }
    requests.run(this, message, api.get("/me/coin-packages"), reply -> {
      var offers = reply.body();
      if (!offers.isArray() || offers.isEmpty()) {
        message.setText("Sandbox local chưa có gói xu khả dụng.");
        return;
      }
      String[] labels = new String[offers.size()];
      for (int i = 0; i < offers.size(); i++) {
        var offer = offers.get(i);
        labels[i] = offer.path("paidCoin").asText() + " xu · "
            + offer.path("amountVnd").asText() + " VND thử nghiệm";
      }
      EditDialog dialog = new EditDialog(this, "Nạp xu Sandbox · không dùng tiền thật");
      JComboBox<String> packages = new JComboBox<>(labels);
      dialog.form.field("Gói xu", packages);
      dialog.submit(() -> {
        int selected = packages.getSelectedIndex();
        if (selected < 0) throw new IllegalArgumentException("Chọn một gói xu.");
        Map<String, Object> body = Map.of("packageCode",
            offers.get(selected).path("code").asText());
        return api.create("/me/payment-orders", body,
            paymentKey.forInput("sandbox-payment", body));
      }, order -> {
        paymentKey.confirmed();
        pendingOrderId = order.body().path("id").asLong();
        retryPayment.setEnabled(true);
        settlePending();
      });
      dialog.setSize(500, 270);
      dialog.setVisible(true);
    }, error -> {
      Throwable cause = ApiClient.unwrap(error);
      message.setText(cause instanceof ApiClient.ApiFailure failure && failure.status() == 404
          ? "Sandbox nạp xu chỉ hoạt động khi backend chạy profile local."
          : "Không tải được gói xu: " + cause.getMessage());
    });
  }

  private void settlePending() {
    if (pendingOrderId <= 0) return;
    long orderId = pendingOrderId;
    requests.run(this, message,
        api.request("POST", "/me/payment-orders/" + orderId + "/sandbox-settle", null, null),
        reply -> {
          if ("COMPLETED".equals(reply.body().path("status").asText())) {
            pendingOrderId = 0;
            retryPayment.setEnabled(false);
            onShown();
            orders.reload();
          } else message.setText("Đơn #" + orderId + " chưa hoàn tất; thử lại sau.");
        }, error -> {
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
    requests.run(this, message,
        api.request("POST", "/me/payment-orders/" + id + "/sandbox-fail", null, null),
        reply -> {
          pendingOrderId = 0;
          retryPayment.setEnabled(false);
          failPayment.setEnabled(false);
          orders.reload();
          message.setText("Đơn #" + id + " đã được đánh dấu thất bại trong sandbox.");
        });
  }

  private JPanel balanceCard(String caption, JLabel amount) {
    JPanel card = new JPanel(new BorderLayout(0, 8));
    card.setBackground(Theme.SURFACE);
    card.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER), new EmptyBorder(18, 20, 18, 20)));
    JLabel label = Theme.plainLabel(caption);
    label.setForeground(Theme.MUTED);
    amount.setFont(Theme.heading(30));
    card.add(label, BorderLayout.NORTH);
    card.add(amount, BorderLayout.CENTER);
    return card;
  }

  @Override public void onShown() {
    if (!api.signedIn()) {
      paid.setText("—"); reward.setText("—");
      message.setText("Đăng nhập để xem ví.");
      ledger.clear();
      orders.clear();
      entitlements.clear();
      return;
    }
    requests.run(this, message, api.get("/me/wallet"), reply -> {
      paid.setText(Long.toString(reply.body().path("paidCoin").asLong()));
      reward.setText(Long.toString(reply.body().path("rewardCoin").asLong()));
      ledger.first();
      orders.first();
      entitlements.first();
    });
  }

  @Override public void sessionChanged() {
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
  @Override public void close() { requests.close(); ledger.close(); orders.close(); entitlements.close(); }
}
