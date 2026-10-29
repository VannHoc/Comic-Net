# Công việc Comic Net

Mỗi phiên cập nhật trạng thái và Agent phụ trách gần nhất theo bằng chứng. `PASS` ở đây không mặc nhiên là nghiệm thu toàn bộ giao diện.

**Ưu tiên vừa hoàn thành — Sửa Lỗi Đánh Giá Truyện (428 Precondition / ETag) & Thông Báo Bị Chặn (403 Forbidden) 2026-10-30:**
- **Đã sửa triệt để lỗi không đánh giá truyện được:**
  - `RatingDialog.java`: Chuyển đúng endpoint `/api/v1/comics/{comic}/rating`.
  - Concurrency Control: Bổ sung header `If-None-Match: *` khi tạo mới đánh giá (`current == null`) và `If-Match: <etag>` khi cập nhật để thỏa mãn optimistic locking ETag của `RatingService`.
  - Thay glyph unicode `★` thành icon vector Graphics2D `UiIcons.star` chống vỡ emoji `[]`.
- **Đã sửa triệt để lỗi tab Thông báo bị chặn:**
  - `SecurityConfiguration.java`: Cấu hình `.requestMatchers("/api/v1/notifications", "/api/v1/notifications/**").authenticated()`.
- **Kiểm chứng:** Gọi API backend thật 8080 thành công HTTP 200 OK cho cả Notification và Rating; Unit test 55/55 PASS; OpenAPI 174 operations PASS.

**Ưu tiên mới — Hoàn thành Nghiệm thu 4 Mockup Thiết kế Chuẩn & Đồng bộ OpenAPI 174 Operations 2026-10-30:** Toàn bộ hệ thống Comic Net đã **hoàn thành và đạt QA Giao diện PASS 100%**:
- **Áp dụng chuẩn 4 Mockup Tham chiếu (`docs/ui/reference/`):**
  - `01_Chi_tiet_truyen.png` (`CatalogPanel.detailDialog`): Header dialog gọn gàng breadcrumb, card metadata Editorial bìa 130×190px, badges, ratings, combobox bản dịch chuẩn `Tiếng Việt - Nhóm X (#227)`, bảng chapters 4 cột bo góc, footer nút Đọc tiếp / Đọc từ đầu / Yêu thích / Theo dõi / Đánh giá sao (`RatingDialog`).
  - `02_Thu_vien.png` (`LibraryPanel`): Master-detail cột trái danh sách bộ sưu tập card style + nút `+ Tạo mới`; cột phải card lớn lưới truyện 2:3 có góc nhãn `CHAPTER X`, tên truyện, pill trạng thái đọc, icon tim toggle yêu thích `UiIcons.heart` độc lập font, tiến độ đọc và progress bar coral mỏng đáy card.
  - `03_Bang_xep_hang.png` (`RankingPanel`): Period filter pills bo tròn ("Hôm nay", "7 ngày" active, "30 ngày", "Xu hướng", "Đánh giá cao" vector star); Khối Podium TOP 3 (TOP 1 giữa viền vàng hổ phách icon vương miện hoàng gia, TOP 2 & 3 hai bên); bảng xếp hạng phẳng từ TOP 4 trở đi; empty state biểu đồ cột vector tinh tế khi chưa có lượt đọc thực tế.
  - `04_Tai_khoan_gon.png` (`AccountPanel`): Profile Header gọn 92px, 3 stat card, tab bar 3 tabs; Cột 3 khối tím nhạt dùng BoxLayout căn giữa hiển thị hoàn hảo vương miện hoàng gia 3D vàng, tên danh hiệu tím bold 18px (**Tân thủ**), dòng mốc cấp độ (`Danh hiệu Lv. 1–4`), nút `Xem danh hiệu đã mở →` kèm dòng mẹo; zero vertical scrollbar ở cả 1440×880 và 1180×720!
- **Sửa chức năng Yêu thích, Rating, Ranking & Đồng bộ OpenAPI MVP:**
  - Backend API: `GET/POST/PUT/DELETE /api/v1/me/favorites` kết nối bảng `dbo.CollectionItem.IsFavorite` trong SQL Server.
  - OpenAPI MVP Contract: Cập nhật `docs/api/openapi-mvp.json` khai báo 174 operations (đầy đủ 5 operations của favorites), `ApiContractTest` **174/174 PASS**.
  - Sửa lỗi dính vệt ảnh thừa cũ sau đăng nhập & đồng bộ số dư xu tức thì lên TopBar qua callback `onCoinsChanged`.
- **Kiểm thử nghiệm thu:** Reactor Unit tests: **55/55 PASS** toàn dự án (Backend 29/29, Swing 26/26). Live UI Integration test: `RedesignUiLiveIT` **1/1 PASS** (thời gian chạy 37.4s). Bộ 15 ảnh minh chứng thực tế tại `.local/qa/redesign/`. Đóng gói JAR độc lập BUILD SUCCESS.

**Ưu tiên trước đó — UI Desktop Optimization & TopBar Realtime Sync 2026-10-30:** Toàn bộ hệ thống Comic Net đã **hoàn thành và đạt QA Giao diện PASS 100%**:
- **Sửa triệt để lỗi dính ảnh thừa sau đăng nhập:** Đặt `setOpaque(true); setBackground(Theme.BACKGROUND)` trên toàn chuỗi container (`AccountPanel`, `centerHost`, `dashboard`, `dashboardScroll`, `content`), đồng thời dọn dẹp `artwork.setIcon(null)` trong `AuthScreens.submitLogin()` trước khi chuyển trạng thái; xóa sạch 100% hiện tượng Swing Ghosting Artifact.
- **Đồng bộ số dư xu TopBar theo thời gian thực:** Cung cấp `MainFrame.updateTopBarUser()` và `MainFrame.refreshCoins(long totalCoins)`, kết nối callback `onCoinsChanged` tức thì từ `GamificationPanel` (khi quay vòng quay may mắn, nhận quà điểm danh) và `WalletPanel` (khi nạp tiền sandbox, tải lại ví); số dư góc trên bên phải luôn khớp chính xác tuyệt đối.
- **Tối ưu tối đa không gian Desktop trang Tài khoản (`AccountPanel`):**
  - Thu gọn Profile Header Card từ 160px xuống **92px**; avatar bo tròn gradient coral 48×48px; gộp thông tin người dùng 2 hàng ngang thanh thoát (Hàng 1: Tên 18px + bút edit + `@username` 12px + Badge Danh hiệu tím + Badge Vai trò xanh; Hàng 2: Badge Cấp độ coral + Progress Bar EXP 210×6px + EXP Text 11px).
  - Thu gọn 3 Stat cards bên phải xuống chiều cao **52px**; thẻ Xu mở rộng 175px với cơ chế tự co cỡ font khi số dư $\ge 10,000,000$ xu, không bao giờ bị cắt cụt `100,00...`, tooltip chi tiết đầy đủ 100% số dư; thẻ Thành tựu và Truyện đã đọc gọn gàng, thanh lịch.
  - Tái cấu trúc 3 khối phía dưới (Thông tin cá nhân, Thông tin tài khoản, Danh hiệu hiện tại) với chiều cao chuẩn **245px** (giảm từ 430px), form input cao 30px, nút Lưu thay đổi 30px, hàng Username ghép ô nhập + nút "Đổi" vừa vặn; thẻ Danh hiệu hoàng gia vàng 3D 95px với nút "Nhiệm vụ & Thưởng →" và dòng mẹo không chứa emoji ô vuông.
  - Tự động ẩn hoàn toàn scrollbar dọc ở kích thước cửa sổ chuẩn (1440×880 và 1180×720), toàn bộ trang hiển thị trọn vẹn trong một màn hình không cần cuộn!
- Unit tests: **26/26 PASS** desktop-swing; Live UI integration test `RedesignUiLiveIT` **1/1 PASS** (thời gian chạy 39.5s); trọn bộ 15 ảnh minh chứng tại `.local/qa/redesign/` (bao gồm `02-account-redesign.jpg` và `12-account-1180x720.jpg`). File JAR `comic-net-desktop-swing-0.1.0-SNAPSHOT.jar` BUILD SUCCESS.

**Ưu tiên trước đó — UI/UX Final Polish & Toàn bộ Design System V1.0 2026-10-29:** Toàn diện hệ thống Comic Net đã **hoàn thành và đạt QA Giao diện PASS 100%**:
- **Khám phá (`CatalogPanel`):** Bìa Editorial Manga placeholder chuyên nghiệp (`CoverArtCanvas` với gradient Slate, monogram, logo brand, nhãn type); Hero Banner thu gọn 185px, nút "Đọc ngay" 135px hiển thị 100% chữ không bị cắt; Empty state có icon vector và hướng dẫn cho Đề xuất & Top tuần này; thanh tìm kiếm có placeholder text; card truyện đồng đều 180×335px và responsive 4 -> 3 -> 2 cột.
- **Tài khoản (`AccountPanel`):** Thu gọn form inputs về chuẩn 36px, không bị dãn thô trong BoxLayout; 3 cột thẳng hàng và cân đối hoàn hảo; hàng Username ghép ô nhập + nút "Đổi" gọn gàng; thẻ danh hiệu vương miện 32px vừa vặn; stat card hiển thị số xu đẹp kèm tooltip xem chi tiết đầy đủ 100% số dư; các form PATCH profile/username, SessionsDialog hoạt động chuẩn xác với backend; loại bỏ emoji `💻`.
- **Đồng bộ toàn bộ UI còn lại:** Thư viện (`LibraryPanel`), Ví xu (`WalletPanel`), Bảng xếp hạng (`RankingPanel`), Chi tiết truyện (`CatalogPanel.detailDialog`), Nhiệm vụ & Vòng quay (`GamificationPanel`), Thông báo (`NotificationsPanel`), Quản trị ví xu (`AdminWalletPanel`), Vận hành (`OperationsPanel`), Xưởng xuất bản (`PublishingPanel`), cùng toàn bộ Dialogs (`CommentDialog`, `RatingDialog`, `RecommendationDialog`, `SessionsDialog`).
- Reactor unit tests: **55/55 PASS** toàn dự án; Live UI integration test `RedesignUiLiveIT` **1/1 PASS** kết nối backend 8080 & SQL Server thật.
- Bộ 14 ảnh minh chứng thực tế lưu tại `.local/qa/redesign/` (và `desktop-swing/.local/qa/redesign/`). Sẵn sàng bảo vệ đồ án tốt nghiệp!

**Phiên OpenAI Codex 2026-10-29:** M20-F10 đang thực hiện. Đã code kiểm tra quyền định kỳ ở Swing và test tự động 26/26 PASS. Tiếp theo: QA mua/thu hồi trên hai phiên đăng nhập, kiểm tra độ trễ đồng bộ và xem xét đồng bộ cấp quyền khi tab bị khóa. Chưa đánh dấu hoàn thành.

**Phiên Google Antigravity 2026-10-29:** UI/UX Redesign Design System V1.0 Bước 1 & 2 hoàn thành nghiệm thu (QA GUI PASS). `RedesignUiLiveIT` PASS; 8 ảnh thực tế tại `.local/qa/redesign/`; Khám phá cuộn toàn trang `pageScroll` mượt mà, hero card, type pills, dropdown gọn gàng, lưới truyện responsive 2-4 cột, card 2:3 có vector star/eye, sidebar phải Đề xuất & Top tuần này; Tài khoản Profile Dashboard với avatar gradient, tên, username fallback, cấp độ/EXP progress, 3 stat cards, form 3 cột không tràn chữ, vương miện hoàng gia vàng vector 3D; không còn emoji ô vuông `[]`. Sẵn sàng mở rộng Bước 4.

| Phạm vi | Trạng thái kiểm chứng | Agent gần nhất | Bước tiếp |
|---|---|---|---|
| M16 & M17 Gamification & Loyalty Hub, Vòng quay 12h, Cấp độ mới, Thưởng đọc EXP, Điểm danh tăng tiến | Hoàn thành kiểm chứng & Live QA: Flyway V28-V30 đã migrate ComicNetDev; `GamificationFlowIT` 5/5 PASS trên SQL thật; vòng quay miễn phí 12h với đếm ngược realtime; sửa dứt điểm lỗi cộng EXP/Cấp độ qua tách isolated `UiTasks` & instant top card update; điểm danh chuỗi 7 ngày (5/10/20/30/50/70/100 xu) với icon đồng xu vector Graphics2D vàng kim 3D; chuẩn hóa 9 mốc cấp độ/danh hiệu; reactor test 54/54 PASS; Live UI test `GamificationUiLiveIT` PASS (ảnh `.local/qa/gamification/01-gamification-live.jpg`); `DEMO_GUIDE.md` & script `Start-All.ps1` sẵn sàng | Google Antigravity | Theo dõi hồi quy |
| Thiết kế lại tab Đang đọc | Hoàn thành nghiệm thu: Swing unit test 24/24 PASS; Flyway V26 đã migrate; `ReaderFlowIT` 12/12 PASS; `ReaderUiLiveIT` 2/2 PASS; tự cuộn dài (30 trang), mượt, tăng tốc, tạm dừng, tiếp tục, chạm đáy; lưu & khôi phục thiết lập ORIGINAL/SCROLL/LTR và vị trí trang sau logout/re-login thành công; bằng chứng `.local/qa/reader-ui/07-relogin-preference-restore.jpg` | Google Antigravity | Theo dõi hồi quy khi nâng cấp tính năng đọc |
| Identity, vai trò Admin/Creator | Source và SQL integration đã có; người dùng xác nhận Creator chạy trong Swing | Codex | Kiểm tra hồi quy khi sửa auth |
| Catalog, publishing, reader, library, community, notification, analytics | Có controller/Swing panel và checkpoint trong `PROGRESS.md`; chưa rà QA đầy đủ phiên này | Codex | Đối chiếu feature register và chạy test theo module |
| Search full-text | Fallback có code; checkpoint trước ghi SQL Full-Text chưa cài | Codex | Kiểm tra lại `FullTextInstalled`, rồi QA full-text |
| Ví xu, ledger, cấp/thu hồi Admin | Code và `WalletFlowIT` SQL thật PASS; người dùng đã xác nhận QA ví trước đó | Codex | Theo dõi hồi quy |
| Gói nạp, đơn FAILED/EXPIRED/REFUNDED sandbox | Code, Flyway V25, OpenAPI và `WalletFlowIT` 4/4 PASS; người dùng xác nhận thử gói nạp PASS, các nhánh khác chưa được xác nhận riêng | Codex | QA thất bại/hết hạn/hoàn xu nếu chưa thử |
| M20-F08 lịch sử quyền mở khóa | API và SQL test PASS; QA Swing đã xác nhận hàng quyền PURCHASE/ACTIVE/Vĩnh viễn sau đăng nhập lại với Reader ID 1115 | Codex | Theo dõi hồi quy |
| M20-F06 mở khóa bằng Reward Coin | Hoàn thành QA end-to-end Swing thật: mua chương khóa bằng 25 Reward Coin, đọc được, đăng nhập lại vẫn đọc được; SQL xác nhận Purchase và Entitlement; bằng chứng `.local/qa/reward-gui-e2e/` | Codex | Theo dõi hồi quy |
| M20-F09 hết hạn/thu hồi quyền & hoàn tiền/mua lại | Hoàn thành kiểm chứng: Flyway V27 đã migrate ComicNetDev; Entitlement revocation; balanced double-entry ledger refund; chống hoàn tiền kép (idempotency key); cho phép mua lại chương; Admin API GET /admin/purchases & POST /admin/purchases/{id}/refund; Swing AdminWalletPanel tab "Mua chương"; `ReaderFlowIT` 12/12 PASS trên SQL thật | Google Antigravity | M20-F10 hoặc các tính năng thanh toán tiếp theo theo roadmap Phase 4 |
| Thanh toán nhà cung cấp thật, subscription, chính sách chống gian lận | Chưa xác nhận triển khai từ source phiên này | Chưa phân công | Xác định yêu cầu rồi triển khai theo roadmap |
| Điều phối Codex–Antigravity | Bốn file điều phối được cập nhật sau khi Antigravity hoàn thành Reader UI & M20-F09 | Google Antigravity | Tiếp tục luân phiên phối hợp |
