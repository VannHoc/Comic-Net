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
    super(new BorderLayout(0, 14));
    this.api = api;
    setOpaque(false);
    setBorder(new EmptyBorder(26, 30, 20, 30));
    JLabel heading = new JLabel("Vận hành và nhật ký");
    heading.setFont(Theme.heading(28));
    add(heading, BorderLayout.NORTH);
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
    JPanel jobView = new JPanel(new BorderLayout(0, 8));
    jobView.setOpaque(false);
    jobView.add(jobs);
    JButton retry = new JButton("Thử lại job đã chọn");
    retry.addActionListener(
        e -> {
          if (jobs.selected() == null || actions.busy()) return;
          String id = jobs.selected().path("id").asText();
          actions.run(
              this,
              message,
              api.create("/publishing/uploads/" + id + "/retry", null, key.forInput("retry", id)),
              reply -> {
                key.confirmed();
                jobs.reload();
              });
        });
    jobView.add(retry, BorderLayout.SOUTH);
    JTabbedPane tabs = new JTabbedPane();
    tabs.addTab("Upload jobs", jobView);
    tabs.addTab("Audit", audit);
    tabs.addTab("Backup / Restore — hướng dẫn", new BackupRestoreGuidePanel());
    tabs.addChangeListener(
        e -> {
          if (api.signedIn()) {
            if (tabs.getSelectedIndex() == 0) jobs.reload();
            else if (tabs.getSelectedIndex() == 1) audit.reload();
          }
        });
    add(tabs);
    add(message, BorderLayout.SOUTH);
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
  }
}
