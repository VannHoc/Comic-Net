# Comic Net — Bộ mockup bố cục UI

Các PNG này là **ảnh tham chiếu thiết kế**, không phải màn hình thực tế đã triển khai. Nội dung và số liệu trên ảnh chỉ có tác dụng minh họa bố cục; **không đưa vào database, không tạo API giả hoặc hardcode dữ liệu**.

## Thứ tự hình ảnh
1. `01_Chi_tiet_truyen.png`: Popup chi tiết truyện gọn; một bộ chọn bản dịch; chương có khóa/xu; nút Đọc tiếp / Đọc từ đầu / Yêu thích / Theo dõi / Đánh giá sao.
2. `02_Thu_vien.png`: Master-detail với cột trái bộ sưu tập và cột phải card truyện; hành động theo ngữ cảnh, không còn hai bảng và dàn nút kéo dài.
3. `03_Bang_xep_hang.png`: Top 3 nổi bật + danh sách gọn; bảng xếp hạng chính theo lượt đọc hợp lệ, rating được hiển thị riêng khi có dữ liệu. Khi backend không có dữ liệu thực, dùng empty state thay vì fake rows.
4. `04_Tai_khoan_gon.png`: Profile Header thấp, card thông tin vừa nội dung, hiển thị tab Hồ sơ không cần cuộn ở cửa sổ 1600x900.

## Quy tắc triển khai
- Giữ Java Swing + Java 21, Spring Boot, SQL Server, Session Cookie + CSRF và nghiệp vụ hiện tại.
- Giữ visual identity Comic Net hiện có: coral, navy, nền sáng, sidebar và header.
- Ảnh chỉ ràng buộc **cấu trúc, mật độ, kích thước tương đối, thứ bậc thị giác**. Không ép pixel-perfect nếu dữ liệu hoặc độ phân giải yêu cầu khác.
- Ưu tiên `LibraryPanel`, `CatalogPanel.detailDialog`, `RankingPanel`, màn hình Tài khoản.
- Giữ `Yêu thích`, `Theo dõi`, `Bộ sưu tập`, `Bookmark` là những nghiệp vụ riêng; sửa lỗi thay vì ẩn.
- Với rating chưa hoạt động, kiểm tra API/bảng/service trước khi bổ sung. Không cho rating làm điều kiện để ranking theo lượt đọc hợp lệ hoạt động.
- Build, chạy Swing thật và kiểm tra ở 1536x960, 1366x768, 1180x720; không report visual pass nếu chưa quan sát được GUI.
