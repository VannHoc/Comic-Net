package com.comicnet.desktop.features.publishing;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.features.catalog.CatalogPanel.CoverArtCanvas;
import com.comicnet.desktop.ui.Theme;
import com.comicnet.desktop.ui.UiIcons;
import com.comicnet.desktop.ui.Workspace;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import tools.jackson.databind.JsonNode;

public final class PublishingPanel extends JPanel implements Workspace {
  private final ApiClient api;
  private final Consumer<JsonNode> previewCallback;
  private boolean administrator = false;

  private final CardLayout rootLayout = new CardLayout();
  private final JPanel rootHost = new JPanel(rootLayout);

  // Dashboard components (Mockup 01)
  private final JPanel dashboardView = new JPanel(new BorderLayout(0, 16));
  private final JLabel kpiTotal = new JLabel("00");
  private final JLabel kpiDraft = new JLabel("00");
  private final JLabel kpiPending = new JLabel("00");

  private final JPanel cardsGrid = new JPanel();
  private final JTextField searchField = new JTextField(18);
  private final JComboBox<String> filterCombo = new JComboBox<>(new String[] {"Trạng thái: Tất cả", "Bản nháp", "Chờ duyệt", "Đã xuất bản", "Đã gỡ xuất bản"});
  private final List<JsonNode> allComics = new ArrayList<>();
  private final Map<String, String> comicVisibility = new HashMap<>();
  private String currentFilter = "ALL";

  // Wizard and Feedback sub-panels
  private ComicEditorWizard wizard;
  private CreatorFeedbackPanel feedbackPanel;

  public PublishingPanel(ApiClient api, Consumer<JsonNode> previewCallback) {
    super(new BorderLayout());
    this.api = api;
    this.previewCallback = previewCallback;

    setOpaque(false);
    setBorder(new EmptyBorder(Theme.CONTENT_PADDING, Theme.CONTENT_PADDING, 24, Theme.CONTENT_PADDING));

    buildDashboard();

    wizard = new ComicEditorWizard(api, administrator, () -> {
      rootLayout.show(rootHost, "DASHBOARD");
      loadDashboardData();
    });

    feedbackPanel = new CreatorFeedbackPanel(
        api,
        () -> {
          rootLayout.show(rootHost, "DASHBOARD");
          loadDashboardData();
        },
        chapterId -> {
          // Open wizard on chapter step
          rootLayout.show(rootHost, "WIZARD");
        });

    rootHost.setOpaque(false);
    rootHost.add(dashboardView, "DASHBOARD");
    rootHost.add(wizard, "WIZARD");
    rootHost.add(feedbackPanel, "FEEDBACK");

    add(rootHost, BorderLayout.CENTER);
  }

  // ==================== DASHBOARD VIEW (Mockup 01) ====================
  private void buildDashboard() {
    dashboardView.setOpaque(true);
    dashboardView.setBackground(Theme.BACKGROUND);

    // Top Header: Title & "+ Tạo truyện" button
    JPanel topHeader = new JPanel(new BorderLayout(0, 6));
    topHeader.setOpaque(false);

    JPanel titleBox = new JPanel(new GridLayout(0, 1, 0, 2));
    titleBox.setOpaque(false);

    JLabel eyebrow = Theme.plainLabel("CREATOR STUDIO");
    eyebrow.setFont(Theme.heading(11));
    eyebrow.setForeground(Theme.PRIMARY);

    JLabel heading = new JLabel("Xưởng xuất bản");
    heading.setFont(Theme.heading(28));
    heading.setForeground(Theme.TEXT);

    JLabel sub = Theme.plainLabel("Quản lý truyện của bạn — tạo mới, thêm chương và gửi duyệt trước khi xuất bản.");
    sub.setFont(Theme.label(13));
    sub.setForeground(Theme.MUTED);

    titleBox.add(eyebrow);
    titleBox.add(heading);
    titleBox.add(sub);
    topHeader.add(titleBox, BorderLayout.WEST);

    JButton createComicBtn = new JButton("+ Tạo truyện");
    createComicBtn.setFont(Theme.heading(13));
    createComicBtn.setBackground(Theme.ACTION);
    createComicBtn.setForeground(Color.WHITE);
    createComicBtn.setFocusPainted(false);
    createComicBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    createComicBtn.setPreferredSize(new Dimension(135, 40));
    createComicBtn.addActionListener(e -> {
      wizard.startNewComic();
      rootLayout.show(rootHost, "WIZARD");
    });
    JPanel createWrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    createWrap.setOpaque(false);
    createWrap.add(createComicBtn);
    topHeader.add(createWrap, BorderLayout.EAST);

    // 3 KPI Stat Cards (Mockup 01)
    JPanel kpiRow = new JPanel(new GridLayout(1, 3, 16, 0));
    kpiRow.setOpaque(false);
    kpiRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));
    kpiRow.setPreferredSize(new Dimension(600, 78));

    kpiRow.add(buildKpiCard(kpiTotal, "Truyện đang quản lý", Theme.PRIMARY));
    kpiRow.add(buildKpiCard(kpiDraft, "Bản nháp / cần sửa", new Color(0xD9, 0x77, 0x06)));
    kpiRow.add(buildKpiCard(kpiPending, "Đang chờ Admin duyệt", new Color(0x25, 0x63, 0xEB)));

    // Filter toolbar
    JPanel filterBar = new JPanel(new BorderLayout(14, 0));
    filterBar.setOpaque(false);
    filterBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
    filterBar.setPreferredSize(new Dimension(600, 38));

    // Left filter pills
    JPanel pillsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    pillsPanel.setOpaque(false);
    pillsPanel.add(buildFilterPill("Tất cả", "ALL"));
    pillsPanel.add(buildFilterPill("Bản nháp", "DRAFT"));
    pillsPanel.add(buildFilterPill("Chờ duyệt", "PENDING"));
    pillsPanel.add(buildFilterPill("Đã xuất bản", "PUBLISHED"));
    pillsPanel.add(buildFilterPill("Đã gỡ xuất bản", "UNPUBLISHED"));
    filterBar.add(pillsPanel, BorderLayout.WEST);

    // Right search & sort
    JPanel searchBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    searchBox.setOpaque(false);

    JPanel searchPill = new JPanel(new BorderLayout(8, 0));
    searchPill.setBackground(Theme.SURFACE);
    searchPill.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER, 1, true),
        new EmptyBorder(4, 10, 4, 10)));
    searchPill.add(new JLabel(UiIcons.search(14, Theme.MUTED)), BorderLayout.WEST);
    searchField.setBorder(null);
    searchField.setOpaque(false);
    searchField.setFont(Theme.label(13));
    searchPill.add(searchField, BorderLayout.CENTER);
    searchBox.add(searchPill);

    filterCombo.setFont(Theme.label(12));
    filterCombo.setBackground(Theme.SURFACE);
    filterCombo.addActionListener(e -> {
      currentFilter = switch (filterCombo.getSelectedIndex()) {
        case 1 -> "DRAFT";
        case 2 -> "PENDING";
        case 3 -> "PUBLISHED";
        case 4 -> "UNPUBLISHED";
        default -> "ALL";
      };
      filterAndRenderCards();
    });
    searchField.getDocument().addDocumentListener(new DocumentListener() {
      @Override public void insertUpdate(DocumentEvent e) { filterAndRenderCards(); }
      @Override public void removeUpdate(DocumentEvent e) { filterAndRenderCards(); }
      @Override public void changedUpdate(DocumentEvent e) { filterAndRenderCards(); }
    });
    searchBox.add(filterCombo);

    filterBar.add(searchBox, BorderLayout.EAST);

    // Top Section combines topHeader + kpiRow + filterBar cleanly with Y_AXIS layout
    JPanel topSection = new JPanel();
    topSection.setLayout(new BoxLayout(topSection, BoxLayout.Y_AXIS));
    topSection.setOpaque(true);
    topSection.setBackground(Theme.BACKGROUND);
    topSection.setBorder(new EmptyBorder(0, 0, 14, 0));

    topHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
    topSection.add(topHeader);
    topSection.add(Box.createVerticalStrut(14));

    kpiRow.setAlignmentX(Component.LEFT_ALIGNMENT);
    topSection.add(kpiRow);
    topSection.add(Box.createVerticalStrut(16));

    filterBar.setAlignmentX(Component.LEFT_ALIGNMENT);
    topSection.add(filterBar);

    dashboardView.add(topSection, BorderLayout.NORTH);

    // Cards Grid (2 columns) inside JScrollPane with wrapper to avoid vertical stretching and artifacts
    cardsGrid.setOpaque(true);
    cardsGrid.setBackground(Theme.BACKGROUND);
    cardsGrid.setLayout(new GridLayout(0, 2, 16, 16));

    JPanel cardsWrapper = new JPanel(new BorderLayout());
    cardsWrapper.setOpaque(true);
    cardsWrapper.setBackground(Theme.BACKGROUND);
    cardsWrapper.add(cardsGrid, BorderLayout.NORTH);

    JScrollPane scroll = new JScrollPane(cardsWrapper);
    scroll.setBorder(null);
    scroll.setOpaque(true);
    scroll.setBackground(Theme.BACKGROUND);
    scroll.getViewport().setOpaque(true);
    scroll.getViewport().setBackground(Theme.BACKGROUND);
    scroll.getVerticalScrollBar().setUnitIncrement(24);
    scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    dashboardView.add(scroll, BorderLayout.CENTER);
  }

  private JPanel buildKpiCard(JLabel valueLabel, String title, Color accent) {
    JPanel card = new JPanel(new BorderLayout(14, 0));
    card.setBackground(Theme.SURFACE);
    card.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER, 1, true),
        new EmptyBorder(12, 16, 12, 16)));

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

  private JButton buildFilterPill(String label, String code) {
    JButton pill = new JButton(label);
    pill.setFont(Theme.label(12));
    pill.setBackground(Theme.SURFACE);
    pill.setFocusPainted(false);
    pill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    pill.addActionListener(e -> {
      filterCombo.setSelectedIndex(switch (code) {
        case "DRAFT" -> 1;
        case "PENDING" -> 2;
        case "PUBLISHED" -> 3;
        case "UNPUBLISHED" -> 4;
        default -> 0;
      });
    });
    return pill;
  }

  public void loadDashboardData() {
    // Check role
    api.get("/me").whenComplete((meReply, meErr) -> {
      if (meReply != null && meErr == null) {
        boolean isAdmin = false;
        for (var role : meReply.body().path("roles")) {
          String r = role.asText().toUpperCase(java.util.Locale.ROOT);
          if ("ADMINISTRATOR".equals(r) || "ADMIN".equals(r)) isAdmin = true;
        }
        boolean adminRole = isAdmin;
        SwingUtilities.invokeLater(() -> {
          this.administrator = adminRole;
          wizard.setAdministrator(adminRole);
        });
      }
    });

    api.get("/publishing/comics?page=0&size=50").whenComplete((reply, err) -> {
      SwingUtilities.invokeLater(() -> {
        if (err != null || reply == null) return;
        allComics.clear();
        comicVisibility.clear();
        JsonNode items = reply.body().path("items");
        if (items.isArray()) {
          for (JsonNode item : items) {
            allComics.add(item);
            String visibility = item.path("visibility").asText("");
            if (!visibility.isBlank()) comicVisibility.put(item.path("id").asText(), visibility);
          }
        }
        kpiTotal.setText(String.format("%02d", allComics.size()));
        filterAndRenderCards();
        for (JsonNode item : allComics) {
          String id = item.path("id").asText();
          if (comicVisibility.containsKey(id)) continue;
          api.get("/publishing/comics/" + id).whenComplete((detail, detailError) ->
              SwingUtilities.invokeLater(() -> {
                if (detail != null && detailError == null) {
                  comicVisibility.put(id, detail.body().path("visibility").asText("HIDDEN"));
                  filterAndRenderCards();
                }
              }));
        }
      });
    });
  }

  private void filterAndRenderCards() {
    cardsGrid.removeAll();

    int draftCount = 0;
    int pendingCount = 0;

    for (JsonNode comic : allComics) {
      String title = comic.path("title").asText("Truyện");
      String type = comic.path("type").asText("MANGA");
      String desc = comic.path("description").asText("Chưa có mô tả.");
      String comicId = comic.path("id").asText();

      String visibility = comicVisibility.get(comicId);
      String status = comic.path("publicationState").asText("");
      if (status.isBlank()) status = "PUBLIC".equals(visibility) ? "PUBLISHED" : "UNKNOWN";
      if ("DRAFT".equals(status)) draftCount++;
      if ("PENDING".equals(status)) pendingCount++;

      boolean match = true;
      if (!"ALL".equals(currentFilter) && !currentFilter.equals(status)) match = false;
      if (!title.toLowerCase(java.util.Locale.ROOT)
          .contains(searchField.getText().strip().toLowerCase(java.util.Locale.ROOT))) match = false;

      if (match) {
        cardsGrid.add(buildComicCard(comicId, title, type, desc, status));
      }
    }

    kpiDraft.setText(String.format("%02d", draftCount));
    kpiPending.setText(String.format("%02d", pendingCount));

    if (cardsGrid.getComponentCount() == 0) {
      JPanel emptyBox = new JPanel(new GridLayout(0, 1, 0, 8));
      emptyBox.setOpaque(false);
      JLabel emptyLbl = new JLabel("Chưa có truyện nào trong mục này.", JLabel.CENTER);
      emptyLbl.setFont(Theme.label(14));
      emptyLbl.setForeground(Theme.MUTED);
      emptyBox.add(emptyLbl);
      cardsGrid.add(emptyBox);
    }

    cardsGrid.revalidate();
    cardsGrid.repaint();
  }

  private JPanel buildComicCard(String comicId, String title, String type, String desc, String status) {
    JPanel card = new JPanel(new BorderLayout(14, 0));
    card.setBackground(Theme.SURFACE);
    card.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER, 1, true),
        new EmptyBorder(14, 14, 14, 14)));
    card.setPreferredSize(new Dimension(380, 160));

    // Cover art thumbnail (left)
    CoverArtCanvas cover = new CoverArtCanvas(6);
    cover.setPreferredSize(new Dimension(90, 130));
    cover.setMetadata(title, type);
    card.add(cover, BorderLayout.WEST);

    // Right Content Info
    JPanel info = new JPanel();
    info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
    info.setOpaque(false);

    // Status pill top row
    JPanel topRow = new JPanel(new BorderLayout());
    topRow.setOpaque(false);

    JLabel titleLbl = new JLabel(title);
    titleLbl.setFont(Theme.heading(15));
    titleLbl.setForeground(Theme.TEXT);
    titleLbl.setToolTipText(title + " · ID #" + comicId);
    topRow.add(titleLbl, BorderLayout.CENTER);

    JLabel statusPill = buildStatusPill(status);
    topRow.add(statusPill, BorderLayout.EAST);
    info.add(topRow);
    info.add(Box.createVerticalStrut(4));

    JLabel meta = Theme.plainLabel(type + " · Tiếng Việt");
    meta.setFont(Theme.label(11));
    meta.setForeground(Theme.MUTED);
    meta.setAlignmentX(Component.LEFT_ALIGNMENT);
    info.add(meta);
    info.add(Box.createVerticalStrut(4));

    JLabel descLbl = new JLabel("<html><body style='width: 180px; color: #64748B; font-size: 11px;'>"
        + (desc.length() > 60 ? desc.substring(0, 60) + "..." : desc)
        + "</body></html>");
    descLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
    info.add(descLbl);
    info.add(Box.createVerticalGlue());

    // Action buttons bottom row
    JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    actionRow.setOpaque(false);
    actionRow.setAlignmentX(Component.LEFT_ALIGNMENT);

    JButton manageBtn = new JButton("✎ Quản lý");
    manageBtn.setFont(Theme.heading(11));
    manageBtn.setBackground(Theme.SURFACE);
    manageBtn.setFocusPainted(false);
    manageBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    manageBtn.addActionListener(e -> {
      wizard.editComic(comicId, title, type, desc);
      rootLayout.show(rootHost, "WIZARD");
    });
    actionRow.add(manageBtn);

    JButton moreBtn = new JButton("···");
    moreBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
    moreBtn.setBackground(Theme.SURFACE);
    moreBtn.setFocusPainted(false);
    moreBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    JPopupMenu popup = new JPopupMenu();
    JMenuItem itemEdit = new JMenuItem("Chỉnh sửa thông tin");
    itemEdit.addActionListener(e -> {
      wizard.editComic(comicId, title, type, desc);
      rootLayout.show(rootHost, "WIZARD");
    });

    JMenuItem itemHistory = new JMenuItem("Lịch sử phiên bản (Nâng cao)");
    itemHistory.addActionListener(e -> {
      // Find default translation and first chapter to show version history
      api.get("/publishing/comics/" + comicId + "/translations").whenComplete((tReply, tErr) -> {
        if (tReply != null && tErr == null) {
          JsonNode trans = tReply.body().path("items");
          if (trans.isArray() && !trans.isEmpty()) {
            String transId = trans.get(0).path("id").asText();
            api.get("/publishing/translations/" + transId + "/chapters?page=0&size=10").whenComplete((cReply, cErr) -> {
              SwingUtilities.invokeLater(() -> {
                if (cReply != null && cErr == null) {
                  JsonNode chs = cReply.body().path("items");
                  if (chs.isArray() && !chs.isEmpty()) {
                    String chId = chs.get(0).path("id").asText();
                    String chTitle = chs.get(0).path("title").asText("Chương 1");
                    VersionHistoryDialog dlg = new VersionHistoryDialog(
                        SwingUtilities.getWindowAncestor(this),
                        api,
                        chId,
                        chTitle,
                        this::loadDashboardData);
                    dlg.setVisible(true);
                    return;
                  }
                }
                JOptionPane.showMessageDialog(this, "Truyện chưa có chương nào để xem lịch sử.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
              });
            });
          }
        }
      });
    });

    popup.add(itemEdit);
    popup.add(itemHistory);
    if (administrator) {
      boolean isPub = "PUBLIC".equalsIgnoreCase(comicVisibility.get(comicId)) || "PUBLISHED".equals(status);
      JMenuItem visibilityAction = new JMenuItem(isPub ? "Gỡ xuất bản (Tạm ẩn)" : "✓ Công khai lên Trang chủ (Xuất bản)");
      visibilityAction.addActionListener(e -> changeVisibility(comicId, isPub));
      popup.add(visibilityAction);
    }
    moreBtn.addActionListener(e -> popup.show(moreBtn, 0, moreBtn.getHeight()));
    actionRow.add(moreBtn);

    info.add(actionRow);
    card.add(info, BorderLayout.CENTER);
    return card;
  }

  private JLabel buildStatusPill(String status) {
    JLabel pill = new JLabel();
    pill.setFont(Theme.heading(10));
    if ("PUBLISHED".equals(status)) {
      pill.setText("● Đã xuất bản");
      pill.setForeground(new Color(0x16, 0xA3, 0x4A));
    } else if ("NEEDS_CHANGES".equals(status)) {
      pill.setText("● Cần sửa lại");
      pill.setForeground(new Color(0xD9, 0x77, 0x06));
    } else if ("PENDING".equals(status)) {
      pill.setText("● Chờ duyệt");
      pill.setForeground(new Color(0x25, 0x63, 0xEB));
    } else if ("UNPUBLISHED".equals(status)) {
      pill.setText("● Đã gỡ xuất bản");
      pill.setForeground(Theme.MUTED);
    } else if ("UNKNOWN".equals(status)) {
      pill.setText("● Chưa tải trạng thái");
      pill.setForeground(Theme.MUTED);
    } else {
      pill.setText("● Bản nháp");
      pill.setForeground(Theme.MUTED);
    }
    return pill;
  }

  private void changeVisibility(String comicId, boolean hide) {
    String reason = JOptionPane.showInputDialog(this,
        hide ? "Lý do gỡ xuất bản:" : "Lý do tái xuất bản:",
        hide ? "Gỡ xuất bản" : "Tái xuất bản", JOptionPane.QUESTION_MESSAGE);
    if (reason == null) return;
    if (reason.isBlank()) {
      JOptionPane.showMessageDialog(this, "Cần nhập lý do thao tác.", "Thiếu lý do", JOptionPane.WARNING_MESSAGE);
      return;
    }
    api.get("/publishing/comics/" + comicId)
        .thenCompose(current -> {
          if (!hide) {
            return api.request("PATCH", "/comics/" + comicId, Map.of("ageRating", "ALL_AGE"), current.etag())
                .thenCompose(patched -> api.request("PUT", "/admin/comics/" + comicId + "/visibility",
                    Map.of("visibility", "PUBLIC", "reason", reason.trim()), patched.etag()));
          }
          return api.request("PUT", "/admin/comics/" + comicId + "/visibility",
              Map.of("visibility", "HIDDEN", "reason", reason.trim()), current.etag());
        })
        .whenComplete((reply, err) -> SwingUtilities.invokeLater(() -> {
          if (err != null || reply == null) {
            Throwable cause = err == null ? new IllegalStateException("Máy chủ không trả lời") : ApiClient.unwrap(err);
            String detail = cause instanceof ApiClient.ApiFailure failure
                ? "HTTP " + failure.status() + " · " + failure.code() + " · " + failure.getMessage()
                    + " · Mã yêu cầu: " + failure.requestId()
                : String.valueOf(cause.getMessage());
            JOptionPane.showMessageDialog(this, "Không thể đổi trạng thái: " + detail, "Lỗi", JOptionPane.ERROR_MESSAGE);
          } else {
            // Also synchronize translation visibility
            api.get("/publishing/comics/" + comicId + "/translations").whenComplete((tReply, tErr) -> {
              if (tReply != null && tErr == null) {
                JsonNode trans = tReply.body().path("items");
                if (trans.isArray()) {
                  for (JsonNode t : trans) {
                    String trId = t.path("id").asText();
                    api.get("/publishing/translations/" + trId).whenComplete((trDetail, trErr) -> {
                      if (trDetail != null) {
                        api.request("PATCH", "/translations/" + trId, Map.of("visibility", hide ? "HIDDEN" : "PUBLIC"), trDetail.etag());
                      }
                    });
                  }
                }
              }
            });
            JOptionPane.showMessageDialog(this, hide ? "Đã tạm ẩn truyện khỏi Trang chủ." : "Đã công khai truyện lên Trang chủ thành công!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
            loadDashboardData();
          }
        }));
  }

  @Override
  public void onShown() {
    loadDashboardData();
  }

  @Override
  public void onHidden() {}

  @Override
  public void sessionChanged() {
    loadDashboardData();
  }

  @Override
  public void close() {}

  public static java.util.Map<String, String> reviewInput(boolean approved, String rawReason) {
    String clean = rawReason == null ? "" : rawReason.trim();
    if (approved) {
      return clean.isEmpty()
          ? java.util.Map.of("decision", "APPROVE")
          : java.util.Map.of("decision", "APPROVE", "reason", clean);
    }
    return clean.isEmpty()
        ? java.util.Map.of("decision", "REJECT")
        : java.util.Map.of("decision", "REJECT", "reason", clean);
  }

}
