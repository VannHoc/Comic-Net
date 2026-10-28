# Testing strategy và NFR mục tiêu

Revision3 Swing-first là baseline kế hoạch; application source, migration và provider integration đã có. Kết quả nghiệm thu hiện tại ở [biên bản Phase 2](phase-2-acceptance.md). Các chỉ tiêu NFR bên dưới là **target**, chưa được benchmark đầy đủ. MVP dùng JUnit6/presenter tests + GUI tests trên Windows desktop thật; browser/Playwright/Vitest không là điều kiện P1/P2, chỉ cân nhắc ở WEB-EXT.

## Ma trận bắt buộc

| Test ID | Story/phase | Ca kiểm thử và kết quả mong đợi |
|---|---|---|
| TC-BOOT-01..04 | US00/18/P2 | Swing setup wizard→local REST secret, disabled/nonloopback/wrong/expired token deny; parallel requests/lost response không Admin thứ hai; audit+state atomic; team provision trước Comic, không cần US05 |
| TC-AUTH-01..05 | US01–03/P2 | Wrong login, duplicate email generic, expired/reset replay, refresh concurrency, suspended/revoked session; không leak/token replay |
| TC-SEC-01..05 | US05/08/15/P2 | 9-role allow/deny; other-team; mixed Chapter/Version/Page IDs; self review; last-admin race; deny/audit đúng |
| TC-DB-01..04 | US05–08/P2 | Migrations DB sạch; same-parent composite FKs; unique sort/page/draft; transaction rollback publish; không dùng H2 thay SQL Server cho evidence |
| TC-UP-01..05 | US06/P2 | ZIP slip/bomb/unsupported decode; empty pages; interruption object/DB; retry same key; worker restart reclaim; không public chapter lỗi |
| TC-VER-01..04 | US07–09/P2 | Replace image same PageId; reorder; remove page; publish V2 trong khi đang đọc V1; purchase/comment anchors không bị xóa |
| TC-READ-01..04 | US09/P2 | Restricted/hidden/direct URL attempts; expired signed URL refresh; lazy load error; next/previous đúng translation |
| TC-SYNC-01..05 | US10/14/P2 | Two devices same revision; intentional rewind; duplicate mutation; stale offline reconnect; clear history rồi retry cũ; conflict không overwrite |
| TC-LIB-01..03 | US13–14/P2 | Unique follow/item; owner-only collections/bookmarks; system collection cannot delete; no comic cascade |
| TC-UI-01..04 | US17/P2 | Swing resize/minimum window/DPI100/125/150%, focus/keyboard/labels, loading/empty/errors/backend unavailable; real REST, no React dependency; Figma là reference, không pixel-identical mobile |
| TC-SW-01..08 | US00..18/P1–2 | EDT-only view updates; HTTP/decode/upload off-EDT; stale-response/cancel/navigation/logout isolation; separate cookie store/users; memory-bounded reader; JPEG/PNG MIME decode; JFileChooser/multipart/ZIP progress; NetBeans two child mains |
| TC-COM-01..04 | P3 | Sanitize XSS, reply tombstones, duplicate votes/rating, hidden recommendation/search filtering |
| TC-NOT-01..03 | P3 | Commit publish event once; preference mute; retry delivery dedup; failure không rollback publication |
| TC-FIN-01..08 | P4 | Same chapter repeated purchase, two spends same balance, duplicate/out-of-order webhook, price change snapshot, failure after debit before grant rollback, refund/reversal, renewal/expiry, reconcile timeout |
| TC-REW-01..05 | P5 | Duplicate EXP source; cap/day; spam comments; two daily spins; retry returns same RNG result; reward grant atomic |
| TC-OFF-01..04 | P6 | Offline quota/license expiry; revoke on reconnect; stale progress; partial download/checksum failure |
| TC-OPS-01..04 | US16/P2 then P7 | SQL unavailable→503 và Swing error; jobs failure; operator DB+asset restore evidence/runbook với Swing log viewer; không nút giả BACKUP/RESTORE; log redaction/correlation IDs |

Backend: JUnit6/Mockito theo Boot4.1.2 BOM, thay riêng JUnit5 theo user cho phép2026-10-27; Spring Boot Test cho auth/controller/integration; repository test trên SQL Server2025 disposable test database cùng engine (container nếu máy hỗ trợ, không bắt buộc Docker). Không dùng production DB cho destructive tests. Financial concurrency tests chạy threads thực với transactions riêng và barriers, assert balances+ledger+grants, không chỉ mock calls.

Desktop: JUnit6 cho presenter/validation/DTO/error/ETag mapping và session logic; HTTP adapter tests dùng test server và API contract, không coi mock là E2E. GUI integration/E2E cần Windows non-headless, NetBeans hai process, bàn phím/focus/window/DPI; JDK Robot hoặc thao tác kiểm chứng có steps/evidence trước khi chọn framework GUI bên ngoài. Không dùng JavaFX/TestFX hoặc JUnit5 GUI runner ngầm. Backend và pure presenter tests có thể headless; GUI test không được auto-skip rồi báo toàn suite GUI pass.

MVP E2E: setup/register/verify/login → catalog/search → reader/progress/library; Creator upload/reorder/preview → reviewer khác approve/publish → Reader; Admin team/roles/suspend → blocked session. Tất cả qua Swing với SQL/storage thật, hai phiên memory độc lập, không embedded browser/React. Email token nhập qua dialog, không phụ thuộc React callback. Ops backup/restore vẫn operator-run procedure user đã duyệt. Screenshots ghi window size/DPI/font/build; so desktop với layout đã review và Figma identity, không mobile pixel equality. Web component/responsive/browser E2E sau MVP ở WEB-EXT có gate riêng.

## NFR proposal và phương pháp

| Metric | Target local demo | Cách đo / phase |
|---|---|---|
| API catalog p95 | <=500ms warm, exclude image transfer | P2; 20 concurrent readers, 1,000 comics/10,000 chapters, đo 5 phút sau warmup, log hardware/indexes |
| Basic search p95 | <=800ms với dataset trên | P2/3; Unicode/alias/author/genre query mix; không tính thành semantic search |
| Manifest p95 | <=800ms cho tối đa 100 trang/request theo dữ liệu test | P2; gồm access/signing, không download ảnh; nếu 500 trang payload cần đo lại, không giả định đạt |
| Reader image readiness | First visible JPEG/PNG <=2s trên profile10Mbps/100ms RTT sau Swing view ready | P2; Java client request/decode/EDT-render timings, ghi ảnh/cache/DPI/window/heap |
| Reader memory | Không crash chapter200trang, bounded decoded-image cache theo viewport | P2/P6; JVM heap/GC/render trace, switch chapter nhiều lần; không download/cache private để claim offline |
| Swing responsiveness | Không HTTP/ImageIO/upload hoặc Future.join/get chờ mạng trên EDT; resize/cancel vẫn dùng khi network chậm | P1–2; inject delay, thread/EDT assertions và manual/Robot evidence; không tự đưa số benchmark đã đo |
| Upload processing | Job không mất sau restart; 100 images test hoàn tất hoặc FAILED rõ lý do | P2; ghi CPU/codec/size, chưa chốt throughput số khi chưa đo |
| Availability demo | SQL/storage failure có controlled error/requestId; không trả success giả | P2; fault injection local |
| Backup/restore | RPO đề xuất 24h; RTO đề xuất 2h cho demo dataset | P2 mẫu/P7 drill; ghi DB+asset backup time, restore duration, referential/object checks |
| Security | Không unresolved critical auth/integrity issue trong scope release | Mỗi phase; negative auth, dependency scan khi có deps, manual threat cases |
| Usability/accessibility | Keyboard và form label/error; contrast kiểm tra, exceptions documented | P2–7; source token nhạt cần audit, điều chỉnh có chủ đích và review |

Trước benchmark ghi Windows/JDK/Node/SQL versions, CPU/RAM/storage, heap, dataset, network profile, warm/cold, sample count và percentile. Nếu không đạt, đo bottleneck trước khi thêm Redis/search engine/broker. Production SLO/HA/disaster recovery đa vùng là tài liệu mục tiêu sau này, không tuyên bố đồ án local đã đạt.

## Gate từng phase

Requirement → story → API/schema → implementation → test evidence. Không chuyển Verified chỉ vì UI đẹp hoặc test mock pass. Phase 1 chạy smoke/build thực; Phase 2 integration/auth/reader/upload/restore; P4 finance concurrency; P5 abuse; P6 offline/AI; P7 tổng kiểm các feature đã xây, không bỏ negative tests đến cuối.

Revision3: Phase1 dùng SEC-P1-01..10 trong [security foundation](../security/foundation.md), không coi fixture thành Auth/Setup implemented.19stories/74features có [traceability](../requirements/mvp-traceability.md) và [Swing views](../design/swing-screen-map.md). TC-UP gồm compatible image derivative+staging/orphan cleanup P2; retention/multiple-resolutions/WebP/AVIF output đầy đủ P3. Payment/subscription reconciliation P4; payout P5. Web tests không chặn demo desktop, toàn bộ target chưa đo.
