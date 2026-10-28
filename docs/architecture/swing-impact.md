# Thay đổi baseline — Swing là UI chính

Ngày 2026-10-27. Yêu cầu mới của user thay các quyết định Web-first trước đây. Đây là thiết kế Phase 0; không project, code ứng dụng hoặc dịch vụ mới được tạo/chạy.

## Phạm vi thay đổi

| Area | Trước | Baseline mới |
|---|---|---|
| UI MVP | React/TypeScript/Vite | Java Swing Java21, chạy và demo trong NetBeans31 |
| Web | UI bắt buộc từ Phase1 | Giữ React/TypeScript/Vite/Tailwind/Router trong Web extension, ngoài MVP, xác nhận riêng khi bắt đầu |
| Backend | Spring Boot4.1.2 Modular Monolith | Giữ nguyên, 23 feature packages trong một backend |
| Build | Một Maven backend + npm frontend | Maven parent hai children backend/desktop-swing; không Maven project cho từng nghiệp vụ |
| Data | SQL2025/Flyway/JPA | Giữ nguyên; chỉ backend truy cập SQL. Client không có DB driver/credentials |
| API | REST dùng bởi Web | REST dùng bởi Swing trước, Web dùng lại sau; không trả JPA entity hoặc phụ thuộc widget |
| Auth | JWT memory + cookie rotation/CSRF cho browser | Cùng protocol trên Swing bằng CookieManager riêng trong memory, không global cookie handler; Web giữ chính sách browser khi triển khai |
| Figma | Mobile visual source của UI chính | Tham khảo identity/asset/tokens, desktop layout có thiết kế riêng; không claim frame mobile là thiết kế Swing |
| Quality | Browser responsive/E2E là MVP gate | JUnit6 + Swing presenter/EDT/resize/DPI + E2E Swing→REST→SQL/storage; Web E2E ngoài MVP |

Java21/Boot4.1.2/JUnit6/Maven/SQL2025/NetBeans31 không đổi. Toàn bộ 243 bullet chức năng trong 23 module giữ nguyên; các qualifier browser/mobile trong bullet gốc được giữ cho Web/mobile phù hợp, ánh xạ desktop ở SRS. ERD nội dung/ledger, permission và dependency nghiệp vụ không phải thiết kế lại chỉ vì đổi UI.

## Quyết định bổ sung đã hỏi và được user đồng ý

1. US-00: wizard Swing gọi REST để tạo Admin một lần bằng setup token bí mật, chỉ local. Không còn bắt user dùng CLI/React để thao tác chức năng khởi tạo MVP. API đề xuất POST `/api/v1/setup/administrator`; mặc định disabled, gate an toàn trong security foundation.
2. US-16: backup/restore vẫn là quy trình vận hành/kiểm chứng theo runbook; Swing xem hướng dẫn và nhật ký/job thực. User không yêu cầu nút thực thi SQL BACKUP/RESTORE trong MVP. Không thêm API RESTORE database trực tiếp hoặc đưa DB admin credential vào Swing.

Đây là hai ngoại lệ/điều chỉnh đã có trả lời trực tiếp, không tự phân loại để lược bỏ scope. Mọi chức năng tương tác người dùng trong MVP có màn Swing; procedure/quality gates được phân biệt với UI thao tác nghiệp vụ.

## Cấu trúc NetBeans/Maven đề xuất

Root `pom.xml` packaging=pom, groupId Maven vẫn là `com.comicplatform`, artifact `comic-net-parent`; Java root package đã chuyển sang `com.comicnet`. Children `comic-net-backend` và `comic-net-desktop-swing`; cả hai release21. Root quản lý versions/plugins, không main class/repackage. Backend dùng Boot BOM/plugin repackage trong child backend. Desktop là Java SE application, không Spring Boot starter/repackage, JPA/JDBC/Flyway hoặc dependency vào backend JAR. JSON mapper là thư viện độc lập theo version baseline; không tạo shared-domain/DTO Maven module khi chưa cần.

NetBeans mở parent và hai children: chạy backend trước, rồi Run/Debug desktop main. Hai JVM là hai runtime client/server, không biến 23 module thành microservices. Không khởi động backend bằng Swing event handler hoặc nhúng Spring container vào desktop. Parent không có Run main; thao tác đúng child phải có trong hướng dẫn P1.

Maven hỗ trợ aggregation/reactor; chọn hai child theo hai executable cần build, không phân rã domain quá mức. [Maven guide](https://maven.apache.org/guides/mini/guide-multiple-modules.html).

## Tác động API và Reader

- Business endpoints/ID/ETag/revision/transaction giữ client-neutral. Bổ sung capability-gated setup endpoint cho wizard và MIME/format contract cho ảnh.
- Java21 HttpClient tái sử dụng connection, cookie store riêng và timeout/cancel; không block Event Dispatch Thread. Completion phải marshal về EDT; version/generation của view loại response cũ. [HttpClient](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html), [SwingWorker](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/javax/swing/SwingWorker.html).
- API mặc định cấp ảnh display JPEG/PNG cho cả cover/banner/reader. WebP input hoặc AVIF/WebP output không tự đảm bảo JDK ImageIO decode được; server cần compatible derivative, codec/variant checks ở P2. Không thêm WebView/browser chỉ để đọc ảnh hoặc coi WebP support đã kiểm chứng.
- Signed media dùng HTTP client không Cookie/Authorization; không gửi refresh cookie/JWT sang storage/CDN. Không lưu ảnh riêng tư như offline download ở MVP.

## Gate và phần còn mở

P1 chỉ parent/backend/Swing shell+ping thật, SQL migration/security foundation; không React scaffold/npm build. P2 tất cả19 stories có Swing acceptance. Web extension có gate riêng sau P2, không là prerequisite P3–7 hoặc graduation demo.

Trước feature P2 cần review wireframe desktop, font/assets hợp lệ, image codec/derivative, reviewer scope/age và setup-token lifecycle cụ thể. Shared-cookie transport cần native/browser negative tests, không chấp nhận `X-Client-Type` để bypass auth/CSRF. NetBeans chạy project, SQL login/TLS/build chưa được chứng minh trong P0. Chờ xác nhận riêng trước bắt đầu P1.
