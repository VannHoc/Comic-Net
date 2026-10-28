package com.comicnet.desktop.features.analytics;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.ui.*;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** Daily/weekly/monthly/trending ranking built from valid reads (M07-F02/F03). */
public final class RankingPanel extends JPanel implements Workspace {
  private static final List<String> PERIODS = List.of("DAILY", "WEEKLY", "MONTHLY", "TRENDING");

  private final ApiClient api;
  private final Consumer<String> openComic;
  private final JLabel message = Theme.plainLabel("Bảng xếp hạng dựa trên lượt đọc hợp lệ.");
  private final JComboBox<String> period =
      new JComboBox<>(new String[] {"Hôm nay", "7 ngày", "30 ngày", "Xu hướng"});
  private final RemoteTable table;

  public RankingPanel(ApiClient api, Consumer<String> openComic) {
    super(new BorderLayout(0, 14));
    this.api = api;
    this.openComic = openComic;
    setOpaque(false);
    setBorder(new EmptyBorder(26, 30, 20, 30));
    JLabel heading = new JLabel("Bảng xếp hạng");
    heading.setFont(Theme.heading(28));
    add(heading, BorderLayout.NORTH);
    table =
        new RemoteTable(
            api,
            new String[] {"Hạng", "Truyện", "Lượt đọc"},
            p ->
                p == 0
                    ? "/rankings?period=" + PERIODS.get(period.getSelectedIndex()) + "&size=20"
                    : null,
            v ->
                new Object[] {
                  v.path("rank").asInt(), v.path("title").asText(), v.path("views").asLong()
                });
    JPanel body = new JPanel(new BorderLayout(0, 10));
    body.setOpaque(false);
    body.add(table);
    JPanel actions = new JPanel(new WrapLayout(FlowLayout.LEFT, 7, 4));
    actions.setOpaque(false);
    actions.add(new JLabel("Khoảng thời gian"));
    actions.add(period);
    JButton open = new JButton("Mở truyện");
    open.addActionListener(
        e -> {
          var selected = table.selected();
          if (selected != null) openComic.accept(selected.path("comicId").asText());
        });
    JButton refresh = new JButton("Làm mới");
    refresh.addActionListener(e -> table.first());
    actions.add(open);
    actions.add(refresh);
    body.add(actions, BorderLayout.SOUTH);
    add(body);
    add(message, BorderLayout.SOUTH);
    period.addActionListener(e -> table.first());
  }

  @Override
  public void onShown() {
    table.first();
  }

  @Override
  public void sessionChanged() {
    table.clear();
  }

  @Override
  public void close() {
    table.close();
  }
}
