package com.comicnet.desktop.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.DefaultTableCellRenderer;
import com.formdev.flatlaf.FlatLightLaf;

/**
 * Comic Net Design System V1.0 - Foundations & Tokens
 */
public final class Theme {
  // Brand & Action
  public static final Color ACTION = new Color(0xF6, 0x5D, 0x4E);       // #F65D4E
  public static final Color PRIMARY = ACTION;
  public static final Color ACTION_HOVER = new Color(0xE8, 0x4A, 0x3D); // #E84A3D
  public static final Color ACTION_TINT = new Color(0xFF, 0xF0, 0xED);  // #FFF0ED
  public static final Color ARTWORK_TEAL = ACTION;

  // Typography Inks
  public static final Color TEXT = new Color(0x17, 0x20, 0x33);         // #172033 - ink.primary
  public static final Color MUTED = new Color(0x66, 0x75, 0x8A);        // #66758A - ink.secondary
  public static final Color HINT = new Color(0x98, 0xA3, 0xB3);         // #98A3B3 - ink.muted

  // Backgrounds & Surfaces
  public static final Color BACKGROUND = new Color(0xF6, 0xF8, 0xFB);   // #F6F8FB - canvas
  public static final Color SURFACE = Color.WHITE;                      // #FFFFFF - surface
  public static final Color ELEVATED = new Color(0xF1, 0xF4, 0xF8);     // #F1F4F8 - elevated
  public static final Color BORDER = new Color(0xE6, 0xEA, 0xF0);       // #E6EAF0 - border

  // Status & Feedback
  public static final Color SUCCESS = new Color(0x1D, 0x9B, 0x75);      // #1D9B75
  public static final Color SUCCESS_TINT = new Color(0xDC, 0xFC, 0xE7); // #DCFCE7
  public static final Color WARNING = new Color(0xE6, 0xA3, 0x40);      // #E6A340
  public static final Color DANGER = new Color(0xDC, 0x4F, 0x52);       // #DC4F52
  public static final Color REWARD = new Color(0xF2, 0xB8, 0x4A);       // #F2B84A

  // Dimensions
  public static final int SIDEBAR_WIDTH = 228;
  public static final int TOPBAR_HEIGHT = 70;
  public static final int CONTENT_PADDING = 28;
  public static final int CARD_RADIUS = 16;
  public static final int CONTROL_RADIUS = 10;

  private static Font regular = new Font("Segoe UI", Font.PLAIN, 14);
  private static Font medium = new Font("Segoe UI", Font.PLAIN, 14);
  private static Font semiBold = new Font("Segoe UI", Font.BOLD, 14);
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
    prepareFonts();
    UIManager.setLookAndFeel(new FlatLightLaf());
    setPalette();
    var font = new FontUIResource(body.deriveFont(14f));
    for (Object key : UIManager.getDefaults().keySet().toArray()) {
      if (UIManager.get(key) instanceof FontUIResource) UIManager.put(key, font);
    }
  }

  private static void setPalette() {
    Color field = SURFACE;
    Color selected = ACTION_TINT;
    Color border = BORDER;

    // Modern Roundings
    UIManager.put("Button.arc", 10);
    UIManager.put("Component.arc", 10);
    UIManager.put("TextComponent.arc", 10);
    UIManager.put("ScrollBar.thumbArc", 999);
    UIManager.put("ScrollBar.width", 10);
    UIManager.put("ProgressBar.arc", 999);
    UIManager.put("TabbedPane.showTabSeparators", false);
    UIManager.put("Table.showHorizontalLines", true);
    UIManager.put("Table.intercellSpacing", new Dimension(0, 1));
    UIManager.put("Table.rowHeight", 34);
    UIManager.put("TableHeader.height", 36);

    UIManager.put("Panel.background", BACKGROUND);
    UIManager.put("Panel.foreground", TEXT);
    UIManager.put("Label.foreground", TEXT);

    UIManager.put("Button.background", ELEVATED);
    UIManager.put("Button.foreground", TEXT);
    UIManager.put("Button.disabledText", MUTED);

    UIManager.put("TextField.background", field);
    UIManager.put("TextField.foreground", TEXT);
    UIManager.put("TextField.caretForeground", TEXT);
    UIManager.put("TextField.inactiveForeground", MUTED);
    UIManager.put("TextField.inactiveBackground", ELEVATED);
    UIManager.put("TextField.selectionBackground", selected);
    UIManager.put("TextField.selectionForeground", TEXT);

    UIManager.put("TextArea.background", field);
    UIManager.put("TextArea.foreground", TEXT);
    UIManager.put("TextArea.caretForeground", TEXT);
    UIManager.put("TextArea.selectionBackground", selected);
    UIManager.put("TextArea.selectionForeground", TEXT);

    UIManager.put("PasswordField.background", field);
    UIManager.put("PasswordField.foreground", TEXT);
    UIManager.put("PasswordField.caretForeground", TEXT);

    UIManager.put("ComboBox.background", field);
    UIManager.put("ComboBox.foreground", TEXT);
    UIManager.put("ComboBox.selectionBackground", selected);
    UIManager.put("ComboBox.selectionForeground", TEXT);
    UIManager.put("ComboBox.disabledForeground", MUTED);
    UIManager.put("ComboBox.disabledBackground", ELEVATED);

    UIManager.put("PopupMenu.background", SURFACE);
    UIManager.put("PopupMenu.foreground", TEXT);
    UIManager.put("MenuItem.background", SURFACE);
    UIManager.put("MenuItem.foreground", TEXT);
    UIManager.put("MenuItem.selectionBackground", selected);
    UIManager.put("MenuItem.selectionForeground", ACTION);

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
    UIManager.put("TabbedPane.foreground", MUTED);
    UIManager.put("TabbedPane.selected", SURFACE);
    UIManager.put("TabbedPane.contentAreaColor", SURFACE);

    UIManager.put("ToolTip.background", TEXT);
    UIManager.put("ToolTip.foreground", Color.WHITE);
    UIManager.put("OptionPane.background", BACKGROUND);
    UIManager.put("OptionPane.messageForeground", TEXT);

    UIManager.put("ScrollBar.background", BACKGROUND);
    UIManager.put("ScrollBar.thumb", border);
    UIManager.put("ScrollBar.track", BACKGROUND);
    UIManager.put("Separator.foreground", border);
  }
}
