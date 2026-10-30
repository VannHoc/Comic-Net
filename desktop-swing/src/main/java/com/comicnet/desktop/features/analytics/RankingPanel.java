package com.comicnet.desktop.features.analytics;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.features.catalog.CatalogPanel.CoverArtCanvas;
import com.comicnet.desktop.ui.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import tools.jackson.databind.JsonNode;

/** Daily/weekly/monthly/trending ranking built from valid reads matching Mockup 03_Bang_xep_hang.png */
public final class RankingPanel extends JPanel implements Workspace {
  private static final List<String> PERIODS = List.of("DAILY", "WEEKLY", "MONTHLY", "TRENDING", "RATING");
  private static final String[] PERIOD_LABELS = {"Hôm nay", "7 ngày", "30 ngày", "Xu hướng", "Đánh giá cao"};

  private final ApiClient api;
  private final Consumer<String> openComic;
  private final UiTasks tasks = new UiTasks();
  private final JLabel message = Theme.plainLabel("Bảng xếp hạng dựa trên lượt đọc hợp lệ.");
  private int selectedPeriodIndex = 1; // Default: 7 ngày (WEEKLY)
  private final JButton[] periodButtons = new JButton[PERIOD_LABELS.length];

  // Components for Podium & Table
  private final JPanel contentCard = new JPanel(new BorderLayout(0, 16));
  private final JPanel podiumPanel = new JPanel(new GridLayout(1, 3, 16, 0));
  private final JPanel tableContainer = new JPanel(new BorderLayout(0, 10));
  private final DefaultTableModel tableModel =
      new DefaultTableModel(new Object[] {"HẠNG", "TRUYỆN", "THỂ LOẠI", "ĐÁNH GIÁ", "LƯỢT ĐỌC"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
          return false;
        }
      };
  private final JTable rankingTable = new JTable(tableModel);
  private final List<JsonNode> rankingRows = new ArrayList<>();
  private final JPanel emptyPanel = new JPanel(new BorderLayout(0, 12));

  public RankingPanel(ApiClient api, Consumer<String> openComic) {
    super(new BorderLayout(0, 16));
    this.api = api;
    this.openComic = openComic;
    setOpaque(false);
    setBorder(new EmptyBorder(Theme.CONTENT_PADDING, Theme.CONTENT_PADDING, 24, Theme.CONTENT_PADDING));

    // Header Section matching Mockup 03_Bang_xep_hang.png
    JPanel header = new JPanel(new BorderLayout(0, 4));
    header.setOpaque(false);

    JLabel eyebrow = Theme.plainLabel("BẢNG XẾP HẠNG");
    eyebrow.setForeground(Theme.PRIMARY);
    eyebrow.setFont(Theme.heading(11));

    JLabel heading = new JLabel("Truyện được yêu thích nhất tuần");
    heading.setFont(Theme.heading(28));
    heading.setForeground(Theme.TEXT);

    JLabel sub =
        Theme.plainLabel("Xếp hạng theo lượt đọc hợp lệ, độc lập với đánh giá sao.");
    sub.setFont(Theme.label(13));
    sub.setForeground(Theme.MUTED);

    JPanel titleBox = new JPanel(new GridLayout(0, 1, 0, 2));
    titleBox.setOpaque(false);
    titleBox.add(eyebrow);
    titleBox.add(heading);
    titleBox.add(sub);
    header.add(titleBox, BorderLayout.WEST);

    // Period Filter Pills Bar (Rounded)
    JPanel periodBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    periodBar.setOpaque(false);
    for (int i = 0; i < PERIOD_LABELS.length; i++) {
      int idx = i;
      JButton btn = new JButton(PERIOD_LABELS[i]);
      btn.setFont(Theme.brand(12));
      btn.setFocusPainted(false);
      btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      btn.addActionListener(e -> {
        selectedPeriodIndex = idx;
        updatePeriodButtons();
        if (idx == 0) heading.setText("Truyện được yêu thích hôm nay");
        else if (idx == 1) heading.setText("Truyện được yêu thích nhất tuần");
        else if (idx == 2) heading.setText("Truyện được yêu thích nhất tháng");
        else if (idx == 3) heading.setText("Bảng xếp hạng xu hướng đọc");
        else heading.setText("Truyện có đánh giá cao nhất");
        loadRankings();
      });
      periodButtons[i] = btn;
      periodBar.add(btn);
    }
    updatePeriodButtons();
    header.add(periodBar, BorderLayout.EAST);
    add(header, BorderLayout.NORTH);

    // Setup main body container
    contentCard.setOpaque(false);
    setupPodiumAndTable();
    setupEmptyPanel();

    add(contentCard, BorderLayout.CENTER);

    // Bottom Action Bar
    JPanel bottomBar = new JPanel(new BorderLayout());
    bottomBar.setOpaque(true);
    bottomBar.setBackground(Theme.SURFACE);
    bottomBar.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER, 1),
            new EmptyBorder(10, 16, 10, 16)));

    PrimaryButton openBtn = new PrimaryButton("Mở truyện đã chọn");
    openBtn.setIcon(UiIcons.bookOpen(14, Color.WHITE));
    openBtn.addActionListener(e -> {
      int row = rankingTable.getSelectedRow();
      if (row >= 0 && row < rankingRows.size()) {
        openComic.accept(rankingRows.get(row).path("comicId").asText());
      } else if (!rankingRows.isEmpty()) {
        openComic.accept(rankingRows.get(0).path("comicId").asText());
      }
    });

    JButton refreshBtn = new JButton("Làm mới");
    refreshBtn.setFont(Theme.label(13));
    refreshBtn.setIcon(UiIcons.refresh(14, Theme.TEXT));
    refreshBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    refreshBtn.addActionListener(e -> loadRankings());

    JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
    leftActions.setOpaque(false);
    leftActions.add(openBtn);
    leftActions.add(refreshBtn);

    message.setFont(Theme.label(12));
    message.setForeground(Theme.MUTED);

    bottomBar.add(leftActions, BorderLayout.WEST);
    bottomBar.add(message, BorderLayout.CENTER);
    add(bottomBar, BorderLayout.SOUTH);
  }

  private void updatePeriodButtons() {
    for (int i = 0; i < periodButtons.length; i++) {
      boolean active = (i == selectedPeriodIndex);
      JButton btn = periodButtons[i];
      if (i == 4) {
        btn.setIcon(UiIcons.star(12, active ? Color.WHITE : new Color(0xF5, 0x9E, 0x0B)));
      }
      if (active) {
        btn.setBackground(Theme.ACTION);
        btn.setForeground(Color.WHITE);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.ACTION, 1),
            new EmptyBorder(6, 14, 6, 14)));
      } else {
        btn.setBackground(Theme.SURFACE);
        btn.setForeground(Theme.TEXT);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER, 1),
            new EmptyBorder(6, 14, 6, 14)));
      }
    }
  }

  private void setupPodiumAndTable() {
    podiumPanel.setOpaque(false);

    // Modern flat table matching Mockup 03_Bang_xep_hang.png
    rankingTable.setRowHeight(44);
    rankingTable.setFont(Theme.label(13));
    rankingTable.setForeground(Theme.TEXT);
    rankingTable.setBackground(Theme.SURFACE);
    rankingTable.setSelectionBackground(new Color(0xFF, 0xF1, 0xEE));
    rankingTable.setSelectionForeground(Theme.TEXT);
    rankingTable.setShowHorizontalLines(true);
    rankingTable.setShowVerticalLines(false);
    rankingTable.setGridColor(Theme.BORDER);
    if (rankingTable.getTableHeader() != null) {
      rankingTable.getTableHeader().setFont(Theme.brand(11));
      rankingTable.getTableHeader().setBackground(new Color(0xF8, 0xFA, 0xFC));
      rankingTable.getTableHeader().setForeground(Theme.MUTED);
      rankingTable.getTableHeader().setPreferredSize(new Dimension(100, 34));
    }

    setupTableColumns();

    rankingTable.addMouseListener(new MouseAdapter() {
      @Override public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() == 2) {
          int row = rankingTable.getSelectedRow();
          if (row >= 0 && row < rankingRows.size()) {
            openComic.accept(rankingRows.get(row).path("comicId").asText());
          }
        }
      }
    });

    JPanel tableHeader = new JPanel(new BorderLayout());
    tableHeader.setOpaque(false);
    JLabel tTitle = new JLabel("Danh sách xếp hạng");
    tTitle.setFont(Theme.heading(16));
    tTitle.setForeground(Theme.TEXT);
    JLabel tSub = Theme.plainLabel("Chỉ tính lượt đọc hợp lệ trong 7 ngày gần nhất");
    tSub.setFont(Theme.label(12));
    tSub.setForeground(Theme.MUTED);
    tableHeader.add(tTitle, BorderLayout.WEST);
    tableHeader.add(tSub, BorderLayout.EAST);

    tableContainer.setOpaque(true);
    tableContainer.setBackground(Theme.SURFACE);
    tableContainer.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER, 1),
            new EmptyBorder(14, 16, 14, 16)));
    tableContainer.add(tableHeader, BorderLayout.NORTH);

    JScrollPane tableScroll = new JScrollPane(rankingTable);
    tableScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));
    tableContainer.add(tableScroll, BorderLayout.CENTER);
  }

  private void setupTableColumns() {
    rankingTable.getColumnModel().getColumn(0).setPreferredWidth(80);
    rankingTable.getColumnModel().getColumn(0).setMaxWidth(100);
    rankingTable.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
      @Override public Component getTableCellRendererComponent(
          JTable t, Object val, boolean isSel, boolean hasFoc, int row, int col) {
        JLabel l = (JLabel) super.getTableCellRendererComponent(t, val, isSel, hasFoc, row, col);
        l.setFont(Theme.heading(13));
        l.setForeground(new Color(0x64, 0x74, 0x8B));
        l.setHorizontalAlignment(SwingConstants.CENTER);
        return l;
      }
    });

    rankingTable.getColumnModel().getColumn(1).setPreferredWidth(340);
    rankingTable.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
      @Override public Component getTableCellRendererComponent(
          JTable t, Object val, boolean isSel, boolean hasFoc, int row, int col) {
        JLabel l = (JLabel) super.getTableCellRendererComponent(t, val, isSel, hasFoc, row, col);
        l.setFont(Theme.heading(13));
        l.setForeground(Theme.TEXT);
        l.setBorder(new EmptyBorder(0, 12, 0, 0));
        return l;
      }
    });

    rankingTable.getColumnModel().getColumn(2).setPreferredWidth(160);
    rankingTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
      @Override public Component getTableCellRendererComponent(
          JTable t, Object val, boolean isSel, boolean hasFoc, int row, int col) {
        JLabel l = (JLabel) super.getTableCellRendererComponent(t, val, isSel, hasFoc, row, col);
        l.setFont(Theme.label(12));
        l.setForeground(Theme.MUTED);
        return l;
      }
    });

    rankingTable.getColumnModel().getColumn(3).setPreferredWidth(120);
    rankingTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
      @Override public Component getTableCellRendererComponent(
          JTable t, Object val, boolean isSel, boolean hasFoc, int row, int col) {
        JLabel l = (JLabel) super.getTableCellRendererComponent(t, val, isSel, hasFoc, row, col);
        l.setIcon(UiIcons.star(12, new Color(0xF5, 0x9E, 0x0B)));
        l.setIconTextGap(4);
        l.setFont(Theme.heading(12));
        l.setForeground(new Color(0xD9, 0x77, 0x06));
        l.setHorizontalAlignment(SwingConstants.CENTER);
        return l;
      }
    });

    rankingTable.getColumnModel().getColumn(4).setPreferredWidth(140);
    rankingTable.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
      @Override public Component getTableCellRendererComponent(
          JTable t, Object val, boolean isSel, boolean hasFoc, int row, int col) {
        JLabel l = (JLabel) super.getTableCellRendererComponent(t, val, isSel, hasFoc, row, col);
        l.setFont(Theme.heading(13));
        l.setForeground(Theme.TEXT);
        l.setHorizontalAlignment(SwingConstants.CENTER);
        return l;
      }
    });
  }

  private void setupEmptyPanel() {
    emptyPanel.setOpaque(true);
    emptyPanel.setBackground(Theme.SURFACE);
    emptyPanel.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Theme.BORDER, 1),
            new EmptyBorder(50, 20, 50, 20)));

    JLabel emptyIcon = new JLabel(UiIcons.barChart(54, new Color(0x94, 0xA3, 0xB8)));
    emptyIcon.setHorizontalAlignment(SwingConstants.CENTER);

    JPanel textBox = new JPanel(new GridLayout(2, 1, 0, 6));
    textBox.setOpaque(false);
    JLabel emptyHeading = Theme.plainLabel("Chưa có lượt đọc hợp lệ trong khoảng thời gian này");
    emptyHeading.setFont(Theme.heading(16));
    emptyHeading.setForeground(Theme.TEXT);
    emptyHeading.setHorizontalAlignment(SwingConstants.CENTER);

    JLabel emptySub = Theme.plainLabel("Đọc truyện ngay để ghi nhận lượt đọc hợp lệ và đưa tác phẩm lên Bảng xếp hạng Comic Net!");
    emptySub.setFont(Theme.label(13));
    emptySub.setForeground(Theme.MUTED);
    emptySub.setHorizontalAlignment(SwingConstants.CENTER);

    textBox.add(emptyHeading);
    textBox.add(emptySub);

    emptyPanel.add(emptyIcon, BorderLayout.NORTH);
    emptyPanel.add(textBox, BorderLayout.CENTER);
  }

  private void loadRankings() {
    String period = PERIODS.get(Math.min(selectedPeriodIndex, PERIODS.size() - 1));

    tasks.run(
        this,
        message,
        api.get("/rankings?period=" + period + "&size=20"),
        reply -> {
          rankingRows.clear();
          tableModel.setRowCount(0);

          if (reply.body() != null && reply.body().has("items")) {
            for (JsonNode item : reply.body().path("items")) {
              rankingRows.add(item);
            }
          }

          contentCard.removeAll();

          if (rankingRows.isEmpty()) {
            // Empty state when backend has no valid read records
            contentCard.add(emptyPanel, BorderLayout.CENTER);
            message.setText("Không có dữ liệu xếp hạng trong khoảng thời gian đã chọn.");
          } else {
            // Render Podium (Top 3) & Table (Top 4+)
            renderPodium();
            renderTableRows();

            JPanel bodySplit = new JPanel(new BorderLayout(0, 16));
            bodySplit.setOpaque(false);
            if (rankingRows.size() >= 1) {
              bodySplit.add(podiumPanel, BorderLayout.NORTH);
            }
            bodySplit.add(tableContainer, BorderLayout.CENTER);
            contentCard.add(bodySplit, BorderLayout.CENTER);
            message.setText("Đã tải " + rankingRows.size() + " truyện trong bảng xếp hạng.");
          }

          contentCard.revalidate();
          contentCard.repaint();
        });
  }

  // Render Top 3 Podium matching Mockup 03_Bang_xep_hang.png
  private void renderPodium() {
    podiumPanel.removeAll();

    JsonNode top1 = rankingRows.size() > 0 ? rankingRows.get(0) : null;
    JsonNode top2 = rankingRows.size() > 1 ? rankingRows.get(1) : null;
    JsonNode top3 = rankingRows.size() > 2 ? rankingRows.get(2) : null;

    // Podium order in Mockup: [TOP 2 (left)] [TOP 1 (center, amber border)] [TOP 3 (right)]
    podiumPanel.add(buildPodiumCard(top2, 2));
    podiumPanel.add(buildPodiumCard(top1, 1));
    podiumPanel.add(buildPodiumCard(top3, 3));

    podiumPanel.revalidate();
    podiumPanel.repaint();
  }

  private JPanel buildPodiumCard(JsonNode comic, int rank) {
    boolean isTop1 = (rank == 1);
    JPanel card = new JPanel(new BorderLayout(14, 0)) {
      @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        g2.setColor(Color.WHITE);
        g2.fillRoundRect(0, 0, w - 1, h - 1, 14, 14);

        if (isTop1) {
          g2.setColor(new Color(0xF5, 0x9E, 0x0B)); // Amber gold border for TOP 1
          g2.setStroke(new BasicStroke(1.5f));
        } else {
          g2.setColor(Theme.BORDER);
          g2.setStroke(new BasicStroke(1.0f));
        }
        g2.drawRoundRect(0, 0, w - 1, h - 1, 14, 14);
        g2.dispose();
        super.paintComponent(g);
      }
    };
    card.setOpaque(false);
    card.setBorder(new EmptyBorder(14, 14, 14, 14));
    card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    if (comic == null) {
      JLabel emptyLbl = Theme.plainLabel("Vị trí #" + rank + " đang trống");
      emptyLbl.setFont(Theme.label(12));
      emptyLbl.setForeground(Theme.MUTED);
      emptyLbl.setHorizontalAlignment(SwingConstants.CENTER);
      card.add(emptyLbl, BorderLayout.CENTER);
      return card;
    }

    String cId = comic.path("comicId").asText();
    String title = comic.path("title").asText("Truyện");
    long views = comic.path("views").asLong(0);
    double rating = comic.path("rating").asDouble(4.8);

    // Cover art left
    CoverArtCanvas cover = new CoverArtCanvas(10);
    cover.setPreferredSize(new Dimension(105, 150));
    cover.setMetadata(title, "MANGA");

    JPanel coverWrap = new JPanel(new BorderLayout());
    coverWrap.setOpaque(false);
    coverWrap.add(cover, BorderLayout.CENTER);

    // Rank label top-left of cover
    String badgeText = isTop1 ? "TOP #01" : ("#0" + rank);
    JLabel badge = new JLabel("  " + badgeText + "  ") {
      @Override protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(0, 0, 0, 140));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
        g2.dispose();
        super.paintComponent(g);
      }
    };
    badge.setFont(Theme.brand(9));
    badge.setForeground(Color.WHITE);
    badge.setOpaque(false);

    JPanel chipWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
    chipWrap.setOpaque(false);
    chipWrap.add(badge);
    coverWrap.add(chipWrap, BorderLayout.NORTH);
    card.add(coverWrap, BorderLayout.WEST);

    // Info right
    JPanel info = new JPanel();
    info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
    info.setOpaque(false);

    // Rank vector icon/badge (graphics2D vector, no emoji square artifact)
    if (isTop1) {
      JLabel crown = new JLabel(UiIcons.crown(22, new Color(0xF5, 0x9E, 0x0B)));
      info.add(crown);
    } else {
      JLabel rankCircle = new JLabel(String.valueOf(rank)) {
        @Override protected void paintComponent(Graphics g) {
          Graphics2D g2 = (Graphics2D) g.create();
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          g2.setColor(new Color(0xEE, 0xF2, 0xF6));
          g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
          g2.setColor(new Color(0x94, 0xA3, 0xB8));
          g2.drawOval(0, 0, getWidth() - 1, getHeight() - 1);
          g2.dispose();
          super.paintComponent(g);
        }
      };
      rankCircle.setPreferredSize(new Dimension(24, 24));
      rankCircle.setMaximumSize(new Dimension(24, 24));
      rankCircle.setHorizontalAlignment(SwingConstants.CENTER);
      rankCircle.setFont(Theme.heading(12));
      rankCircle.setForeground(new Color(0x47, 0x55, 0x69));
      info.add(rankCircle);
    }
    info.add(Box.createVerticalStrut(6));

    JLabel tLbl = Theme.plainLabel(title);
    tLbl.setFont(Theme.heading(isTop1 ? 16 : 15));
    tLbl.setForeground(Theme.TEXT);
    info.add(tLbl);
    info.add(Box.createVerticalStrut(4));

    JLabel genreLbl = Theme.plainLabel("Phiêu lưu · Manga");
    genreLbl.setFont(Theme.label(12));
    genreLbl.setForeground(Theme.MUTED);
    info.add(genreLbl);
    info.add(Box.createVerticalStrut(8));

    JLabel viewsLbl = Theme.plainLabel(String.format(Locale.ROOT, "%,d lượt đọc", views));
    viewsLbl.setFont(Theme.heading(13));
    viewsLbl.setForeground(Theme.TEXT);
    info.add(viewsLbl);
    info.add(Box.createVerticalStrut(4));

    JLabel starLbl = new JLabel(String.format(Locale.ROOT, "%.1f / 5", rating), UiIcons.star(12, new Color(0xF5, 0x9E, 0x0B)), SwingConstants.LEFT);
    starLbl.setIconTextGap(4);
    starLbl.setFont(Theme.heading(12));
    starLbl.setForeground(new Color(0xD9, 0x77, 0x06));
    info.add(starLbl);

    card.add(info, BorderLayout.CENTER);

    card.addMouseListener(new MouseAdapter() {
      @Override public void mouseClicked(MouseEvent e) {
        openComic.accept(cId);
      }
    });

    return card;
  }

  private void renderTableRows() {
    tableModel.setRowCount(0);
    for (int i = 0; i < rankingRows.size(); i++) {
      JsonNode row = rankingRows.get(i);
      int rank = row.path("rank").asInt(i + 1);
      String title = row.path("title").asText("Truyện");
      long views = row.path("views").asLong(0);
      double rating = row.path("rating").asDouble(4.5);
      tableModel.addRow(new Object[] {
          "#" + rank,
          title,
          "Hành động",
          String.format(Locale.ROOT, "%.1f", rating),
          String.format(Locale.ROOT, "%,d", views)
      });
    }
  }

  @Override
  public void onShown() {
    loadRankings();
  }

  @Override
  public void sessionChanged() {
    rankingRows.clear();
    tableModel.setRowCount(0);
  }

  @Override
  public void close() {
    tasks.close();
  }
}
