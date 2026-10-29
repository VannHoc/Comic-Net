package com.comicnet.desktop.ui;

import com.comicnet.desktop.client.api.ApiClient;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import tools.jackson.databind.JsonNode;

/** Reusable bounded server-paged table. It owns cancellation, never application permissions. */
public final class RemoteTable extends JPanel implements AutoCloseable {
  private final ApiClient api;
  private final IntFunction<String> path;
  private final Function<JsonNode, Object[]> columns;
  private final DefaultTableModel model;
  private final JTable table;
  private final List<JsonNode> rows = new ArrayList<>();
  private final UiTasks requests = new UiTasks();
  private final JLabel message = Theme.plainLabel(" ");
  private Consumer<JsonNode> selection = value -> {};
  private Consumer<JsonNode> loaded = value -> {};
  private int page;
  private final JButton previous = new JButton("Trước"), next = new JButton("Sau");

  public RemoteTable(
      ApiClient api,
      String[] headings,
      IntFunction<String> path,
      Function<JsonNode, Object[]> columns) {
    super(new BorderLayout(0, 8));
    this.api = api;
    this.path = path;
    this.columns = columns;
    setOpaque(false);
    model =
        new DefaultTableModel(headings, 0) {
          @Override
          public boolean isCellEditable(int row, int column) {
            return false;
          }
        };
    table = new JTable(model);
    table.setRowHeight(36);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    Theme.plainTable(table);
    add(new JScrollPane(table));
    JPanel footer = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 4));
    footer.setOpaque(false);
    JButton refresh = new JButton("Tải lại");
    footer.add(previous);
    footer.add(next);
    footer.add(refresh);
    footer.add(message);
    add(footer, BorderLayout.SOUTH);
    previous.addActionListener(
        e -> {
          page = Math.max(0, page - 1);
          reload();
        });
    next.addActionListener(
        e -> {
          page++;
          reload();
        });
    refresh.addActionListener(e -> reload());
    table
        .getSelectionModel()
        .addListSelectionListener(
            e -> {
              if (!e.getValueIsAdjusting()) selection.accept(selected());
            });
  }

  public void onSelection(Consumer<JsonNode> listener) {
    selection = listener;
  }

  public void onLoaded(Consumer<JsonNode> listener) {
    loaded = listener;
  }

  public JsonNode selected() {
    int row = table.getSelectedRow();
    return row >= 0 && row < rows.size() ? rows.get(row) : null;
  }

  public void refreshCells() {
    for (int row = 0; row < rows.size(); row++) {
      Object[] values = columns.apply(rows.get(row));
      for (int column = 0; column < values.length; column++)
        model.setValueAt(values[column], row, column);
    }
  }

  /** Update a returned entity without clearing selection and cancelling its detail request. */
  public void replaceRow(JsonNode replacement) {
    String id = replacement.path("id").asText();
    for (int row = 0; row < rows.size(); row++) {
      if (rows.get(row).path("id").asText().equals(id)) {
        rows.set(row, replacement);
        Object[] values = columns.apply(replacement);
        for (int column = 0; column < values.length; column++)
          model.setValueAt(values[column], row, column);
        return;
      }
    }
  }

  public void first() {
    page = 0;
    reload();
  }

  /** Select only after replacing the old rows, so selection-cleared listeners run first. */
  public void firstSelecting(String id) {
    page = 0;
    reload(id);
  }

  public void reload() {
    reload(null);
  }

  public void reloadSelecting(String id) {
    reload(id);
  }

  private void reload(String selectedId) {
    String url = path.apply(page);
    if (url == null) {
      clear();
      message.setText("Chọn đối tượng trước.");
      return;
    }
    requests.run(
        this,
        message,
        api.get(url),
        reply -> {
          model.setRowCount(0);
          rows.clear();
          for (JsonNode row : reply.body().path("items")) {
            rows.add(row);
            model.addRow(columns.apply(row));
          }
          previous.setEnabled(page > 0);
          long total = reply.body().path("page").path("totalElements").asLong(rows.size());
          int size = reply.body().path("page").path("size").asInt(20);
          next.setEnabled((long) (page + 1) * size < total);
          message.setText(
              rows.isEmpty() ? "Chưa có dữ liệu." : "Trang " + (page + 1) + " · " + total + " mục");
          if (selectedId != null) {
            for (int index = 0; index < rows.size(); index++) {
              if (rows.get(index).path("id").asText().equals(selectedId)) {
                table.setRowSelectionInterval(index, index);
                break;
              }
            }
          }
          loaded.accept(reply.body());
        });
  }

  public void clear() {
    requests.close();
    model.setRowCount(0);
    rows.clear();
    message.setText(" ");
    page = 0;
    selection.accept(null);
  }

  @Override
  public void close() {
    clear();
  }
}
