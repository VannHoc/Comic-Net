# Trạng thái triển khai và điểm tiếp tục

## Checkpoint 2026-10-28 — package-by-feature refactor

- Root Java package: `com.comicnet`; Maven `groupId` giữ `com.comicplatform`. M01 `auth` được đặt package `identity`; các feature khác dùng tên package theo M01–M23 trong `prj_app.md`.
- Backend source có đủ 23 package trực tiếp; 8 module chưa triển khai là package-info-only và vẫn giữ trạng thái Not Started/partial trong feature register. Shared `common/config/infrastructure` tách ngoài 23 module.
- Swing packages theo feature có UI: `identity`, `catalog`, `search`, `analytics`, `recommendation`, `publishing`, `reader`, `library`, `community`, `notification`, `admin`, `observability`, `dataprotection`; transport/UI/config/session vẫn là technical packages.
- `mvnw -o -B -ntp verify`: BUILD SUCCESS; backend 29 unit/contract tests, desktop 20 tests. SQL/S3 integration: 50/50 PASS, log riêng `.local/qa/package-refactor-sql-integration.log`.
- `clean verify` không chạy do Maven clean-plugin cache thiếu `org.codehaus.plexus.util.Os`; vì vậy target còn class artifacts package `com.comicplatform`. Không xóa khi backend PID 9956 đang chạy old entry point; cần dừng/restart backend từ NetBeans sau khi kiểm tra môi trường. Source package mới đã compile/integration-pass.


## Checkpoint 2026-10-28 — Phase 3 metrics, notification, scheduled publish

Đã triển khai và kiểm chứng: M07 view hợp lệ (dedup user/chapter/ngày, 150 chương/giờ), ranking DAILY/WEEKLY/MONTHLY/TRENDING, analytics theo ngày, chapter completion rate; catalog trả rating/views thật và sort VIEWS/RATING/POPULARITY; M13 in-app notification + preference + announcement + email queue + fan-out outbox (`ChapterPublished`, `CommentReply`, `CommentReaction`); M03-F11 scheduled publish + M03-F12 published-version rollback. Migration V14–V16 đã áp dụng trên ComicNetDev; grant script 14 đã chạy bằng Windows sysadmin có sẵn.

Evidence lượt này: reactor `verify` BUILD SUCCESS (27 backend + 16 desktop unit, 0 failure/error/skip); toàn bộ integration SQL/S3 **46 tests PASS** (log `.local/qa/phase3-full-integration.log`), gồm 4 test mới cho notification và analytics/schedule/rollback. Contract hiện **99 paths / 130 operations**.

Vẫn CHƯA KIỂM CHỨNG: thao tác native Swing cho Thông báo/Bảng xếp hạng/Lên lịch/Rollback, DPI/resize/keyboard, email notification qua Mailpit, các mục Phase 5–7 (Redis sorted sets, creator analytics, drop-off/peak traffic). Điểm tiếp nối chi tiết: [PROGRESS](../PROGRESS.md).

---


**Trạng thái mới nhất: Phase 3 đang triển khai theo xác nhận user, giữ roadmap0–7.** Đã thêm rating/review/aggregate, comment/reply/page anchor/vote/report/block/moderation, gợi ý cơ bản và các dialog Swing; V12/V13 + grant5bảng đã áp dụng. Build2JAR, 8 targeted unit/architecture và 8 integration mới PASS; GUI mới chưa kiểm chứng native. Điểm tiếp nối tại [PROGRESS](../PROGRESS.md). Các checkpoint Phase2 bên dưới là lịch sử; user đã cho phép tiếp tục có điều kiện, giữ các AC chưa chạy tới tổng kiểm.

Lượt QA 2026-10-28: đã phân tích đủ 9 file RustFS `.rustfs.sys` lệch hash so với manifest gốc (giữ nguyên phép so volume FAIL, 609 payload khớp); sửa NetBeans backend launcher nạp cấu hình SQL local vào riêng tiến trình backend và health probe Windows PowerShell; Maven mapping Run/Debug đã khởi động QA port 8083, ping/readiness 200, JDWP loopback 5005. Chạy lại 39 unit và 34 integration test: 0 lỗi; SQL invariant sau suite đều 0. Test lease/retry SQL/S3 riêng 1/1 PASS; probe 20 client trên catalog rỗng chỉ là chẩn đoán. [Checklist Swing 19 story](testing/phase-2-swing-manual-checklist.md) vẫn CHƯA KIỂM CHỨNG trực tiếp trong NetBeans; crash/restore job và NFR đầy đủ còn BLOCKED. Phase 2 chưa nghiệm thu.

Người dùng đã chạy **backend Run trực tiếp trong NetBeans** lúc 05:11 +07:00 và cung cấp Output: PID 34076, JDK 21, Flyway 11 migration, Tomcat 8080. Kiểm tra độc lập ping/readiness đều HTTP 200/UP; lỗi thiếu `DB_USER` đã được xác minh không còn trên đường Run Project. Debug breakpoint và checklist GUI 19 story vẫn chưa kiểm chứng.

## Checkpoint rà soát sau khi chuyển thư mục — 2026-10-27, 23:08 Asia/Saigon

**Phase 2 đang chờ hoàn tất nghiệm thu; chưa chuyển Phase 3.** Phase 0/1 đã được người dùng nghiệm thu. Thư mục hiện tại: `C:\Users\studi\OneDrive\Documents\Tools\Comic Net`. Các checkpoint bên dưới là lịch sử tại thời điểm được ghi, có đường dẫn hoặc số test cũ.

### Kết quả rà soát và sửa lỗi

- Khôi phục đúng project tại đường dẫn người dùng cung cấp. NetBeans Maven 3.9.16 resolve lại các dependency AWS SDK/Spring Mail rồi `test-compile` thành công. Người dùng Reload Project và xác nhận hết dấu đỏ. Không đổi phiên bản stack. README dùng lệnh tương đối để không phụ thuộc thư mục cũ.
- Launcher local từng giữ PID đã bị Windows tái sử dụng cho ứng dụng khác. Sửa để bỏ qua PID không đúng executable, giữ kiểm tra endpoint đang bị chiếm và khởi chạy đúng dịch vụ dự án. Chạy launcher hai lần: RustFS/Mailpit sẵn sàng, không tạo bản chạy trùng; tiến trình không thuộc dự án được giữ nguyên.
- Guard cấu hình restore xử lý URI storage thiếu hostname hoặc port vượt 65535 bằng lỗi cấu hình có kiểm soát. Bổ sung ca kiểm tra, không mở rộng quyền hoặc target restore.
- Người dùng chốt M02-F01 xóa mềm: Admin hoặc Creator sở hữu, xác nhận trên Swing, If-Match và audit; giữ dữ liệu chapter/version/page/progress. Đã thêm DELETE `/api/v1/comics/{comicId}`, nút Swing và đồng bộ contract/backlog/permission matrix/traceability. Transaction khóa comic, đặt DeletedAt + ARCHIVED và ghi audit; không xóa cascade. Public/managed reads và cấp manifest mới chặn comic đã xóa. Signed URL đã cấp trước đó vẫn có thể dùng đến TTL; không tuyên bố thu hồi tức thì.
- Worker được phép khóa parent đã xóa chỉ trong đường cleanup nội bộ, giúp upload đang queued chuyển FAILED khi chạy sau xóa mà không treo IsActive. Không thêm phụ thuộc catalog → publishing.
- Feature register giữ nguyên **23 module / 243 tên chức năng và dòng nguồn**. 74 phần MVP được ghi Implemented, chưa Verified toàn bộ AC; 5 phần foundation P1 ghi phạm vi đã nghiệm thu. Chức năng phase sau không bị đánh dấu đã triển khai. Các nhãn thiết kế/lịch sử dễ gây nhầm đã được dẫn về checkpoint này.

### Kiểm chứng mới trong lượt này

- Reactor `mvnw -o -B -ntp verify`: BUILD SUCCESS lúc **23:00:07**, **23 backend + 16 desktop = 39 unit tests**, không failure/error/skip. Evidence private: `.local/qa/audit-final-build.log`.
- Toàn bộ SQL/S3/SMTP integration: BUILD SUCCESS lúc **23:05:30**, **34 tests**, không failure/error/skip. Flyway validate đủ **11 migrations**, schema version 11, không cần migration mới. Evidence: `.local/qa/audit-final-integration.log`.
- Ba ca mới kiểm chứng SQL thật: xóa bởi owner kèm 401/404/428/412/204, Admin xóa truyện của Creator khác, upload queued sau xóa được kết thúc an toàn. Kiểm tra giữ published pointer/version-page/progress/bookmark/history và đúng một audit. Fixtures riêng của test được rollback/cleanup có giới hạn; không dùng dữ liệu người dùng để thử xóa. Đây là kiểm chứng HTTP controller qua MockMvc + service + SQL thật, không đồng nghĩa đã thao tác nút xóa trong Swing.
- SQL sau suite, **23:05:50**: invalidPublishedPointers=0, orphanVersionPages=0, disabledOrUntrustedForeignKeys=0, activeUploads=0, ownedQaActorsRemaining=0. Evidence: `.local/qa/audit-final-data-integrity.json`.
- [OSV querybatch](https://google.github.io/osv.dev/post-v1-querybatch/) đã tra **169 GAV dependency runtime backend** ngày 2026-10-27; không có advisory trả về, không thiếu trang kết quả. Chỉ gửi metadata dependency công khai. Kết quả này không bao phủ Maven plugins, công cụ hệ điều hành, dịch vụ local hoặc lỗi chưa có trong cơ sở dữ liệu OSV; không thay thế security review. Evidence: `.local/qa/osv-audit.json`.
- OpenAPI hiện tại **71 paths / 93 operations / 81 schemas**; route contract và ranh giới module qua unit test. So từng dòng feature register với prj_app.md: đủ 243 tên nguyên văn. Spec SHA-256 giữ `0cf413e66bfc759c2ca849b7c904e8f80bf21b8bafd9ec1555f069f8676a2458`.
- Backend chính đã chạy lại từ thư mục mới trên 8080; ping và readiness HTTP 200/UP. RustFS 9000 và Mailpit 8025 HTTP 200. Evidence: `.local/qa/audit-services.json`. Git chưa có commit; source/docs untracked, do đó git diff không cung cấp baseline commit để chứng minh toàn bộ lịch sử thay đổi. Không stage/reset/commit; SQL local và secrets vẫn bị gitignore.

### Còn tồn đọng, không đánh dấu PASS

- **Nút xóa mềm và dialog xác nhận mới trên Swing chưa được thao tác native**: công cụ điều khiển desktop hiện không khả dụng. Đã compile/test phần desktop; các bước native tạo draft → upload → reviewer khác → publish → đọc được ghi ở checkpoint 18:14, không gọi là vừa chạy lại toàn bộ GUI.
- Ma trận DPI 100/125/150%, resize tối thiểu, keyboard/focus; native setup/reset/auth/admin-role còn cần thao tác người dùng theo checklist.
- Profile hiệu năng SRS 20 readers / 1.000 comics / 10.000 chapters, mạng 10 Mbps / 100 ms RTT và memory/GC soak chưa đo. Không suy từ smoke 200 trang.
- Restore một job đang chạy dở chưa kiểm chứng; bài backup/restore dữ liệu ổn định trước đó giữ evidence riêng. Visual sign-off toàn bộ Figma frames vẫn chưa đủ do giới hạn truy cập/quota ở lượt trước.
- Kiến trúc và phạm vi giữ nguyên; React ngoài MVP; không tự kết luận Phase 2 đã nghiệm thu.

---

## Checkpoint QA mới nhất — 2026-10-27, 18:14 Asia/Saigon

**Phase 2 tiếp tục ở bước nghiệm thu; chưa chuyển Phase 3.** Giữ 23 module / 243 chức năng, Swing → REST → Spring Boot → SQL Server 2025, Java 21 / Maven / NetBeans; React ngoài MVP. Không provision/reset database hoặc thay mật khẩu/quyền runtime.

### Đã kiểm chứng trong lượt tiếp tục

- Swing Creator: tạo draft từ bản published; tải ZIP thật qua JFileChooser; theo dõi durable job; sắp xếp và lưu thứ tự; preview ảnh S3; gửi duyệt. SQL được chụp sau từng bước. V1 và public pointer giữ nguyên trước publish; stable Page ID được giữ khi clone/reorder.
- Creator tự duyệt bị chặn; Admin/reviewer riêng duyệt thành công với ghi chú để trống; Creator publish V2. SQL xác nhận `SubmittedBy != ReviewedBy`, pointer đổi đúng version, chỉ một outbox event mới. Tạo V3 tự chọn/mở editor đúng.
- Swing Reader riêng: ảnh JPEG thật, chế độ cuộn/từng trang, progress, bookmark, preferences; library hiển thị history/bookmark. SQL xác nhận đúng user/version/Page ID/revision. Preview không tạo progress.
- Upload bổ sung 196 PNG 600×900, ZIP 385.184 byte: job thành công lần 1 trong 17.604 ms; replay cùng key trả cùng job; snapshot cuối 200 trang được duyệt/publish qua REST thật. SQL: 200 bindings READY và đủ JPEG/PNG; public pointer/outbox nhất quán.
- Swing mở được trang 200; progress V3/Page259, revision 4, completed=true. Bookmark cũ vẫn mở được trên bản mới. Heap tại hai thời điểm khoảng 22–28 MB, **chỉ là smoke observation**, chưa phải memory benchmark dài hạn.
- NetBeans 31 Debug Project đã khởi chạy `DesktopApplication` thật với JDWP; UI tải catalog từ backend. Backend attach debug đã được kiểm chứng ở lượt trước. Không có SQL credentials trong Swing.
- Maven reactor `verify` thành công 17:57:02: **23 backend unit + 16 desktop unit = 39**, không failure/error/skip. Toàn bộ SQL/S3/SMTP integration suite thành công 18:06:58: **31 tests**, không failure/error/skip. Log private `.local/qa/final-build.log`, `.local/qa/final-integration.log`.

### Lỗi đã sửa

1. `PublishingPanel.createVersion` chạy refresh list và load detail đồng thời; selection-cleared listener hủy detail mới. `RemoteTable.firstSelecting` chỉ chọn sau khi thay rows. Regression test tái hiện fail trước sửa; GUI V3 xác nhận sau sửa.
2. SDK S3 đọc lại stream file không reset được: upload Swing trả `STAGING_FAILED`, dù test byte-array trước đây đạt. Adapter spool có giới hạn 100 MiB xuống file tạm, dùng SDK `fromFile`, kiểm đúng độ dài và cleanup. Integration test stream file 256 KiB fail trước sửa, đạt sau sửa; ZIP native và batch 196 ảnh đều qua.
3. Version list giữ state/page count cũ; publish refresh chapter làm mất editor. Cập nhật entity trả về ngay trong row, giữ selection. Test submit và GUI publish đạt. Giữ upload key khi staging thất bại để chọn lại cùng file.
4. Ghi chú duyệt tùy chọn bị gửi thành chuỗi rỗng, vi phạm validation. Swing bỏ trường này khi trống; từ chối vẫn yêu cầu lý do. Unit và reviewer GUI thật đạt.
5. Helper cleanup fixture lặp các join rows của cùng comic khi có nhiều version/upload. Giới hạn ownership bằng version gốc đã review, dedup comic; không mở rộng điều kiện xóa. Regression SQL/S3 thêm draft/upload và cả suite đạt.

### Backup/restore mẫu đã được người dùng cho phép và thực hiện

- Dừng đúng backend/QA và RustFS do dự án khởi chạy; chụp volume 625 file, đối chiếu SHA-256 toàn bộ trước khi phục hồi. Database gốc không bị restore đè.
- SSMS kết nối sa có sẵn chạy script 12: COPY_ONLY + CHECKSUM, VERIFYONLY, restore database mới `ComicNetRestoreQA_BB52A5FD5E62`, CHECKDB không lỗi. File .bak 1.282.048 byte; SHA-256 đã ghi trong `.local/qa/backup-file-checksum.json`, tính qua SQL service vì Windows ACL chặn process agent đọc thư mục Backup. Không đổi ACL hoặc đọc mật khẩu sa.
- 19 nhóm bảng lõi khớp trước khi bản gốc thu hồi fixture; schema Flyway 11, 3 version, 200 stable pages, 205 version bindings, 203 assets / 406 variants, progress/bookmark/preferences đúng.
- Backend kiểm chứng riêng 8082 + RustFS volume riêng 9100: login, GET cả 200 signed assets, decode mẫu đầu/giữa/cuối, progress revision 4 / completed, bookmark và PAGED preference đều đạt. Swing resume được trang 200 từ cặp dữ liệu đã restore.
- Bổ sung cấu hình operator `RESTORE_VERIFICATION` chỉ cho local, tên database kiểm chứng bị giới hạn, endpoint storage riêng; bắt buộc tắt setup/Flyway/mail/upload workers. Unit test giữ chặn sa, shared login, master, worker bật hoặc endpoint storage chính. Hướng dẫn Swing đã cập nhật, không thêm nút giả backup/restore.
- Evidence private: `.local/qa/restore-evidence.json`, `restore-http-evidence.json`, `restore-20261027-1756/storage-manifest.json` và các snapshot `data-*.json`. Giữ database/volume phục hồi để đối chiếu; chỉ fixture thuộc namespace test trong ComicNetDev được thu hồi bằng helper có guard.
- Bàn giao: backend chính 8080 đã khởi động lại, ping/readiness UP; storage chính 9000 và SMTP local giữ hoạt động. Các tiến trình kiểm chứng 8082/9100 và Reader QA đã dừng, database/volume/backup không bị xóa. SQL kiểm tra cuối 18:12:53: không public pointer sai, orphan version-page, FK disabled/untrusted hoặc active upload; ba actor fixture gốc không còn trong ComicNetDev. Log private `final-data-integrity.json`.

### Còn phải kiểm chứng trước khi kết luận nghiệm thu đầy đủ

- Ma trận resize tối thiểu và DPI Windows 100/125/150%, keyboard/focus toàn bộ màn chưa hoàn tất. Automation resize chưa xác nhận được kích thước đích; lượt sau có tương tác người dùng/cửa sổ bị che nên không tiếp tục dùng tọa độ cũ. Không gọi mục này PASS.
- Luồng dialog xác thực/setup/reset/role administration cần người dùng thao tác GUI theo checklist; backend security/auth/SMTP thật đã qua integration, nhưng không thay thế nghiệm thu native các dialog này.
- Chưa đo profile hiệu năng SRS với 20 readers, 1.000 comics / 10.000 chapters và mạng 10 Mbps / 100 ms RTT; chưa có memory/GC soak qua nhiều chapter. Bài batch/200-page local không chứng minh các target này.
- Chưa thực hiện dependency vulnerability scan cập nhật hoặc phục hồi một job đang chạy dở trong bài restore mẫu. Không tự coi build/tests xanh là đã đạt các mục chưa đo.
- Figma Home dùng reference đã đọc; quota Figma MCP còn chặn kiểm tra live Manga/Details, nên chưa có visual sign-off cuối trên toàn bộ source frames.

`prj_app.md` SHA-256 vẫn `0cf413e66bfc759c2ca849b7c904e8f80bf21b8bafd9ec1555f069f8676a2458`. Git chưa có commit; source/docs còn untracked. Không reset/stage/commit. Phần bên dưới là **checkpoint khôi phục lịch sử**, không mô tả trạng thái QA mới nhất.

---

Checkpoint khôi phục 2026-10-27, khoảng 17:05 Asia/Saigon. Đây là trạng thái thực thi hiện tại; các tài liệu Phase 0 giữ nội dung thiết kế và có nhiều nhãn trạng thái lịch sử chưa cập nhật.

- Phase 0 và Phase 1 đã được người dùng nghiệm thu. **Phase 2 đang triển khai/QA, chưa hoàn tất hoặc nghiệm thu.** Phase 3–7 và React Web extension chưa bắt đầu.
- Giữ Java 21, Swing, Spring Boot 4.1.2, JUnit 6, Maven, SQL Server 2025 và Modular Monolith. Phạm vi 23 module / 243 chức năng giữ nguyên; MVP gồm 19 stories và 74 feature có phần Phase 2.
- Đã có code auth/setup/RBAC, creator team tối thiểu, catalog/search, storage, publishing/version/upload/review/publish, access policy, reader/progress/preferences, library/bookmark, admin/audit/jobs và các màn Swing tương ứng. Không đồng nghĩa mọi acceptance criterion Swing đã được xác minh.
- OpenAPI hiện tại: 71 paths, 92 operations, 81 schemas. Migration nguồn V1–V11. Kiến trúc và contract có unit test kiểm tra ranh giới module / HTTP routes.

## Điểm bị gián đoạn

Đang kiểm tra giao diện QA Creator: Xuất bản → chọn truyện → bản dịch → chương → Version / Trang. Đã hiển thị version đầu tiên PUBLISHED. **Chưa thực hiện bước Tạo bản nháp trong lượt GUI QA này.**

Tiếp tục tại `desktop-swing/src/main/java/com/comicnet/desktop/features/publishing/PublishingPanel.java`, phương thức `createVersion(boolean copy)`, sau đó kiểm tra `upload`, `submit`, `review`, `publish` và mở Reader bằng actor riêng. Các phương thức đã có code đầy đủ; không có đoạn source bị cắt giữa chừng được phát hiện.

Hai chỉnh sửa cuối trước gián đoạn:

- `MainFrame` constructor: nhãn tài khoản phản ánh session được cung cấp lúc mở frame.
- `PublishingPanel.onShown()`: xóa thông báo đăng nhập cũ khi đã có session.

Các sửa này được giữ nguyên và đã qua lượt build/unit test khôi phục.

## Evidence và giới hạn

- Mới chạy lại Maven reactor `test`: **22 backend + 14 desktop = 36 tests**, không failure/error/skip; BUILD SUCCESS lúc 16:59:45 +07:00. Log private: `.local/recovery-build.log`.
- Mới chạy riêng **2 SqlFoundationIT** chỉ đọc dữ liệu lúc 17:05:32 +07:00: SQL Server major version 17, đúng ComicNetDev, migration marker, quyền runtime tối thiểu, Tomcat ping/readiness/firewall đều đạt. Dùng các goal test-compile/Failsafe để không thay JAR backend đang chạy. Log private: `.local/recovery-sql.log`. Không chạy lại toàn bộ 29 integration tests trong bước khôi phục.
- Trước gián đoạn: reactor `verify` thành công lúc 13:46:59 +07:00; báo cáo Failsafe ghi **29 integration tests** đạt. Đây là evidence trước gián đoạn, không gọi là toàn bộ integration suite vừa chạy lại.
- Backend chính localhost:8080: ping UP, SQL readiness UP ở lượt khôi phục. RustFS localhost:9000 và Mailpit localhost:8025 trả HTTP 200.
- QA backend localhost:8081 đã tự dừng lúc 14:32:52 theo timeout 30 phút của `ManualDesktopReview`; log ghi BUILD SUCCESS. `.local/qa/review.json` cũ không chứng minh fixture/session còn tồn tại. Không dùng lại token hoặc ID cũ để báo GUI pass.
- NetBeans Run Swing và attach JPDA backend đã được kiểm tra trước gián đoạn. Cần hoàn tất GUI nghiệp vụ, resize/DPI/keyboard, kiểm chứng đóng gói cuối và backup/restore mẫu trước khi kết luận Phase 2 đạt yêu cầu.
- Chưa có commit Git; các thư mục source/docs đang untracked. `git diff` không thể cung cấp lịch sử cho các file chưa được track. Không stage, commit, reset hoặc xóa source trong lượt khôi phục.
- `prj_app.md` giữ SHA-256 `0cf413e66bfc759c2ca849b7c904e8f80bf21b8bafd9ec1555f069f8676a2458`.
- Lượt khôi phục chưa sửa code ứng dụng, chưa tạo lại fixture nghiệp vụ, chưa provision/reset database hoặc xóa dữ liệu hiện có.

## Quy tắc tiếp tục

Dùng lại code và công cụ QA hiện có; chỉ khởi tạo lại fixture kiểm thử tạm khi tiếp tục GUI vì phiên cũ đã hết hạn. Không provision lại database/login, không reset BootstrapState, không chạy script SQL 02, không thay mật khẩu/quyền hoặc sửa công nghệ. Mật khẩu SQL chỉ ở môi trường tiến trình backend, không đi vào Swing hoặc checkpoint này.

