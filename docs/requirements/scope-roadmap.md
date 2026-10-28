# Phạm vi, MVP và roadmap

Baseline đề xuất. Nguồn: Phần II, V, XI, XIII, XVII của `prj_app.md`.

## Phạm vi bất biến

Giữ đủ M01–M23 và243 bullet trong [feature register](feature-register.md). **Java Swing là UI chính và MVP**, chạy/demo trong NetBeans31, gọi REST backend; không SQL trực tiếp. React + TypeScript responsive Web giữ trong WEB-EXT sau MVP, xác nhận riêng, không bị xóa. PWA/offline Web và mobile native vẫn định hướng phù hợp; desktop offline P6 có thiết kế riêng. Reverse image search/watermark/reward-expiry/encryption giữ điều kiện đúng như nguồn.

Java21/Swing + Spring Boot4.1.2/Maven/NetBeans31, SQL2025/SSMS22, JPA/Spring Security/Flyway/REST/OpenAPI/JUnit6 giữ nguyên. Maven parent hai children backend/desktop-swing;23 nghiệp vụ vẫn trong một backend. React/TypeScript/Vite/Tailwind ngoài MVP, không Maven/npm build gate của P1/P2. [SRS](srs.md), [impact analysis](../architecture/swing-impact.md), [environment baseline](../deployment/environment-baseline.md) là baseline revision3; lịch sử đổi hai dòng Boot/JUnit không còn là toàn bộ delta source của lần thay UI này.

## MVP đến hết Phase 2

**Must Have:** đăng ký/xác minh/reset password/login/logout/refresh; user status và RBAC; catalog đầy đủ metadata cơ bản và bản dịch; upload ZIP/ảnh an toàn, version/page ordering, review và publish ngay; đọc cuộn dọc và page-by-page, LTR/RTL, lazy loading, preferences; kiểm tra quyền đọc và signed access; search tên/tên khác/tác giả/thể loại; thư viện mặc định, collection, follow, bookmark, history/resume, sync dùng revision; admin CRUD/moderation cơ bản, audit; log/health, migration, backup/restore mẫu. Truyện trong MVP chỉ có chế độ FREE hoặc RESTRICTED; nội dung thương mại chưa được cấu hình bán trước Phase 4.

Mọi chức năng trên được sử dụng bằng Swing qua REST, không cần React.19 stories và74 source features có phần MVP giữ scope; US00 có wizard REST một lần do user duyệt, US16 backup/restore procedure vận hành với hướng dẫn/nhật ký Swing theo user đã xác nhận. Backend lo transaction/permission/codecs; UI chỉ presentation/UX. Cần ảnh display JPEG/PNG compatible với Swing từ pipeline P2, không mất WebP/AVIF roadmap P3.

**Should Have sau MVP:** ranking, community, rating, notification và recommendation cơ bản ở Phase 3; scheduled publishing, tối ưu reader nâng cao và account security mở rộng ở Phase 3. Không cần queue broker/search engine ngoài chỉ để có MVP.

**Advanced trong scope:** thương mại/finance ở Phase 4; team portal/EXP/game ở Phase 5; AI/semantic/offline/analytics/realtime và auth federation hoàn thiện ở Phase 6. Feature có nhiều bước ghi phase đầu tiên và bước mở rộng trong register.

## Phân bổ 23 module

| Module / owner | Phase nền → mở rộng | Phần triển khai chính |
|---|---|---|
| M01 auth | 1–2 → 3,6 | Core auth/RBAC; devices/MFA/recovery; Google/Facebook/Apple, OTP/passwordless |
| M02 catalog | 2 | Comic, translation, genres/tags/warnings, contributors, age/visibility |
| M03 publishing | 2 → 3 | Upload/review/version/publish và job recovery tối thiểu; schedule, published-version rollback UI, WebP/AVIF variants |
| M04 reader | 2 → 3,6 | Reader/progress/preferences; auto scroll/fullscreen/brightness; advanced memory/sync |
| M05 storage | 2 → 3,7 | S3-compatible private storage, signed URLs, validation, staging/orphan cleanup an toàn; CDN/watermark/cache/retention cleanup nâng cao theo nhu cầu |
| M06 search | 2 → 3,6 | SQL search/filter; autocomplete/history/full-text; semantic/reverse image và index service |
| M07 analytics | 3 → 5,6 | Valid views/ranking; creator analytics; advanced analytics/Redis sorted sets |
| M08 library | 2 → 6 | Favorites/follow/collections/status/history/bookmark, revision sync; realtime/offline merging |
| M09 community | 3 → 5 | Comment/reply/vote/report/block/spoiler; GIF/sticker nâng cao |
| M10 recommendation | 3 → 6 | Rating/review/content-based/trending/cold start; personalization/collaborative |
| M11 wallet | 4 → 5 | Paid/Reward Coin, balanced ledger, spend/refund/reconcile; reward use |
| M12 monetization | 4 | Price/products/purchase, VIP/Fast Pass, promotion/voucher/rewarded ads |
| M13 notification | 3 → 6 | In-app/email/preferences/retry/batch; push |
| M14 creator | 2 tối thiểu → 5 | Team ownership seed/admin setup; portal/invites/tasks/shares/payout |
| M15 admin | 2 → 3,4,7 | CRUD/users/roles/review/audit; reports/banners/feature flags/settings/financial controls |
| M16 gamification | 5 | EXP/levels/check-in/achievement/title/frame/badge/perk và anti-abuse |
| M17 minigame | 5 | Spin/gacha/cards/quiz/voucher/inventory/history, server RNG và dedup |
| M18 ai | 6 | Moderation/NSFW/spam/abuse/tags/embedding/features/human review/jobs |
| M19 offline | 6 | Download/index/progress/queue/quota/expiration/sync, encryption nếu phù hợp |
| M20 entitlement | 2 nền → 4,6 | FREE/RESTRICTED access; purchase/VIP/Fast Pass/promo/reward grants; offline policy |
| M21 observability | 1 → mọi phase,7 | Logs/request ID/health/audit; metrics/jobs/error tracking/tracing/alerts |
| M22 dataprotection | 1–2 → 6–7 | Secret/redaction/backup design và restore mẫu; export/delete/retention/privacy/DR |
| M23 finance | 4 → 5 | P4 webhook/orders/reconcile/subscription/refund/chargeback/dispute; payout reconciliation thực tế P5 khi có payout requests |

Scope tối thiểu M14 trong MVP không có team portal hoặc chia doanh thu: admin tạo team/membership và gắn bản dịch; role-specific publishing dùng đúng ownership từ đầu. Không tạo shortcut Creator = quyền mọi truyện.

## Roadmap và gate

| Phase | Dependencies | Output / tables / API / screens | DoD và test bắt buộc |
|---|---|---|---|
| 0 | Spec và Figma | Bộ docs tại README; ERD/API/permission/backlog/risks | Draft được kiểm tra, blockers và điểm chưa duyệt ghi rõ; stakeholder review trước nghiệm thu |
| 1 | Desktop baseline và cấu hình DB/auth đã chốt; cần xác nhận bắt đầu riêng | Maven parent/backend/Swing child, ComicNetDev/Flyway smoke, ping/health, Swing status shell | Hai child Run/Debug từ NetBeans; Swing ping thật không block EDT; Maven build2JAR; SEC-P1 checks; không npm/Vite/React |
| 2 | Phase1 + desktop wireframe/API/codec/security review | Core tables + BootstrapState;93 operations hiện tại;19 Swing screens/stories | Tất cả19stories usable trên Swing; source74features giữ scope, bootstrap→team→catalog không vòng; EDT/media/native-cookie/negative permissions/sync và ops restore evidence |
| 3 | Core read/publish events | Community/rating/notification/read aggregate/search history; API comment/rating/notification/ranking; moderation UI | XSS/spam/dedup, hidden-content filtering, notification retry; schedule/version rollback; review visual |
| 4 | Access service + reviewed finance rules | Wallet/ledger/purchase/pricing/grants/orders/subscriptions/webhooks/refunds/reconcile; wallet/purchase/VIP/admin finance UI | Sandbox payment E2E; duplicate purchase/webhook; concurrent spend; rollback giữa debit/grant; renewal/refund/chargeback |
| 5 | Core ownership + finance + reward policies | Team/task/share/payout/EXP/game/reward/card; creator/team/reward/game UI | Team isolation, revenue snapshots, payout reconcile; EXP dedup, server spin, concurrent turns/rewards |
| 6 | Entitlements/event data + từng advanced design | AI jobs/features, offline manifest/license/local store, advanced metrics; download/AI review/push/semantic UI | Offline expiry/quota/conflict, human review, search fallback, AI evaluation, SSO/MFA integration đã phân phase |
| 7 | Các feature đã triển khai | Backend+Swing distributions, desktop ops dashboard, retention/export/delete/restore docs và full handover | Security/SQL integration/Swing E2E/performance/desktop visual+DPI/accessibility; restore DB+assets; máy sạch; React không là gate |

**WEB-EXT — Web mở rộng, sau Swing MVP P2 và xác nhận riêng:** giữ React/TypeScript/Vite/Tailwind/Router + API integration, source Figma375×812/mobile/tablet/desktop, browser cookie/CSRF và E2E. Không là prerequisite P3–7; không tạo npm project hoặc code Web trong P1/P2. Các browser-specific capability như PWA/push có platform acceptance riêng khi refine, không âm thầm thay desktop features bằng Web-only UI.

Không trì hoãn kiểm thử/bảo mật/backup đến Phase 7. Mỗi phase cập nhật story/module status: Not Started → In Progress → Implemented → Tested → Verified; feature sandbox/mock/planned được ghi tách biệt.

## Deliverable gốc và thời điểm

SRS/backlog/business rules/architecture/security/roadmap/testing/UI audit bắt đầu Phase 0. ERD và OpenAPI được version cùng thay đổi. Use case, component và deployment ở Phase 0; sequence/activity/state machine trong workflows; class diagram chi tiết theo module khi thiết kế triển khai. Migration/seed thực thi ở Phase 1–2; Swagger runtime, deployment guide thực chứng và kết quả benchmark chỉ sau khi có ứng dụng. Không tuyên bố đã có các deliverable thực thi này trong Phase 0.
