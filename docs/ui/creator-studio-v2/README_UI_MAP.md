# Comic Net — Creator Studio UI Reference V2

**Phạm vi:** Xưởng xuất bản Comic Net; desktop Java Swing, Java 21, NetBeans 31; backend Spring Boot REST, SQL Server. **Đây là mockup tham chiếu, không phải code ứng dụng đã chạy.** Toàn bộ tên truyện, ảnh bìa cách điệu, số liệu và trạng thái đều là **dữ liệu minh họa**. Không nạp vào database. Giữ cấu trúc và theme hiện có khi triển khai.

## Bảng ánh xạ 13 ảnh → màn hình và nghiệp vụ

| Ảnh PNG | Vai trò | Màn hình / thời điểm mở | Hành động bắt buộc |
|---|---|---|---|
| `01_Xuong_xuat_ban_tong_quan.png` | Creator/Admin | Trang danh sách tác phẩm | Tạo truyện, lọc, tìm kiếm, chọn sửa/quản lý theo trạng thái; Admin được xem tác phẩm toàn hệ thống trong phạm vi được phân quyền |
| `02_Tao_va_sua_truyen.png` | Creator/Admin | Wizard 1: Thông tin | Tạo/sửa metadata, ảnh bìa, lưu nháp, tiếp tục |
| `03_Quan_ly_chuong.png` | Creator/Admin | Wizard 2: Chương & ảnh | Thêm/sửa chương, giá xu, upload hàng loạt, sắp trang |
| `04_Upload_nhieu_anh_va_sap_xep.png` | Creator/Admin | Dialog từ Wizard 2 | Multi-file / ZIP, tự sắp xếp natural sort, reorder, validate, progress, retry |
| `05_Xem_truoc_va_gui_duyet_Creator.png` | Creator | Wizard 3 | Validate trước gửi; lưu bản nháp hoặc gửi Admin duyệt |
| `06_Admin_hang_cho_duyet.png` | Administrator | Hàng chờ xét duyệt | Tìm/lọc yêu cầu; mở xem yêu cầu được gửi từ Creator |
| `07_Admin_xem_va_duyet_chi_tiet.png` | Administrator | Chi tiết xét duyệt | Preview, đối chiếu, yêu cầu sửa, từ chối hoặc duyệt & xuất bản |
| `08_Admin_tu_choi_yeu_cau_sua.png` | Administrator | Dialog yêu cầu chỉnh sửa | Lý do bắt buộc, chuyển NEEDS_CHANGES, báo Creator |
| `09_Creator_sua_va_gui_lai.png` | Creator | Sau khi bị yêu cầu sửa | Xem lý do, sửa metadata/ảnh, kiểm tra và gửi lại |
| `10_Admin_xuat_ban_truc_tiep.png` | Administrator | Wizard 3 của nội dung do Admin quản lý | Xuất bản ngay, lưu nháp hoặc lên lịch, **không bắt buộc gửi duyệt** |
| `11_Lich_su_phien_ban_Nang_cao.png` | Người có quyền quản lý | Menu `Nâng cao > Lịch sử phiên bản` | Xem/so sánh, tạo nháp phục hồi; bảo toàn phiên bản công khai |
| `12_Admin_tu_choi_xuat_ban.png` | Administrator | Dialog từ chối cuối cùng | Lý do bắt buộc, chuyển REJECTED, không xuất bản |
| `13_Xem_truoc_chuong_truoc_khi_duyet.png` | Creator sở hữu / Admin | Dialog xem trước Reader | Xem ảnh theo thứ tự thật, chuyển trang/scroll, không công khai nội dung |

## Các quy tắc thống nhất

1. **Không phải 13 tab.** Chỉ có một màn Xưởng xuất bản, một wizard 3 bước; phần còn lại là chức năng theo ngữ cảnh và màn xét duyệt riêng. Mỗi PNG là một **trạng thái hoặc cửa sổ tham chiếu**.
2. Creator chỉ được sửa nội dung thuộc phạm vi sở hữu/quyền nhóm và chỉ có hành động `Gửi duyệt`. Không tự duyệt hoặc xuất bản trực tiếp.
3. Admin được tự duyệt và xuất bản nội dung do mình quản lý, duyệt/từ chối/yêu cầu sửa nội dung Creator. Không áp dụng quy tắc cấm người gửi tự duyệt đối với `ADMINISTRATOR`.
4. Bỏ **MODERATOR** khỏi RBAC hiệu lực, nhưng phải xử lý role cũ và dữ liệu lịch sử bằng migration an toàn. Không tự nâng Moderator thành Admin.
5. Truyện đã công khai vẫn giữ phiên bản cũ khi Creator nộp revision; chỉ thay `activeVersion` sau khi Admin phê duyệt transaction hợp lệ.
6. Ảnh chapter nhiều file: natural sort `1,2,3,...10`, xem thumbnail, đổi thứ tự, validation MIME/nội dung/kích thước, ZIP chống traversal, retry idempotent, không block EDT.
7. `volume` là tùy chọn; không bắt Creator tạo volume trước chapter. Không bắt người dùng nhập ID nội bộ, UUID, Asset ID, Job ID, Snapshot.
8. Đưa các thao tác Version, Snapshot, Retry job, Rollback vào `Nâng cao`; không xóa backend đã có.
9. Các hành động bật/tắt và disabled phải tuân thủ quyền và trạng thái thực của server. Form, loading, error, empty state phải triển khai bằng Swing thực.
10. **Nghiệm thu** là chạy Swing thật qua REST/SQL, không phải ảnh hay unit tests đơn thuần.

## State machine tối thiểu

`DRAFT → UPLOADING/PROCESSING → READY → PENDING_REVIEW → (PUBLISHED | NEEDS_CHANGES | REJECTED)`

`NEEDS_CHANGES → DRAFT_REVISION → READY → PENDING_REVIEW`.

Với Admin: `DRAFT → READY → PUBLISHED` hoặc `READY → SCHEDULED → PUBLISHED` (khi hợp lệ). Khi có phiên bản công khai, thay đổi mới xử lý trên bản revision. Giữ trạng thái kỹ thuật sâu hơn trong backend nếu dự án đã dùng; ánh xạ UI thân thiện.

## Design tokens

- Light theme; coral primary `#F45B50`, navy `#16243D`, nền `#F5F7FB`, surface trắng, border `#E5EAF2`.
- Sidebar khoảng 235px; topbar 75px; card radius 12–14px; input/button 36–44px; spacing 8px.
- Bố cục desktop tham chiếu 1600×900. Tại 1366×768 và 1180×720: thích ứng, **cho phép cuộn vùng nội dung khi cần**, không cắt mất chức năng.
- Ảnh bìa trên mockup là hình CSS trừu tượng tự thiết kế. Khi lập trình cần tải ảnh thực từ storage hoặc placeholder của app.

## Sử dụng HTML đi kèm

Các file `.html` là **bản dựng tĩnh để xem bố cục và tham khảo CSS token**; không có API hoặc nút chức năng. Không được thay Java Swing bằng HTML/React. Không sao chép dữ liệu hoặc ảnh minh họa vào ứng dụng.
