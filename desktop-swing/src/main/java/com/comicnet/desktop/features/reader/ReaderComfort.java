package com.comicnet.desktop.features.reader;

import java.awt.*;
import java.awt.event.*;
import java.util.function.BooleanSupplier;
import javax.swing.*;

/** Session-local presentation controls; never modifies image assets or system display settings. */
final class ReaderComfort implements AutoCloseable {
  private final JPanel reader;
  private final JScrollPane viewport;
  private final BooleanSupplier canScroll;
  private final JToggleButton auto = new JToggleButton("Tự cuộn");
  private final JSlider speed = new JSlider(10, 200, 40);
  private final JSlider brightness = new JSlider(20, 100, 100);
  private final Timer timer;
  private long previousTick;
  private double remainder;
  private JDialog fullscreen;
  private Container parent;
  private int originalIndex;
  private Object constraint;

  ReaderComfort(JPanel reader, JScrollPane viewport, BooleanSupplier canScroll) {
    this.reader = reader;
    this.viewport = viewport;
    this.canScroll = canScroll;
    timer = new Timer(40, e -> tick(System.nanoTime()));
    speed.setPreferredSize(new Dimension(100, 28));
    brightness.setPreferredSize(new Dimension(100, 28));
    speed.setToolTipText("Tốc độ tự cuộn: 10–200 pixel/giây");
    brightness.setToolTipText("Độ sáng ảnh: 20–100%; không đổi độ sáng màn hình");
    speed.getAccessibleContext().setAccessibleName("Tốc độ tự cuộn");
    brightness.getAccessibleContext().setAccessibleName("Độ sáng ảnh");
    brightness.addChangeListener(e -> viewport.repaint());
    auto.addActionListener(
        e -> {
          if (auto.isSelected() && canScroll.getAsBoolean()) {
            previousTick = System.nanoTime();
            remainder = 0;
            timer.start();
          } else pause();
        });
    viewport.addMouseWheelListener(e -> pause());
    bind(reader, KeyEvent.VK_F11, this::toggleFullscreen);
  }

  JComponent[] controls() {
    JButton full = new JButton("Toàn màn hình (F11)");
    full.addActionListener(e -> toggleFullscreen());
    return new JComponent[] {
      full, auto, new JLabel("Tốc độ"), speed, new JLabel("Độ sáng"), brightness
    };
  }

  void tick(long now) {
    Window window = SwingUtilities.getWindowAncestor(reader);
    if (!canScroll.getAsBoolean() || (window != null && !window.isFocused())) {
      pause();
      return;
    }
    // Bound elapsed time after an EDT stall instead of jumping past unread pages.
    double elapsed = Math.max(0, Math.min(.1, (now - previousTick) / 1_000_000_000.0));
    previousTick = now;
    remainder += speed.getValue() * elapsed;
    int pixels = (int) remainder;
    remainder -= pixels;
    JScrollBar bar = viewport.getVerticalScrollBar();
    int end = Math.max(bar.getMinimum(), bar.getMaximum() - bar.getVisibleAmount());
    bar.setValue(Math.min(end, bar.getValue() + pixels));
    if (bar.getValue() >= end) pause();
  }

  void paintShade(Graphics graphics, int x, int width, int height) {
    if (brightness.getValue() == 100) return;
    Graphics2D copy = (Graphics2D) graphics.create();
    try {
      copy.setColor(new Color(0, 0, 0, (100 - brightness.getValue()) * 255 / 100));
      copy.fillRect(x, 0, width, height);
    } finally {
      copy.dispose();
    }
  }

  void pause() {
    timer.stop();
    auto.setSelected(false);
    remainder = 0;
  }

  private static void bind(JComponent component, int key, Runnable action) {
    String name = "reader-comfort-" + key;
    component
        .getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        .put(KeyStroke.getKeyStroke(key, 0), name);
    component
        .getActionMap()
        .put(
            name,
            new AbstractAction() {
              @Override
              public void actionPerformed(ActionEvent event) {
                action.run();
              }
            });
  }

  private void toggleFullscreen() {
    if (fullscreen != null) {
      exitFullscreen();
      return;
    }
    Window owner = SwingUtilities.getWindowAncestor(reader);
    if (owner == null || GraphicsEnvironment.isHeadless()) return;
    pause();
    parent = reader.getParent();
    originalIndex = parent.getComponentZOrder(reader);
    constraint =
        parent.getLayout() instanceof BorderLayout layout ? layout.getConstraints(reader) : null;
    fullscreen = new JDialog(owner);
    fullscreen.setUndecorated(true);
    fullscreen.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    fullscreen.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent event) {
            exitFullscreen();
          }
        });
    fullscreen.setBounds(owner.getGraphicsConfiguration().getBounds());
    fullscreen.add(reader);
    bind(fullscreen.getRootPane(), KeyEvent.VK_ESCAPE, this::exitFullscreen);
    parent.revalidate();
    parent.repaint();
    fullscreen.setVisible(true);
    viewport.requestFocusInWindow();
  }

  private void exitFullscreen() {
    if (fullscreen == null) return;
    pause();
    JDialog dialog = fullscreen;
    fullscreen = null;
    dialog.remove(reader);
    parent.add(reader, constraint, Math.min(originalIndex, parent.getComponentCount()));
    parent.revalidate();
    parent.repaint();
    dialog.dispose();
    viewport.requestFocusInWindow();
  }

  @Override
  public void close() {
    pause();
    exitFullscreen();
  }
}
