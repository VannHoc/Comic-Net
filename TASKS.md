# Công việc Comic Net

Mỗi phiên cập nhật trạng thái và Agent phụ trách gần nhất theo bằng chứng. `PASS` ở đây không mặc nhiên là nghiệm thu toàn bộ giao diện.

| Phạm vi | Trạng thái kiểm chứng | Agent gần nhất | Bước tiếp |
|---|---|---|---|
| Identity, vai trò Admin/Creator | Source và SQL integration đã có; người dùng xác nhận Creator chạy trong Swing | Codex | Kiểm tra hồi quy khi sửa auth |
| Catalog, publishing, reader, library, community, notification, analytics | Có controller/Swing panel và checkpoint trong `PROGRESS.md`; chưa rà QA đầy đủ phiên này | Codex | Đối chiếu feature register và chạy test theo module |
| Search full-text | Fallback có code; checkpoint trước ghi SQL Full-Text chưa cài | Codex | Kiểm tra lại `FullTextInstalled`, rồi QA full-text |
| Ví xu, ledger, cấp/thu hồi Admin | Code và `WalletFlowIT` SQL thật PASS; người dùng đã xác nhận QA ví trước đó | Codex | Theo dõi hồi quy |
| Gói nạp, đơn FAILED/EXPIRED/REFUNDED sandbox | Code, Flyway V25, OpenAPI và `WalletFlowIT` 4/4 PASS; người dùng xác nhận thử gói nạp PASS, các nhánh khác chưa được xác nhận riêng | Codex | QA thất bại/hết hạn/hoàn xu nếu chưa thử |
| M20-F08 lịch sử quyền mở khóa | API và SQL test PASS; QA Swing đã xác nhận hàng quyền PURCHASE/ACTIVE/Vĩnh viễn sau đăng nhập lại với Reader ID 1115 | Codex | Theo dõi hồi quy |
| M20-F06 mở khóa bằng Reward Coin | Hoàn thành QA end-to-end Swing thật: mua chương khóa bằng 25 Reward Coin, đọc được, đăng nhập lại vẫn đọc được; SQL xác nhận Purchase và Entitlement; bằng chứng `.local/qa/reward-gui-e2e/` | Codex | Theo dõi hồi quy |
| M20-F09 hết hạn/thu hồi quyền | `ValidTo` đã được kiểm tra khi đọc; chưa có quy trình thu hồi quyền mua vĩnh viễn an toàn | Codex | Thiết kế chính sách hoàn xu và mua lại trước khi thêm thao tác thu hồi |
| Thanh toán nhà cung cấp thật, subscription, chính sách chống gian lận | Chưa xác nhận triển khai từ source phiên này | Chưa phân công | Xác định yêu cầu rồi triển khai theo roadmap |
| Điều phối Codex–Antigravity | Bốn file điều phối được thiết lập trong phiên này | Codex | Agent tiếp theo đọc và xác minh trạng thái trước khi sửa |
