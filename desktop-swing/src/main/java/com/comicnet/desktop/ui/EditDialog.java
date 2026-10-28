package com.comicnet.desktop.ui;

import com.comicnet.desktop.client.api.ApiClient;
import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import javax.swing.*;

/** Small modal form with asynchronous submission and a lifetime-bound request. */
public final class EditDialog extends JDialog {
  public final FormGrid form = new FormGrid();
  public final JLabel message = Theme.plainLabel(" ");
  private final UiTasks tasks = new UiTasks();
  private final JButton save = new PrimaryButton("Lưu");

  public EditDialog(Component owner, String title) {
    super(SwingUtilities.getWindowAncestor(owner), title, Dialog.ModalityType.APPLICATION_MODAL);
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    add(new JScrollPane(form));
    JPanel footer = new JPanel(new BorderLayout());
    footer.add(message);
    JPanel buttons = new JPanel();
    buttons.add(save);
    JButton cancel = new JButton("Đóng");
    cancel.addActionListener(e -> dispose());
    buttons.add(cancel);
    footer.add(buttons, BorderLayout.SOUTH);
    add(footer, BorderLayout.SOUTH);
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosed(WindowEvent event) {
            tasks.close();
          }
        });
    setSize(720, 660);
    setLocationRelativeTo(owner);
    getRootPane().setDefaultButton(save);
  }

  public boolean busy() {
    return tasks.busy();
  }

  public void submit(
      Supplier<CompletableFuture<ApiClient.Reply>> request, Consumer<ApiClient.Reply> success) {
    save.addActionListener(
        e -> {
          if (tasks.busy()) return;
          try {
            tasks.run(
                form,
                message,
                request.get(),
                reply -> {
                  success.accept(reply);
                  dispose();
                });
          } catch (IllegalArgumentException error) {
            message.setText(error.getMessage());
          }
        });
  }

  public void operation(
      CompletableFuture<ApiClient.Reply> request, Consumer<ApiClient.Reply> success) {
    if (tasks.busy()) return;
    tasks.run(form, message, request, success);
  }

  public static JTextField text(String value) {
    return new JTextField(value, 24);
  }

  public static JComboBox<String> options(String value, String... values) {
    JComboBox<String> result = new JComboBox<>(values);
    if (value != null && !value.isBlank()) result.setSelectedItem(value);
    return result;
  }
}
