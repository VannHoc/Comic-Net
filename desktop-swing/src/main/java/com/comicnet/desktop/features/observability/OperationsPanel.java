package com.comicnet.desktop.features.observability;

import com.comicnet.desktop.client.api.*;
import com.comicnet.desktop.features.dataprotection.BackupRestoreGuidePanel;
import com.comicnet.desktop.ui.*;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public final class OperationsPanel extends JPanel implements Workspace {
  private final RemoteTable jobs, audit;
  private final ApiClient api;
  private final UiTasks actions = new UiTasks();
  private final JLabel message = Theme.plainLabel("Dành cho quản trị viên.");
  private final MutationKey key = new MutationKey();

  public OperationsPanel(ApiClient api) {
    super(new BorderLayout(0, 16));
    this.api = api;
    setOpaque(false);
    setBorder(new EmptyBorder(Theme.CONTENT_PADDING, Theme.CONTENT_PADDING, 24, Theme.CONTENT_PADDING));

    // Header Section V1.0
    JPanel header = new JPanel(new BorderLayout(0, 4));
    header.setOpaque(false);
    JLabel eyebrow = Theme.plainLabel("GIÁM SÁT HỆ THỐNG");
    eyebrow.setForeground(Theme.PRIMARY);
    eyebrow.setFont(Theme.heading(11));
    JLabel heading = new JLabel("Vận hành & Nhật ký");
    heading.setFont(Theme.heading(28));
    heading.setForeground(Theme.TEXT);
    JLabel sub =
        Theme.plainLabel("Theo dõi upload jobs, nhật ký kiểm toán và quy trình bảo vệ dữ liệu.");
    sub.setFont(Theme.label(13));
    sub.setForeground(Theme.MUTED);

    JPanel titleBox = new JPanel(new GridLayout(0, 1, 0, 2));
    titleBox.setOpaque(false);
    titleBox.add(eyebrow);
    titleBox.add(heading);
    titleBox.add(sub);
    header.add(titleBox, BorderLayout.WEST);
    add(header, BorderLayout.NORTH);

    jobs =
        new RemoteTable(
            api,
            new String[] {"Job", "Chương", "Version", "Trạng thái", "Lần thử", "Mã lỗi"},
            p -> "/admin/upload-jobs?page=" + p + "&size=20",
            v ->
                new Object[] {
                  v.path("id").asText(),
                  v.path("chapterId").asText(),
                  v.path("versionId").asText(),
                  v.path("status").asText(),
                  v.path("attempts").asInt(),
                  v.path("errorCode").asText("—")
                });
    audit =
        new RemoteTable(
            api,
            new String[] {
              "Thời gian UTC", "Actor", "Hành động", "Đối tượng", "Kết quả", "Request ID"
            },
            p -> "/admin/audit-logs?page=" + p + "&size=20",
            v ->
                new Object[] {
                  v.path("createdAt").asText(),
                  v.path("actorUserId").asText("—"),
                  v.path("action").asText(),
                  v.path("targetType").asText() + " #" + v.path("targetId").asText(),
                  v.path("outcome").asText(),
                  v.path("requestId").asText("—")
                });

    JPanel jobView = new JPanel(new BorderLayout(0, 10));
    jobView.setOpaque(false);
    jobView.add(jobs, BorderLayout.CENTER);

    JPanel jobActionCard = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
    jobActionCard.setOpaque(true);
    jobActionCard.setBackground(Theme.SURFACE);
    jobActionCard.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER, 1),
            new EmptyBorder(10, 14, 10, 14)));

    PrimaryButton retry = new PrimaryButton("Thử lại job đã chọn");
    retry.setIcon(UiIcons.refresh(14, Color.WHITE));
    retry.addActionListener(
        e -> {
          if (jobs.selected() == null || actions.busy()) return;
          String id = jobs.selected().path("id").asText();
          actions.run(
              this,
              message,
              api.create(
                  "/publishing/uploads/" + id + "/retry", null, key.forInput("retry", id)),
              reply -> {
                key.confirmed();
                jobs.reload();
              });
        });
    jobActionCard.add(retry);
    jobView.add(jobActionCard, BorderLayout.SOUTH);

    JTabbedPane tabs = new JTabbedPane();
    tabs.putClientProperty("JTabbedPane.tabType", "card");
    tabs.setFont(Theme.brand(13));
    tabs.addTab("Upload jobs", UiIcons.settings(14, Theme.MUTED), jobView);
    tabs.addTab("Audit Logs", UiIcons.shield(14, Theme.MUTED), audit);
    tabs.addTab("Backup / Restore", UiIcons.gift(14, Theme.MUTED), new BackupRestoreGuidePanel());
    tabs.addChangeListener(
        e -> {
          if (api.signedIn()) {
            if (tabs.getSelectedIndex() == 0) jobs.reload();
            else if (tabs.getSelectedIndex() == 1) audit.reload();
          }
        });
    add(tabs, BorderLayout.CENTER);

    JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    footer.setOpaque(false);
    message.setFont(Theme.label(12));
    message.setForeground(Theme.MUTED);
    footer.add(message);
    add(footer, BorderLayout.SOUTH);
  }

  @Override
  public void onShown() {
    if (api.signedIn()) {
      jobs.reload();
      message.setText("Nhật ký và job lấy trực tiếp từ backend.");
    }
  }

  @Override
  public void sessionChanged() {
    actions.close();
    jobs.clear();
    audit.clear();
    message.setText("Phiên tài khoản đã đổi.");
  }

  @Override
  public void close() {
    sessionChanged();
    jobs.close();
    audit.close();
  }
}
