# HƯỚNG DẪN BẢO VỆ ĐỒ ÁN — COMIC NET (DEMO HANDBOOK)

> **Tài liệu chuẩn bị cho buổi bảo vệ đồ án tốt nghiệp**  
> Dự án: **Comic Net — Nền tảng Đọc & Xuất bản Truyện tranh Trực tuyến**  
> Nền tảng: **Java 21 · Spring Boot 4.1.2 · Java Swing (FlatLaf) · SQL Server (Flyway v29) · RustFS (S3) · Mailpit**

---

## 1. TỔNG QUAN HỆ THỐNG VÀ ĐIỂM NỔI BẬT

### Kiến trúc Kỹ thuật (Technical Architecture)
1. **Mô hình Modular Monolith (23 Modules độc lập)**: Phân tách ranh giới rõ ràng (`identity`, `catalog`, `publishing`, `reader`, `wallet`, `entitlement`, `monetization`, `gamification`, `analytics`...). Kiểm tra kiến trúc tự động bằng ArchUnit/Reflective test không cho phép phụ thuộc vòng (`ModuleBoundariesTest`).
2. **Bảo mật & Phân quyền RBAC**: Spring Security hỗ trợ Session Cookie + CSRF protection chống giả mạo request, Bearer JWT, Rate Limiting, phân quyền chặt chẽ (`READER`, `CREATOR`, `MODERATOR`, `ADMINISTRATOR`).
3. **Sổ cái Kế toán Cân bằng (Balanced Double-Entry Ledger)**: Quản lý 2 loại xu riêng biệt (**Xu mua - Paid Coin** và **Xu thưởng - Reward Coin**). Mọi giao dịch nạp, mua chương, hoàn tiền, quay thưởng đều ghi nhận bất biến (immutable), chống hoàn tiền kép bằng Idempotency Key.
4. **Hệ thống Gamification & Minigame (Nhiệm vụ & Thưởng)**:
   - Điểm danh 7 ngày liên tục với phần thưởng tăng tiến EXP + Xu thưởng.
   - Vòng quay may mắn 8 ô thưởng với hiệu ứng quay và giảm tốc mượt mà (Cubic Ease-Out Canvas).
   - Cấp bậc độc giả (Level 1..10+) với danh hiệu tương ứng ("Tân thủ đọc truyện", "Học đồ mê truyện", "Mọt truyện tinh anh", "Chí tôn độc giả"...).
   - Tự động cộng +20 EXP khi đọc hoàn thành chương truyện (chống cày lặp duplicate farming).
5. **Đầu đọc Truyện tranh Chuyên nghiệp (Reader UI)**:
   - Cuộn dọc mượt mà, hỗ trợ chương dài hàng chục trang.
   - Đa chế độ vừa khung: **Vừa chiều rộng (WIDTH)**, **Vừa chiều cao (HEIGHT)**, **Kích thước gốc (ORIGINAL)**.
   - Tự động lưu vị trí trang và khôi phục cài đặt đọc sau khi đăng xuất/đăng nhập lại.
   - Phím tắt tiện lợi: **F11** toàn màn hình, thanh tiến trình trượt nhanh, chọn chương nhanh.

---

## 2. TÀI KHOẢN DEMO ĐÃ TẠO SẴN THEO VAI TRÒ

| Vai trò | Tên đăng nhập | Mật khẩu | Quyền hạn & Chức năng trình diễn |
|---|---|---|---|
| **Độc giả (Reader)** | `hoc` | `hoc` | Đọc truyện, mua chương khóa, nạp xu sandbox, điểm danh 7 ngày, quay vòng quay may mắn, nhận EXP thăng cấp |
| **Tác giả (Creator)** | `creator` | `creator` | Đăng truyện, quản lý chương, tải ảnh bản thảo lên RustFS S3, lên lịch phát hành |
| **Quản trị (Admin)** | `admin` | `admin` | Quản trị tài khoản, cấp/thu hồi xu, thu hồi quyền & hoàn tiền mua chương, quản lý gói nạp |

---

## 3. CÁCH KHỞI ĐỘNG HỆ THỐNG (1-CLICK HOẶC TỪNG BƯỚC)

### Cách 1: Khởi động 1-Click tự động toàn bộ (Khuyên dùng khi bảo vệ)
Mở cửa sổ **PowerShell** tại thư mục dự án:
```powershell
.\deploy\Start-All.ps1
```
> Script sẽ tự động:
> 1. Kiểm tra và kích hoạt dịch vụ RustFS (9000) và Mailpit (8025).
> 2. Khởi động Backend Spring Boot trên cổng 8080 (chờ đến khi Actuator Health báo `UP`).
> 3. Bật ngay ứng dụng giao diện Desktop Swing FlatLaf.

---

### Cách 2: Khởi động thủ công / qua NetBeans

1. **Khởi động dịch vụ hỗ trợ (RustFS + Mailpit)**:
   ```powershell
   .\deploy\Start-LocalServices.ps1
   ```
2. **Khởi động Backend Spring Boot**:
   ```powershell
   .\deploy\Start-Backend.ps1 -Mode Run -UseLocalConfiguration
   ```
   *(Chờ dòng `Started ComicNetApplication in ... seconds` và `Tomcat started on port 8080`)*.
3. **Mở Desktop Swing**:
   - **Cách A (PowerShell)**: `.\deploy\Start-Desktop.ps1`
   - **Cách B (Apache NetBeans)**: Mở project `comic-net-desktop-swing` trong NetBeans, nhấn **F6** hoặc chuột phải chọn **Run**.

### Cách dừng toàn bộ hệ thống (khi kết thúc buổi demo):
```powershell
.\deploy\Stop-All.ps1
```
*(Tắt sạch Backend Spring Boot, RustFS và Mailpit)*.

---

## 4. KỊCH BẢN DEMO CHI TIẾT TRÌNH BÀY TRƯỚC HỘI ĐỒNG

### Phần 1: Trải nghiệm Độc giả (Reader Experience)
1. **Đăng nhập**:
   - Nhập: `hoc` / Mật khẩu: `hoc` -> Bấm **Đăng nhập**.
   - Góc trên bên phải hiển thị: `hoc (READER)`.
2. **Khám phá & Tìm kiếm Truyện**:
   - Chọn tab **Khám phá**: Hiển thị lưới truyện tranh với ảnh bìa thực tế từ S3.
   - Thử gõ tìm kiếm truyện trên ô tìm kiếm toàn cục.
3. **Trải nghiệm Đọc truyện Chuyên nghiệp**:
   - Bấm vào truyện để mở danh sách chương.
   - Mở **Chương hai - kiểm thử nhiều trang**:
     + Cuộn dọc mượt mà xem các trang truyện 1, 2, 3.
     + Thử các chế độ vừa khung: *Vừa chiều rộng*, *Vừa chiều cao*, *Kích thước gốc (ORIGINAL)*.
     + Kéo thanh trượt tiến độ ở footer để nhảy nhanh tới trang cuối.
     + Khi đọc đến cuối chương, hệ thống tự động ghi nhận hoàn thành và cộng thưởng **+20 EXP**.
     + Nhấn **F11** để bật/tắt chế độ toàn màn hình.
4. **Hệ thống Nhiệm vụ & Thưởng (Gamification)**:
   - Nhấp vào tab **"Nhiệm vụ & Thưởng"** trên thanh menu:
     + Thấy thẻ **Cấp độ độc giả**: Cấp hiện tại, danh hiệu, thanh tiến trình EXP (`JProgressBar`).
     + Thẻ **Điểm danh 7 ngày**: Bấm nút **"ĐIỂM DANH HÔM NAY"** -> Chuỗi ngày tăng 🔥, nhận ngay EXP + Xu thưởng! Nút chuyển sang "ĐÃ ĐIỂM DANH HÔM NAY" và chặn điểm danh lặp lại trong ngày.
     + Thẻ **Vòng quay may mắn**: Bấm nút **"QUAY NGAY (Miễn phí)"** -> Vòng quay 8 cánh quạt màu sắc FlatLaf bắt đầu quay tròn sống động, giảm tốc mượt mà và dừng lại đúng ô trúng thưởng được server tính toán!
     + Bảng lịch sử ở dưới: Hiển thị tab *Lịch sử điểm danh*, *Lịch sử quay thưởng*, và *Thưởng đọc truyện* (+20 EXP chương vừa đọc).
5. **Mở khóa Chương truyện có phí (Monetization)**:
   - Quay lại Khám phá, mở truyện có chương khóa (RESTRICTED - giá 25 Reward Coin).
   - Dialog xác nhận mua mở ra, hiển thị rõ số dư xu hiện tại và chi phí.
   - Bấm **Xác nhận mua** -> Giao dịch thành công, ví xu trừ chính xác 25 Reward Coin, chương truyện mở ra đọc bình thường.
   - Tab **Ví xu**: Hiển thị tab *Quyền mở khóa* có bản ghi `PURCHASE / ACTIVE / Vĩnh viễn`.

---

### Phần 2: Quản trị viên (Administrator Management)
1. **Chuyển sang tài khoản Quản trị**:
   - Bấm **Đăng xuất**. Đăng nhập lại với tài khoản: `admin` / `admin`.
   - Các workspace quản trị xuất hiện: **Quản trị**, **Quản lý ví xu**.
2. **Quản lý Ví xu & Cấp/Thu hồi Xu**:
   - Vào tab **Quản lý ví xu**:
     + Tìm kiếm người dùng `hoc`.
     + Xem số dư Xu mua, Xu thưởng, tổng xu.
     + Thử tính năng **Cấp xu thưởng** (ví dụ +50 xu kèm lý do) -> Cập nhật số dư tức thời, ghi nhận Audit Log.
3. **Quản trị Thu hồi Quyền & Hoàn tiền Mua chương**:
   - Chuyển sang tab **"Mua chương"**:
     + Xem danh sách tất cả các đơn mua chương của các độc giả.
     + Chọn đơn mua chương của độc giả `hoc` vừa mua lúc trước.
     + Bấm **"Thu hồi quyền & hoàn xu đơn đã chọn"** -> Hệ thống hiển thị hộp thoại xác nhận.
     + Bấm **Đồng ý**: Backend hoàn lại xu về ví độc giả qua balanced ledger `CHAPTER_REFUND`, cập nhật trạng thái đơn thành `REFUNDED`, thu hồi quyền đọc của độc giả, ghi nhận kiểm toán.
     + Khi độc giả `hoc` đăng nhập lại, quyền đã bị thu hồi nhưng độc giả hoàn toàn có thể mua lại khi cần (nhờ Filtered Index V27 đã giải quyết triệt để).

---

### Phần 3: Tác giả Sáng tác (Creator Experience)
1. Đăng xuất `admin`, đăng nhập bằng `creator` / `creator`.
2. Workspace **Xuất bản** hiển thị:
   - Quản lý danh sách tác phẩm sáng tác.
   - Thêm chương mới, tải các trang truyện bản thảo lên hệ thống lưu trữ S3 RustFS.
   - Lên lịch phát hành tự động theo thời gian định trước.

---

## 5. DỮ LIỆU BẰNG CHỨNG KIỂM THỬ THỰC TẾ

- **Unit Tests**: Reactor build 54/54 tests PASS (Backend 29/29, Swing 25/25).
- **API Contracts**: 169/169 REST operations PASS chuẩn OpenAPI (`ApiContractTest`).
- **Kiểm thử Tích hợp SQL Server thật (`ComicNetDev`)**:
  - `GamificationFlowIT` (5/5 tests PASS).
  - `ReaderFlowIT` (12/12 tests PASS).
  - `WalletFlowIT` (4/4 tests PASS).
  - `IdentityHttpIT` (6/6 tests PASS).
- **Kiểm thử Giao diện Native Live (Chụp ảnh màn hình thực tế lưu trữ tại `.local/qa/`)**:
  - `.local/qa/gamification/01-gamification-live.jpg` (Giao diện Gamification thật, vòng quay canvas, chuỗi điểm danh).
  - `.local/qa/reader-ui/07-relogin-preference-restore.jpg` (Đầu đọc khôi phục thiết lập gốc ORIGINAL, vị trí đọc).
  - `.local/qa/reward-gui-e2e/` (Luồng mua chương bằng Reward Coin, đọc truyện, đăng xuất đăng nhập lại).
