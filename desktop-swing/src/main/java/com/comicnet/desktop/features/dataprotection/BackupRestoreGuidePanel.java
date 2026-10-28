package com.comicnet.desktop.features.dataprotection;

import java.awt.BorderLayout;
import java.nio.charset.StandardCharsets;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JPanel;
import com.comicnet.desktop.ui.FormGrid;

/** Read-only operator guidance; backup and restore remain explicit SQL Server operations. */
public final class BackupRestoreGuidePanel extends JPanel {
  public BackupRestoreGuidePanel() {
    super(new BorderLayout());
    JTextArea guide = FormGrid.text(24);
    guide.setEditable(false);
    guide.setText(loadGuide());
    guide.setCaretPosition(0);
    add(new JScrollPane(guide), BorderLayout.CENTER);
  }

  private String loadGuide() {
    try (var source =
        BackupRestoreGuidePanel.class.getResourceAsStream("/operations/backup-restore-v1.txt")) {
      if (source == null) throw new java.io.IOException("Runbook missing.");
      return new String(source.readAllBytes(), StandardCharsets.UTF_8);
    } catch (java.io.IOException error) {
      return "Không đọc được runbook đóng gói. Kiểm tra lại bản phân phối.";
    }
  }
}
