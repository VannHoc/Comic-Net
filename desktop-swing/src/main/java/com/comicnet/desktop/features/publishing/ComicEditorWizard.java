package com.comicnet.desktop.features.publishing;

import com.comicnet.desktop.client.api.ApiClient;
import com.comicnet.desktop.features.catalog.CatalogPanel.CoverArtCanvas;
import com.comicnet.desktop.ui.Theme;
import com.comicnet.desktop.ui.UiIcons;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import tools.jackson.databind.JsonNode;

public final class ComicEditorWizard extends JPanel {
  private final ApiClient api;
  private boolean administrator;
  private final Runnable onCompleteOrCancel;

  private final CardLayout wizardLayout = new CardLayout();
  private final JPanel stepHost = new JPanel(wizardLayout);

  // Wizard state
  private String comicId;
  private String translationId;
  private String activeChapterId;
  private String activeVersionId;
  private String activeVersionEtag;
  private File selectedCoverFile;

  // Step 1: Info components (Mockup 02)
  private final JPanel step1View = new JPanel(new BorderLayout(0, 16));
  private final JTextField titleField = new JTextField(24);
  private final JTextField altTitleField = new JTextField(24);
  private final JComboBox<String> typeCombo = new JComboBox<>(new String[] {"MANGA", "MANHWA", "MANHUA", "COMIC"});
  private final JComboBox<String> langCombo = new JComboBox<>(new String[] {"Tiếng Việt (vi)", "English (en)", "Japanese (ja)"});
  private final JTextField authorField = new JTextField(20);
  private final JComboBox<String> statusCombo = new JComboBox<>(new String[] {"Đang cập nhật", "Hoàn thành"});
  private final JTextArea descArea = new JTextArea(4, 24);
  private final CoverArtCanvas coverCanvas = new CoverArtCanvas(8);
  private final List<JCheckBox> genreBoxes = new ArrayList<>();

  // Step 2: Chapter components (Mockup 03)
  private final JPanel step2View = new JPanel(new BorderLayout(0, 16));
  private final DefaultListModel<ChapterItem> chapterListModel = new DefaultListModel<>();
  private final JList<ChapterItem> chapterJList = new JList<>(chapterListModel);
  private final JTextField chapterTitleField = new JTextField(20);
  private final JTextField chapterNumField = new JTextField(6);
  private final JRadioButton freeRadio = new JRadioButton("Miễn phí", true);
  private final JRadioButton coinRadio = new JRadioButton("Khóa bằng xu (10 xu)");
  private final JPanel pageThumbnailsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
  private final List<JsonNode> currentVersionPages = new ArrayList<>();

  // Step 3: Review components (Mockup 05 / 10)
  private final JPanel step3View = new JPanel(new BorderLayout(0, 16));
  private final JLabel reviewComicTitle = new JLabel("Tiêu đề truyện");
  private final CoverArtCanvas reviewCover = new CoverArtCanvas(8);
  private final JLabel reviewMetaLbl = Theme.plainLabel("Manga · Tiếng Việt");
  private final JTextArea reviewDesc = new JTextArea(3, 20);
  private final DefaultTableModel reviewChapterModel = new DefaultTableModel(
      new Object[] {"CHƯƠNG", "SỐ TRANG", "QUYỀN ĐỌC", "TRẠNG THÁI", "XEM TRƯỚC"}, 0) {
    @Override public boolean isCellEditable(int r, int c) { return c == 4; }
  };
  private final JTable reviewChapterTable = new JTable(reviewChapterModel);
  private final JTextArea noteForAdminArea = new JTextArea(3, 20);
  private final JRadioButton adminPublishNowRadio = new JRadioButton("Xuất bản ngay (công khai tức thì)", true);
  private final JRadioButton adminScheduleRadio = new JRadioButton("Lên lịch phát hành tự động");

  public static final class ChapterItem {
    final String id;
    String title;
    String number;
    String policy;
    String versionId;
    int pageCount;

    ChapterItem(String id, String title, String number, String policy) {
      this.id = id;
      this.title = title;
      this.number = number;
      this.policy = policy;
    }

    @Override public String toString() {
      return "Chương " + number + " — " + title + " (" + pageCount + " trang)";
    }
  }

  public ComicEditorWizard(ApiClient api, boolean administrator, Runnable onCompleteOrCancel) {
    super(new BorderLayout());
    this.api = api;
    this.administrator = administrator;
    this.onCompleteOrCancel = onCompleteOrCancel;

    setOpaque(false);
    setBorder(new EmptyBorder(16, 24, 24, 24));

    buildStep1();
    buildStep2();
    buildStep3();

    stepHost.setOpaque(false);
    stepHost.add(step1View, "STEP1");
    stepHost.add(step2View, "STEP2");
    stepHost.add(step3View, "STEP3");

    add(stepHost, BorderLayout.CENTER);
  }

  public void setAdministrator(boolean administrator) {
    if (this.administrator == administrator) return;
    this.administrator = administrator;
    step3View.removeAll();
    buildStep3();
    step3View.revalidate();
    step3View.repaint();
  }

  private JPanel buildWizardHeader(int activeStep, String stepTitle, String stepDesc) {
    JPanel header = new JPanel(new BorderLayout(0, 10));
    header.setOpaque(false);

    JPanel titleBox = new JPanel(new GridLayout(0, 1, 0, 2));
    titleBox.setOpaque(false);

    JLabel eyebrow = Theme.plainLabel("XƯỞNG XUẤT BẢN / TẠO & QUẢN LÝ TRUYỆN");
    eyebrow.setFont(Theme.heading(11));
    eyebrow.setForeground(Theme.PRIMARY);

    JLabel heading = new JLabel(stepTitle);
    heading.setFont(Theme.heading(24));
    heading.setForeground(Theme.TEXT);

    JLabel sub = Theme.plainLabel(stepDesc);
    sub.setFont(Theme.label(13));
    sub.setForeground(Theme.MUTED);

    titleBox.add(eyebrow);
    titleBox.add(heading);
    titleBox.add(sub);
    header.add(titleBox, BorderLayout.NORTH);

    // 3-step indicator pill bar
    JPanel wizardBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
    wizardBar.setOpaque(false);
    wizardBar.add(buildStepPill(1, "Thông tin truyện", activeStep == 1));
    wizardBar.add(buildStepPill(2, "Chương & ảnh", activeStep == 2));
    wizardBar.add(buildStepPill(3, administrator ? "Xuất bản truyện" : "Kiểm tra & gửi duyệt", activeStep == 3));
    header.add(wizardBar, BorderLayout.SOUTH);

    return header;
  }

  private JPanel buildStepPill(int step, String name, boolean active) {
    JPanel pill = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    pill.setBackground(active ? Theme.SURFACE : Theme.ELEVATED);
    pill.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(active ? Theme.PRIMARY : Theme.BORDER, active ? 2 : 1, true),
        new EmptyBorder(2, 10, 2, 10)));

    JLabel numLbl = new JLabel(String.valueOf(step));
    numLbl.setFont(Theme.heading(12));
    numLbl.setForeground(active ? Theme.PRIMARY : Theme.MUTED);

    JLabel nameLbl = Theme.plainLabel(name);
    nameLbl.setFont(active ? Theme.heading(12) : Theme.label(12));
    nameLbl.setForeground(active ? Theme.TEXT : Theme.MUTED);

    pill.add(numLbl);
    pill.add(nameLbl);
    return pill;
  }

  // ==================== STEP 1: INFO (Mockup 02) ====================
  private void buildStep1() {
    step1View.setOpaque(false);
    step1View.add(buildWizardHeader(1, "Tạo truyện mới", "Bước 1/3: Điền thông tin cơ bản. Bạn có thể lưu nháp và hoàn thiện sau."), BorderLayout.NORTH);

    JPanel formPanel = new JPanel(new BorderLayout(24, 0));
    formPanel.setOpaque(false);

    // Left Column: Cover Art Upload
    JPanel coverCol = new JPanel();
    coverCol.setLayout(new BoxLayout(coverCol, BoxLayout.Y_AXIS));
    coverCol.setPreferredSize(new Dimension(240, 0));
    coverCol.setBackground(Theme.SURFACE);
    coverCol.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER, 1, true),
        new EmptyBorder(16, 16, 16, 16)));

    JLabel coverTitle = new JLabel("Ảnh bìa truyện");
    coverTitle.setFont(Theme.heading(15));
    coverTitle.setForeground(Theme.TEXT);
    coverTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

    coverCanvas.setPreferredSize(new Dimension(150, 220));
    coverCanvas.setMaximumSize(new Dimension(150, 220));
    coverCanvas.setAlignmentX(Component.CENTER_ALIGNMENT);

    JButton chooseCoverBtn = new JButton("🖼 Chọn ảnh bìa");
    chooseCoverBtn.setFont(Theme.heading(12));
    chooseCoverBtn.setBackground(Theme.SURFACE);
    chooseCoverBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
    chooseCoverBtn.setFocusPainted(false);
    chooseCoverBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    chooseCoverBtn.addActionListener(e -> chooseCoverFile());

    JLabel coverHint = new JLabel("<html><center style='color:#64748B; font-size:10px;'>"
        + "Hỗ trợ JPG, PNG, WebP<br>Tối đa 5MB · Tỷ lệ 2:3"
        + "</center></html>");
    coverHint.setAlignmentX(Component.CENTER_ALIGNMENT);

    coverCol.add(coverTitle);
    coverCol.add(Box.createVerticalStrut(14));
    coverCol.add(coverCanvas);
    coverCol.add(Box.createVerticalStrut(14));
    coverCol.add(chooseCoverBtn);
    coverCol.add(Box.createVerticalStrut(8));
    coverCol.add(coverHint);
    coverCol.add(Box.createVerticalGlue());
    formPanel.add(coverCol, BorderLayout.WEST);

    // Right Column: Metadata form fields
    JPanel rightCol = new JPanel();
    rightCol.setLayout(new BoxLayout(rightCol, BoxLayout.Y_AXIS));
    rightCol.setBackground(Theme.SURFACE);
    rightCol.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER, 1, true),
        new EmptyBorder(18, 22, 18, 22)));

    JLabel formTitle = new JLabel("Thông tin tác phẩm");
    formTitle.setFont(Theme.heading(16));
    formTitle.setForeground(Theme.TEXT);
    formTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
    rightCol.add(formTitle);
    rightCol.add(Box.createVerticalStrut(14));

    // Title & Alt Title
    JPanel row1 = new JPanel(new GridLayout(1, 2, 14, 0));
    row1.setOpaque(false);
    row1.setAlignmentX(Component.LEFT_ALIGNMENT);
    row1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
    row1.add(buildFieldBox("Tên truyện *", titleField));
    row1.add(buildFieldBox("Tên phụ / Alt title", altTitleField));
    rightCol.add(row1);
    rightCol.add(Box.createVerticalStrut(10));

    // Type & Language
    JPanel row2 = new JPanel(new GridLayout(1, 2, 14, 0));
    row2.setOpaque(false);
    row2.setAlignmentX(Component.LEFT_ALIGNMENT);
    row2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
    row2.add(buildFieldBox("Loại truyện *", typeCombo));
    row2.add(buildFieldBox("Ngôn ngữ bản dịch *", langCombo));
    rightCol.add(row2);
    rightCol.add(Box.createVerticalStrut(10));

    // Author & Status
    JPanel row3 = new JPanel(new GridLayout(1, 2, 14, 0));
    row3.setOpaque(false);
    row3.setAlignmentX(Component.LEFT_ALIGNMENT);
    row3.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
    row3.add(buildFieldBox("Tác giả", authorField));
    row3.add(buildFieldBox("Trạng thái tác phẩm", statusCombo));
    rightCol.add(row3);
    rightCol.add(Box.createVerticalStrut(10));

    // Genres Tags Checkboxes
    JLabel genreLabel = new JLabel("Thể loại truyện *");
    genreLabel.setFont(Theme.label(12));
    genreLabel.setForeground(Theme.TEXT);
    genreLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
    rightCol.add(genreLabel);
    rightCol.add(Box.createVerticalStrut(4));

    JPanel genreBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
    genreBox.setOpaque(false);
    genreBox.setAlignmentX(Component.LEFT_ALIGNMENT);
    for (String g : List.of("Hành động", "Phiêu lưu", "Fantasy", "Hài hước", "Romance", "Học đường", "Sci-Fi", "Kinh dị")) {
      JCheckBox cb = new JCheckBox(g);
      cb.setFont(Theme.label(12));
      cb.setOpaque(false);
      if ("Fantasy".equals(g) || "Hành động".equals(g)) cb.setSelected(true);
      genreBoxes.add(cb);
      genreBox.add(cb);
    }
    rightCol.add(genreBox);
    rightCol.add(Box.createVerticalStrut(10));

    // Description
    JLabel descLbl = new JLabel("Mô tả truyện");
    descLbl.setFont(Theme.label(12));
    descLbl.setForeground(Theme.TEXT);
    descLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
    rightCol.add(descLbl);
    rightCol.add(Box.createVerticalStrut(4));

    descArea.setFont(Theme.label(12));
    descArea.setLineWrap(true);
    descArea.setWrapStyleWord(true);
    descArea.setBorder(new EmptyBorder(6, 8, 6, 8));
    JScrollPane descScroll = new JScrollPane(descArea);
    descScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1, true));
    descScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
    descScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
    rightCol.add(descScroll);

    formPanel.add(rightCol, BorderLayout.CENTER);
    step1View.add(formPanel, BorderLayout.CENTER);

    // Step 1 Footer
    JPanel footer = new JPanel(new BorderLayout());
    footer.setOpaque(false);
    footer.setBorder(new EmptyBorder(14, 0, 0, 0));

    JButton backBtn = new JButton("< Quay lại danh sách");
    backBtn.setFont(Theme.label(13));
    backBtn.setFocusPainted(false);
    backBtn.addActionListener(e -> onCompleteOrCancel.run());
    footer.add(backBtn, BorderLayout.WEST);

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    actions.setOpaque(false);

    JButton saveDraftBtn = new JButton("Lưu bản nháp");
    saveDraftBtn.setFont(Theme.label(13));
    saveDraftBtn.setFocusPainted(false);
    saveDraftBtn.addActionListener(e -> saveComicMetadata(false));

    JButton nextStepBtn = new JButton("Tiếp tục: Thêm chương →");
    nextStepBtn.setFont(Theme.heading(13));
    nextStepBtn.setBackground(Theme.ACTION);
    nextStepBtn.setForeground(Color.WHITE);
    nextStepBtn.setFocusPainted(false);
    nextStepBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    nextStepBtn.addActionListener(e -> saveComicMetadata(true));

    actions.add(saveDraftBtn);
    actions.add(nextStepBtn);
    footer.add(actions, BorderLayout.EAST);
    step1View.add(footer, BorderLayout.SOUTH);
  }

  private JPanel buildFieldBox(String label, JComponent field) {
    JPanel box = new JPanel(new BorderLayout(0, 4));
    box.setOpaque(false);
    JLabel l = new JLabel(label);
    l.setFont(Theme.label(12));
    l.setForeground(Theme.TEXT);
    box.add(l, BorderLayout.NORTH);
    field.setFont(Theme.label(13));
    box.add(field, BorderLayout.CENTER);
    return box;
  }

  private void chooseCoverFile() {
    JFileChooser chooser = new JFileChooser();
    chooser.setFileFilter(new FileNameExtensionFilter("Ảnh bìa (*.jpg, *.png, *.webp)", "jpg", "jpeg", "png", "webp"));
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      selectedCoverFile = chooser.getSelectedFile();
      if (selectedCoverFile != null && selectedCoverFile.exists()) {
        coverCanvas.setMetadata(titleField.getText(), (String) typeCombo.getSelectedItem());
      }
    }
  }

  private void saveComicMetadata(boolean advanceToStep2) {
    String title = titleField.getText().trim();
    if (title.isBlank()) {
      JOptionPane.showMessageDialog(this, "Vui lòng nhập tên truyện.", "Thiếu thông tin", JOptionPane.WARNING_MESSAGE);
      return;
    }

    String type = (String) typeCombo.getSelectedItem();
    String desc = descArea.getText().trim();
    String alt = altTitleField.getText().trim();
    String author = authorField.getText().trim();

    List<String> tags = new ArrayList<>();
    for (JCheckBox cb : genreBoxes) {
      if (cb.isSelected()) tags.add(cb.getText());
    }

    // Call API to create comic if not created yet
    if (comicId == null) {
      var body = new HashMap<String, Object>();
      body.put("title", title);
      body.put("type", type);
      body.put("description", desc);
      String slugBase = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
      body.put("slug", (slugBase.isEmpty() ? "comic" : slugBase) + "-" + UUID.randomUUID().toString().substring(0, 8));
      body.put("countryCode", "VN");
      body.put("status", "ONGOING");
      body.put("ageRating", "ALL_AGE");
      body.put("alternativeTitles", alt.isBlank() ? List.of() : List.of(alt));
      body.put("genreIds", List.of());
      body.put("tagIds", List.of());
      body.put("warningIds", List.of());
      body.put("contributors", List.of());
      body.put("publisherIds", List.of());

      var cover = selectedCoverFile == null
          ? java.util.concurrent.CompletableFuture.<ApiClient.Reply>completedFuture(null)
          : api.upload("/assets", selectedCoverFile.toPath(), Map.of("kind", "COVER"), UUID.randomUUID().toString(), null);
      cover.thenCompose(asset -> {
        body.put("coverAssetId", asset == null ? null : asset.body().path("id").asText());
        body.put("bannerAssetId", null);
        return api.create("/comics", body, UUID.randomUUID().toString());
      }).whenComplete((reply, err) -> {
        SwingUtilities.invokeLater(() -> {
          if (err != null || reply == null) {
            JOptionPane.showMessageDialog(this, "Không thể lưu thông tin truyện: " + (reply != null ? reply.body() : err), "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
          }
          this.comicId = reply.body().path("id").asText();

          // Create default Vietnamese translation
          createDefaultTranslation(advanceToStep2);
        });
      });
    } else {
      var cover = selectedCoverFile == null
          ? java.util.concurrent.CompletableFuture.<ApiClient.Reply>completedFuture(null)
          : api.upload("/assets", selectedCoverFile.toPath(), Map.of("kind", "COVER"), UUID.randomUUID().toString(), null);
      cover.thenCompose(asset -> api.get("/publishing/comics/" + comicId).thenCompose(current -> {
        var patch = new HashMap<String, Object>();
        patch.put("title", title);
        patch.put("description", desc);
        patch.put("type", type);
        patch.put("ageRating", "ALL_AGE");
        if (asset != null) patch.put("coverAssetId", asset.body().path("id").asText());
        return api.request("PATCH", "/comics/" + comicId, patch, current.etag());
      })).whenComplete((reply, err) -> SwingUtilities.invokeLater(() -> {
        if (err != null || reply == null) {
          JOptionPane.showMessageDialog(this, "Không thể lưu thông tin truyện: " + err, "Lỗi", JOptionPane.ERROR_MESSAGE);
          return;
        }
        selectedCoverFile = null;
        if (advanceToStep2) {
          loadChaptersForStep2();
          wizardLayout.show(stepHost, "STEP2");
        } else {
          JOptionPane.showMessageDialog(this, "Đã lưu bản nháp thông tin truyện!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
        }
      }));
    }
  }

  private void createDefaultTranslation(boolean advanceToStep2) {
    api.get(administrator ? "/admin/teams?page=0&size=50" : "/me/teams?page=0&size=50")
        .whenComplete((teamsReply, teamsError) -> SwingUtilities.invokeLater(() -> {
      if (teamsError != null || teamsReply == null) {
        JOptionPane.showMessageDialog(this, "Không thể tải nhóm sở hữu: " + teamsError, "Lỗi", JOptionPane.ERROR_MESSAGE);
        return;
      }
      JsonNode teamItems = teamsReply.body().path("items");
      if (!teamItems.isArray() || teamItems.isEmpty()) {
        JOptionPane.showMessageDialog(this, "Cần có nhóm dịch để tạo bản dịch và chương.", "Thiếu nhóm", JOptionPane.WARNING_MESSAGE);
        return;
      }
      String[] labels = new String[teamItems.size()];
      for (int i = 0; i < teamItems.size(); i++) labels[i] = teamItems.get(i).path("name").asText() + " (#" + teamItems.get(i).path("id").asText() + ")";
      String selected = (String) JOptionPane.showInputDialog(this, "Chọn nhóm sở hữu bản dịch:", "Nhóm dịch",
          JOptionPane.QUESTION_MESSAGE, null, labels, labels[0]);
      if (selected == null) return;
      int teamIndex = java.util.Arrays.asList(labels).indexOf(selected);
      var transBody = new HashMap<String, Object>();
      transBody.put("teamId", teamItems.get(teamIndex).path("id").asText());
      transBody.put("languageTag", "vi");
      transBody.put("editionKey", "default-edition");
      transBody.put("titleOverride", null);

    api.create("/comics/" + comicId + "/translations", transBody, UUID.randomUUID().toString()).whenComplete((tReply, tErr) -> {
      SwingUtilities.invokeLater(() -> {
        if (tReply == null || tErr != null) {
          JOptionPane.showMessageDialog(this, "Không thể tạo bản dịch: " + tErr, "Lỗi", JOptionPane.ERROR_MESSAGE);
          return;
        }
        this.translationId = tReply.body().path("id").asText();
        if (advanceToStep2) {
          loadChaptersForStep2();
          wizardLayout.show(stepHost, "STEP2");
        } else {
          JOptionPane.showMessageDialog(this, "Đã lưu bản nháp tác phẩm thành công!", "Thành công", JOptionPane.INFORMATION_MESSAGE);
        }
      });
    });
    }));
  }

  // ==================== STEP 2: CHAPTERS (Mockup 03) ====================
  private void buildStep2() {
    step2View.setOpaque(false);
    step2View.add(buildWizardHeader(2, "Quản lý chương", "Bước 2/3: Thêm chương, tải ảnh trang hoặc tải từ file nén ZIP."), BorderLayout.NORTH);

    JPanel masterDetail = new JPanel(new BorderLayout(18, 0));
    masterDetail.setOpaque(false);

    // Left Master: Chapter list
    JPanel masterCol = new JPanel(new BorderLayout(0, 10));
    masterCol.setPreferredSize(new Dimension(280, 0));
    masterCol.setBackground(Theme.SURFACE);
    masterCol.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER, 1, true),
        new EmptyBorder(14, 14, 14, 14)));

    JPanel masterTop = new JPanel();
    masterTop.setLayout(new BoxLayout(masterTop, BoxLayout.Y_AXIS));
    masterTop.setOpaque(false);

    JLabel masterTitle = new JLabel("Danh sách chương");
    masterTitle.setFont(Theme.heading(15));
    masterTitle.setForeground(Theme.TEXT);
    masterTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
    masterTop.add(masterTitle);
    masterTop.add(Box.createVerticalStrut(8));

    JPanel masterBtns = new JPanel(new GridLayout(1, 2, 8, 0));
    masterBtns.setOpaque(false);
    masterBtns.setAlignmentX(Component.LEFT_ALIGNMENT);
    masterBtns.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
    masterBtns.setPreferredSize(new Dimension(252, 32));

    JButton addChapterBtn = new JButton("+ Thêm");
    addChapterBtn.setFont(Theme.heading(12));
    addChapterBtn.setBackground(Theme.ACTION);
    addChapterBtn.setForeground(Color.WHITE);
    addChapterBtn.setFocusPainted(false);
    addChapterBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    addChapterBtn.addActionListener(e -> createNewChapter());

    JButton delChapterBtn = new JButton("🗑 Xóa");
    delChapterBtn.setFont(Theme.label(12));
    delChapterBtn.setBackground(Theme.SURFACE);
    delChapterBtn.setForeground(new Color(0xDC, 0x26, 0x26));
    delChapterBtn.setFocusPainted(false);
    delChapterBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    delChapterBtn.setToolTipText("Xóa chương đang chọn");
    delChapterBtn.addActionListener(e -> deleteCurrentChapter());

    masterBtns.add(addChapterBtn);
    masterBtns.add(delChapterBtn);
    masterTop.add(masterBtns);
    masterCol.add(masterTop, BorderLayout.NORTH);

    chapterJList.setFont(Theme.label(13));
    chapterJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    chapterJList.addListSelectionListener(e -> {
      ChapterItem selected = chapterJList.getSelectedValue();
      if (selected != null) {
        selectChapter(selected);
      }
    });

    JScrollPane listScroll = new JScrollPane(chapterJList);
    listScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1, true));
    masterCol.add(listScroll, BorderLayout.CENTER);
    masterDetail.add(masterCol, BorderLayout.WEST);

    // Right Detail: Chapter settings + Upload Dropzone
    JPanel detailCol = new JPanel();
    detailCol.setLayout(new BoxLayout(detailCol, BoxLayout.Y_AXIS));
    detailCol.setBackground(Theme.SURFACE);
    detailCol.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER, 1, true),
        new EmptyBorder(18, 20, 18, 20)));

    // Settings Row
    JPanel settingsRow = new JPanel(new GridLayout(1, 3, 14, 0));
    settingsRow.setOpaque(false);
    settingsRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
    settingsRow.setAlignmentX(Component.LEFT_ALIGNMENT);

    settingsRow.add(buildFieldBox("Tên chương *", chapterTitleField));
    settingsRow.add(buildFieldBox("Số thứ tự chương *", chapterNumField));

    ButtonGroup bg = new ButtonGroup();
    bg.add(freeRadio);
    bg.add(coinRadio);
    JPanel radioBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    radioBox.setOpaque(false);
    radioBox.add(freeRadio);
    radioBox.add(coinRadio);
    settingsRow.add(buildFieldBox("Chính sách truy cập", radioBox));
    detailCol.add(settingsRow);
    detailCol.add(Box.createVerticalStrut(16));

    // Upload Dropzone Area (Mockup 03)
    JPanel dropzone = new JPanel();
    dropzone.setLayout(new BoxLayout(dropzone, BoxLayout.Y_AXIS));
    dropzone.setBackground(new Color(0xFA, 0xFA, 0xFD));
    dropzone.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createDashedBorder(Theme.BORDER, 4, 3),
        new EmptyBorder(20, 20, 20, 20)));
    dropzone.setAlignmentX(Component.LEFT_ALIGNMENT);
    dropzone.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

    JLabel uploadIcon = new JLabel(UiIcons.upload(32, Theme.PRIMARY));
    uploadIcon.setAlignmentX(Component.CENTER_ALIGNMENT);

    JLabel dropText = new JLabel("Kéo và thả ảnh vào đây để tải lên");
    dropText.setFont(Theme.heading(14));
    dropText.setForeground(Theme.TEXT);
    dropText.setAlignmentX(Component.CENTER_ALIGNMENT);

    JLabel dropSub = new JLabel("Chấp nhận JPG, PNG, WebP · Tự động sắp xếp natural sort 1, 2, 3... 10");
    dropSub.setFont(Theme.label(12));
    dropSub.setForeground(Theme.MUTED);
    dropSub.setAlignmentX(Component.CENTER_ALIGNMENT);

    JPanel dropBtns = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
    dropBtns.setOpaque(false);

    JButton multiImgBtn = new JButton("🖼 Chọn nhiều ảnh");
    multiImgBtn.setFont(Theme.heading(12));
    multiImgBtn.setBackground(Theme.ACTION);
    multiImgBtn.setForeground(Color.WHITE);
    multiImgBtn.setFocusPainted(false);
    multiImgBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    multiImgBtn.addActionListener(e -> openBulkUploadDialog());

    JButton zipBtn = new JButton("↑ Tải từ ZIP");
    zipBtn.setFont(Theme.label(12));
    zipBtn.setBackground(Theme.SURFACE);
    zipBtn.setFocusPainted(false);
    zipBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    zipBtn.addActionListener(e -> openBulkUploadDialog());

    dropBtns.add(multiImgBtn);
    dropBtns.add(zipBtn);

    dropzone.add(uploadIcon);
    dropzone.add(Box.createVerticalStrut(6));
    dropzone.add(dropText);
    dropzone.add(Box.createVerticalStrut(2));
    dropzone.add(dropSub);
    dropzone.add(Box.createVerticalStrut(10));
    dropzone.add(dropBtns);
    detailCol.add(dropzone);
    detailCol.add(Box.createVerticalStrut(14));

    // Pages preview strip
    JPanel pagesHeader = new JPanel(new BorderLayout());
    pagesHeader.setOpaque(false);
    pagesHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
    pagesHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
    pagesHeader.setPreferredSize(new Dimension(500, 32));

    JLabel pagesTitle = new JLabel("Trang ảnh trong chương:");
    pagesTitle.setFont(Theme.heading(13));
    pagesTitle.setForeground(Theme.TEXT);
    pagesHeader.add(pagesTitle, BorderLayout.WEST);

    JButton clearPagesBtn = new JButton("🗑 Xóa tất cả trang");
    clearPagesBtn.setFont(Theme.label(12));
    clearPagesBtn.setForeground(new Color(0xDC, 0x26, 0x26));
    clearPagesBtn.setBackground(Theme.SURFACE);
    clearPagesBtn.setFocusPainted(false);
    clearPagesBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    clearPagesBtn.setToolTipText("Xóa toàn bộ trang ảnh đã tải của chương này");
    clearPagesBtn.addActionListener(e -> removeAllPages());
    pagesHeader.add(clearPagesBtn, BorderLayout.EAST);

    detailCol.add(pagesHeader);
    detailCol.add(Box.createVerticalStrut(6));

    pageThumbnailsPanel.setOpaque(false);
    JScrollPane pagesScroll = new JScrollPane(pageThumbnailsPanel);
    pagesScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1, true));
    pagesScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
    detailCol.add(pagesScroll);

    masterDetail.add(detailCol, BorderLayout.CENTER);
    step2View.add(masterDetail, BorderLayout.CENTER);

    // Step 2 Footer
    JPanel footer = new JPanel(new BorderLayout());
    footer.setOpaque(false);
    footer.setBorder(new EmptyBorder(14, 0, 0, 0));

    JButton backBtn = new JButton("< Quay lại thông tin");
    backBtn.setFont(Theme.label(13));
    backBtn.setFocusPainted(false);
    backBtn.addActionListener(e -> wizardLayout.show(stepHost, "STEP1"));
    footer.add(backBtn, BorderLayout.WEST);

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    actions.setOpaque(false);

    JButton saveDraftBtn = new JButton("Lưu bản nháp");
    saveDraftBtn.setFont(Theme.label(13));
    saveDraftBtn.setFocusPainted(false);
    saveDraftBtn.addActionListener(e -> saveCurrentChapter());

    JButton nextStepBtn = new JButton("Tiếp tục: Kiểm tra & gửi duyệt →");
    nextStepBtn.setFont(Theme.heading(13));
    nextStepBtn.setBackground(Theme.ACTION);
    nextStepBtn.setForeground(Color.WHITE);
    nextStepBtn.setFocusPainted(false);
    nextStepBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    nextStepBtn.addActionListener(e -> saveCurrentChapter(() -> {
      prepareStep3();
      wizardLayout.show(stepHost, "STEP3");
    }));

    actions.add(saveDraftBtn);
    actions.add(nextStepBtn);
    footer.add(actions, BorderLayout.EAST);
    step2View.add(footer, BorderLayout.SOUTH);
  }

  private void loadChaptersForStep2() {
    if (translationId == null) return;
    api.get("/publishing/translations/" + translationId + "/chapters?page=0&size=50").whenComplete((reply, err) -> {
      SwingUtilities.invokeLater(() -> {
        if (err != null || reply == null) return;
        chapterListModel.clear();
        JsonNode items = reply.body().path("items");
        if (items.isArray()) {
          for (JsonNode item : items) {
            String chId = item.path("id").asText();
            String title = item.path("title").asText("Chương");
            String num = item.path("displayNumber").asText("1");
            String pol = item.path("accessPolicy").asText("FREE");
            chapterListModel.addElement(new ChapterItem(chId, title, num, pol));
          }
        }
        if (chapterListModel.isEmpty()) {
          createNewChapter();
        } else {
          chapterJList.setSelectedIndex(0);
        }
      });
    });
  }

  private void createNewChapter() {
    if (translationId == null) {
      JOptionPane.showMessageDialog(this, "Lưu thông tin truyện trước khi thêm chương.", "Thiếu dữ liệu", JOptionPane.WARNING_MESSAGE);
      return;
    }
    int nextNum = 1;
    for (int i = 0; i < chapterListModel.size(); i++) {
      try { nextNum = Math.max(nextNum, Integer.parseInt(chapterListModel.get(i).number) + 1); }
      catch (NumberFormatException ignored) { /* Backend validates nonnumeric sort orders. */ }
    }
    final int chapterNumber = nextNum;
    var body = Map.of(
        "displayNumber", String.valueOf(chapterNumber),
        "title", "Chương " + chapterNumber,
        "sortOrder", String.valueOf(chapterNumber),
        "accessPolicy", "FREE");

    api.create("/translations/" + translationId + "/chapters", body, UUID.randomUUID().toString()).whenComplete((reply, err) -> {
      SwingUtilities.invokeLater(() -> {
        if (reply != null && err == null) {
          String chId = reply.body().path("id").asText();
          ChapterItem item = new ChapterItem(chId, "Chương " + chapterNumber, String.valueOf(chapterNumber), "FREE");
          chapterListModel.addElement(item);
          chapterJList.setSelectedValue(item, true);
        } else {
          JOptionPane.showMessageDialog(this, "Không thể tạo chương: " + err, "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
      });
    });
  }

  private void selectChapter(ChapterItem item) {
    this.activeChapterId = item.id;
    chapterTitleField.setText(item.title);
    chapterNumField.setText(item.number);
    freeRadio.setSelected("FREE".equals(item.policy));
    coinRadio.setSelected(!"FREE".equals(item.policy));

    // Fetch or create draft version for chapter
    api.get("/publishing/chapters/" + item.id + "/versions").whenComplete((reply, err) -> {
      SwingUtilities.invokeLater(() -> {
        if (reply != null && err == null) {
          JsonNode versions = reply.body().path("items");
          JsonNode draftVersion = null;
          for (JsonNode v : versions) {
            String state = v.path("state").asText();
            if ("DRAFT".equals(state) || "NEEDS_CHANGES".equals(state)) {
              draftVersion = v;
              break;
            }
          }

          if (draftVersion != null) {
            this.activeVersionId = draftVersion.path("id").asText();
            item.versionId = activeVersionId;
            loadVersionPages(activeVersionId, item);
          } else {
            // Create a new draft version
            createDraftVersion(item);
          }
        }
      });
    });
  }

  private void createDraftVersion(ChapterItem item) {
    api.create("/chapters/" + item.id + "/versions", Map.of(), UUID.randomUUID().toString()).whenComplete((reply, err) -> {
      SwingUtilities.invokeLater(() -> {
        if (reply != null && err == null) {
          this.activeVersionId = reply.body().path("id").asText();
          item.versionId = activeVersionId;
          loadVersionPages(activeVersionId, item);
        }
      });
    });
  }

  private void loadVersionPages(String versionId, ChapterItem item) {
    api.get("/publishing/versions/" + versionId).whenComplete((reply, err) -> {
      SwingUtilities.invokeLater(() -> {
        if (err != null || reply == null) return;
        this.activeVersionEtag = reply.etag();
        pageThumbnailsPanel.removeAll();
        currentVersionPages.clear();

        JsonNode pages = reply.body().path("pages");
        if (pages.isArray()) {
          for (JsonNode p : pages) {
            currentVersionPages.add(p);
            int pos = p.path("position").asInt(1);
            String pageId = p.path("pageId").asText();

            JPanel pageBadge = new JPanel(new BorderLayout(4, 0));
            pageBadge.setOpaque(true);
            pageBadge.setBackground(new Color(0xF8, 0xFA, 0xFC));
            pageBadge.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.PRIMARY, 1, true),
                new EmptyBorder(3, 8, 3, 5)));

            JLabel posLabel = new JLabel("Trang " + pos);
            posLabel.setFont(Theme.heading(12));
            posLabel.setForeground(Theme.TEXT);
            pageBadge.add(posLabel, BorderLayout.CENTER);

            JButton delBtn = new JButton("×");
            delBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
            delBtn.setForeground(new Color(0xDC, 0x26, 0x26));
            delBtn.setContentAreaFilled(false);
            delBtn.setBorderPainted(false);
            delBtn.setFocusPainted(false);
            delBtn.setMargin(new Insets(0, 2, 0, 0));
            delBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            delBtn.setToolTipText("Xóa trang này");
            delBtn.addActionListener(e -> removeSinglePage(versionId, pageId, item));
            pageBadge.add(delBtn, BorderLayout.EAST);

            pageThumbnailsPanel.add(pageBadge);
          }
        }
        item.pageCount = currentVersionPages.size();
        chapterJList.repaint();
        pageThumbnailsPanel.revalidate();
        pageThumbnailsPanel.repaint();
      });
    });
  }

  private void openBulkUploadDialog() {
    if (activeVersionId == null) return;
    Window owner = SwingUtilities.getWindowAncestor(this);
    BulkImageUploadDialog dlg = new BulkImageUploadDialog(
        owner,
        api,
        activeVersionId,
        chapterTitleField.getText(),
        activeVersionEtag,
        () -> {
          ChapterItem sel = chapterJList.getSelectedValue();
          if (sel != null) loadVersionPages(activeVersionId, sel);
        });
    dlg.setVisible(true);
  }

  private void saveCurrentChapter() {
    saveCurrentChapter(null);
  }

  private void saveCurrentChapter(Runnable afterSave) {
    ChapterItem sel = chapterJList.getSelectedValue();
    if (sel == null) {
      JOptionPane.showMessageDialog(this, "Chưa có chương để lưu.", "Thiếu dữ liệu", JOptionPane.WARNING_MESSAGE);
      return;
    }

    String title = chapterTitleField.getText().trim();
    String number = chapterNumField.getText().trim();
    if (title.isEmpty() || number.isEmpty()) {
      JOptionPane.showMessageDialog(this, "Nhập tên và số thứ tự chương.", "Thiếu dữ liệu", JOptionPane.WARNING_MESSAGE);
      return;
    }
    if (!number.matches("[0-9]{1,8}(\\.[0-9]{1,4})?")) {
      JOptionPane.showMessageDialog(this, "Số thứ tự chương phải là số dương hợp lệ.", "Dữ liệu không hợp lệ", JOptionPane.WARNING_MESSAGE);
      return;
    }
    for (int i = 0; i < chapterListModel.size(); i++) {
      ChapterItem other = chapterListModel.get(i);
      if (other != sel && number.equals(other.number)) {
        JOptionPane.showMessageDialog(this, "Số thứ tự chương đã tồn tại.", "Trùng chương", JOptionPane.WARNING_MESSAGE);
        return;
      }
    }

    var body = Map.of(
        "title", title,
        "displayNumber", number,
        "sortOrder", number,
        "accessPolicy", freeRadio.isSelected() ? "FREE" : "RESTRICTED");

    api.get("/publishing/chapters/" + sel.id).thenCompose(current ->
        api.request("PATCH", "/chapters/" + sel.id, body, current.etag()))
        .whenComplete((reply, err) -> SwingUtilities.invokeLater(() -> {
          if (err != null || reply == null) {
            JOptionPane.showMessageDialog(this, "Không thể lưu chương: " + err, "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
          }
          sel.title = title;
          sel.number = number;
          sel.policy = freeRadio.isSelected() ? "FREE" : "RESTRICTED";
          chapterJList.repaint();
          if (afterSave != null) afterSave.run();
        }));
  }

  // ==================== STEP 3: REVIEW / PUBLISH (Mockup 05 / 10) ====================
  private void buildStep3() {
    step3View.setOpaque(false);
    step3View.add(buildWizardHeader(
        3,
        administrator ? "Xuất bản truyện" : "Kiểm tra trước khi gửi",
        administrator
            ? "Xem lại thông tin và xuất bản ngay hoặc lên lịch xuất bản."
            : "Bước 3/3: Xem lại thông tin, chương và gửi Admin duyệt trước khi xuất bản."),
        BorderLayout.NORTH);

    JPanel center = new JPanel(new BorderLayout(20, 0));
    center.setOpaque(false);

    // Left Column: Comic & Chapters Summary
    JPanel leftCol = new JPanel();
    leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));
    leftCol.setOpaque(false);

    JPanel comicSummary = new JPanel(new BorderLayout(14, 0));
    comicSummary.setBackground(Theme.SURFACE);
    comicSummary.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER, 1, true),
        new EmptyBorder(16, 16, 16, 16)));
    comicSummary.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

    reviewCover.setPreferredSize(new Dimension(70, 100));
    comicSummary.add(reviewCover, BorderLayout.WEST);

    JPanel meta = new JPanel(new GridLayout(0, 1, 0, 3));
    meta.setOpaque(false);
    reviewComicTitle.setFont(Theme.heading(18));
    reviewComicTitle.setForeground(Theme.TEXT);
    reviewMetaLbl.setFont(Theme.label(12));
    reviewMetaLbl.setForeground(Theme.PRIMARY);
    reviewDesc.setFont(Theme.label(12));
    reviewDesc.setForeground(Theme.MUTED);
    reviewDesc.setEditable(false);
    reviewDesc.setOpaque(false);
    meta.add(reviewComicTitle);
    meta.add(reviewMetaLbl);
    meta.add(reviewDesc);
    comicSummary.add(meta, BorderLayout.CENTER);
    leftCol.add(comicSummary);
    leftCol.add(Box.createVerticalStrut(16));

    // Chapter List Table
    JPanel chapCard = new JPanel(new BorderLayout(0, 8));
    chapCard.setBackground(Theme.SURFACE);
    chapCard.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER, 1, true),
        new EmptyBorder(14, 16, 14, 16)));

    JLabel chapHead = new JLabel("Danh sách chương sẵn sàng");
    chapHead.setFont(Theme.heading(15));
    chapHead.setForeground(Theme.TEXT);
    chapCard.add(chapHead, BorderLayout.NORTH);

    reviewChapterTable.setRowHeight(40);
    reviewChapterTable.setFont(Theme.label(13));
    reviewChapterTable.getTableHeader().setFont(Theme.heading(12));

    reviewChapterTable.getColumnModel().getColumn(4).setCellRenderer((t, val, sel, foc, row, col) -> {
      JButton btn = new JButton("👁 Xem trước");
      btn.setFont(Theme.label(12));
      btn.setBackground(Theme.SURFACE);
      return btn;
    });

    reviewChapterTable.getColumnModel().getColumn(4).setCellEditor(new DefaultCellEditor(new JCheckBox()) {
      private JButton btn;
      private int selRow;
      @Override public Component getTableCellEditorComponent(JTable t, Object val, boolean sel, int row, int col) {
        selRow = row;
        btn = new JButton("👁 Xem trước");
        btn.setFont(Theme.label(12));
        btn.addActionListener(e -> {
          fireEditingStopped();
          if (selRow >= 0 && selRow < chapterListModel.size()) {
            ChapterItem item = chapterListModel.get(selRow);
            if (item.versionId != null) {
              ChapterPreviewDialog dlg = new ChapterPreviewDialog(
                  SwingUtilities.getWindowAncestor(ComicEditorWizard.this),
                  api,
                  item.versionId,
                  item.title);
              dlg.setVisible(true);
            }
          }
        });
        return btn;
      }
      @Override public Object getCellEditorValue() { return ""; }
    });

    JScrollPane scroll = new JScrollPane(reviewChapterTable);
    scroll.setBorder(null);
    chapCard.add(scroll, BorderLayout.CENTER);
    leftCol.add(chapCard);
    center.add(leftCol, BorderLayout.CENTER);

    // Right Column: Creator checklist or Admin publish settings
    JPanel rightCol = new JPanel();
    rightCol.setLayout(new BoxLayout(rightCol, BoxLayout.Y_AXIS));
    rightCol.setPreferredSize(new Dimension(320, 0));
    rightCol.setBackground(Theme.SURFACE);
    rightCol.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Theme.BORDER, 1, true),
        new EmptyBorder(18, 20, 18, 20)));

    if (administrator) {
      // Mockup 10: Admin Direct Publishing Options
      JLabel adminBadge = new JLabel("👑 Quyền Admin: Xuất bản trực tiếp");
      adminBadge.setFont(Theme.heading(13));
      adminBadge.setForeground(new Color(0x7C, 0x3A, 0xED));
      adminBadge.setAlignmentX(Component.LEFT_ALIGNMENT);

      JLabel optsTitle = new JLabel("Thiết lập xuất bản");
      optsTitle.setFont(Theme.heading(16));
      optsTitle.setForeground(Theme.TEXT);
      optsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

      ButtonGroup abg = new ButtonGroup();
      abg.add(adminPublishNowRadio);
      abg.add(adminScheduleRadio);
      adminPublishNowRadio.setFont(Theme.label(13));
      adminPublishNowRadio.setAlignmentX(Component.LEFT_ALIGNMENT);
      adminScheduleRadio.setFont(Theme.label(13));
      adminScheduleRadio.setAlignmentX(Component.LEFT_ALIGNMENT);

      JPanel notice = new JPanel(new BorderLayout(8, 0));
      notice.setBackground(new Color(0xEC, 0xFD, 0xF5));
      notice.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(new Color(0xA7, 0xF3, 0xD0), 1, true),
          new EmptyBorder(10, 12, 10, 12)));
      notice.setAlignmentX(Component.LEFT_ALIGNMENT);
      notice.add(new JLabel(UiIcons.check(16, new Color(0x05, 0x96, 0x69))), BorderLayout.WEST);
      notice.add(new JLabel("<html><body style='width: 200px; color: #065F46; font-size: 11px;'>"
          + "Nội dung do Admin xuất bản sẽ được duyệt tự động mà không cần nộp hàng chờ."
          + "</body></html>"), BorderLayout.CENTER);

      rightCol.add(adminBadge);
      rightCol.add(Box.createVerticalStrut(10));
      rightCol.add(optsTitle);
      rightCol.add(Box.createVerticalStrut(14));
      rightCol.add(adminPublishNowRadio);
      rightCol.add(Box.createVerticalStrut(6));
      rightCol.add(adminScheduleRadio);
      rightCol.add(Box.createVerticalStrut(20));
      rightCol.add(notice);
      rightCol.add(Box.createVerticalGlue());
    } else {
      // Mockup 05: Creator Review Checklist
      JLabel checkTitle = new JLabel("Kiểm tra trước khi gửi");
      checkTitle.setFont(Theme.heading(16));
      checkTitle.setForeground(Theme.TEXT);
      checkTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

      JPanel checks = new JPanel(new GridLayout(0, 1, 0, 8));
      checks.setOpaque(false);
      checks.setAlignmentX(Component.LEFT_ALIGNMENT);
      checks.add(buildCheckItem("Thông tin truyện đầy đủ"));
      checks.add(buildCheckItem("Các chương đã có ảnh sẵn sàng"));
      checks.add(buildCheckItem("Nội dung tuân thủ chính sách Comic Net"));

      JPanel warning = new JPanel(new BorderLayout(8, 0));
      warning.setBackground(new Color(0xFE, 0xF3, 0xC7));
      warning.setBorder(BorderFactory.createCompoundBorder(
          BorderFactory.createLineBorder(new Color(0xFC, 0xD3, 0x4D), 1, true),
          new EmptyBorder(8, 10, 8, 10)));
      warning.setAlignmentX(Component.LEFT_ALIGNMENT);
      warning.add(new JLabel(UiIcons.alertCircle(16, new Color(0xD9, 0x77, 0x06))), BorderLayout.WEST);
      warning.add(new JLabel("<html><body style='width: 200px; color: #92400E; font-size: 10px;'>"
          + "Lưu ý: Sau khi gửi, Admin sẽ kiểm duyệt. Bạn không thể chỉnh sửa trong khi đang chờ duyệt."
          + "</body></html>"), BorderLayout.CENTER);

      JLabel noteHead = Theme.plainLabel("Ghi chú cho Admin (tùy chọn):");
      noteHead.setFont(Theme.label(12));
      noteHead.setForeground(Theme.TEXT);
      noteHead.setAlignmentX(Component.LEFT_ALIGNMENT);

      noteForAdminArea.setFont(Theme.label(12));
      noteForAdminArea.setLineWrap(true);
      noteForAdminArea.setWrapStyleWord(true);
      JScrollPane noteScroll = new JScrollPane(noteForAdminArea);
      noteScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1, true));
      noteScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
      noteScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));

      rightCol.add(checkTitle);
      rightCol.add(Box.createVerticalStrut(14));
      rightCol.add(checks);
      rightCol.add(Box.createVerticalStrut(16));
      rightCol.add(warning);
      rightCol.add(Box.createVerticalStrut(14));
      rightCol.add(noteHead);
      rightCol.add(Box.createVerticalStrut(4));
      rightCol.add(noteScroll);
      rightCol.add(Box.createVerticalGlue());
    }

    center.add(rightCol, BorderLayout.EAST);
    step3View.add(center, BorderLayout.CENTER);

    // Step 3 Footer
    JPanel footer = new JPanel(new BorderLayout());
    footer.setOpaque(false);
    footer.setBorder(new EmptyBorder(14, 0, 0, 0));

    JButton backBtn = new JButton("< Quay lại sửa");
    backBtn.setFont(Theme.label(13));
    backBtn.setFocusPainted(false);
    backBtn.addActionListener(e -> wizardLayout.show(stepHost, "STEP2"));
    footer.add(backBtn, BorderLayout.WEST);

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    actions.setOpaque(false);

    JButton saveDraftBtn = new JButton("Lưu nháp");
    saveDraftBtn.setFont(Theme.label(13));
    saveDraftBtn.setFocusPainted(false);
    saveDraftBtn.addActionListener(e -> onCompleteOrCancel.run());

    JButton finalActionBtn = new JButton(administrator ? "✓ Xuất bản ngay" : "✈ Gửi Admin duyệt");
    finalActionBtn.setFont(Theme.heading(13));
    finalActionBtn.setBackground(Theme.ACTION);
    finalActionBtn.setForeground(Color.WHITE);
    finalActionBtn.setFocusPainted(false);
    finalActionBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    finalActionBtn.addActionListener(e -> {
      if (administrator) {
        doAdminDirectPublish();
      } else {
        doCreatorSubmitForReview();
      }
    });

    actions.add(saveDraftBtn);
    actions.add(finalActionBtn);
    footer.add(actions, BorderLayout.EAST);
    step3View.add(footer, BorderLayout.SOUTH);
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

  private void prepareStep3() {
    reviewComicTitle.setText(titleField.getText());
    reviewMetaLbl.setText((String) typeCombo.getSelectedItem() + " · Tiếng Việt");
    reviewDesc.setText(descArea.getText());

    reviewChapterModel.setRowCount(0);
    for (int i = 0; i < chapterListModel.size(); i++) {
      ChapterItem item = chapterListModel.get(i);
      reviewChapterModel.addRow(new Object[] {
        item.title,
        item.pageCount + " trang",
        "FREE".equals(item.policy) ? "Miễn phí" : "10 xu",
        "Sẵn sàng",
        ""
      });
    }
  }

  private void doCreatorSubmitForReview() {
    if (activeVersionId == null) {
      JOptionPane.showMessageDialog(this, "Chưa có bản nháp để gửi duyệt.", "Thông báo", JOptionPane.WARNING_MESSAGE);
      return;
    }

    int conf = JOptionPane.showConfirmDialog(
        this,
        "Xác nhận gửi toàn bộ nội dung lên Ban Quản Trị (Admin) để kiểm duyệt?",
        "Gửi Admin duyệt",
        JOptionPane.YES_NO_OPTION);
    if (conf != JOptionPane.YES_OPTION) return;

    api.request("POST", "/publishing/versions/" + activeVersionId + "/submit", Map.of(), activeVersionEtag)
        .whenComplete((reply, err) -> {
          SwingUtilities.invokeLater(() -> {
            if (err != null || reply == null) {
              JOptionPane.showMessageDialog(this, "Không thể gửi duyệt: " + (reply != null ? reply.body() : err), "Lỗi", JOptionPane.ERROR_MESSAGE);
            } else {
              JOptionPane.showMessageDialog(this, "Đã gửi bản xét duyệt thành công! Admin sẽ phản hồi trong 24 giờ.", "Thành công", JOptionPane.INFORMATION_MESSAGE);
              onCompleteOrCancel.run();
            }
          });
        });
  }

  private void doAdminDirectPublish() {
    if (comicId == null || chapterListModel.isEmpty()) {
      JOptionPane.showMessageDialog(this, "Chưa có thông tin truyện hoặc chương để xuất bản.", "Thông báo", JOptionPane.WARNING_MESSAGE);
      return;
    }

    int conf = JOptionPane.showConfirmDialog(
        this,
        "Quyền Administrator: Xuất bản và công khai truyện này lên Trang chủ Comic Net ngay bây giờ?",
        "Xác nhận xuất bản trực tiếp",
        JOptionPane.YES_NO_OPTION);
    if (conf != JOptionPane.YES_OPTION) return;

    // 1. Publish all chapters that have a version
    List<ChapterItem> toPublish = new ArrayList<>();
    for (int i = 0; i < chapterListModel.size(); i++) {
      ChapterItem it = chapterListModel.get(i);
      if (it.versionId != null) toPublish.add(it);
    }

    if (toPublish.isEmpty() && activeChapterId != null && activeVersionId != null) {
      ChapterItem active = chapterJList.getSelectedValue();
      if (active != null) {
        active.versionId = activeVersionId;
        toPublish.add(active);
      }
    }

    publishChaptersSequentially(toPublish, 0, () -> {
      // 2. Set Comic.Visibility = 'PUBLIC' via PUT /admin/comics/{comicId}/visibility
      api.get("/publishing/comics/" + comicId).whenComplete((cReply, cErr) -> {
        String cEtag = cReply != null ? cReply.etag() : "";
        api.request("PATCH", "/comics/" + comicId, Map.of("ageRating", "ALL_AGE"), cEtag).whenComplete((pReply, pErr) -> {
          String latestEtag = pReply != null ? pReply.etag() : cEtag;
          var visBody = Map.of("visibility", "PUBLIC", "reason", "Admin direct published");
          api.request("PUT", "/admin/comics/" + comicId + "/visibility", visBody, latestEtag, Map.of()).whenComplete((vReply, vErr) -> {
          // 3. Set Translation.Visibility = 'PUBLIC' via PATCH /translations/{translationId}
          if (translationId != null) {
            api.get("/publishing/translations/" + translationId).whenComplete((tReply, tErr) -> {
              String tEtag = tReply != null ? tReply.etag() : "";
              api.request("PATCH", "/translations/" + translationId, Map.of("visibility", "PUBLIC"), tEtag, Map.of())
                  .whenComplete((trReply, trErr) -> {
                    SwingUtilities.invokeLater(() -> {
                      JOptionPane.showMessageDialog(this, "Đã xuất bản và công khai truyện lên Trang chủ thành công!", "Xuất bản thành công", JOptionPane.INFORMATION_MESSAGE);
                      onCompleteOrCancel.run();
                    });
                  });
            });
          } else {
            SwingUtilities.invokeLater(() -> {
              JOptionPane.showMessageDialog(this, "Đã xuất bản và công khai truyện lên Trang chủ thành công!", "Xuất bản thành công", JOptionPane.INFORMATION_MESSAGE);
              onCompleteOrCancel.run();
            });
          }
        });
        });
      });
    });
  }

  private void publishChaptersSequentially(List<ChapterItem> list, int index, Runnable onFinished) {
    if (index >= list.size()) {
      onFinished.run();
      return;
    }
    ChapterItem it = list.get(index);
    api.get("/publishing/chapters/" + it.id).whenComplete((chReply, chErr) -> {
      String etag = chReply != null ? chReply.etag() : "";
      var body = Map.of("versionId", it.versionId);
      api.request("POST", "/publishing/chapters/" + it.id + "/publish", body, etag, Map.of())
          .whenComplete((pReply, pErr) -> {
            publishChaptersSequentially(list, index + 1, onFinished);
          });
    });
  }

  public void startNewComic() {
    this.comicId = null;
    this.translationId = null;
    this.activeChapterId = null;
    this.activeVersionId = null;
    titleField.setText("");
    altTitleField.setText("");
    authorField.setText("");
    descArea.setText("");
    chapterListModel.clear();
    wizardLayout.show(stepHost, "STEP1");
  }

  public void editComic(String comicId, String title, String type, String desc) {
    this.comicId = comicId;
    titleField.setText(title);
    descArea.setText(desc);
    typeCombo.setSelectedItem(type);

    // Fetch translations
    api.get("/publishing/comics/" + comicId + "/translations").whenComplete((reply, err) -> {
      if (reply != null && err == null) {
        JsonNode trans = reply.body().path("items");
        if (trans.isArray() && !trans.isEmpty()) {
          this.translationId = trans.get(0).path("id").asText();
        }
      }
    });

    wizardLayout.show(stepHost, "STEP1");
  }

  public void openChapterStep(String comicId, String translationId) {
    this.comicId = comicId;
    this.translationId = translationId;
    loadChaptersForStep2();
    wizardLayout.show(stepHost, "STEP2");
  }

  private void removeSinglePage(String versionId, String pageId, ChapterItem item) {
    if (activeVersionEtag == null) return;
    int confirm = JOptionPane.showConfirmDialog(this,
        "Bạn có chắc muốn xóa trang ảnh này không?",
        "Xác nhận xóa trang",
        JOptionPane.YES_NO_OPTION,
        JOptionPane.QUESTION_MESSAGE);
    if (confirm != JOptionPane.YES_OPTION) return;

    api.request("DELETE", "/publishing/versions/" + versionId + "/pages/" + pageId, null, activeVersionEtag, Map.of())
        .whenComplete((reply, err) -> {
          SwingUtilities.invokeLater(() -> {
            if (err != null) {
              Throwable cause = ApiClient.unwrap(err);
              JOptionPane.showMessageDialog(this, "Không thể xóa trang: " + cause.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            } else {
              loadVersionPages(versionId, item);
            }
          });
        });
  }

  private void removeAllPages() {
    if (activeVersionId == null || currentVersionPages.isEmpty()) {
      JOptionPane.showMessageDialog(this, "Chương hiện chưa có trang ảnh nào để xóa.", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    ChapterItem sel = chapterJList.getSelectedValue();
    if (sel == null) return;

    int confirm = JOptionPane.showConfirmDialog(this,
        "Bạn có chắc chắn muốn xóa toàn bộ " + currentVersionPages.size() + " trang ảnh trong chương này không?",
        "Xác nhận xóa tất cả trang",
        JOptionPane.YES_NO_OPTION,
        JOptionPane.WARNING_MESSAGE);
    if (confirm != JOptionPane.YES_OPTION) return;

    deletePagesSequentially(activeVersionId, new java.util.ArrayList<>(currentVersionPages), 0, sel);
  }

  private void deletePagesSequentially(String versionId, java.util.List<JsonNode> pagesToDelete, int index, ChapterItem item) {
    if (index >= pagesToDelete.size()) {
      loadVersionPages(versionId, item);
      JOptionPane.showMessageDialog(this, "Đã xóa toàn bộ trang ảnh của chương.", "Thành công", JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    String pageId = pagesToDelete.get(index).path("pageId").asText();
    api.request("DELETE", "/publishing/versions/" + versionId + "/pages/" + pageId, null, activeVersionEtag, Map.of())
        .whenComplete((reply, err) -> {
          SwingUtilities.invokeLater(() -> {
            if (err == null && reply != null) {
              this.activeVersionEtag = reply.etag();
              deletePagesSequentially(versionId, pagesToDelete, index + 1, item);
            } else {
              loadVersionPages(versionId, item);
              Throwable cause = err != null ? ApiClient.unwrap(err) : new Exception("Lỗi xóa trang");
              JOptionPane.showMessageDialog(this, "Đã xóa " + index + " trang. Lỗi ở trang tiếp theo: " + cause.getMessage(), "Thông báo", JOptionPane.WARNING_MESSAGE);
            }
          });
        });
  }

  private void deleteCurrentChapter() {
    ChapterItem sel = chapterJList.getSelectedValue();
    if (sel == null) return;
    if (chapterListModel.size() <= 1) {
      JOptionPane.showMessageDialog(this, "Truyện cần tối thiểu 1 chương.", "Không thể xóa", JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    int confirm = JOptionPane.showConfirmDialog(this,
        "Bạn có chắc chắn muốn xóa chương \"" + sel.title + "\" khỏi danh sách không?",
        "Xác nhận xóa chương",
        JOptionPane.YES_NO_OPTION,
        JOptionPane.WARNING_MESSAGE);
    if (confirm != JOptionPane.YES_OPTION) return;

    int index = chapterJList.getSelectedIndex();
    chapterListModel.removeElement(sel);
    int newIndex = Math.min(index, chapterListModel.size() - 1);
    if (newIndex >= 0) {
      chapterJList.setSelectedIndex(newIndex);
    }
    JOptionPane.showMessageDialog(this, "Đã xóa chương \"" + sel.title + "\" thành công.", "Thành công", JOptionPane.INFORMATION_MESSAGE);
  }
}
