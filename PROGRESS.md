# Comic Net — điểm tiếp nối

**Checkpoint 2026-10-29 — tiếp tục sau QA vai trò Creator:** Người dùng xác nhận cấp vai trò CREATOR hoạt động trong Swing. Đã đồng bộ OpenAPI với source: xóa `/setup/administrator`, thêm OTP xác minh/reset và đổi username; bỏ `reason` khỏi schema quản trị status/roles. `ApiContractTest` PASS với 149 operations; toàn bộ reactor `test` backend + desktop Swing PASS. `IdentityHttpIT` trên ComicNetDev (Flyway v24) 6/6 PASS; `WalletFlowIT` 2/2 PASS trên SQL thật. Phase 4 vẫn đang triển khai; bước gần nhất là QA native ví xu/đặt giá/mở khóa, sau đó triển khai các mục M11/M12/M20 chưa có theo feature register. Không coi Phase 3/4 đã nghiệm thu.

**Checkpoint 2026-10-29 — quản trị vai trò:** Ảnh GUI khi lưu CREATOR báo không kết nối; kiểm tra cổng 8080 không có backend lắng nghe. Đã gỡ ô Lý do thay đổi khỏi Swing và bỏ yêu cầu reason khỏi hai request quản trị status/roles. Danh sách tài khoản khi tìm rỗng lấy tất cả, khi nhập từ khóa tìm cả Username. Đóng gói backend + desktop Swing thành công với `-DskipTests`; JAR backend 81.7 MB có Start-Class đúng và BOOT-INF class. Chạy unit tests đầy đủ còn thất bại ở ApiContractTest do OpenAPI cũ vẫn liệt kê endpoint setup đã gỡ và thiếu ba route auth/profile hiện có; 28/29 backend tests khác pass. Chưa xác minh lưu vai trò bằng GUI với backend đang chạy.

**Checkpoint 2026-10-29 — đơn giản hóa cấp vai trò ban đầu:** `deploy/sql/05_assign_local_demo_roles.sql` giờ chỉ gán ADMINISTRATOR cho tài khoản `admin` đã đăng ký; không yêu cầu hoặc tự gán CREATOR. Các vai trò tài khoản tiếp theo chọn ở Swing Quản trị → Tài khoản. README đã cập nhật. Đây là chỉnh sửa script/tài liệu, chưa chạy trên database người dùng.

**Checkpoint 2026-10-29 — nút Hiện/Ẩn mật khẩu:** `AuthScreens` trước đây lấy ký tự che từ `JPasswordField` mỗi lần dựng lại màn; sau khi đã bấm Hiện, màn dựng lại có thể ghi nhận ký tự rỗng nên không che lại được. Đã giữ ký tự che ban đầu và khôi phục khi đổi/dựng lại màn. Reactor `desktop-swing -am -DskipTests package` offline PASS; chưa kiểm tra thao tác Swing native.

**Checkpoint 2026-10-29 — sửa backend JAR không khởi động:** `backend/pom.xml` còn `start-class` cũ `com.comicplatform.ComicNetApplication` sau khi đổi package thành `com.comicnet`. Đã sửa và đóng gói lại backend offline thành công; manifest JAR xác nhận `Start-Class: com.comicnet.ComicNetApplication`. Chưa kiểm tra kết nối SQL/health trong phiên này.

**Checkpoint 2026-10-29 — tài khoản demo local:** Đã gỡ màn khởi tạo quản trị viên và endpoint `/api/v1/setup/administrator`; bỏ hai nút chọn Email/Số điện thoại trên màn đăng ký. Tạo `admin`/`admin` và `creator`/`creator` bằng đăng ký Swing, rồi chạy `deploy/sql/05_assign_local_demo_roles.sql` trong SSMS để gán vai trò; hướng dẫn trong README. Script không đặt thời hạn tài khoản hoặc thay mật khẩu. Reactor backend + Swing `package -DskipTests` offline PASS; chưa chạy SQL script trên dữ liệu người dùng, chưa QA giao diện native và chưa chạy integration tests cho thay đổi này.

**NetBeans Run/Debug fix 2026-10-28:** `desktop-swing/nbactions.xml` còn gọi main class cũ `com.comicplatform.desktop.DesktopApplication`; đã đổi cả Run/Debug sang `com.comicnet.desktop.DesktopApplication`. `mvnw -o -pl desktop-swing process-classes` PASS, `javap` xác nhận class/main mới. Chưa bấm Run trực tiếp trong NetBeans sau thay đổi.

**Hồi quy sau checkpoint Phase 4:** toàn bộ `ReaderFlowIT` 8/8 PASS trên ComicNetDev/RustFS (`.local/qa/phase4-reader-regression.log`); không có sửa code sau bài test. GUI Native cho ví/giá/mở khóa vẫn CHƯA KIỂM CHỨNG.

**Checkpoint 2026-10-28 — Phase 4 đang triển khai:** V19–V22 đã migrate ComicNetDev; grant runtime scripts 15–18 đã chạy. Wallet Paid/Reward + balanced double-entry ledger; chapter price, purchase, permanent entitlement, reader access, local-only sandbox payment order/settle và đối soát snapshot có backend/REST/SQL. Swing có Ví xu/nạp sandbox, đặt giá, mở khóa; **GUI chưa Verified**. `WalletFlowIT` 2/2 PASS, `ReaderFlowIT#paidChapterUnlockUsesRealLedgerAndGrantsOnlyItsBuyer` 1/1 PASS, reactor 29 backend +20 desktop unit PASS; OpenAPI 147 operations. Còn subscription, refund/reversal, webhook nhà cung cấp thật, chính sách anti-fraud và các feature Phase 4 khác; sandbox chỉ chạy profile `local`. Phase 3 còn BLOCKED SQL Full-Text (`FullTextInstalled=0`) và DPI/Figma GUI; không đánh dấu hoàn tất. Tiếp theo: kiểm chứng GUI giá/nạp/mở khóa, hoàn thiện subscription/finance theo roadmap. `git rev-parse` nhận root Tools nhưng `git status`/`git diff` trả `fatal: this operation must be run in a work tree`; chưa dùng diff để kết luận.

**Checkpoint 2026-10-28 — Phase 3 Search:** Source hiện có `SearchHistoryService`, `SearchController` suggestions và Swing `SearchAssist`; `SearchFlowIT` 2/2 PASS với ComicNetDev. Thêm V18 full-text index có điều kiện và SQL `CHANGE_TRACKING AUTO`, Flyway đã lên v18. Kiểm tra SQL thật: `FullTextInstalled=0`, `ComicFullTextIndexes=0`; F01/F09 **BLOCKED verification** do instance chưa cài Full-Text Search. `CatalogService` tự fallback sang truy vấn có tham số, F10 hoạt động qua đường này. Feature Register M06 đã đồng bộ. CommentDialog moderation UI đã tồn tại, không làm lại. Swing light-theme đã chạy và chụp ảnh ở cửa sổ thường/tối đa; DPI/kích thước yêu cầu đầy đủ và đối chiếu Figma còn BLOCKED. Phase 3 chưa nghiệm thu. Tiếp theo: thiết lập Full-Text Search trên SQL instance khi khả dụng và chạy lại SearchFlowIT; sau đó Phase 4 wallet/ledger → entitlement/monetization → finance theo roadmap.

**Checkpoint package refactor 2026-10-28:** backend root `com.comicnet`, đủ 23 direct business packages; M01 package `identity`; 8 module chưa có code giữ package-info-only. Swing feature packages khớp module có UI. Reactor verify 29 backend +20 desktop PASS; SQL/S3 integration 50 PASS (`.local/qa/package-refactor-sql-integration.log`). Maven clean-plugin cache lỗi thiếu `plexus.util.Os`; target còn stale class namespace cũ. Không xóa khi PID 9956 vẫn chạy `com.comicplatform.ComicNetApplication`; sau khi dừng app trong NetBeans, clean/restart từ source mới.

**Phase 3 gần hoàn tất backend.** Giữ Java 21 / Swing / Spring Boot 4.1.2 / SQL Server 2025 / Maven / NetBeans 31, Modular Monolith, package-by-feature, 23 module / 243 chức năng. React ngoài MVP. Không dựng lại source, không đổi stack.

## UI checkpoint 2026-10-28

- Swing shell dùng dark palette mặc định, có nút Light/Dark, tìm kiếm toàn cục và ẩn Publishing/Admin/Operations/System theo role `/me`.
- Khám phá có banner + grid bìa cuộn dọc; card, chi tiết, chương, tìm kiếm và ảnh đều lấy từ API/media thật. Tài khoản dùng card hẹp; theme token áp dụng toàn shell; reader giữ API/tiến độ và nền theo theme.
- `mvnw -o -B -ntp -f desktop-swing/pom.xml test`: BUILD SUCCESS, 20 tests. Ứng dụng mở được bằng `mvnw -o -B -ntp -f desktop-swing/pom.xml exec:java`.
- Chưa xác minh feed có dữ liệu vì backend localhost đang tắt. Gamification M11/M20 và P5 chưa có API backend nên chưa có UI thao tác; không dựng mock.

## Checkpoint 2026-10-28 — Phase 3: analytics, notification, scheduled publish

### Đã triển khai trong lượt này (code thật + SQL thật)

- **M07 analytics** (`analytics/`, V14): `ComicView` (dedup 1 lượt đọc hợp lệ / user / chapter / ngày, giới hạn 150 chương/giờ), `ComicStatDaily` (views + completions theo ngày), `ChapterCompletion`. API: `POST /comics/{id}/views`, `POST /chapters/{id}/completion`, `GET /rankings?period=DAILY|WEEKLY|MONTHLY|TRENDING`, `GET /comics/{id}/stats` (30 ngày, viewers, rating), `GET /chapters/{id}/stats` (completion rate). Quyền mới `analytics.self`, `analytics.read`.
- **Catalog**: `ComicSummary`/`ComicDetail` trả `rating` và `views` thật; thêm sort `VIEWS_DESC`, `RATING_DESC`, `POPULARITY_DESC` (M06-F06).
- **M13 notification** (`notification/`, V15): `Notification` (unique user/type/dedup), `NotificationPreference`, `Announcement`, `OutboxEvent.ErrorCode`. API inbox/summary/read/unread/read-all/preferences/announcements. Quyền `notification.self`, `notification.broadcast`.
- **Fan-out durable**: `NotificationWorker` tiêu thụ outbox `ChapterPublished` (báo chương mới cho `ComicFollow`, batch insert + email theo preference), `CommentReply`, `CommentReaction`; retry backoff, dừng sau 5 lần. Community phát event reply/reaction trong transaction bình luận. Email qua `AccountNotifier` → `AccountMail` (job table mã hóa, retry sẵn có).
- **M03-F11 scheduled publishing** (V16 `ChapterVersion.ScheduledAt`): `POST /chapters/{id}/schedule` (snapshot APPROVED, 1 phút–90 ngày, cho phép đổi lịch), `DELETE /chapters/{id}/schedule`, `ScheduledPublishWorker` phát hành khi tới hạn (archived/pages-not-ready → FAILED có audit).
- **M03-F12 rollback**: `POST /chapters/{id}/rollback` đưa snapshot PUBLISHED/UNPUBLISHED trở lại làm bản hiện hành, audit `CHAPTER_ROLLED_BACK`, event `ChapterPublished`.
- **Swing**: workspace `Thông báo`, workspace `Bảng xếp hạng`, nút `Lên lịch` + `Quay lại bản cũ` trong Xuất bản, Reader ghi lượt đọc khi mở chương và completion khi lưu tiến độ hoàn tất.
- **Contract**: `docs/api/openapi-mvp.json` 99 paths / 130 operations; `ApiContractTest` cập nhật 130. Grant script mới `deploy/sql/14_grant_phase3_metrics_runtime.sql` đã áp dụng cho ComicNetDev bằng Windows sysadmin có sẵn.

### Kiểm chứng thực tế trong lượt này

- Flyway: log backend xác nhận `Successfully applied 3 migrations ... now at version v16` (V14, V15, V16) trên ComicNetDev.
- `mvnw -o -B -ntp verify` (reactor): **BUILD SUCCESS** — 27 backend unit + 16 desktop unit, 0 failure/error/skip (gồm ApiContractTest 130 operation, ModuleBoundariesTest 23 module).
- Toàn bộ integration SQL/S3: **46 tests, 0 failure/error/skip, BUILD SUCCESS** (`.local/qa/phase3-full-integration.log`), gồm 4 test mới: `NotificationFlowIT` (reply/reaction dedup, preference tắt in-app, announcement batch, read-all) và `AnalyticsPublishingFlowIT` (dedup view theo ngày, ranking/trending, completion rate 50.00, schedule/đổi lịch/hủy/phát hành đúng hạn, rollback giữ audit).
- Lỗi đã sửa trong quá trình kiểm chứng: (1) bind `Instant` vào datetime2 bị lệch múi giờ → dùng `LocalDateTime` UTC như lease upload; (2) không thể đổi lịch khi version đang SCHEDULED → cho phép reschedule; (3) repackage fail do JAR đang bị backend giữ file — dừng đúng PID dự án rồi build lại.
- Smoke HTTP thật (backend 8080, JDK 21): `GET /api/v1/rankings?period=WEEKLY` → 200 (public, danh sách rỗng vì DB dev chưa có lượt đọc), `GET /api/v1/notifications` và `GET /api/v1/comics/1/stats` → 401 khi chưa đăng nhập, `readiness` → 200. Đã thêm `/api/v1/rankings` vào allowlist GET công khai của SecurityConfiguration.
- **CHƯA KIỂM CHỨNG**: thao tác native các màn mới (Thông báo, Bảng xếp hạng, Lên lịch/Rollback), DPI/resize/keyboard; email thật qua Mailpit cho notification; Redis sorted sets (P6), creator analytics (P5), reading drop-off/peak traffic (P6).

### Việc tiếp theo (thứ tự ưu tiên)

1. **M06 Phase 3 còn lại**: Search History, autocomplete, suggestions, index sync/fallback; unit + integration test.
2. Moderation UI cho report/hidden comment trên Swing (backend đã có).
3. Phase 4: wallet/ledger + virtual coin (M11) và unlock chương bằng coin (M20/M12), rồi gamification/minigame (P5: EXP, check-in, spin).
4. Kiểm chứng native các màn Phase 3 mới + checklist GUI 19 story (vẫn CHƯA KIỂM CHỨNG).

### Dịch vụ/tiến trình

RustFS 9000 + Mailpit 8025 đang chạy. Backend QA 8080 đã dừng sau khi dùng để migrate. Script tiện ích: `.local/qa/run-phase3-migration.sh`, `.local/Verify-FinalIntegration.ps1 -TestFilter ...`. Không commit Git (repo vẫn chưa có commit), không xóa source/dữ liệu.

---

## Lịch sử ngắn (giữ nguyên ngữ cảnh trước)

Phase 2 đã có luồng Reader → Creator draft/upload/preview/submit → Admin approve/publish → soft-delete, restore mẫu, soak 20 luồng; 39 unit/34 integration PASS ở thời điểm đó. Phase 3 trước lượt này: rating/review/aggregate (V12), comment/reply/page anchor/vote/report/block/moderation (V13), gợi ý tương tự/trending theo rating, dialog Swing tương ứng, V12/V13 đã migrate. Các mục GUI/DPI/crash-restore/restore-job vẫn CHƯA KIỂM CHỨNG; xem `docs/implementation-status.md`.

### 2026-10-28 — Authentication redesign (checkpoint lịch sử; xem 2026-10-29)
- Đã xem 6 ảnh tham chiếu và rà soát Identity/AccountPanel. Backend hiện chỉ hỗ trợ email + token dạng link, chưa có username, số điện thoại/SMS OTP, OTP 6 số hay remember-me bền vững.
- Đã đổi validation mật khẩu tạo mới/đặt lại/setup thành 3–12 ký tự. Login vẫn nhận đến 128 ký tự để tài khoản cũ không bị khóa. Swing đổi nhãn và validation tương ứng. Backend + desktop compile PASS.
- Tại thời điểm checkpoint này chưa kiểm thử SQL integration, chưa triển khai 6 màn và chưa xác minh GUI; các mục được cập nhật ở checkpoint 2026-10-29 bên dưới.

### 2026-10-29 — Authentication tiếp nối
- Đã triển khai 6 view Swing theo `docs/ui/auth/`, Light Mode; `AccountPanel` hiển thị auth trực tiếp, không còn `JScrollPane` bọc ngoài. Đăng ký và wizard setup yêu cầu username và tên hiển thị 3–12, mật khẩu mới 3–12; email và số điện thoại tùy chọn khi đăng ký. Login email cũ vẫn được giữ. Phone lưu ở `RecoveryPhone`; khôi phục qua SMS **BLOCKED** vì chưa có nhà cung cấp, UI không báo gửi SMS thành công.
- SQL V23/V24: OTP email 6 số, giới hạn 5 lần thử/60 giây gửi lại, username duy nhất, email nullable; phiên nhớ đăng nhập mã hóa DPAPI. Không tạo admin/creator mặc định. `/me/username` cho tài khoản cũ đặt username.
- Kiểm chứng: Desktop `test package` **21/21 PASS**; `IdentityHttpIT` với SQL Server ComicNetDev **6/6 PASS**, gồm username-only, số điện thoại tùy chọn và đổi username. Đã mở ứng dụng thật, xem trực quan đăng nhập/đăng ký/quên mật khẩu ở cửa sổ 1267×795; thêm bố cục đăng ký hai cột khi thấp và xác minh tại 1267×659: không có thanh cuộn ngoài, form/nút/link đều nhìn thấy. Chưa kiểm chứng trực quan OTP/reset/success, DPI 125/150%, và SMS recovery; không đánh dấu PASS các mục này.
- Tiếp theo: nếu có SMS provider, triển khai xác thực số điện thoại và khôi phục qua SMS; kiểm tra GUI OTP/reset/success cùng DPI trên máy đích. Giữ Phase 4 ngoài phạm vi lượt này.

