# 11. TỪ ĐIỂN THUẬT NGỮ, ANNOTATION VÀ TỪ VIẾT TẮT COMIC NET

---

## 1. CÁC TỪ VIẾT TẮT CHUYÊN NGÀNH (ACRONYMS)

| Viết tắt | Tên đầy đủ tiếng Anh | Giải thích nghĩa và ý nghĩa trong dự án Comic Net |
|---|---|---|
| **API** | Application Programming Interface | Giao diện lập trình ứng dụng; phương thức để Desktop Swing giao tiếp dữ liệu với Spring Boot Backend. |
| **REST** | Representational State Transfer | Phong cách kiến trúc thiết kế dịch vụ web phi trạng thái; dự án sử dụng chuẩn RESTful qua HTTP. |
| **JSON** | JavaScript Object Notation | Định dạng văn bản nhẹ dùng để tuần tự hóa và trao đổi dữ liệu có cấu trúc giữa Swing và Backend. |
| **JWT** | JSON Web Token | Chuẩn mở (RFC 7519) định nghĩa mã xác thực nhỏ gọn, độc lập; dùng làm Access Token Bearer trong RAM của ứng dụng Swing. |
| **RBAC** | Role-Based Access Control | Mô hình kiểm soát truy cập dựa trên vai trò người dùng (`READER`, `CREATOR`, `ADMINISTRATOR`). |
| **EDT** | Event Dispatch Thread | Luồng xử lý sự kiện giao diện đơn của Java Swing; chịu trách nhiệm vẽ UI và xử lý sự kiện bàn phím/chuột. |
| **ORM** | Object-Relational Mapping | Kỹ thuật ánh xạ các bảng trong cơ sở dữ liệu quan hệ sang các đối tượng Class trong ngôn ngữ lập trình Java (Hibernate). |
| **JPA** | Jakarta Persistence API | Bộ đặc tả tiêu chuẩn của Java cho việc quản lý dữ liệu bền vững (Persistence) và ORM. |
| **DTO** | Data Transfer Object | Đối tượng thuần túy chỉ dùng để vận chuyển dữ liệu qua mạng giữa Client và Server, không chứa logic nghiệp vụ. |
| **DAO** | Data Access Object | Mẫu thiết kế phân tách tầng truy cập cơ sở dữ liệu ra khỏi tầng logic nghiệp vụ (`Repository`). |
| **ACID** | Atomicity, Consistency, Isolation, Durability | Bốn thuộc tính bảo đảm tính toàn vẹn và tin cậy của một giao dịch cơ sở dữ liệu (Database Transaction). |
| **S3** | Simple Storage Service | Chuẩn giao thức lưu trữ đối tượng qua mạng; dự án dùng kết nối tới máy chủ RustFS S3 lưu ảnh bìa và ảnh chương. |
| **SMTP** | Simple Mail Transfer Protocol | Giao thức truyền tải thư điện tử; dự án dùng gửi OTP và thông báo qua dịch vụ Mailpit. |
| **OTP** | One-Time Password | Mật khẩu dùng một lần có thời hạn ngắn (5 phút), dùng để xác minh email hoặc khôi phục mật khẩu. |
| **MVP** | Minimum Viable Product | Sản phẩm khả thi tối thiểu; phiên bản hoàn chỉnh các chức năng cốt lõi được ưu tiên nghiệm thu bảo vệ đồ án. |
| **SRS** | Software Requirements Specification | Tài liệu đặc tả yêu cầu phần mềm; tài liệu nền tảng xác định phạm vi và tiêu chí chấp nhận của hệ thống. |
| **ERD** | Entity-Relationship Diagram | Sơ đồ biểu diễn các thực thể và mối quan hệ ràng buộc giữa các bảng trong cơ sở dữ liệu. |
| **IDOR** | Insecure Direct Object Reference | Lỗ hổng bảo mật khi người dùng thay đổi ID trên URL/API để truy cập hoặc sửa đổi trái phép dữ liệu của người khác. |
| **CSRF** | Cross-Site Request Forgery | Kỹ thuật tấn công giả mạo yêu cầu từ phía người dùng trình duyệt; được phòng vệ bằng `OriginPolicyFilter`. |
| **XSS** | Cross-Site Scripting | Tấn công chèn mã kịch bản độc hại; ứng dụng Desktop Swing không thực thi HTML/JS nên miễn nhiễm tự nhiên với XSS. |

---

## 2. BẢNG TRA CỨU ANNOTATION JAVA & SPRING BOOT

| Annotation | Tầng áp dụng | Ý nghĩa kỹ thuật và Cách Comic Net sử dụng |
|---|---|---|
| `@SpringBootApplication` | Bootstrap | Đánh dấu class khởi động chính (`ComicNetApplication`), tự động kích hoạt Component Scan, Auto-Configuration và Configuration. |
| `@RestController` | Web / Controller | Đánh dấu class là REST Controller xử lý HTTP Request; tự động serialize đối tượng trả về thành JSON qua Jackson. |
| `@Service` | Business Logic | Đánh dấu class chứa logic nghiệp vụ do Spring IoC Container quản lý vòng đời; ví dụ: `CatalogService`, `WalletService`. |
| `@Repository` | Data Access | Đánh dấu tầng truy cập dữ liệu; tự động dịch các lỗi SQL từ JDBC/JPA sang Spring DataAccessException. |
| `@Entity` & `@Table` | Persistence | Khai báo một Java Class là thực thể cơ sở dữ liệu được ánh xạ tới bảng tương ứng trong SQL Server (ví dụ: `dbo.Comic`). |
| `@Id` & `@GeneratedValue` | Persistence | Chỉ định thuộc tính là Khóa chính (`PRIMARY KEY`) và cấu hình cơ chế sinh giá trị tự tăng (`IDENTITY`). |
| `@Transactional` | Service Layer | Quản lý giao dịch dữ liệu mang tính nguyên tử (Atomicity). Tự động Commit khi kết thúc phương thức và Rollback khi có ngoại lệ. |
| `@Configuration` & `@Bean` | Configuration | Đánh dấu class cấu hình hệ thống và phương thức sinh ra Spring Bean đưa vào quản lý trong Application Context. |
| `@GetMapping` | Controller | Ánh xạ HTTP GET Request; dùng cho các thao tác đọc dữ liệu không làm thay đổi trạng thái hệ thống. |
| `@PostMapping` | Controller | Ánh xạ HTTP POST Request; dùng cho các thao tác tạo mới dữ liệu, đăng nhập, nạp tiền hoặc xuất bản chương. |
| `@PutMapping` | Controller | Ánh xạ HTTP PUT Request; dùng cho thao tác cập nhật toàn phần hoặc cập nhật trạng thái hiển thị của truyện. |
| `@DeleteMapping` | Controller | Ánh xạ HTTP DELETE Request; dùng cho thao tác xóa chương, xóa trang ảnh hoặc xóa đánh giá. |
| `@PathVariable` | Controller Method | Trích xuất tham số động từ đường dẫn URI; ví dụ: lấy `id` trong `@GetMapping("/comics/{id}")`. |
| `@RequestParam` | Controller Method | Trích xuất tham số truy vấn (Query Parameter) từ URL; ví dụ: phân trang `@RequestParam(defaultValue = "0") int page`. |
| `@RequestBody` | Controller Method | Tự động chuyển đổi nội dung JSON trong phần thân (Body) của HTTP Request thành đối tượng Java DTO. |
| `@Valid` | Controller Method | Kích hoạt bộ kiểm tra hợp lệ dữ liệu của Jakarta Bean Validation (`@NotNull`, `@Size`, `@Min`...) trước khi thực thi hàm. |
| `@RestControllerAdvice` | Global Exception | Đánh dấu bộ xử lý ngoại lệ toàn cục (`ApiExceptionHandler`); bắt các lỗi phát sinh trong controller để định dạng thành chuẩn RFC 7807. |
| `@ExceptionHandler` | Exception Handling | Chỉ định phương thức xử lý riêng cho một loại ngoại lệ cụ thể (ví dụ: `DomainException.class`). |

---

## 3. CÁC THUẬT NGỮ KIẾN TRÚC VÀ MẪU THIẾT KẾ (DESIGN PATTERNS)

### 3.1. Modular Monolith
Kiến trúc phần mềm trong đó toàn bộ hệ thống chạy chung trong một tiến trình duy nhất (Single Process / Monolith), nhưng mã nguồn bên trong được phân chia nghiêm ngặt thành các module nghiệp vụ độc lập, có ranh giới rõ ràng và chỉ giao tiếp với nhau qua các Interface hoặc DTO công khai, không phụ thuộc vào triển khai nội bộ của nhau.

### 3.2. Inversion of Control (IoC) & Dependency Injection (DI)
Nguyên lý đảo ngược quyền điều khiển; thay vì một class tự khởi tạo các đối tượng phụ thuộc bằng từ khóa `new`, Spring Framework (IoC Container) sẽ tự động tạo và "tiêm" (Inject) các phụ thuộc đó vào class thông qua Constructor. Giúp mã nguồn dễ bảo trì và dễ viết bài kiểm thử đơn vị độc lập.

### 3.3. Double-entry Ledger (Sổ cái kế toán kép)
Nguyên tắc kế toán trong đó mọi biến động số dư ví xu đều phải được ghi nhận thành một bản ghi bút toán đối ứng không thể chỉnh sửa (`dbo.LedgerEntry`), bao gồm số tiền thay đổi, loại giao dịch, số dư trước và sau. Giúp chống thất thoát tiền tệ và dễ dàng đối soát tài chính.

### 3.4. Transactional Outbox Pattern
Mẫu thiết kế giải quyết bài toán giao dịch phân tán giữa Cơ sở dữ liệu và Hệ thống thông báo/hàng đợi. Thay vì gửi trực tiếp thông báo ra ngoài (dễ thất bại nếu mạng chập chờn), hệ thống chèn thông báo vào bảng `dbo.Notification` hoặc `dbo.OutboxEvent` nằm trong cùng một Transaction với thao tác chính; sau đó một Worker nền quét bảng này để gửi đi một cách an toàn.

### 3.5. Natural Sort (Sắp xếp tự nhiên)
Giải thuật sắp xếp chuỗi văn bản có chứa số theo thứ tự logic của con người (1, 2, ... 9, 10) thay vì sắp xếp theo bảng mã ký tự ASCII (1, 10, 2). Cực kỳ quan trọng đối với việc sắp xếp số thứ tự trang ảnh và chương truyện tranh.

### 3.6. FlatLaf Look and Feel
Bộ giao diện đồ họa phẳng dành riêng cho Java Swing, mô phỏng phong cách hiện đại của Windows 11 và macOS, hỗ trợ chuyển đổi chủ đề Tối/Sáng (Dark/Light Mode), tự động co giãn phông chữ và icon theo tỷ lệ DPI màn hình (100%, 125%, 150%).
