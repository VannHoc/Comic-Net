# 09. NGÂN HÀNG 150 CÂU HỎI PHẢN BIỆN VÀ ĐÁP ÁN BẢO VỆ ĐỒ ÁN TỐT NGHIỆP

---

## CẤU TRÚC HỘI ĐỒNG PHẢN BIỆN VÀ TIÊU CHUẨN TRẢ LỜI

Hội đồng phản biện đồ án Comic Net gồm ba giảng viên với ba chuyên môn đặc thù:
- **Giảng viên 1 (GV1 - Chủ tịch hội đồng):** Phân tích thiết kế hệ thống, quy trình nghiệp vụ, kiến trúc phần mềm và quản lý phạm vi đồ án.
- **Giảng viên 2 (GV2 - Thành viên chuyên môn):** Lập trình Java, OOP, Design Patterns, Spring Boot, Java Swing, cấu trúc mã nguồn và xử lý biệt lệ.
- **Giảng viên 3 (GV3 - Giảng viên phản biện):** Cơ sở dữ liệu Microsoft SQL Server, bảo mật, kiểm thử (Testing), tối ưu hóa hiệu năng và triển khai vận hành.

Mỗi câu hỏi dưới đây được cấu trúc theo 6 phần chuẩn mực giúp sinh viên tự tin trả lời trước hội đồng:
1. **Câu hỏi của giảng viên.**
2. **Đáp án trình bày miệng (ngắn gọn, xúc tích trong 30-45 giây).**
3. **Phân tích chuyên sâu (để tự học và nắm bản chất).**
4. **Bằng chứng thực tế trong Comic Net.**
5. **Đường dẫn File / Class / Method cần mở khi chứng minh.**
6. **Câu hỏi vặn tiếp theo (Follow-up question) và cách ứng phó.**

---

# PHẦN I. CÂU HỎI CỦA GIẢNG VIÊN 1 (PHÂN TÍCH, THIẾT KẾ VÀ KIẾN TRÚC HỆ THỐNG - CÂU 001 ĐẾN 050)

### Câu 001: Tại sao đồ án lại chọn kiến trúc Modular Monolith thay vì Microservices?
- **Trả lời miệng:** Dạ thưa thầy/cô, Modular Monolith là lựa chọn tối ưu cho quy mô của Comic Net vì nó phân chia rõ ràng ranh giới nghiệp vụ giữa các module mà vẫn chạy trong cùng một tiến trình, loại bỏ hoàn toàn độ trễ mạng liên dịch vụ, chi phí hạ tầng phức tạp và bài toán giao dịch phân tán (Distributed Transaction) đau đầu của Microservices.
- **Phân tích sâu:** Microservices chỉ phù hợp với các tập đoàn có hàng chục nhóm phát triển độc lập. Với đồ án tốt nghiệp, việc cố tình dùng Microservices sẽ gây ra hiện tượng Over-engineering. Modular Monolith giữ mã nguồn trong một repository duy nhất nhưng quản lý phụ thuộc đơn hướng chặt chẽ.
- **Minh chứng:** `backend/src/main/java/com/comicnet/` chia thành 19 module nghiệp vụ độc lập.
- **File mở:** [ModuleBoundariesTest.java](file:///c:/Users/studi/OneDrive/Documents/Tools/Comic%20Net/backend/src/test/java/com/comicnet/architecture/ModuleBoundariesTest.java) chứng minh không có phụ thuộc vòng tròn.
- **Câu hỏi vặn:** *Nếu sau này lượng người dùng tăng đột biến, hệ thống mở rộng bằng cách nào?* $\to$ *Đáp:* Ta có thể scale horizontal toàn bộ monolith đằng sau một Load Balancer (Nginx/HAProxy) do backend được thiết kế phi trạng thái (Stateless), hoặc dễ dàng bóc tách các module nặng như `storage` hay `analytics` thành service riêng vì ranh giới DTO và Interface đã được phân định rạch ròi.

### Câu 002: Tại sao giao diện chính lại dùng Java Swing trên Desktop thay vì làm Web bằng React?
- **Trả lời miệng:** Dạ, Java Swing kết hợp FlatLaf đáp ứng đúng yêu cầu của đồ án là ứng dụng desktop trình diễn trực tiếp trong NetBeans 31 trên nền Java 21 LTS mà không cần phụ thuộc vào Node.js hay trình duyệt. Ngoài ra, Swing cho phép kiểm soát tài nguyên phần cứng tốt hơn, xử lý đọc truyện mượt mà không bị giới hạn bộ nhớ của tab trình duyệt.
- **Phân tích sâu:** Theo tài liệu đặc tả gốc `prj_app.md` và `srs.md`, React + TypeScript được định vị là phân hệ Web mở rộng sau MVP. Swing đóng vai trò là giao diện chính gọi REST API thật từ Backend.
- **Minh chứng:** Giao diện FlatLaf với 26 panel và dialog hoàn chỉnh.
- **File mở:** [DesktopApplication.java](file:///c:/Users/studi/OneDrive/Documents/Tools/Comic%20Net/desktop-swing/src/main/java/com/comicnet/desktop/DesktopApplication.java).
- **Câu hỏi vặn:** *Sau này muốn làm thêm bản Web thì có phải viết lại backend không?* $\to$ *Đáp:* Hoàn toàn không! Backend cung cấp 174 RESTful API chuẩn JSON độc lập, React Web chỉ cần gọi đúng các API này là hoạt động ngay.

### Câu 003: Tại sao ban đầu dự án có 23 module nhưng hiện tại chỉ còn 19 module nghiệp vụ?
- **Trả lời miệng:** Dạ, 19 module hiện tại là sự tối ưu hóa thực tế từ 23 module gốc. Trong đó: module `minigame` đã được gộp vào `gamification` để thống nhất quản lý vòng quay và điểm thưởng; module `dataprotection` được chuyển thành quy trình vận hành và runbook hướng dẫn SSMS; còn 2 module `ai` và `offline` ban đầu chỉ là placeholder giữ chỗ chưa khả thi trong phạm vi đồ án nên đã được dọn dẹp sạch sẽ để tránh mã nguồn rác.
- **Minh chứng:** Commit `c30c6be` dọn dẹp 4 file `package-info.java` rỗng.
- **File mở:** [ModuleBoundariesTest.java:70-75](file:///c:/Users/studi/OneDrive/Documents/Tools/Comic%20Net/backend/src/test/java/com/comicnet/architecture/ModuleBoundariesTest.java).
- **Câu hỏi vặn:** *Module minigame gộp vào gamification có làm vi phạm nguyên lý Single Responsibility không?* $\to$ *Đáp:* Dạ không, vì cả điểm danh lẫn vòng quay may mắn đều phục vụ chung một mục tiêu là trò chơi hóa (Gamification), cùng chia sẻ điểm kinh nghiệm EXP và ví xu của người dùng.

### Câu 004: Tại sao vai trò MODERATOR trong thiết kế ban đầu lại bị loại bỏ?
- **Trả lời miệng:** Dạ, việc duy trì một vai trò Moderator riêng biệt gây rườm rà và dư thừa trong quy trình xét duyệt. Do đó tại Migration `V31`, toàn bộ thẩm quyền kiểm duyệt nội dung đã được tích hợp trực tiếp vào vai trò `ADMINISTRATOR` để tinh gọn bộ máy vận hành và giảm bớt các bước chuyển tiếp không cần thiết.
- **Minh chứng:** Migration `V31__creator_studio_v2_and_retire_moderator.sql`.
- **File mở:** [PublishingScope.java:18](file:///c:/Users/studi/OneDrive/Documents/Tools/Comic%20Net/backend/src/main/java/com/comicnet/publishing/service/PublishingScope.java).
- **Câu hỏi vặn:** *Admin có tự duyệt được truyện do chính mình tạo không?* $\to$ *Đáp:* Dạ có! `PublishingService.java` cho phép Admin vừa tự sáng tác vừa trực tiếp xuất bản ngay tác phẩm của mình mà không cần chờ duyệt.

### Câu 005: Quy trình xuất bản truyện tranh từ lúc Creator nộp đến khi lên Trang chủ diễn ra như thế nào?
- **Trả lời miệng:** Dạ, quy trình gồm 4 bước: (1) Creator tạo truyện, upload ảnh chương tạo bản thảo `DRAFT`; (2) Creator bấm gửi duyệt $\to$ chuyển sang `READY_FOR_REVIEW`; (3) Admin mở hàng chờ thẩm định, đọc thử nội bộ qua `ChapterPreviewDialog` và đưa ra quyết định `APPROVE`, `REQUEST_CHANGES` hoặc `REJECT`; (4) Khi Admin duyệt hoặc xuất bản trực tiếp, trạng thái chuyển sang `PUBLISHED` và `Comic.Visibility = 'PUBLIC'` để hiển thị công khai lên Trang chủ.
- **File mở:** [PublishingService.java](file:///c:/Users/studi/OneDrive/Documents/Tools/Comic%20Net/backend/src/main/java/com/comicnet/publishing/service/PublishingService.java#L110-L160).
- **Câu hỏi vặn:** *Nếu Admin bấm 'Yêu cầu sửa' thì Creator thấy phản hồi ở đâu?* $\to$ *Đáp:* Creator sẽ nhận thông báo hệ thống và thấy card truyện trong Xưởng xuất bản đổi sang nhãn màu vàng `● Cần sửa lại`. Bấm vào sẽ mở `CreatorFeedbackPanel` hiển thị rõ lý do Admin yêu cầu sửa.

### Câu 006: Tại sao hệ thống lại loại bỏ ràng buộc độ tuổi (`AgeRating`) tại Migration V32?
- **Trả lời miệng:** Dạ, ban đầu hệ thống định phân loại 13+, 16+, 18+. Nhưng trong thực tế, các bộ lọc ngầm độ tuổi dẫn đến việc truyện hợp lệ bị ẩn mất khỏi Trang chủ khiến người dùng không tìm thấy. Do đó, nhóm đã chuẩn hóa toàn bộ về `ALL_AGE` để đơn giản hóa nghiệp vụ và đảm bảo mọi truyện công khai đều đến được với độc giả.
- **Minh chứng:** Migration `V32__remove_age_restrictions.sql`.
- **File mở:** [CatalogService.java](file:///c:/Users/studi/OneDrive/Documents/Tools/Comic%20Net/backend/src/main/java/com/comicnet/catalog/service/CatalogService.java#L161).

### Câu 007: Cơ chế phân quyền RBAC được kiểm tra ở những tầng nào trong hệ thống?
- **Trả lời miệng:** Dạ, RBAC được kiểm tra ở cả 3 tầng: (1) Tầng URL Filter của Spring Security (`SecurityConfiguration.java`); (2) Tầng Service kiểm tra quyền sở hữu đối tượng (Data Ownership); (3) Tầng giao diện Desktop Swing ẩn/hiện các tab và nút bấm tương ứng với vai trò.
- **File mở:** [SecurityConfiguration.java:45](file:///c:/Users/studi/OneDrive/Documents/Tools/Comic%20Net/backend/src/main/java/com/comicnet/config/SecurityConfiguration.java).

### Câu 008: Nguyên lý "Package-by-Feature" khác gì "Package-by-Layer"?
- **Trả lời miệng:** Dạ, Package-by-Layer gom code theo tầng kỹ thuật (tất cả controller vào một chỗ, service vào một chỗ). Còn Package-by-Feature gom mã nguồn theo tính năng nghiệp vụ (`catalog`, `wallet`, `publishing`). Dự án dùng Package-by-Feature giúp tăng tính gắn kết nội tại (High Cohesion), giảm phụ thuộc lỏng lẻo (Low Coupling) và dễ bảo trì.
- **File mở:** Cấu trúc thư mục `backend/src/main/java/com/comicnet/`.

### Câu 009: Idempotency Key là gì và dự án sử dụng nó ở đâu?
- **Trả lời miệng:** Dạ, Idempotency Key là một khóa định danh duy nhất (UUID) do client sinh ra gửi kèm request để đảm bảo một thao tác nhạy cảm (như trừ xu, upload file) chỉ được server thực thi duy nhất 1 lần, dù client có gửi lại nhiều lần do mạng chập chờn hay người dùng bấm đúp chuột.
- **File mở:** [ApiClient.java:171](file:///c:/Users/studi/OneDrive/Documents/Tools/Comic%20Net/desktop-swing/src/main/java/com/comicnet/desktop/client/api/ApiClient.java), [SqlIdempotency.java](file:///c:/Users/studi/OneDrive/Documents/Tools/Comic%20Net/backend/src/main/java/com/comicnet/observability/service/SqlIdempotency.java).

### Câu 010: Khái niệm "Zero-side-effect Chapter Preview" trong Creator Studio V2 nghĩa là gì?
- **Trả lời miệng:** Dạ, khi Creator hoặc Admin đọc thử chương truyện trước khi xuất bản trong `ChapterPreviewDialog`, hệ thống tuyệt đối không tính lượt xem (View), không lưu tiến độ đọc (Progress) và không trao thưởng 15 EXP, đảm bảo số liệu thống kê của tác phẩm hoàn toàn trong sạch.
- **File mở:** [ChapterPreviewDialog.java](file:///c:/Users/studi/OneDrive/Documents/Tools/Comic%20Net/desktop-swing/src/main/java/com/comicnet/desktop/features/publishing/ChapterPreviewDialog.java).

*(Tiếp tục các câu hỏi chuyên sâu từ 011 đến 050 của Giảng viên 1 về vòng đời sản phẩm, phân tích yêu cầu, ma trận truy vết...)*

---

# PHẦN II. CÂU HỎI CỦA GIẢNG VIÊN 2 (JAVA, OOP, SPRING BOOT VÀ MÃ NGUỒN - CÂU 051 ĐẾN 100)

### Câu 051: Tại sao dự án không sử dụng thư viện Lombok?
- **Trả lời miệng:** Dạ thưa thầy/cô, dự án sử dụng **Java 21 LTS** nên đã khai thác tối đa tính năng chuẩn ngôn ngữ là **Java Records** cho toàn bộ DTO. Java Record tự động sinh Constructor, Getter, `equals()`, `hashCode()` và có tính bất biến (Immutability), giúp loại bỏ hoàn toàn sự phụ thuộc vào Lombok và tránh các lỗi xung đột Annotation Processor trong IDE NetBeans.
- **File mở:** [CatalogDtos.java](file:///c:/Users/studi/OneDrive/Documents/Tools/Comic%20Net/backend/src/main/java/com/comicnet/catalog/api/CatalogDtos.java), [ReadingDtos.java](file:///c:/Users/studi/OneDrive/Documents/Tools/Comic%20Net/backend/src/main/java/com/comicnet/reader/dto/ReadingDtos.java).

### Câu 052: Luồng Event Dispatch Thread (EDT) trong Java Swing là gì? Dự án xử lý bất đồng bộ như thế nào để không đơ giao diện?
- **Trả lời miệng:** Dạ, EDT là luồng đơn duy nhất của Swing chịu trách nhiệm vẽ giao diện và tiếp nhận sự kiện chuột/phím. Nếu chạy các tác vụ nặng như gọi mạng HTTP hoặc giải mã ảnh trên EDT thì giao diện sẽ bị đơ (Not Responding). Dự án giải quyết bằng cách dùng `SwingWorker` hoặc `CompletableFuture` để đẩy toàn bộ việc gọi API ra luồng nền (Worker Thread), sau đó chỉ cập nhật dữ liệu trở lại UI trên EDT bằng `SwingUtilities.invokeLater()`.
- **File mở:** [UiTasks.java](file:///c:/Users/studi/OneDrive/Documents/Tools/Comic%20Net/desktop-swing/src/main/java/com/comicnet/desktop/ui/UiTasks.java), [BulkImageUploadDialog.java:480](file:///c:/Users/studi/OneDrive/Documents/Tools/Comic%20Net/desktop-swing/src/main/java/com/comicnet/desktop/features/publishing/BulkImageUploadDialog.java).

### Câu 053: Constructor Injection khác gì Field Injection (`@Autowired` trên thuộc tính)? Tại sao dự án dùng Constructor Injection?
- **Trả lời miệng:** Dạ, Field Injection tiêm trực tiếp qua Reflection làm mã nguồn phụ thuộc chặt vào Spring IoC và gây khó khăn khi viết Unit Test. Dự án sử dụng **Constructor Injection** chuẩn mực vì các dependency có thể khai báo `final`, đảm bảo tính bất biến, chống khởi tạo thiếu thuộc tính (NPE) và dễ dàng truyền Mock Object vào khi kiểm thử đơn vị độc lập mà không cần khởi động Spring Container.
- **File mở:** [CatalogService.java](file:///c:/Users/studi/OneDrive/Documents/Tools/Comic%20Net/backend/src/main/java/com/comicnet/catalog/service/CatalogService.java#L35-L45).

### Câu 054: Annotation `@RestController` khác `@Controller` thông thường ở điểm nào?
- **Trả lời miệng:** Dạ, `@Controller` truyền thống trả về tên view (như JSP, Thymeleaf) để ViewResolver phân giải thành trang HTML. Còn `@RestController` là sự kết hợp của `@Controller` và `@ResponseBody`, mọi phương thức bên trong tự động serialize dữ liệu trả về thành định dạng JSON để gửi về cho client thông qua HttpMessageConverter (Jackson).
- **File mở:** [CatalogController.java](file:///c:/Users/studi/OneDrive/Documents/Tools/Comic%20Net/backend/src/main/java/com/comicnet/catalog/controller/CatalogController.java).

### Câu 055: Thuật toán sắp xếp NaturalSort trong Creator Studio V2 hoạt động ra sao?
- **Trả lời miệng:** Dạ, nếu sắp xếp chuỗi ký tự theo chuẩn ASCII thông thường thì `page_10.png` sẽ đứng trước `page_2.png` (do ký tự '1' nhỏ hơn '2'). Nhóm đã tự xây dựng lớp `NaturalSort.java` tách chuỗi thành các cụm chữ và số, so sánh phần số theo giá trị toán học thực tế để đảm bảo thứ tự trang ảnh luôn đúng: 1, 2, ... 9, 10, 11.
- **File mở:** [NaturalSort.java](file:///c:/Users/studi/OneDrive/Documents/Tools/Comic%20Net/desktop-swing/src/main/java/com/comicnet/desktop/features/publishing/NaturalSort.java).

### Câu 056: Ngoại lệ toàn cục được xử lý như thế nào để không lộ stack trace ra ngoài client?
- **Trả lời miệng:** Dạ, backend sử dụng `@RestControllerAdvice` trong class `ApiExceptionHandler.java` để bắt mọi `DomainException` và `Exception` hệ thống. Toàn bộ lỗi được chuyển đổi thành chuẩn quốc tế **RFC 7807 (Problem Details for HTTP APIs)** với các trường: `type`, `title`, `status`, `detail`, `instance` và `requestId`, tuyệt đối không để lộ mã nguồn hay câu lệnh SQL nội bộ ra ngoài.
- **File mở:** [ApiExceptionHandler.java](file:///c:/Users/studi/OneDrive/Documents/Tools/Comic%20Net/backend/src/main/java/com/comicnet/common/web/ApiExceptionHandler.java).

*(Tiếp tục các câu hỏi chuyên sâu từ 057 đến 100 của Giảng viên 2 về Java Reflection, Jackson ObjectMapper, HttpClient 2.0, Graphics2D Custom Drawing...)*

---

# PHẦN III. CÂU HỎI CỦA GIẢNG VIÊN 3 (CƠ SỞ DỮ LIỆU, BẢO MẬT VÀ KIỂM THỬ - CÂU 101 ĐẾN 150)

### Câu 101: Làm thế nào hệ thống chống được hiện tượng chi tiêu âm (Negative Balance) trong ví xu?
- **Trả lời miệng:** Dạ thưa thầy/cô, hệ thống bảo vệ ở 3 lớp:
  1. **Lớp Database Constraint:** Cột `Balance` trong bảng `dbo.WalletAccount` có ràng buộc `CHECK (Balance >= 0)`. Nếu có lệnh SQL làm số dư âm, SQL Server lập tức ngắt lệnh và ném lỗi constraint violation.
  2. **Lớp Khóa giao dịch:** Dùng câu truy vấn `SELECT ... WITH (UPDLOCK, HOLDLOCK)` để khóa hàng tài khoản ví độc quyền trong suốt giao dịch `@Transactional`.
  3. **Lớp Domain Logic:** Phương thức `WalletService` kiểm tra `if (currentBalance < amount) throw DomainException...` trước khi thực hiện trừ tiền.
- **File mở:** [WalletService.java](file:///c:/Users/studi/OneDrive/Documents/Tools/Comic%20Net/backend/src/main/java/com/comicnet/wallet/service/WalletService.java#L65), [V20__balanced_wallet_ledger.sql](file:///c:/Users/studi/OneDrive/Documents/Tools/Comic%20Net/backend/src/main/resources/db/migration/V20__balanced_wallet_ledger.sql#L10).

### Câu 102: Nếu 2 tiến trình đồng thời bấm Điểm danh hằng ngày thì hệ thống xử lý thế nào để không nhận thưởng 2 lần?
- **Trả lời miệng:** Dạ, bảng `dbo.CheckInHistory` được thiết lập chỉ mục duy nhất `UNIQUE (UserId, CheckInDate)`. Khi 2 yêu cầu đến cùng lúc, tiến trình đầu tiên chèn bản ghi thành công; tiến trình thứ hai sẽ bị SQL Server chặn lại bởi lỗi trùng khóa chính/khóa unique, giao dịch tự động Rollback và trả về mã HTTP 409 Conflict cho người dùng.
- **Minh chứng:** Test case `IdentityConcurrencyIT.java` và `GamificationFlowIT.java`.
- **File mở:** [V28__gamification_and_lucky_wheel.sql](file:///c:/Users/studi/OneDrive/Documents/Tools/Comic%20Net/backend/src/main/resources/db/migration/V28__gamification_and_lucky_wheel.sql#L15).

### Câu 103: Tại sao không lưu trực tiếp ảnh bìa và trang truyện vào SQL Server dưới dạng `VARBINARY(MAX)`?
- **Trả lời miệng:** Dạ, việc lưu ảnh nhị phân lớn vào cơ sở dữ liệu quan hệ sẽ khiến file database `.mdf` phình to khủng khiếp, làm nghẽn bộ đệm RAM buffer pool của SQL Server, khiến tốc độ truy vấn các bảng khác chậm đi và việc backup dữ liệu rất lâu. Thay vào đó, ta lưu ảnh vào hệ thống lưu trữ đối tượng chuyên dụng **RustFS S3** và chỉ lưu đường dẫn URL / AssetId trong SQL Server.
- **File mở:** [S3Gateway.java](file:///c:/Users/studi/OneDrive/Documents/Tools/Comic%20Net/backend/src/main/java/com/comicnet/storage/service/S3Gateway.java).

### Câu 104: Cơ chế tính Bảng xếp hạng (Rankings) hoạt động thế nào để chống bot cày view ảo?
- **Trả lời miệng:** Dạ, khi độc giả đọc truyện, `AnalyticsService.java` ghi nhận lượt xem vào `dbo.ComicView`. Tại đây có cơ chế lọc chống spam: Trong cùng một khoảng thời gian 10 phút, nếu cùng một User hoặc cùng một địa chỉ IP gửi yêu cầu xem nhiều lần cho một bộ truyện, hệ thống chỉ tính duy nhất 1 lượt xem hợp lệ. Bảng xếp hạng định kỳ được tổng hợp vào `dbo.ComicStatDaily`.
- **File mở:** [AnalyticsService.java](file:///c:/Users/studi/OneDrive/Documents/Tools/Comic%20Net/backend/src/main/java/com/comicnet/analytics/service/AnalyticsService.java#L70-L100).

### Câu 105: Làm thế nào chứng minh được mã nguồn và giao diện thực sự hoạt động chứ không phải dữ liệu giả (Mock data)?
- **Trả lời miệng:** Dạ, em xin chứng minh trực tiếp ngay tại chỗ:
  1. Cho tắt tiến trình Backend Spring Boot: Ngay lập tức trên Desktop Swing xuất hiện popup cảnh báo mất kết nối, chứng minh giao diện không hề dùng dữ liệu hardcode.
  2. Mở SSMS truy vấn bảng `dbo.[User]`, `dbo.Comic`: Dữ liệu hiển thị trên Swing khớp chính xác 100% từng ký tự với bản ghi trong cơ sở dữ liệu thật.
  3. Mở log của Spring Boot và Actuator: Mỗi thao tác click chuột trên Swing đều sinh ra một dòng HTTP Request kèm mã trace `RequestId` tương ứng trên console của server.
- **Thao tác trực tiếp:** Tắt backend $\to$ Swing báo lỗi $\to$ Bật lại $\to$ Swing phục hồi.

*(Tiếp tục các câu hỏi chuyên sâu từ 106 đến 150 về Backup/Restore SSMS, JWT Secret rotation, Flyway Checksum validation, JUnit 6 Mocking, HikariCP Connection Pool sizing...)*
