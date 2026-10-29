package com.comicnet.desktop.features.recommendation;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.ui.*;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public final class RecommendationDialog extends JDialog {
  public RecommendationDialog(Component owner, ApiClient api, String comic, Consumer<String> open) {
    super(
        SwingUtilities.getWindowAncestor(owner),
        comic == null ? "Gợi ý khám phá" : "Truyện tương tự",
        Dialog.ModalityType.APPLICATION_MODAL);
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);

    JPanel contentPane = new JPanel(new BorderLayout(0, 12));
    contentPane.setBackground(Theme.BACKGROUND);
    contentPane.setBorder(new EmptyBorder(16, 18, 16, 18));

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
    contentPane.add(table, BorderLayout.CENTER);

    JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    footer.setOpaque(false);

    JButton close = new JButton("Đóng");
    close.setFont(Theme.label(13));
    close.addActionListener(e -> dispose());

    PrimaryButton select = new PrimaryButton("Mở truyện đã chọn");
    select.setIcon(UiIcons.bookOpen(14, Color.WHITE));
    select.addActionListener(
        e -> {
          var item = table.selected();
          if (item != null) {
            String id = item.path("id").asText();
            dispose();
            open.accept(id);
          }
        });

    footer.add(close);
    footer.add(select);
    contentPane.add(footer, BorderLayout.SOUTH);

    setContentPane(contentPane);
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
    setSize(800, 580);
    setMinimumSize(new Dimension(680, 480));
    setLocationRelativeTo(owner);
    table.first();
  }
}
