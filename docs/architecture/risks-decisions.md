# Risks, decisions và readiness

Ngày 2026-10-27. P0 = nguy cơ integrity/security/architecture nghiêm trọng; P1 = high; P2 = medium. Xác suất H/M/L là đánh giá thiết kế, chưa có số đo production. Phase deadline là lúc phải giải quyết, không phải yêu cầu dựng mọi hệ thống từ đầu.

Revision3: [Swing impact](swing-impact.md)/[SRS](../requirements/srs.md) thay baseline Web-first;23module/243features giữ nguyên. React/Web vẫn retained ngoài MVP, không dùng làm giải pháp ngầm cho màn Swing còn thiếu.

## Risk register

| ID | Mức / xác suất | Rủi ro và hậu quả | Xử lý / evidence cần có | Deadline |
|---|---|---|---|---|
| R01 | P0/H | Page/version identity sai làm hỏng anchor và quyền mua | Stable ChapterPage + VersionPage bindings, composite FK, test thay/reorder/remove/version publish | Thiết kế trước code domain P2 |
| R02 | P0/H | Role rộng hoặc trộn team gây IDOR | Permission matrix, same-parent FK, other-team negative tests; deny backend | Trước auth/catalog P2 |
| R03 | P0/M | Circular dependency, business logic chảy sang admin/common | Ownership + DAG đã lập; code import check khi có source | Trước P1 package plan; verify mỗi module |
| R04 | P0/M | Double spend/duplicate purchase/webhook | Shared tx, balanced ledger, account locks, idempotency/inbox, rollback/concurrency test | Thiết kế lõi hiện có; chốt policy trước P4 |
| R05 | P0/H | Upload ZIP/bomb/path traversal, public draft ảnh | Decode/limits/staging/private buckets/job recovery; evil archive tests | Trước upload P2 |
| R06 | P1/H | Sync stale ghi đè hoặc clear history bị retry tạo lại | Revision CAS + mutationId + HistoryGeneration; two-device tests | Trước Reader P2 |
| R07 | P1/H | Dùng mobile Figma như desktop spec làm UI khó dùng | Figma reference + desktop wireframes/Swing screen map, native focus/DPI/resize review | Shell P1 rõ giới hạn; feature UI P2 |
| R08 | P1/M | Spring/Flyway/JDBC/SQL Server 2025 không tương thích cấu hình máy | Verify Java/NetBeans/SQL/driver/runtime docs; test DB sạch, không thay DB | Trước scaffold P1 |
| R09 | P1/M | Token refresh replay/suspension delay/CSRF | Rotation+session lookup+cookie/Origin/CSRF; concurrency tests | Security plan P1; chứng minh P2 |
| R10 | P1/H | Object storage và DB không atomic; job mất khi restart | Persistent job lease, staged object grace cleanup, pointer publish transaction | P2 |
| R11 | P1/M | Backup DB không đủ ảnh hoặc restore không thật | Backup manifest DB/object refs; restore sample then full drill | P2 mẫu; P7 đầy đủ |
| R12 | P1/H | Scope vượt nguồn lực đồ án | MVP rõ, 243 feature giữ phase, không build đồng thời; feature status trung thực | Theo sprint/phase |
| R13 | P1/M | Paid content đã ký URL không revoke ngay | Short TTL, renew access check, private storage; ghi residual exposure | P2 nền; P4 hardening |
| R14 | P1/M | Coin refund/chargeback khi xu đã tiêu | Freeze/manual review hoặc policy được duyệt; append-only reversal | Trước P4, không block toolchain P1 |
| R15 | P1/M | Game/EXP abuse | Server-authoritative event validation/RNG, cap và source dedup | Trước P5 |
| R16 | P1/M | Offline encryption bị hiểu là DRM tuyệt đối | License expiry/threat model/key management + platform limitations | Trước P6, không dựng DRM P1 |
| R17 | P2/H | Analytics/search/AI chậm hoặc không chính xác | Basic SQL search trước, measured indexing/cache/queue sau; AI test set và human review | P3/P6 |
| R18 | P1/M | Privacy deletion xóa nhầm ledger hoặc giữ PII quá lâu | Retention/anonymization matrix; export/delete tests; tách dữ liệu phải giữ | Data design hiện tại; trước P6–7 |
| R19 | P2/M | CDN/queue/distributed cache vận hành quá phức tạp | SQL-backed jobs/single app baseline; introduce service only at measured need | Quyết định từng phase |
| R20 | P1/H | Swing block EDT/memory leak/stale async làm freeze hoặc hiện data user cũ | Worker/HttpClient async, EDT updates, bounded image cache, generation guard/cancel; TC-SW | P1 shell; P2 reader/auth |
| R21 | P0/M | Nhầm desktop trusted, bỏ CSRF/permission hoặc gửi cookies sang media host | Per-session cookie memory, no-Origin không bypass, no client-type trust; media client tách; negative tests | P1 foundation/P2 auth |
| R22 | P1/H | ImageIO không đọc WebP/AVIF và chapter public không đọc được Swing | Server JPEG/PNG display derivatives + MIME/codec/readiness tests, không WebView workaround | Trước upload/publish/reader P2 |
| R23 | P0/M | Setup wizard thành backdoor cấp Admin | Default disabled/local+loopback+secret+one-time BootstrapState tx/audit, reject browser Origin; no public reset | Trước US00 P2; không mở mock route P1 |
| R24 | P1/M | Parent Boot repackage/headless làm Swing child không chạy NetBeans | Boot plugin chỉ backend; Java SE main desktop, hai JVM, real GUI smoke không headless skip | Trước nghiệm thu P1 |

## Proposed decisions (chưa thay stakeholder approval)

| ID | Đề xuất và lý do | Tác động / cần review |
|---|---|---|
| D01 | Revision3: parent Maven hai children backend/desktop-swing;23 business packages chỉ trong backend | Hai executables NetBeans, no desktop→backend Java dependency/SQL; React retained ngoài Maven reactor/MVP |
| D02 | ComicTranslation theo team/language/edition; Chapter ổn định; ChapterPage identity + ChapterVersionPage content | Nền tảng schema, cần duyệt trước domain code |
| D03 | MVP chỉ FREE/RESTRICTED, Guest All-age public; nội dung age-restricted chưa bật trước policy | Cần xác nhận age/guest rule trước publish public |
| D04 | Team minimum từ P2, creator portal full P5; admin provision teams | Không shortcut scope; đủ quyền Translator/Editor/Uploader MVP |
| D05 | Không self review; Creator publish được sau reviewer khác approve; không last-admin removal | Demo cần hai actor độc lập; bootstrap hai account an toàn |
| D06 | User đã duyệt JWT memory10m + refresh cookie/session30d/rotation/revocation/CSRF; Swing dùng memory CookieManager | Origin validate khi có, native không giả browser Origin và không bypass;5173 chỉ Web tương lai,8080 backenddev; không auth runtime |
| D07 | MinIO/S3 adapter local; SQL-backed jobs; proposed upload limits; basic SQL search | Kiểm tra endpoint/codec/config, không add broker/Redis/search engine P1 |
| D08 | Revision/mutationId/historyGeneration cho sync; không client-clock last-write-wins | Cần UI conflict state và tombstone retention |
| D09 | Coin nguyên BIGINT; ledger cân theo currency + system account; purchase orchestrated monetization; subscription owned finance | Chốt spend priority/expiry/refund/grace trước P4; không đổi ranh giới lõi |
| D10 | React Web tương lai giữ TanStack Query/RHF/Zod/Fetch/Context khi cần; không đưa vào Swing | Version/library set revalidate ở WEB-EXT, không dependency/gate P1/P2 |
| D11 | **User approved:** Spring Boot4.1.2 và JUnit6, cập nhật hai dòng trong prj_app.md | BOM versions và springdoc3.2.0 đã đối chiếu official docs; không đổiJava21/SQL2025/23module |
| D12 | **User approved:** SQL default `(local)`, ComicNetDev, SQL Authentication, runtime login riêng thay sa; dev encrypt=true/trustServerCertificate=true | Registry/TCP listener1433 xác minh; chưa SQL login/certificate handshake hoặc provision DB/login. Production phải validate certificate |
| D13 | User yêu cầu Swing là UI chính/MVP trong NetBeans, React/TypeScript chuyển Web mở rộng | Mọi functional MVP story có Swing UI; Figma là reference desktop, không pixel-identical mobile; source V2.1 cập nhật |
| D14 | User đồng ý wizard Swing→local REST first-admin một lần và giữ backup/restore operator procedure | Setup capability contract thêm1operation; US16 Swing runbook/log viewer, không SQL RESTORE API hoặc DB credential UI |

Lịch sử quyết định: khi kiểm tra phát hiện Boot3.5 hết OSS và JUnit5 trong source, đã hỏi thay vì tự nâng; user cho phép Boot4.1.2/JUnit6. Các câu hỏi Q-STACK/Q-DB/Q-TLS/Q-AUTH/Q-DBNAME đã có trả lời trực tiếp, không còn pending. Việc cho phép sửa baseline không cho phép bắt đầu Phase1.

## Readiness blockers

| ID | Block gì | Tình trạng và đường xử lý |
|---|---|---|
| B01 | Phase 1 bắt đầu | Người dùng yêu cầu chờ xác nhận. Đã có plan, chưa scaffold/code/build/install |
| B02 | Domain feature P2, không chặn P1 scaffold khi được phép | Guest/age/reviewer assignment/self-review/sync/limits còn product review trước feature. TTL/session/DB/browser policy đã được user duyệt; backlog bootstrap vòng đã sửa US00→US18→US05 |
| B03 | Swing feature UI tương ứng | Source typography/color đã đọc, desktop wireframe/states/native controls/font delivery/DPI còn review; shell P1 giới hạn ping; không bắt mobile pixel equality hoặc React/Vite để chạy MVP |
| B04 | P1 runtime acceptance, chưa có build trong P0 | Giữ evidence JDK/Maven/SQLregistry/TCP cũ; còn parent/child NetBeans run, SQL login/TLS/DB riêng, JSON deps/Boot+Swing build; Web peer/build checks chuyển WEB-EXT |

## Lịch sử revision2 — năm vấn đề trước khi đổi Swing

1. Version/environment: target Boot4.1.2/BOM/JUnit6 và frontend versions có nguồn; user đã chọn DB/auth/TLS/browser; runtime/metadata limits tách rõ.
2. Backlog cycle: bổ sung US00 bootstrap/US18 team provision, xóa dependency US15→US05; story graph19 nodes được kiểm tra riêng module graph.
3. Security Foundation: route allowlist/deny, CSRF/Origin/cookies, JWT key strategy/password config, secrets và SEC-P1 tests có contract, không auth mock.
4. Traceability: 74 features có phần MVP được liên kết story/API/tables/tests; job recovery/staging cleanup có P2, retention P3; payout full P5. Chỉ sửa phase labels, giữ nguyên243 bullet gốc.
5. UI source normalization: hai màu không gộp, Poppins+Inter đúng layer, card/search/button radius riêng; P1 không dựng màn giả. Không claim thiết kế mọi state đã có.

Các câu hỏi P4–6 không chặn việc lập cấu trúc P1 khi đã được phép: ưu tiên tiêu xu, Reward expiry, refund spent coins, VIP grace, revenue share/payout hold, reward day timezone, offline license, AI provider. Giữ pending cùng deadline, không tự quyết cơ chế tài chính/pháp lý.

## Điều kiện không overengineering

Không tạo entity/service/package rỗng cho23module; không queue broker/cache/search cluster/AI model/mobile-native Android/iOS ngay P1. Swing desktop là UI chính theo user, không nằm phần hoãn này. DB constraints/resource auth/idempotency/audit cần cho integrity. Chưa cần tracing backend riêng/Kubernetes/distributed transactions/generic workflow engine/permission DSL.

Sau thay đổi desktop: dùng Swing/JDKHttpClient, không JavaFX/WebView/embedded React, không auto-start Spring/SQL từ UI hoặc thêm23Mavenchildren. Hai Mavenchildren cần vì hai executables, không lý do thêm shared-domain library. GUI-test framework/Look&Feel bên ngoài/native installer chỉ khi có nhu cầu và compatibility được kiểm chứng.
