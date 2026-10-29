# Bàn giao gần nhất

## Phiên 2026-10-29 — OpenAI Codex, M20-F06 GUI end-to-end PASS

- Đọc bốn file điều phối, rà source, xác nhận SQL Server và backend 8080 đã chạy; khởi chạy Swing Java thật. Dùng `@oai/sky` điều khiển GUI đăng nhập Reader QA mới, mở truyện/chương khóa, xác nhận dialog 25 Reward Coin, đọc trang 1, đăng xuất/đăng nhập lại và đọc lại.
- Bằng chứng trước/sau: PAID 7 giữ nguyên; REWARD 40→15. Sau đăng nhập lại, tab Ví xu hiển thị quyền PURCHASE/ACTIVE/Vĩnh viễn; SQL có đúng một Purchase COMPLETED/REWARD/25 và Entitlement ACTIVE/ValidTo NULL (Reader 1115, chương 227). Ảnh GUI, kết quả SQL và báo cáo ở `.local/qa/reward-gui-e2e/`.
- File sửa phiên này: `backend/src/test/java/com/comicnet/ReaderFlowIT.java` (fixture QA chỉ chạy khi bật biến môi trường), `docs/requirements/feature-register.md`, `PROGRESS.md`, `TASKS.md`, `HANDOFF.md`; tạo bằng chứng trong `.local/qa/`. Không sửa logic production, API, migration hoặc kiến trúc. Không commit/reset/xóa dữ liệu.
- Test: `ReaderFlowIT#prepareRewardGuiFixture` trên SQL/RustFS thật PASS 1/1; toàn bộ luồng GUI mua và đọc lại PASS. Công cụ GUI có một lần báo không tìm được monitor sau click mua; đã đối chiếu SQL trước khi thao tác tiếp, không click mua lần hai.
- Tồn đọng ngoài M20-F06: M20-F09 cần chính sách hoàn xu và mua lại trước khi triển khai; các tính năng Phase 4 khác xem `TASKS.md`. Backend và Swing có thể còn mở, kiểm tra tiến trình trước khi chạy phiên mới.

## Phiên 2026-10-29 — OpenAI Codex xác minh Reward-Based Unlock

- Đọc bốn file điều phối và kiểm tra code hiện tại. Ban đầu xem M20-F09 nhưng không thêm thu hồi quyền mua vĩnh viễn: ràng buộc `Purchase(UserId,ChapterId)` sẽ chặn mua lại sau thu hồi. Đây là việc tồn đọng cần quy tắc refund/mua lại.
- Xác nhận M20-F06 đã có trong `ChapterPurchaseService` và ReaderPanel; thêm bài `rewardCoinUnlockDebitsRewardOnlyAndGrantsBuyerAccess` trong `ReaderFlowIT`.
- File sửa: `backend/src/test/java/com/comicnet/ReaderFlowIT.java`, `docs/requirements/feature-register.md`, `PROGRESS.md`, `TASKS.md`, `HANDOFF.md`. Không sửa API production, DB hoặc migration.
- Test: `Start-Backend.ps1 -Mode Verify -IntegrationTest ReaderFlowIT -UseLocalConfiguration` trên SQL/RustFS thật PASS 9/9. Chưa QA mua bằng REWARD trong GUI đăng nhập.
- Tiếp theo: chốt chính sách hoàn xu/mua lại cho M20-F09 hoặc chọn feature Phase 4 độc lập; kiểm thử GUI bằng phiên đăng nhập do người dùng điều khiển. Backend/Swing từ phiên trước có thể đang chạy; kiểm tra PID và source trước khi khởi động lại.


## Phiên 2026-10-29 — OpenAI Codex tự QA trên máy

- Đã đọc bốn file điều phối, kiểm tra source và working tree. Khởi động backend mới trên 8080 và Swing từ source. Computer Use xác nhận màn Ví xu và tab `Quyền mở khóa` cùng cột `Truyện`/`Chương` hiển thị.
- Swing hiện ở trạng thái khách. Công cụ Windows không được tự thao tác màn đăng nhập; vì vậy chưa xác nhận hàng dữ liệu trong GUI. `ReaderFlowIT` trên SQL/RustFS thật 8/8 PASS xác nhận dữ liệu đúng tài khoản và tên truyện/chương.
- File sửa: `EntitlementService.java`, `WalletPanel.java`, `ReaderFlowIT.java`, `PROGRESS.md`, `TASKS.md`, `HANDOFF.md`. API `GET /api/v1/me/entitlements` bổ sung `comicTitle`, `chapterTitle`; DB không migration.
- Unit test backend+Swing PASS; SQL integration `ReaderFlowIT` 8/8 PASS. Backend/Swing mới đang mở; không tự commit/reset/xóa dữ liệu.
- Tiếp theo: kiểm thử hàng dữ liệu tab bằng phiên đăng nhập do người dùng điều khiển, rồi chọn M20-F09 hoặc hạng mục Phase 4 tiếp theo sau khi chốt quy tắc nghiệp vụ.


## Phiên 2026-10-29 — OpenAI Codex

- Đầu phiên đọc bốn file điều phối, kiểm tra source/feature register và `git status`. Người dùng xác nhận **gói nạp PASS**; chưa suy rộng sang refund/expire.
- Công việc: M20-F08 lịch sử quyền mở khóa. API `GET /api/v1/me/entitlements` chỉ đọc quyền của tài khoản hiện tại; Swing Ví xu thêm tab `Quyền mở khóa`. DB dùng `UserEntitlement` V21, không migration mới, không đổi chính sách đọc chương.
- File sửa: `EntitlementService.java`, `EntitlementController.java` (mới), `WalletPanel.java`, `ReaderFlowIT.java`, `ApiContractTest.java`, `docs/api/openapi-mvp.json`, `docs/requirements/feature-register.md`, `PROGRESS.md`, `TASKS.md`, `HANDOFF.md`.
- Test: reactor backend + Swing unit test PASS; `ReaderFlowIT` trên SQL/RustFS thật 8/8 PASS. Chưa QA tab mới bằng tay.
- Tồn đọng: working tree còn sửa đổi chưa commit từ trước; SQL Full-Text từng thiếu; sandbox chưa có provider thật. Tiếp theo khởi động backend/Swing từ code mới và QA tab lịch sử quyền theo hai tài khoản, rồi chọn hạng mục Phase 4 tiếp theo theo feature register.


- Agent: OpenAI Codex; ngày: 2026-10-29.
- Công việc phiên này: thiết lập điều phối Codex–Google Antigravity; chỉ sửa tài liệu, không sửa logic ứng dụng.
- File thay đổi phiên này: `AGENTS.md`, `PROGRESS.md`, `TASKS.md`, `HANDOFF.md`.
- Trạng thái source trước phiên: working tree đã có nhiều sửa đổi chưa commit ở finance, wallet, Swing, deploy và OpenAPI. Giữ nguyên các sửa đổi này; xem `git status --short` trước khi làm tiếp.
- API/DB liên quan gần nhất: local payment API trong `LocalPaymentSandboxController`; Flyway V25; `ComicNetDev`. Phiên điều phối này không đổi API hoặc DB.
- Bằng chứng test gần nhất từ phiên trước: Maven backend + Swing unit tests PASS; `WalletFlowIT` với SQL thật 4/4 PASS; Flyway V25 success. Phiên này không chạy lại test ứng dụng vì không sửa logic.
- Tồn đọng: chưa QA trực tiếp giao diện Swing mới cho gói nạp, đơn thất bại/hết hạn và hoàn xu; SQL Full-Text từng bị chặn do instance thiếu tính năng, cần kiểm tra lại. Sandbox chưa phải cổng thanh toán thật.
- Tiếp theo: đọc cả bốn file, kiểm tra `git status`, source và báo cáo test mới nhất; khởi động backend bằng lệnh trong `AGENTS.md`, mở Swing từ source mới và QA vòng đời đơn. Sau đó cập nhật cả ba tài liệu tiến độ/bàn giao.
