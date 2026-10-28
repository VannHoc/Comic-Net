# SRS baseline — Comic Net Swing MVP

Revision3, ngày 2026-10-27, đã được dùng làm baseline được duyệt. Evidence triển khai/QA hiện tại tại [implementation status](../implementation-status.md). Nguồn chính thức [prj_app.md](../../prj_app.md) đã cập nhật theo yêu cầu user: Swing chính, React Web mở rộng, không giảm 23 module/243 chức năng. Phạm vi chi tiết nguồn nằm tại [feature register](feature-register.md); story/AC tại [MVP backlog](mvp-backlog.md), không suy ra một module hoàn thành khi mới làm phần MVP.

## Mục tiêu và actors

Ứng dụng desktop đọc/quản lý truyện, dữ liệu thật, chạy/demo bằng NetBeans31. Java Swing Java21 gọi REST Spring Boot4.1.2 Modular Monolith, Microsoft SQL Server2025, JPA/Flyway/Security, Maven/JUnit6. Backend là nơi duy nhất quyết định quyền, state và transaction. Swing không kết nối DB hoặc gọi Java service nội bộ backend.

Actors giữ Guest, Reader, Creator, Translator, Editor, Proofreader, Uploader, Moderator, Administrator. Role hệ thống/team khác scope; deny by default, ownership và same-parent checks tại backend. [Permission matrix](../security/permission-matrix.md) là bảng quyền chi tiết; Swing ẩn/disable control chỉ là UX.

## Phân loại yêu cầu

| ID | Requirement / acceptance observable | Phase |
|---|---|---|
| SRS-PLAT-01 | Parent Maven mở được trong NetBeans; Run backend child và Swing child, Java21; không cần npm/browser cho MVP | 1 |
| SRS-PLAT-02 | Swing chỉ có HTTP API base/config UI; không driver SQL/JPA/entity/repository/DB secret; backend SQL2025/Flyway | 1–2 |
| SRS-PLAT-03 | Toàn bộ functional stories US00..18 thao tác qua Swing; US16 ops procedure/runbook theo user đã xác nhận | 2 |
| SRS-AUTH-01 | Swing register/verify/resend/reset/login/logout, JWT10m memory, per-client refresh cookie/session absolute30d, rotation/revocation/CSRF | 2 |
| SRS-AUTH-02 | Wizard setup Admin một lần qua token-gated local REST; account không tự có admin từ registration; retry/race không tạo admin thứ hai | 2 |
| SRS-CAT-01 | Swing catalog/search/details, metadata/translations/volumes/chapter CRUD; hidden/age/permission đúng, pagination/API thật | 2 |
| SRS-PUB-01 | Swing file chooser ảnh/ZIP, progress job, reorder/replace/version preview, submit/review/publish/hide; review actor khác submitter | 2 |
| SRS-READ-01 | Reader Swing scroll/paged/RTL/LTR/fit, loading/retry images; JPEG/PNG display từ API; không phát URL chưa có quyền | 2 |
| SRS-SYNC-01 | Auto-save/resume/history/bookmark/preferences qua API; revision/mutation/generation và conflict UI, không stale overwrite | 2 |
| SRS-LIB-01 | Default/custom collections, status/favorite/follow, bookmark/history management trong Swing; owner-only | 2 |
| SRS-ADM-01 | Swing users/roles/team minimum, review queue, visibility/suspension, jobs/audit; không last-admin removal/other-team escalation | 2 |
| SRS-OPS-01 | Swing health/errors/jobs/audit và runbook backup/restore; procedure thực do operator kiểm chứng, không nút giả thực thi SQL | 2/7 |
| SRS-UI-01 | Controls và state cập nhật trên EDT; HTTP/decode/upload ngoài EDT; hủy/timeout/logout không để response cũ cập nhật user/view mới | 1–2 |
| SRS-UI-02 | Desktop layout resizable, keyboard/focus/DPI/contrast, states loading/empty/error/401/403/409/412/backend-down; không ghép mobile screenshot thành UI | 1–2 |
| SRS-WEB-01 | React/TypeScript/Vite/Tailwind/Router giữ ngoài MVP, dùng cùng REST; Web responsive/CSRF/browser testing chỉ khi Web phase được duyệt | WEB-EXT |

US00..18 là19 stories, không19 module mới.74 source features có phần MVP được liên kết [traceability](mvp-traceability.md), không tự giảm vì khác desktop widgets. Setup wizard là delivery của bootstrap story đã có, không tạo module nghiệp vụ thứ24.

## Coverage 23 module và platform

| Module | Desktop delivery / nâng cao | Phase |
|---|---|---|
| M01 auth | Auth/session/permissions cơ bản trong Swing; devices/MFA/recovery/SSO/passwordless theo feature register | 1–2/3/6 |
| M02 catalog | Desktop catalog, details, editor; giữ mọi metadata/source features | 2 |
| M03 publishing | Desktop upload/version/review/publish; schedule/rollback/format variants nâng cao | 2/3 |
| M04 reader | Native Swing reader; fullscreen/auto-scroll/brightness trong reader window khi nâng cao, không chỉnh OS toàn cục | 2/3/6 |
| M05 storage | Backend delivery; Swing nhận URL/ảnh tương thích, không raw storage credential | 2/3/7 |
| M06 search | Swing search/filter qua API; fulltext/semantic/reverse-image nâng cao giữ scope | 2/3/6 |
| M07 analytics | Desktop ranking/report khi feature có; không số liệu giả trong MVP | 3/5/6 |
| M08 library | Collections/history/progress/bookmark qua API, sync không phụ thuộc React | 2/6 |
| M09 community | Swing comment/reply/report/moderation; emoji/GIF/sticker khi phase tương ứng | 3/5 |
| M10 recommendation | API rating/review/recommendation dùng cho Swing/Web sau này; AI không tạo hệ gợi ý thứ hai | 3/6 |
| M11 wallet | Backend dual coin/ledger, desktop wallet UI khi P4; không desktop balance authoritative | 4/5 |
| M12 monetization | Desktop price/purchase/VIP/Fast Pass/voucher/ads; provider/browser redirect cần thiết kế riêng P4, React không điều kiện | 4 |
| M13 notification | In-app Swing; email/provider/push có adapter/platform mapping, không giả browser push là Swing push | 3/6 |
| M14 creator | Team setup trong Swing MVP; portal/tasks/revenue/payout desktop mở rộng | 2/5 |
| M15 admin | Desktop Admin views gọi REST, không SQL trực tiếp | 2/3/4/7 |
| M16 gamification | EXP/level/achievement/cosmetics/perks qua server, desktop view khi P5 | 5 |
| M17 minigame | Swing gameplay dùng result server, đủ inventory/card/quiz/redemption khi phase tương ứng | 5 |
| M18 ai | Backend jobs/human review desktop, giữ toàn chức năng AI | 6 |
| M19 offline | Desktop download/storage/license/sync thiết kế riêng P6; Web/PWA offline vẫn là nhánh bổ sung giữ scope | 6 |
| M20 entitlement | Server access policy và grants, desktop không tự suy ra quyền từ wallet/cached UI | 2/4/6 |
| M21 observability | Backend metrics/logs/jobs + desktop error/requestId; không log token/signed URLs | 1–7 |
| M22 dataprotection | Secrets/backup procedure/verification sớm; export/delete/privacy/retention/DR theo phase | 1–2/6/7 |
| M23 finance | Server webhooks/reconciliation/subscription lifecycle, desktop admin controls khi feature có | 4/5 |

Browser-specific gốc giữ ý nghĩa cho Web extension; desktop adapters không xóa chức năng hoặc tự coi đã hoàn tất. Mobile native tương lai không thuộc MVP. Mọi advanced story phải có UI target (Swing hoặc Web/platform đặc thù được duyệt) khi refine; không lấy React làm dependency ngầm của chức năng desktop.

## Use case và flow chính

Guest → Swing auth/verify → Reader tìm truyện → detail → reader → progress/library → tiếp tục bằng client session khác. Creator/Editor/Uploader → desktop editor/upload → Moderator review → publish → Reader đọc. Admin → setup wizard một lần → provision team/roles → vận hành GUI. Bootstrap dùng REST setup capability, SQL/password database không đi qua client.

Email verification/reset dùng nhập token/link code trong Swing; browser mở link email có thể là tiện ích ngoài, không cần React page để hoàn tất. Same REST verify/reset token payload. File upload dùng chooser/multipart; preview/reader dùng image components, không WebView. Backend/storage down hiển thị retry và giữ view/input phù hợp; không báo save/publish thành công trước server commit.

## NFR và acceptance gates

Giữ latency/reliability/integrity/security targets tại [testing strategy](../testing/strategy.md), đo Swing EDT/render/memory trên Windows thay browser trace cho MVP. Wireframe desktop proposed1280×800, kiểm tra1024×640 và Windows DPI100/125/150%; chưa coi là user-approved visual design. JVM heap/dataset/network/DPI phải ghi khi benchmark; không claim performance trước chạy.

P1: hai child chạy từ NetBeans, Swing gọi ping thật, SQL/Flyway và deny/security checks; không business placeholders. P2:19 stories và74 source features có phần MVP được kiểm thử qua Swing + REST + SQL/storage; không Node/Vite/React bắt buộc. P3–7 tiếp tục scope nghiệp vụ. WEB-EXT sau MVP có xác nhận và DoD riêng, không chặn Swing release. Source/Phase1 chỉ bắt đầu sau user xác nhận.
