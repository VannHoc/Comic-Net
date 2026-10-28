package com.comicnet.desktop.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.DefaultTableCellRenderer;
import com.formdev.flatlaf.FlatLightLaf;

public final class Theme {
  public static final Color ACTION = new Color(0xF05A42);
  public static final Color ARTWORK_TEAL = ACTION;
  public static final Color TEXT = new Color(0x111827);
  public static final Color MUTED = new Color(0x64748B);
  public static final Color BACKGROUND = new Color(0xF8FAFC);
  public static final Color SURFACE = Color.WHITE;
  public static final Color ELEVATED = new Color(0xF1F5F9);
  public static final Color BORDER = new Color(0xE5E7EB);
  private static Font regular = new Font("Segoe UI", Font.PLAIN, 14),
      medium = regular,
      semiBold = regular.deriveFont(Font.BOLD);
  private static Font body = regular, heading = semiBold;

  private Theme() {}

  public static void prepareFonts() {
    String sample = "MỚI CẬP NHẬT KHÁM PHÁ THỂ LOẠI TRUYỆN DÀNH CHO BẠN";
    regular = new Font("Segoe UI", Font.PLAIN, 14);
    semiBold = new Font("Segoe UI", Font.BOLD, 14);
    if (regular.canDisplayUpTo(sample) >= 0) {
      regular = new Font(Font.DIALOG, Font.PLAIN, 14);
      semiBold = new Font(Font.DIALOG, Font.BOLD, 14);
    }
    body = regular;
    heading = semiBold;
  }

  public static Font heading(float size) {
    return heading.deriveFont(size);
  }

  public static Font label(float size) {
    return body.deriveFont(size);
  }

  public static Font brand(float size) {
    return semiBold.deriveFont(size);
  }

  public static JLabel plainLabel(String text) {
    JLabel label = new JLabel();
    label.putClientProperty("html.disable", Boolean.TRUE);
    label.addPropertyChangeListener("text", event -> label.setToolTipText(label.getText()));
    label.setText(text);
    return label;
  }

  public static void plainTable(JTable table) {
    var renderer = new DefaultTableCellRenderer();
    renderer.putClientProperty("html.disable", Boolean.TRUE);
    table.setDefaultRenderer(Object.class, renderer);
    table.setDefaultRenderer(String.class, renderer);
  }

  public static void plainCombo(JComboBox<?> combo) {
    var renderer = new DefaultListCellRenderer();
    renderer.putClientProperty("html.disable", Boolean.TRUE);
    combo.setRenderer(renderer);
  }

  public static void install() throws Exception {
    UIManager.setLookAndFeel(new FlatLightLaf());
    setPalette();
    var font = new FontUIResource(body.deriveFont(14f));
    for (Object key : UIManager.getDefaults().keySet().toArray()) {
      if (UIManager.get(key) instanceof FontUIResource) UIManager.put(key, font);
    }
  }

  private static void setPalette() {
    Color field = SURFACE;
    Color selected = new Color(0xFFE5DF);
    Color border = BORDER;
    UIManager.put("Panel.background", BACKGROUND);
    UIManager.put("TextField.inactiveForeground", MUTED);
    UIManager.put("TextField.inactiveBackground", ELEVATED);
    UIManager.put("ComboBox.selectionBackground", selected);
    UIManager.put("ComboBox.selectionForeground", TEXT);
    UIManager.put("Panel.foreground", TEXT);
    UIManager.put("Label.foreground", TEXT);
    UIManager.put("Button.background", ELEVATED);
    UIManager.put("Button.foreground", TEXT);
    UIManager.put("Button.disabledText", MUTED);
    UIManager.put("TextField.background", field);
    UIManager.put("TextField.foreground", TEXT);
    UIManager.put("TextField.caretForeground", TEXT);
    UIManager.put("TextArea.background", field);
    UIManager.put("TextArea.foreground", TEXT);
    UIManager.put("TextArea.caretForeground", TEXT);
    UIManager.put("PasswordField.background", field);
    UIManager.put("PasswordField.foreground", TEXT);
    UIManager.put("PasswordField.caretForeground", TEXT);
    UIManager.put("TextPane.background", field);
    UIManager.put("TextPane.foreground", TEXT);
    UIManager.put("TextField.selectionBackground", selected);
    UIManager.put("TextField.selectionForeground", TEXT);
    UIManager.put("TextArea.selectionBackground", selected);
    UIManager.put("TextArea.selectionForeground", TEXT);
    UIManager.put("ComboBox.background", field);
    UIManager.put("ComboBox.foreground", TEXT);
    UIManager.put("ComboBox.disabledForeground", MUTED);
    UIManager.put("ComboBox.disabledBackground", ELEVATED);
    UIManager.put("PopupMenu.background", SURFACE);
    UIManager.put("PopupMenu.foreground", TEXT);
    UIManager.put("MenuItem.background", SURFACE);
    UIManager.put("MenuItem.foreground", TEXT);
    UIManager.put("MenuItem.selectionBackground", selected);
    UIManager.put("MenuItem.selectionForeground", TEXT);
    UIManager.put("List.background", field);
    UIManager.put("List.foreground", TEXT);
    UIManager.put("List.selectionBackground", selected);
    UIManager.put("List.selectionForeground", TEXT);
    UIManager.put("Table.background", field);
    UIManager.put("Table.foreground", TEXT);
    UIManager.put("Table.selectionBackground", selected);
    UIManager.put("Table.selectionForeground", TEXT);
    UIManager.put("Table.gridColor", border);
    UIManager.put("Viewport.background", BACKGROUND);
    UIManager.put("ScrollPane.background", BACKGROUND);
    UIManager.put("TabbedPane.background", BACKGROUND);
    UIManager.put("TabbedPane.foreground", TEXT);
    UIManager.put("TabbedPane.selected", SURFACE);
    UIManager.put("TabbedPane.contentAreaColor", SURFACE);
    UIManager.put("ToolTip.background", ELEVATED);
    UIManager.put("ToolTip.foreground", TEXT);
    UIManager.put("OptionPane.background", BACKGROUND);
    UIManager.put("OptionPane.messageForeground", TEXT);
    UIManager.put("ScrollBar.background", BACKGROUND);
    UIManager.put("ScrollBar.thumb", border);
    UIManager.put("ScrollBar.track", BACKGROUND);
    UIManager.put("Separator.foreground", border);
  }

}
