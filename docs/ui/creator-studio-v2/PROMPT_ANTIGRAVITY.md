# ANTIGRAVITY — TRIỂN KHAI CREATOR STUDIO THEO 13 MOCKUP CỤ THỂ

Bạn là Principal UI/UX Designer, Senior Java Swing Engineer và Spring Boot Security/Publishing Engineer của Comic Net.

## 1. Đọc trước khi sửa

- Đọc `AGENTS.md`, `PROGRESS.md`, `TASKS.md`, `HANDOFF.md`, Master Prompt, code thực tế của Java Swing, API, Spring Security, SQL Server migrations.
- Đọc `docs/ui/creator-studio-v2/README_UI_MAP.md` và **từng ảnh PNG 01–13** (HTML chỉ để tham khảo layout nếu cần).
- Các ảnh là mockup tĩnh, không phải kết quả chạy thực. Không dùng dữ liệu minh họa để seed SQL.
- Giữ Java 21, Swing, NetBeans 31, Maven, Spring Boot REST, SQL Server, Modular Monolith, Session Cookie + CSRF và Comic Net Light Design System.

## 2. Implement đúng từng ảnh, không gộp tất cả vào một Panel

- `01`: Xưởng xuất bản danh sách truyện. Chỉ các thao tác tạo, quản lý, lọc, menu ngữ cảnh; không hiện 4 bảng kỹ thuật.
- `02`: Wizard bước 1 tạo/sửa truyện, bìa, metadata, lưu nháp.
- `03`: Wizard bước 2 danh sách chương, giá xu/quyền đọc, upload; Volume tùy chọn.
- `04`: Dialog upload nhiều JPG/PNG/WebP hoặc ZIP, thumbnail thực, natural sort, kéo thả sắp thứ tự, tiến trình và retry; backend lưu `page_order` chính xác, chống trùng và xử lý file độc hại.
- `05`: Wizard bước 3 Creator xem trước và gửi duyệt; không có xuất bản trực tiếp.
- `06`: Admin riêng màn hàng chờ, không cho Creator truy cập.
- `07`: Admin xem chi tiết, preview, duyệt & xuất bản, yêu cầu sửa/từ chối.
- `08`: Dialog Admin yêu cầu chỉnh sửa với lý do bắt buộc.
- `09`: Creator thấy phản hồi, sửa bản revision và gửi lại.
- `10`: Admin tạo hoặc quản lý truyện được phép tự xuất bản ngay/lên lịch mà không phải nộp hàng duyệt cho chính mình.
- `11`: Lịch sử version/rollback ở menu Nâng cao, giữ nguyên quyền đọc, comment, progress, entitlement liên quan.
- `12`: Dialog Admin từ chối yêu cầu với lý do bắt buộc; phân biệt REJECTED và NEEDS_CHANGES.
- `13`: Preview Reader cho chương chưa công khai bằng quyền chủ sở hữu/Admin; ảnh thực theo thứ tự; không làm phát sinh valid view hoặc reading rewards.

## 3. Phân quyền chính thức

- Creator: CRUD bản nháp thuộc quyền sở hữu/quyền nhóm, tải ảnh, xem trước, gửi và gửi lại yêu cầu; **không duyệt và không xuất bản trực tiếp**.
- Administrator: mọi quyền quản trị xuất bản, duyệt, yêu cầu sửa, từ chối, ẩn/khôi phục, lịch sử phiên bản; có thể tự duyệt và xuất bản nội dung mình tạo; mọi hành động nhạy cảm ghi Audit Log.
- Loại bỏ vai trò MODERATOR khỏi mô hình quyền hoạt động; xử lý role cũ, quyền và lịch sử bằng migration an toàn; **không tự cấp Admin cho Moderator cũ**. Giữ các vai trò nội bộ nhóm dịch nếu cần.
- Kiểm tra quyền ở service/API, không chỉ ẩn UI; phòng idempotent/replay/race khi duyệt và xuất bản.

## 4. Publishing logic

- Chuẩn hóa workflow DRAFT → PROCESSING → READY → PENDING_REVIEW → PUBLISHED / NEEDS_CHANGES / REJECTED.
- Admin có đường trực tiếp READY → PUBLISHED hoặc SCHEDULED khi hợp lệ.
- Chỉ cho gửi/duyệt khi metadata, ít nhất một chương/trang, asset trạng thái READY và quyền sở hữu đều hợp lệ (giữ các kiểm tra hiện hữu chi tiết hơn nếu có).
- Creator chỉnh sửa nội dung đã công khai phải tạo revision riêng; bản đang công khai không bị thay đổi trước khi Admin duyệt.
- Duyệt/xuất bản trong transaction nhất quán; double-click/2 Admin duyệt cùng lúc không tạo 2 active version; có Audit Log.
- Không để lộ Job ID, Version ID, UUID, Snapshot, READY_FOR_REVIEW lên UI chính; hiển thị trạng thái tiếng Việt thân thiện. Job diagnostics chỉ ở phần Nâng cao.

## 5. UI/UX và kiểm thử

- Giữ bố cục theo **đúng ảnh tương ứng**, không chỉ đổi màu trên bảng cũ.
- Form compact, khoảng trắng hợp lý, icon/nút đồng bộ, khả năng resize và DPI; không cắt component; Swing EDT không bị block khi tải ảnh/network.
- Không bắt screenshot cố định áp dụng y nguyên trên màn nhỏ: 1536×960, 1366×768 và 1180×720 phải sử dụng được, cho phép cuộn khi thật sự cần.
- Tạo đầy đủ loading/empty/error/unauthorized/validation states; không sinh dữ liệu giả.
- Kiểm thử kịch bản Creator tạo truyện, lưu nháp, upload 20 ảnh (1,2,...10), reorder, gửi duyệt; Creator không tự duyệt; Admin duyệt/xuất bản trực tiếp và từ chối/yêu cầu sửa có lý do; Creator sửa và gửi lại; trang đọc thật hiển thị ảnh đúng thứ tự; rollback an toàn.
- Build và chạy GUI thực; chụp ảnh từng cửa sổ 01–13 và so sánh trước/sau. Chạy backend tests, Swing tests, authorization/integration tests. Không báo PASS nếu chưa chạy.
- Cập nhật `PROGRESS.md`, `TASKS.md`, `HANDOFF.md` và tài liệu RBAC đúng kết quả thực.

## 6. Thứ tự triển khai

1. Đối chiếu source và các mockup; sao lưu/commit state hiện tại.
2. Sửa RBAC + publishing workflow an toàn và các tests liên quan.
3. Xây đúng màn 01–05 (Creator), 06–08 + 12 (Admin), 09, 10–11, 13.
4. Kiểm thử tích hợp toàn bộ và QA trực quan từng ảnh.
5. Tiếp tục tự động trong phạm vi; không dừng ở việc phân tích hoặc chỉ cập nhật tài liệu.

**Tiêu chí nghiệm thu:** Người dùng thường tạo/sửa/tải ảnh/gửi duyệt trong luồng 3 bước rõ ràng; Admin duyệt hoặc tự xuất bản ít thao tác; giao diện sạch, không bảng và nút kỹ thuật tràn lan; dữ liệu thật nhất quán và phân quyền backend đúng.
