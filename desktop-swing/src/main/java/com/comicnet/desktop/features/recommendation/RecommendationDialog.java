package com.comicnet.desktop.features.recommendation;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.ui.RemoteTable;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;
import javax.swing.*;

public final class RecommendationDialog extends JDialog {
  public RecommendationDialog(Component owner, ApiClient api, String comic, Consumer<String> open) {
    super(
        SwingUtilities.getWindowAncestor(owner),
        comic == null ? "Gợi ý khám phá" : "Truyện tương tự",
        Dialog.ModalityType.APPLICATION_MODAL);
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    RemoteTable table =
        new RemoteTable(
            api,
            new String[] {"Truyện", "Loại"},
            page ->
                page == 0
                    ? (comic == null
                        ? "/recommendations?size=20"
                        : "/comics/" + comic + "/similar?size=20")
                    : null,
            item -> new Object[] {item.path("title").asText(), item.path("type").asText()});
    add(table);
    JButton select = new JButton("Mở truyện đã chọn");
    select.addActionListener(
        e -> {
          var item = table.selected();
          if (item != null) {
            String id = item.path("id").asText();
            dispose();
            open.accept(id);
          }
        });
    add(select, BorderLayout.SOUTH);
    getRootPane()
        .registerKeyboardAction(
            e -> dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosed(WindowEvent event) {
            table.close();
          }
        });
    setSize(760, 560);
    setLocationRelativeTo(owner);
    table.first();
  }
}
