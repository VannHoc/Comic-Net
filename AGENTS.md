# Quy tắc chung cho Codex và Google Antigravity

- Hai Agent dùng cùng thư mục `C:\Users\studi\OneDrive\Documents\Tools\Comic Net`, làm việc luân phiên. Không cần Git push/pull để bàn giao.
- Đầu mỗi phiên: đọc `AGENTS.md`, `PROGRESS.md`, `TASKS.md`, `HANDOFF.md`; kiểm tra source, thay đổi chưa lưu và kết quả test hiện có. Không coi tài liệu cũ là bằng chứng hiện tại.
- Cuối mỗi phiên: cập nhật `PROGRESS.md`, `TASKS.md`, `HANDOFF.md`, kể cả khi chưa hoàn thành; ghi rõ Agent, file sửa, API/DB, test đã chạy, test chưa chạy, lỗi và bước kế tiếp.
- Giữ Java 21, Spring Boot 4, Maven, SQL Server/Flyway, Swing/NetBeans và modular monolith theo package tính năng. Kiểm tra `pom.xml`, source và migration trước khi thay đổi; không tự đổi kiến trúc, viết lại module hoạt động, xóa chức năng hoặc hoàn tác việc của Agent trước.
- Cả hai Agent có thể sửa mọi module theo yêu cầu. Tôn trọng thay đổi chưa commit của người kia; không tự commit, push, reset hay xóa dữ liệu. Không ghi mật khẩu, token hoặc cấu hình nhạy cảm vào tài liệu/log bàn giao.
- Khi tài liệu và code mâu thuẫn, kiểm tra code, DB thực tế khi cần và chạy test phù hợp; chỉ ghi kết luận có bằng chứng. Phân biệt `đã code`, `test tự động PASS`, `QA giao diện PASS` và `chưa kiểm chứng`.
- Backend local dùng `cd "C:\Users\studi\OneDrive\Documents\Tools\Comic Net"` rồi `.\deploy\Start-Backend.ps1 -Mode Run -UseLocalConfiguration`; Ctrl+C để dừng. Không đổi câu lệnh hướng dẫn nếu chưa kiểm tra script.
