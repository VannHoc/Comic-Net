# Phase 0 revision 3 — báo cáo kiểm chứng Swing-first

> Ghi chú audit 2026-10-27: phần bên dưới lưu evidence/kế hoạch tại thời điểm lập. Phase 0 và Phase 1 đã được user nghiệm thu; các nhãn chưa bắt đầu/chưa runtime cũ không phải trạng thái hiện tại. Xem [checkpoint](implementation-status.md).

Ngày 2026-10-27 (Asia/Saigon). **Tài liệu đã cập nhật theo yêu cầu Swing; Phase 0 chưa đánh dấu Completed; Phase 1 chưa bắt đầu.**

## Phạm vi và quyết định được duyệt

- Có 21 file trong docs: 20 Markdown và 1 OpenAPI JSON. Bổ sung SRS, phân tích tác động Swing và ánh xạ màn hình desktop.
- Cập nhật prj_app.md thành V2.1 Swing-first ở các phần nền tảng, UI, kiến trúc, triển khai, testing và roadmap theo yêu cầu trực tiếp của user. Java 21, Boot 4.1.2, JUnit 6, Maven, SQL Server 2025 và Modular Monolith giữ nguyên. React + TypeScript giữ cho Web mở rộng ngoài MVP.
- User đồng ý wizard Swing gọi REST khởi tạo Admin một lần; backup/restore vẫn là quy trình người vận hành, Swing xem hướng dẫn/nhật ký.
- Toàn bộ nội dung 23 phần module và 243 bullet chức năng giữ nguyên, không chỉ giữ số lượng. So sánh SHA256 từng khối tổng hợp với baseline trước khi đổi UI cho kết quả trùng.
- SHA256 đặc tả trước revision 3: 4d33851113506d5412904aeba571187d8ab84dc2e03695a9d4881c7fb90f91c2.
- SHA256 đặc tả hiện tại: 0cf413e66bfc759c2ca849b7c904e8f80bf21b8bafd9ec1555f069f8676a2458.
- SHA256 tổng hợp nguyên văn các phần module, trước và sau: d5550664df1492bb0e9d7e2f3f8592dd40dea8cb321bcbfc1c8a3388e7cab447.
- SHA256 tổng hợp 243 bullet chức năng, trước và sau: fd98fdf2b97a60c43ce1038dddc34abfd225aa7313ac6c4fcc6f340ed9c48f58.
- Root chỉ có prj_app.md và docs; chưa tạo source ứng dụng, POM, migration, DB/login, certificate hoặc JWT key. Cấu trúc Maven/NetBeans là đề xuất tài liệu.

## Kiểm tra tĩnh đã thực hiện

| Kiểm tra | Kết quả thực tế |
|---|---|
| Phạm vi nguồn | 23 module, 243 chức năng; hash nguyên văn giữ nguyên |
| Feature register | 243 ID duy nhất; toàn bộ nội dung và số dòng nguồn đối chiếu đúng |
| MVP traceability | 74 chức năng có phần Phase 2 có mapping và màn Swing |
| Backlog và UI | 19 story US-00..18 có SW00..18; mọi dependency story hợp lệ; DFS không có vòng |
| Module dependency | 23 node trong allowlist; DFS không có vòng |
| OpenAPI | JSON parse được; 3.0.3; 71 path, 91 operation, 80 schema |
| Tham chiếu API | 1093 local $ref resolve; operationId duy nhất; x-story hợp lệ; permission/scope/transaction có khai báo |
| Cấu trúc HTTP/DTO | Path parameters required; GET không requestBody; 204 không content; schema required arrays không rỗng và trỏ đúng property |
| Swing/security contract | SetupToken cho setupAdministrator; login/refresh/logout khai báo CSRF cookie + header; manifest/preview có displayFormat; ManifestPage bắt buộc mimeType |
| Markdown | Code fences cân cặp; 55 local links tồn tại, không broken link |
| Rà soát nội dung | SRS, roadmap, backlog, UI, API, security, ERD, môi trường, testing và Phase 1 cùng baseline Swing chính/React tương lai |

Đây là kiểm tra tài liệu bằng script và rà soát nội dung, không phải application tests. Chưa chạy validator OpenAPI chuẩn độc lập, generate client, Maven build, GUI E2E hoặc SQL integration. Các ràng buộc transactional, authorization, concurrency và HTTP phải được chứng minh khi triển khai.

## Bằng chứng môi trường và thiết kế

- Evidence môi trường từ revision trước: Java/javac 21.0.12, Maven 3.10.0 dùng Java 21, SQL default instance 17.0.1000.7 Developer/TCP enabled/port 1433 và probe TCP thành công. Không chạy lại hoặc nâng mức xác nhận trong revision này. Chưa SQL login/TLS handshake, mixed-mode/quyền database, Maven dependency resolution hoặc NetBeans project run.
- NetBeans 31/SSMS 22 có install metadata; chưa đủ để khẳng định project chạy được. Node/npm/frontend versions chỉ là thông tin Web tương lai, không là gate MVP.
- Baseline SQL đã duyệt: ComicNetDev, app login SQL riêng; development encrypt=true/trustServerCertificate=true; backend HTTP localhost:8080. Chứng chỉ SQL chưa xác minh; production phải TLS/chứng chỉ hợp lệ. Chi tiết và upstream links tại [environment baseline](deployment/environment-baseline.md).
- Figma dùng lại evidence 3 mobile frames đã kiểm tra trước; không đọc lại, tạo hoặc duyệt frame Swing trong lượt này. Screen map và desktop layout là đề xuất, chưa có visual QA thực tế. [UI audit](design/ui-audit.md).
- Quy tắc Java HttpClient/SwingWorker và Maven reactor tham chiếu tài liệu chính thức tại [impact analysis](architecture/swing-impact.md); không coi tài liệu upstream là build evidence.

## Vấn đề còn tồn tại và thời điểm xử lý

| Thời điểm | Việc còn lại |
|---|---|
| Trước bắt đầu Phase 1 | User xác nhận bắt đầu riêng; hiện chưa được phép scaffold/code |
| Preflight và foundation Phase 1 | Xác minh NetBeans/JDK thực chạy hai child, dependency resolution, SQL Authentication/login riêng/quyền ComicNetDev/TLS, Flyway và đóng gói hai executable; không tự đổi SQL server config |
| Trước triển khai feature Phase 2 | Review wireframe desktop, font/asset và DPI; chọn/kiểm chứng codec tạo JPEG/PNG; chốt chi tiết setup-token lifecycle và test native/browser cookie-CSRF-media boundary |
| Trong Phase 2 | Chứng minh đủ 19 story trên Swing, authorization/concurrency thật, reader memory/EDT, SQL/storage, procedure backup/restore và evidence end-to-end |
| Web extension và production | Revalidate React dependencies, browser E2E/responsive/PWA theo platform; TLS và certificate thật; không lấy dev trustServerCertificate làm chuẩn production |

Các chi tiết kỹ thuật còn mở đã có gate trong [Phase 1 plan](deployment/phase-1-plan.md) và [risk register](architecture/risks-decisions.md). Chưa có blocker cần hỏi lại về hai quyết định wizard/backup vì user đã trả lời. Không tự nhận Phase 0 hoàn tất hoặc Phase 1 đã chạy.
