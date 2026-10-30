package com.comicnet.desktop.features.publishing;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.features.catalog.CatalogPanel.CoverArtCanvas;
import com.comicnet.desktop.ui.Theme;
import com.comicnet.desktop.ui.UiIcons;
import com.comicnet.desktop.ui.Workspace;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import tools.jackson.databind.JsonNode;

public final class AdminReviewQueuePanel extends JPanel implements Workspace {
  private final ApiClient api;
  private final CardLayout cardLayout = new CardLayout();
  private final JPanel cardHost = new JPanel(cardLayout);

  // Queue View (Mockup 06)
  private final JPanel queueView = new JPanel(new BorderLayout(0, 16));
  private final JLabel kpiPending = new JLabel("02");
  private final JLabel kpiApproved = new JLabel("06");
  private final JLabel kpiNeedsChanges = new JLabel("01");
  private final JTextField searchField = new JTextField(20);
  private final DefaultTableModel queueModel = new DefaultTableModel(
      new Object[] {"TRUYỆN", "NGƯỜI GỬI", "NỘI DUNG", "TRẠNG THÁI", "HÀNH ĐỘNG"}, 0) {
    @Override public boolean isCellEditable(int r, int c) { return c == 4; }
  };
  private final JTable queueTable = new JTable(queueModel);
  private final List<JsonNode> queueItems = new ArrayList<>();

  // Detail View (Mockup 07)
  private final JPanel detailView = new JPanel(new BorderLayout(0, 16));
  private final CoverArtCanvas detailCover = new CoverArtCanvas(8);
  private final JLabel detailTitle = new JLabel("Tiêu đề truyện");
  private final JLabel detailCreator = Theme.plainLabel("Người gửi: —");
  private final JLabel detailTags = Theme.plainLabel("Manga · Tiếng Việt");
  private final JTextArea detailDesc = new JTextArea(3, 30);
  private final DefaultTableModel chapterModel = new DefaultTableModel(
      new Object[] {"CHƯƠNG", "SỐ TRANG", "QUYỀN ĐỌC", "TRẠNG THÁI", "XEM TRƯỚC"}, 0) {
    @Override public boolean isCellEditable(int r, int c) { return c == 4; }
  };
  private final JTable chapterTable = new JTable(chapterModel);
  private final JLabel creatorNoteLabel = new JLabel("Không có ghi chú.");
  private JsonNode activeVersion;
  private String activeVersionEtag;
  private String activeChapterId;
  private String activeComicTitle;

  public AdminReviewQueuePanel(ApiClient api) {
    super(new BorderLayout());
    this.api = api;
    setOpaque(false);
    setBorder(new EmptyBorder(Theme.CONTENT_PADDING, Theme.CONTENT_PADDING, 24, Theme.CONTENT_PADDING));

    buildQueueView();
    buildDetailView();

    cardHost.setOpaque(false);
    cardHost.add(queueView, "QUEUE");
    cardHost.add(detailView, "DETAIL");
    add(cardHost, BorderLayout.CENTER);
  }

  private void buildQueueView() {
    queueView.setOpaque(false);

    // Header
    JPanel header = new JPanel(new BorderLayout(0, 6));
    header.setOpaque(false);

    JLabel eyebrow = Theme.plainLabel("KIỂM DUYỆT XUẤT BẢN");
    eyebrow.setFont(Theme.heading(11));
    eyebrow.setForeground(Theme.PRIMARY);

    JLabel heading = new JLabel("Hàng chờ duyệt");
    heading.setFont(Theme.heading(28));
    heading.setForeground(Theme.TEXT);

    JLabel sub = Theme.plainLabel("Tất cả yêu cầu Creator gửi lên. Chỉ Admin có quyền xử lý.");
    sub.setFont(Theme.label(13));
    sub.setForeground(Theme.MUTED);

    JPanel titleBox = new JPanel(new GridLayout(0, 1, 0, 2));
    titleBox.setOpaque(false);
    titleBox.add(eyebrow);
    titleBox.add(heading);
    titleBox.add(sub);
    header.add(titleBox, BorderLayout.WEST);

    JButton refreshBtn = new JButton(" Làm mới", UiIcons.refresh(14, Theme.MUTED));
    refreshBtn.setFont(Theme.label(13));
    refreshBtn.setBackground(Theme.SURFACE);
    refreshBtn.setFocusPainted(false);
    refreshBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    refreshBtn.addActionListener(e -> loadQueue());
    header.add(refreshBtn, BorderLayout.EAST);

    // KPI Cards
    JPanel kpiBar = new JPanel(new GridLayout(1, 3, 16, 0));
    kpiBar.setOpaque(false);
    kpiBar.setPreferredSize(new Dimension(0, 80));

    kpiBar.add(buildKpiCard(kpiPending, "Yêu cầu đang chờ", new Color(0xD9, 0x77, 0x06)));
    kpiBar.add(buildKpiCard(kpiApproved, "Đã duyệt tuần này", new Color(0x16, 0xA3, 0x4A)));
    kpiBar.add(buildKpiCard(kpiNeedsChanges, "Yêu cầu cần sửa", new Color(0x25, 0x63, 0xEB)));

    JPanel topSection = new JPanel(new BorderLayout(0, 16));
    topSection.setOpaque(false);
    topSection.add(header, BorderLayout.NORTH);
    topSection.add(kpiBar, BorderLayout.SOUTH);
    queueView.add(topSection, BorderLayout.NORTH);

    // Center Table & Toolbar
    JPanel centerSection = new JPanel(new BorderLayout(0, 12));
    centerSection.setBackground(Theme.SURFACE);
    centerSection.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER, 1, true),
        new EmptyBorder(14, 16, 14, 16)));

    // Toolbar
    JPanel toolbar = new JPanel(new BorderLayout(12, 0));
    toolbar.setOpaque(false);

    JPanel searchPill = new JPanel(new BorderLayout(8, 0));
    searchPill.setBackground(Theme.ELEVATED);
    searchPill.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER, 1, true),
        new EmptyBorder(4, 10, 4, 10)));
    searchPill.add(new JLabel(UiIcons.search(14, Theme.MUTED)), BorderLayout.WEST);
    searchField.setBorder(null);
    searchField.setOpaque(false);
    searchField.setFont(Theme.label(13));
    searchPill.add(searchField, BorderLayout.CENTER);
    toolbar.add(searchPill, BorderLayout.WEST);

    JLabel countInfo = Theme.plainLabel("Yêu cầu đang chờ duyệt");
    countInfo.setFont(Theme.heading(13));
    countInfo.setForeground(Theme.TEXT);
    toolbar.add(countInfo, BorderLayout.EAST);
    centerSection.add(toolbar, BorderLayout.NORTH);

    // Table
    queueTable.setRowHeight(48);
    queueTable.setFont(Theme.label(13));
    queueTable.getTableHeader().setFont(Theme.heading(12));
    queueTable.getTableHeader().setBackground(Theme.BACKGROUND);
    queueTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    queueTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int row, int col) {
        JLabel l = (JLabel) super.getTableCellRendererComponent(t, val, sel, foc, row, col);
        l.setText("● Chờ duyệt");
        l.setForeground(new Color(0xD9, 0x77, 0x06));
        return l;
      }
    });

    // Action button renderer & editor
    queueTable.getColumnModel().getColumn(4).setCellRenderer((t, val, sel, foc, row, col) -> {
      JButton btn = new JButton("👁 Xem & duyệt");
      btn.setFont(Theme.heading(12));
      btn.setBackground(Theme.ACTION);
      btn.setForeground(Color.WHITE);
      btn.setFocusPainted(false);
      btn.setBorderPainted(false);
      return btn;
    });

    queueTable.getColumnModel().getColumn(4).setCellEditor(new DefaultCellEditor(new JCheckBox()) {
      private JButton btn;
      private int selectedRow;

      @Override
      public Component getTableCellEditorComponent(JTable t, Object val, boolean sel, int row, int col) {
        selectedRow = row;
        btn = new JButton("👁 Xem & duyệt");
        btn.setFont(Theme.heading(12));
        btn.setBackground(Theme.ACTION);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.addActionListener(e -> {
          fireEditingStopped();
          if (selectedRow >= 0 && selectedRow < queueItems.size()) {
            openReviewDetail(queueItems.get(selectedRow));
          }
        });
        return btn;
      }

      @Override public Object getCellEditorValue() { return ""; }
    });

    JScrollPane scroll = new JScrollPane(queueTable);
    scroll.setBorder(null);
    scroll.setBackground(Theme.SURFACE);
    scroll.getViewport().setBackground(Theme.SURFACE);
    centerSection.add(scroll, BorderLayout.CENTER);

    queueView.add(centerSection, BorderLayout.CENTER);

    // Audit Log Footer Notice
    JPanel footerNotice = new JPanel(new BorderLayout(8, 0));
    footerNotice.setBackground(new Color(0xEC, 0xFD, 0xF5));
    footerNotice.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new Color(0xA7, 0xF3, 0xD0), 1, true),
        new EmptyBorder(10, 16, 10, 16)));
    JLabel auditIcon = new JLabel(UiIcons.check(16, new Color(0x05, 0x96, 0x69)));
    JLabel auditText = new JLabel("<html><body style='color: #065F46; font-size: 11px;'>"
        + "<b>Audit Log được kích hoạt:</b> Mọi hành động duyệt, từ chối và yêu cầu sửa đều ghi lại nhật ký an toàn không thể xóa."
        + "</body></html>");
    footerNotice.add(auditIcon, BorderLayout.WEST);
    footerNotice.add(auditText, BorderLayout.CENTER);
    queueView.add(footerNotice, BorderLayout.SOUTH);
  }

  private JPanel buildKpiCard(JLabel valueLabel, String title, Color accent) {
    JPanel card = new JPanel(new BorderLayout(12, 0));
    card.setBackground(Theme.SURFACE);
    card.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER, 1, true),
        new EmptyBorder(14, 18, 14, 18)));

    JPanel accentStrip = new JPanel();
    accentStrip.setPreferredSize(new Dimension(4, 0));
    accentStrip.setBackground(accent);
    card.add(accentStrip, BorderLayout.WEST);

    JPanel info = new JPanel(new GridLayout(0, 1, 0, 2));
    info.setOpaque(false);

    valueLabel.setFont(Theme.heading(24));
    valueLabel.setForeground(Theme.TEXT);

    JLabel titleLbl = Theme.plainLabel(title);
    titleLbl.setFont(Theme.label(12));
    titleLbl.setForeground(Theme.MUTED);

    info.add(valueLabel);
    info.add(titleLbl);
    card.add(info, BorderLayout.CENTER);

    return card;
  }

  private void buildDetailView() {
    detailView.setOpaque(false);

    // Header
    JPanel header = new JPanel(new BorderLayout(0, 6));
    header.setOpaque(false);

    JLabel eyebrow = Theme.plainLabel("DUYỆT NỘI DUNG / CHI TIẾT YÊU CẦU");
    eyebrow.setFont(Theme.heading(11));
    eyebrow.setForeground(Theme.PRIMARY);

    JLabel heading = new JLabel("Duyệt yêu cầu xuất bản");
    heading.setFont(Theme.heading(24));
    heading.setForeground(Theme.TEXT);

    JLabel sub = Theme.plainLabel("Kiểm tra bản Creator gửi lên trước khi đưa ra quyết định.");
    sub.setFont(Theme.label(13));
    sub.setForeground(Theme.MUTED);

    JPanel titleBox = new JPanel(new GridLayout(0, 1, 0, 2));
    titleBox.setOpaque(false);
    titleBox.add(eyebrow);
    titleBox.add(heading);
    titleBox.add(sub);
    header.add(titleBox, BorderLayout.WEST);

    JButton backBtn = new JButton("← Quay lại danh sách");
    backBtn.setFont(Theme.label(13));
    backBtn.setFocusPainted(false);
    backBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    backBtn.addActionListener(e -> cardLayout.show(cardHost, "QUEUE"));
    header.add(backBtn, BorderLayout.EAST);

    detailView.add(header, BorderLayout.NORTH);

    // Center 2-column layout
    JPanel center = new JPanel(new BorderLayout(20, 0));
    center.setOpaque(false);

    // Left Column: Comic & Chapters
    JPanel leftCol = new JPanel();
    leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));
    leftCol.setOpaque(false);

    // Comic Summary Card
    JPanel comicCard = new JPanel(new BorderLayout(16, 0));
    comicCard.setBackground(Theme.SURFACE);
    comicCard.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER, 1, true),
        new EmptyBorder(16, 16, 16, 16)));
    comicCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));

    detailCover.setPreferredSize(new Dimension(80, 115));
    comicCard.add(detailCover, BorderLayout.WEST);

    JPanel meta = new JPanel(new GridLayout(0, 1, 0, 4));
    meta.setOpaque(false);

    detailTitle.setFont(Theme.heading(18));
    detailTitle.setForeground(Theme.TEXT);
    detailCreator.setFont(Theme.heading(12));
    detailCreator.setForeground(Theme.PRIMARY);
    detailTags.setFont(Theme.label(12));
    detailTags.setForeground(Theme.MUTED);

    detailDesc.setFont(Theme.label(12));
    detailDesc.setForeground(Theme.MUTED);
    detailDesc.setLineWrap(true);
    detailDesc.setWrapStyleWord(true);
    detailDesc.setEditable(false);
    detailDesc.setOpaque(false);

    meta.add(detailTitle);
    meta.add(detailCreator);
    meta.add(detailTags);
    meta.add(detailDesc);
    comicCard.add(meta, BorderLayout.CENTER);
    leftCol.add(comicCard);
    leftCol.add(Box.createVerticalStrut(16));

    // Chapter List Card
    JPanel chapterCard = new JPanel(new BorderLayout(0, 10));
    chapterCard.setBackground(Theme.SURFACE);
    chapterCard.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER, 1, true),
        new EmptyBorder(16, 16, 16, 16)));

    JLabel chapterTitleLbl = new JLabel("Nội dung xét duyệt xuất bản");
    chapterTitleLbl.setFont(Theme.heading(15));
    chapterTitleLbl.setForeground(Theme.TEXT);
    chapterCard.add(chapterTitleLbl, BorderLayout.NORTH);

    chapterTable.setRowHeight(42);
    chapterTable.setFont(Theme.label(13));
    chapterTable.getTableHeader().setFont(Theme.heading(12));
    chapterTable.getTableHeader().setBackground(Theme.BACKGROUND);

    chapterTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int row, int col) {
        JLabel l = (JLabel) super.getTableCellRendererComponent(t, val, sel, foc, row, col);
        l.setText("● Đạt yêu cầu");
        l.setForeground(new Color(0x16, 0xA3, 0x4A));
        return l;
      }
    });

    chapterTable.getColumnModel().getColumn(4).setCellRenderer((t, val, sel, foc, row, col) -> {
      JButton btn = new JButton("👁 Xem trước");
      btn.setFont(Theme.label(12));
      btn.setBackground(Theme.SURFACE);
      btn.setFocusPainted(false);
      return btn;
    });

    chapterTable.getColumnModel().getColumn(4).setCellEditor(new DefaultCellEditor(new JCheckBox()) {
      private JButton btn;
      @Override
      public Component getTableCellEditorComponent(JTable t, Object val, boolean sel, int row, int col) {
        btn = new JButton("👁 Xem trước");
        btn.setFont(Theme.label(12));
        btn.addActionListener(e -> {
          fireEditingStopped();
          if (activeVersion != null) {
            String vid = activeVersion.path("id").asText();
            ChapterPreviewDialog dlg = new ChapterPreviewDialog(
                SwingUtilities.getWindowAncestor(AdminReviewQueuePanel.this),
                api,
                vid,
                activeComicTitle);
            dlg.setVisible(true);
          }
        });
        return btn;
      }
      @Override public Object getCellEditorValue() { return ""; }
    });

    JScrollPane chapScroll = new JScrollPane(chapterTable);
    chapScroll.setBorder(null);
    chapScroll.setBackground(Theme.SURFACE);
    chapScroll.getViewport().setBackground(Theme.SURFACE);
    chapterCard.add(chapScroll, BorderLayout.CENTER);

    leftCol.add(chapterCard);
    center.add(leftCol, BorderLayout.CENTER);

    // Right Column: Admin Decision Panel (Mockup 07)
    JPanel rightCol = new JPanel();
    rightCol.setLayout(new BoxLayout(rightCol, BoxLayout.Y_AXIS));
    rightCol.setPreferredSize(new Dimension(320, 0));
    rightCol.setBackground(Theme.SURFACE);
    rightCol.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER, 1, true),
        new EmptyBorder(20, 20, 20, 20)));

    JLabel decisionTitle = new JLabel("Quyết định của Admin");
    decisionTitle.setFont(Theme.heading(16));
    decisionTitle.setForeground(Theme.TEXT);
    decisionTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

    // Creator Note Box
    JPanel noteBox = new JPanel(new BorderLayout(0, 4));
    noteBox.setBackground(Theme.ELEVATED);
    noteBox.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER, 1, true),
        new EmptyBorder(10, 12, 10, 12)));
    noteBox.setAlignmentX(Component.LEFT_ALIGNMENT);

    JLabel noteHead = Theme.plainLabel("Ghi chú từ Creator:");
    noteHead.setFont(Theme.heading(11));
    noteHead.setForeground(Theme.MUTED);

    creatorNoteLabel.setFont(Theme.label(12));
    creatorNoteLabel.setForeground(Theme.TEXT);

    noteBox.add(noteHead, BorderLayout.NORTH);
    noteBox.add(creatorNoteLabel, BorderLayout.CENTER);

    // Checklist
    JPanel checkList = new JPanel(new GridLayout(0, 1, 0, 8));
    checkList.setOpaque(false);
    checkList.setAlignmentX(Component.LEFT_ALIGNMENT);

    checkList.add(buildCheckItem("Ảnh không mờ nhòe, không watermark cấm"));
    checkList.add(buildCheckItem("Phù hợp phân loại độ tuổi"));
    checkList.add(buildCheckItem("Quyền Creator và bản quyền hợp lệ"));

    // 3 Buttons
    JButton reqChangeBtn = new JButton("✎ Yêu cầu chỉnh sửa");
    reqChangeBtn.setFont(Theme.heading(13));
    reqChangeBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
    reqChangeBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
    reqChangeBtn.setBackground(new Color(0xD9, 0x77, 0x06));
    reqChangeBtn.setForeground(Color.WHITE);
    reqChangeBtn.setFocusPainted(false);
    reqChangeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    reqChangeBtn.addActionListener(e -> {
      Window w = SwingUtilities.getWindowAncestor(this);
      ReviewDecisionDialogs.showRequestChanges(w, activeComicTitle, reason -> submitDecision("REQUEST_CHANGES", reason));
    });

    JButton rejectBtn = new JButton("✕ Từ chối xuất bản");
    rejectBtn.setFont(Theme.heading(13));
    rejectBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
    rejectBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
    rejectBtn.setBackground(new Color(0xDC, 0x26, 0x26));
    rejectBtn.setForeground(Color.WHITE);
    rejectBtn.setFocusPainted(false);
    rejectBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    rejectBtn.addActionListener(e -> {
      Window w = SwingUtilities.getWindowAncestor(this);
      ReviewDecisionDialogs.showReject(w, activeComicTitle, reason -> submitDecision("REJECT", reason));
    });

    JButton approveBtn = new JButton("✓ Duyệt & xuất bản ngay");
    approveBtn.setFont(Theme.heading(14));
    approveBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
    approveBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
    approveBtn.setBackground(Theme.ACTION);
    approveBtn.setForeground(Color.WHITE);
    approveBtn.setFocusPainted(false);
    approveBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    approveBtn.addActionListener(e -> approveAndPublish());

    rightCol.add(decisionTitle);
    rightCol.add(Box.createVerticalStrut(14));
    rightCol.add(noteBox);
    rightCol.add(Box.createVerticalStrut(16));
    rightCol.add(checkList);
    rightCol.add(Box.createVerticalStrut(24));
    rightCol.add(approveBtn);
    rightCol.add(Box.createVerticalStrut(10));
    rightCol.add(reqChangeBtn);
    rightCol.add(Box.createVerticalStrut(10));
    rightCol.add(rejectBtn);
    rightCol.add(Box.createVerticalGlue());

    center.add(rightCol, BorderLayout.EAST);
    detailView.add(center, BorderLayout.CENTER);
  }

  private JPanel buildCheckItem(String text) {
    JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    row.setOpaque(false);
    row.add(new JLabel(UiIcons.check(14, new Color(0x16, 0xA3, 0x4A))));
    JLabel l = Theme.plainLabel(text);
    l.setFont(Theme.label(12));
    l.setForeground(Theme.TEXT);
    row.add(l);
    return row;
  }

  public void loadQueue() {
    api.get("/publishing/review-queue?page=0&size=50").whenComplete((reply, err) -> {
      SwingUtilities.invokeLater(() -> {
        if (err != null || reply == null) return;
        queueModel.setRowCount(0);
        queueItems.clear();

        JsonNode items = reply.body().path("items");
        if (items.isArray()) {
          for (JsonNode item : items) {
            queueItems.add(item);
            String vid = item.path("id").asText();
            String creator = item.path("submittedBy").asText("Creator");
            int pages = item.path("pages").size();
            queueModel.addRow(new Object[] {
              "Bản xét duyệt #" + vid,
              "@" + creator,
              pages + " trang ảnh mới",
              "READY_FOR_REVIEW",
              ""
            });
          }
        }
        kpiPending.setText(String.format("%02d", queueItems.size()));
      });
    });
  }

  private void openReviewDetail(JsonNode version) {
    this.activeVersion = version;
    String vid = version.path("id").asText();
    this.activeChapterId = version.path("chapterId").asText();

    // Fetch version details + etag
    api.get("/publishing/versions/" + vid).whenComplete((vReply, vErr) -> {
      if (vErr != null || vReply == null) return;
      this.activeVersionEtag = vReply.etag();
      this.activeVersion = vReply.body();

      // Fetch chapter details
      api.get("/publishing/chapters/" + activeChapterId).whenComplete((cReply, cErr) -> {
        SwingUtilities.invokeLater(() -> {
          String chTitle = "Chương";
          if (cReply != null && cErr == null) {
            chTitle = cReply.body().path("title").asText("Chương");
          }
          this.activeComicTitle = chTitle;
          detailTitle.setText(chTitle);
          detailCreator.setText("Người gửi: @" + activeVersion.path("submittedBy").asText("creator"));
          detailDesc.setText("Bản nộp #" + vid + " · " + activeVersion.path("pages").size() + " trang.");

          String reason = activeVersion.path("reviewReason").asText();
          creatorNoteLabel.setText(reason.isBlank() ? "Không có ghi chú thêm." : reason);

          chapterModel.setRowCount(0);
          chapterModel.addRow(new Object[] {
            chTitle,
            activeVersion.path("pages").size() + " trang",
            "Miễn phí",
            "READY",
            ""
          });

          cardLayout.show(cardHost, "DETAIL");
        });
      });
    });
  }

  private void submitDecision(String decision, String reason) {
    if (activeVersion == null) return;
    String vid = activeVersion.path("id").asText();

    var body = java.util.Map.of("decision", decision, "reason", reason);
    api.request("POST", "/publishing/versions/" + vid + "/review", body, activeVersionEtag)
        .whenComplete((reply, err) -> {
          SwingUtilities.invokeLater(() -> {
            if (err != null || reply == null) {
              JOptionPane.showMessageDialog(this, "Thao tác thất bại: " + (reply != null ? reply.body() : err), "Lỗi", JOptionPane.ERROR_MESSAGE);
            } else {
              String msg = "REQUEST_CHANGES".equals(decision)
                  ? "Đã gửi yêu cầu chỉnh sửa tới Creator!"
                  : "Đã từ chối bản xét duyệt này.";
              JOptionPane.showMessageDialog(this, msg, "Thành công", JOptionPane.INFORMATION_MESSAGE);
              cardLayout.show(cardHost, "QUEUE");
              loadQueue();
            }
          });
        });
  }

  private void approveAndPublish() {
    if (activeVersion == null) return;
    String vid = activeVersion.path("id").asText();

    int conf = JOptionPane.showConfirmDialog(
        this,
        "Xác nhận phê duyệt và xuất bản chương này ngay lập tức lên Comic Net?",
        "Xác nhận duyệt & xuất bản",
        JOptionPane.YES_NO_OPTION);
    if (conf != JOptionPane.YES_OPTION) return;

    // 1. Review APPROVE
    var approveBody = java.util.Map.of("decision", "APPROVE", "reason", "Đạt chuẩn duyệt");
    api.request("POST", "/publishing/versions/" + vid + "/review", approveBody, activeVersionEtag)
        .whenComplete((revReply, revErr) -> {
          if (revErr != null || revReply == null) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Duyệt thất bại: " + (revReply != null ? revReply.body() : revErr), "Lỗi", JOptionPane.ERROR_MESSAGE));
            return;
          }

          // 2. Publish
          api.get("/publishing/chapters/" + activeChapterId).whenComplete((cReply, cErr) -> {
            String chEtag = cReply != null ? cReply.etag() : "";
            var pubBody = java.util.Map.of("versionId", vid);
            api.request("POST", "/publishing/chapters/" + activeChapterId + "/publish", pubBody, chEtag)
                .whenComplete((pubReply, pubErr) -> {
                  SwingUtilities.invokeLater(() -> {
                    if (pubErr != null || pubReply == null) {
                      JOptionPane.showMessageDialog(this, "Xuất bản thất bại: " + (pubReply != null ? pubReply.body() : pubErr), "Lỗi", JOptionPane.ERROR_MESSAGE);
                    } else {
                      JOptionPane.showMessageDialog(this, "Phê duyệt và xuất bản thành công!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
                      cardLayout.show(cardHost, "QUEUE");
                      loadQueue();
                    }
                  });
                });
          });
        });
  }

  @Override
  public void onShown() {
    loadQueue();
  }

  @Override
  public void onHidden() {}

  @Override
  public void sessionChanged() {
    loadQueue();
  }

  @Override
  public void close() {}
}
