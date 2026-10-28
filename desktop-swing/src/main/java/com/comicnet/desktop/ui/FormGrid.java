package com.comicnet.desktop.ui;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public final class FormGrid extends JPanel {
  private int row;

  public FormGrid() {
    super(new GridBagLayout());
    setBackground(Color.WHITE);
    setBorder(new EmptyBorder(18, 18, 18, 18));
  }

  public void field(String caption, JComponent component) {
    JLabel label = Theme.plainLabel(caption);
    label.setLabelFor(component);
    component.getAccessibleContext().setAccessibleName(caption);
    var a = new GridBagConstraints();
    a.gridx = 0;
    a.gridy = row;
    a.anchor = GridBagConstraints.NORTHWEST;
    a.insets = new Insets(8, 0, 8, 16);
    add(label, a);
    var b = new GridBagConstraints();
    b.gridx = 1;
    b.gridy = row++;
    b.weightx = 1;
    b.fill = GridBagConstraints.HORIZONTAL;
    b.insets = new Insets(5, 0, 5, 0);
    add(component, b);
  }

  public void full(JComponent component) {
    var c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = row++;
    c.gridwidth = 2;
    c.weightx = 1;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.WEST;
    c.insets = new Insets(10, 0, 10, 0);
    add(component, c);
  }

  public static JTextArea text(int rows) {
    var area = new JTextArea(rows, 24);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    return area;
  }
}
