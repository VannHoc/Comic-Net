# Cài Comic Net trên máy Windows khác

Mỗi máy dùng SQL Server, RustFS và Mailpit **cục bộ**. Bản Swing gọi backend tại `127.0.0.1:8080`; không kết nối máy tác giả. Backend Java 21/Spring Boot 4.1.2, Swing và SQL Server/Flyway. Không cần React.

## Chỉ làm một lần

1. Cài **JDK 21**. Mở PowerShell, chạy `java -version`; phải thấy 21. Nếu chưa thấy, đặt `JAVA_HOME` và thêm `%JAVA_HOME%\bin` vào PATH, mở PowerShell mới.
2. Cài **SQL Server 2025** và **SQL Server Management Studio (SSMS)**. Trong SQL Server Configuration Manager, mở *SQL Server Network Configuration > Protocols for ... > TCP/IP*, bật TCP/IP, mở *IP Addresses > IPAll*, ghi lại `TCP Port`, rồi khởi động lại dịch vụ SQL Server. Trong SSMS, đăng nhập instance local để kiểm tra. Bật Mixed Mode/SQL Authentication nếu dùng SQL login; bước này có thể cần quyền Administrator. Không cần chạy Comic Net bằng Administrator.
3. Cài **sqlcmd** (SQL Server Command Line Utilities). Chạy `sqlcmd -?` trong PowerShell mới để kiểm tra.
4. Clone GitHub bằng Git rồi mở PowerShell tại thư mục gốc Comic Net. Kiểm tra có `mvnw.cmd`, `backend`, `desktop-swing`, `deploy`.
5. Chạy `.\deploy\Start-LocalServices.ps1 -Install` **một lần** để tải RustFS và Mailpit có checksum. Nếu tải thất bại, kiểm tra mạng và chạy lại. Ảnh upload ở `.local/data/rustfs`; sao lưu thư mục này cùng file `.bak` nếu chuyển dữ liệu.
6. Chạy `.\deploy\setup\Setup.ps1 -Mode Fresh`. Nhập server/IP local (thường `localhost`), cổng TCP **thực tế** từ Configuration Manager và mặc định `ComicNetDev`. PowerShell hỏi ba credential: SQL admin chỉ dùng lúc setup, `comicnet_app`, `comicnet_migrator`. Chọn mật khẩu khác nhau, ít nhất 20 ký tự cho hai login ứng dụng. Không gửi mật khẩu qua chat hay đưa vào Git. Setup kiểm tra TCP/login, tạo DB/login nếu trống, chạy Flyway rồi grants. Thành công sẽ in “Flyway/grants đã hoàn tất”. Nếu DB đã tồn tại mà chưa được setup trên máy này, script dừng để tránh ghi đè.
7. Khởi tạo Admin bằng đúng luồng setup một lần hiện có trong ứng dụng; không dùng mật khẩu Admin cố định. Xem `README.md` và màn hướng dẫn setup. Tài khoản demo trong tài liệu cũ không phải điều kiện cài đặt.

### Có file `.bak` của bạn — cách kết nối với dự án

1. Chép `.bak` sang **máy mới**. Mở SSMS, kết nối SQL Server cục bộ với quyền quản trị. Nhấp phải *Databases* → *Restore Database* → *Device* → chọn file `.bak`. Đặt tên database đích **`ComicNetDev`**, kiểm tra tab *Files* để đường dẫn data/log trỏ tới ổ đĩa của máy mới. Chỉ thực hiện khi `ComicNetDev` chưa tồn tại; **không chọn Overwrite existing database**. Đợi SSMS báo restore thành công.
2. Mở PowerShell ở thư mục Comic Net, chạy `.\deploy\setup\Setup.ps1 -Mode Restore` (đây cũng là mode mặc định). Nhập `localhost`, đúng cổng TCP và database `ComicNetDev`. Nhập SQL admin và chọn mật khẩu mới cho `comicnet_app`/`comicnet_migrator`. Script **không restore file**, chỉ tạo hoặc gắn lại login trên instance mới, kiểm tra kết nối, chạy Flyway theo version còn thiếu và cấp quyền runtime. Thành công sẽ in “Flyway/grants đã hoàn tất”.
3. Chạy `.\deploy\setup\Start.ps1`. Nếu readiness báo `UP` và Swing mở, ứng dụng đã kết nối database trên máy mới. Đăng nhập và kiểm tra danh sách truyện. `.bak` chỉ chứa SQL; nếu truyện có ảnh lưu ở RustFS, cần chép **bản sao `.local/data/rustfs` tương ứng** sang máy mới trước khi chạy Start, vì file `.bak` không chứa ảnh.

## Mỗi lần chạy

Mở PowerShell tại thư mục gốc, chạy `.\deploy\setup\Start.ps1`. Script đọc cấu hình mã hóa tại `.local/setup/connection.xml` (chỉ tài khoản Windows hiện tại đọc được), kiểm tra SQL, khởi động dịch vụ local, build backend/Swing, chờ `/actuator/health/readiness` báo `UP`, rồi mở Swing. Giữ cửa sổ Start mở trong lúc dùng ứng dụng.

## Lỗi thường gặp

- Không thấy JDK 21: kiểm tra `java -version`, `JAVA_HOME`, PATH.
- Thiếu `sqlcmd`: cài Command Line Utilities; mở PowerShell mới.
- TCP SQL không kết nối: kiểm tra đúng port IPAll, TCP/IP Enabled, dịch vụ SQL Server Running và firewall local.
- Login failed: kiểm tra Mixed Mode, đúng SQL username/password và quyền admin ở bước Setup.
- Cổng 8080 bận: dừng ứng dụng đang giữ cổng; script không tự dừng tiến trình khác.
- RustFS/Mailpit thiếu: chạy lại `Start-LocalServices.ps1 -Install` và kiểm tra checksum.
- Backend không sẵn sàng: kiểm tra SQL, Flyway/grants và các khóa local trong `.local/security`. Không sửa `application.yaml` để đổi máy.
