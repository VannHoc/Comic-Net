package com.comicnet.desktop.features.catalog;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.ui.RemoteTable;
import java.awt.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import javax.swing.*;

public final class ComicPicker {
  private ComicPicker() {}

  public static void show(Component owner, ApiClient api, Consumer<String> chosen) {
    JDialog dialog =
        new JDialog(
            SwingUtilities.getWindowAncestor(owner),
            "Chọn truyện công khai",
            Dialog.ModalityType.APPLICATION_MODAL);
    JTextField query = new JTextField(24);
    RemoteTable table =
        new RemoteTable(
            api,
            new String[] {"Tên truyện", "Loại"},
            page ->
                (query.getText().isBlank()
                        ? "/comics?"
                        : "/search/comics?q="
                            + URLEncoder.encode(query.getText().strip(), StandardCharsets.UTF_8)
                            + "&")
                    + "page="
                    + page
                    + "&size=20",
            value -> new Object[] {value.path("title").asText(), value.path("type").asText()});
    JPanel top = new JPanel();
    JButton search = new JButton("Tìm"), select = new JButton("Chọn");
    top.add(query);
    top.add(search);
    top.add(select);
    search.addActionListener(e -> table.first());
    query.addActionListener(e -> table.first());
    select.addActionListener(
        e -> {
          if (table.selected() != null) {
            String id = table.selected().path("id").asText();
            dialog.dispose();
            chosen.accept(id);
          }
        });
    dialog.add(top, BorderLayout.NORTH);
    dialog.add(table);
    dialog.setSize(720, 480);
    dialog.setLocationRelativeTo(owner);
    dialog.addWindowListener(
        new java.awt.event.WindowAdapter() {
          @Override
          public void windowClosed(java.awt.event.WindowEvent e) {
            table.close();
          }
        });
    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    table.first();
    dialog.setVisible(true);
  }
}
