# Phase 1 — NetBeans/Maven backend + Swing foundation

> Ghi chú audit 2026-10-27: phần bên dưới lưu evidence/kế hoạch tại thời điểm lập. Phase 0 và Phase 1 đã được user nghiệm thu; các nhãn chưa bắt đầu/chưa runtime cũ không phải trạng thái hiện tại. Xem [checkpoint](../implementation-status.md).

Revision3, ngày2026-10-27. **Đã được user cho phép triển khai; đang nghiệm thu.** Phase 0 đã được user chấp nhận. POM/backend/Swing/test đã có; SQL integration thật đạt 2/2, toàn reactor đạt18 tests; Swing đã kết nối REST và SQL readiness UP. Backend Run/Debug trực tiếp NetBeans và checklist DPI/resize chưa đủ evidence để nghiệm thu toàn Phase 1. Trạng thái/hướng dẫn tại [README](../../README.md). Nội dung bên dưới giữ làm kế hoạch baseline; quyền runtime trên bảng nền tảng được DBA cấp riêng bằng script03 sau Flyway V1.

## Input baseline

- Java21, Boot4.1.2/JUnit6/Maven/NetBeans31, SQL2025/SSMS22 giữ nguyên. Swing là UI chính; React/TypeScript/Web giữ sau MVP, không build gate.
- Default local SQL instance, ComicNetDev, SQL Authentication với app login riêng không sa; dev JDBC encrypt=true/trustServerCertificate=true; production verify certificate. Backend HTTP localhost8080, Swing API URL /api/v1; localhost5173 chỉ Web tương lai.
- Token10m/server session absolute30d/rotation/revocation/CSRF giữ; Swing cookie store riêng trong memory, không giả browser Origin. Setup wizard capability P2 đã được user duyệt; backup/restore operator procedure+Swing runbook/log viewer.
- Evidence tại [environment baseline](environment-baseline.md); chưa NetBeans project run/SQL login/TLS handshake/build. Không lấy password từ chat/source hoặc đưa SQL credential vào desktop.

## Project structure và run model

Root comic-net-parent packaging=pom, hai children backend/desktop-swing; release21 và BOM version management. Backend executable Boot JAR, desktop Java SE main riêng, không Maven dependency desktop→backend. Không shared-domain module hoặc Maven module cho từng nghiệp vụ. [Architecture tree](../architecture/system-design.md).

NetBeans mở parent và hai children, chạy backend child trước, desktop child sau; root không main. Cấu hình Run/Debug/classpath/resources từng child phải kiểm chứng. Swing không tự boot backend/SQL hoặc import SpringApplication. Hai JVM tách headless/runtime/lifecycle.

## Work items sau khi được phép

| Step | Dự kiến tạo/sửa | Verification phải có |
|---|---|---|
| P1-00 Preflight | Chưa source | NetBeans/JDK21; SQL login/mixed-mode/TLS/quyền ComicNetDev; dependency/JSON mapper/codec plan; không đổi server config âm thầm |
| P1-01 Parent/repo | root pom.xml, Maven wrapper, gitignore, README/config examples | Parent aggregate2children/plugin management, không Boot repackage root; Git init khi được phép; không commit secrets |
| P1-02 Backend child | backend/pom, app/config/resources | Boot4.1.2 BOM/JPA/Flyway/JDBC/Security/JUnit6; Run/Debug NetBeans Java21; không23package rỗng |
| P1-03 Database smoke | ComicNetDev/config/Flyway migration tối thiểu | Login riêng; migrate/validate dev DB sạch; foundation marker/schema_history, không toàn ERD/BootstrapState business P2; ddl-auto không update |
| P1-04 API/observability | ping/health/error/requestId | Ping thật /api/v1/system/ping, no secret details; health DOWN503 hợp lý; non-public APIs denied |
| P1-05 Security | filter-chain/config/test fixtures | SEC-P1-01..10; Origin khi có/native no-Origin không bypass CSRF/auth; flags/keys/redaction; không auth/setup mock/default admin password |
| P1-06 Swing child | desktop-swing/pom, DesktopApplication, shell/presenter, client/http, session/config | JFrame trên EDT; ping async thật/status/retry/backend-down/stale guard; không JDBC/JPA/DB secrets; Run/Debug NetBeans |
| P1-07 Desktop dependencies/media plan | JSON mapper độc lập/resource/config docs | Không Spring runtime/backend dependency trong UI, không WebView/framework thừa. Codec checks trước upload/reader P2, không cần full pipeline để ping |
| P1-08 Build/launch smoke | parent/child build/launch docs | Maven build2children; backend executable JAR; desktop JAR/distribution có deps; NetBeans child mains; không npm/Vite/React |
| P1-09 Evidence/handover | Windows setup/run guide/check output/status | SQL/API/Swing actual results, limitations/P2 tasks; không claim nghiệp vụ khi chỉ ping |

## DoD

Backend và Swing chạy từ hai child NetBeans; GUI hiện thật, ping thật, backend-down state và không block EDT. SQL2025/Flyway/login riêng có evidence; Maven build2children/JAR pass; Java21/Security/JUnit6 checks/log/error/secret config đúng. Không React/Node/npm/browser cần cho demo này.

P1 chưa login/catalog/reader/setup wizard nghiệp vụ; không nút giả báo đã triển khai. P2 mới đảm bảo19stories usable trên Swing và restore operator procedure có evidence. Không installer/native-image/jpackage/bundledJRE khi chưa có yêu cầu; thin desktop JAR phải kèm dependency/classpath instructions, không hứa java-jar hoạt động khi chưa đóng gói đúng.

## Web extension và gate

Giữ frontend React/TypeScript/Vite/Tailwind/Router trong cây tương lai, ngoài root reactor/MVP. WEB-EXT chỉ sau Swing MVP và xác nhận riêng; lúc đó revalidate frontend versions/types/peers, responsive/source Figma và browser auth tests. Không Web placeholder/embedded React để thỏa GUI desktop.

User đã xác nhận bắt đầu Phase 1 trong chat ngày 2026-10-27. Chỉ triển khai các bước foundation đã nêu; không tự đổi stack/scope nếu môi trường thiếu. Các chức năng nghiệp vụ Phase 2 giữ nguyên thứ tự dependency.
