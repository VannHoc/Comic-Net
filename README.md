# Comic Net

Java 21 · Spring Boot 4.1.2 · Swing · Maven · SQL Server 2025.

Phase 1 đã được nghiệm thu. Phase 2 triển khai tài khoản/RBAC, danh mục, xuất bản,
bộ đọc, thư viện và quản trị trên Swing qua REST; dữ liệu nằm trong SQL Server và
private S3-compatible storage. Giữ đủ 23 module / 243 chức năng trong roadmap.
React + TypeScript tiếp tục là Web mở rộng sau MVP.

Phase 3: đánh giá/nhận xét, bình luận, gợi ý truyện, đếm lượt đọc hợp lệ + xếp hạng/analytics, thông báo trong ứng dụng (fan-out outbox, preference, announcement, email),
lên lịch xuất bản và rollback version đã có backend + Swing; migration V12–V16 đã áp dụng.
Verify reactor PASS (27 backend + 16 desktop unit) và 46 integration SQL/S3 PASS. Roadmap gốc Phase0–7 được giữ nguyên.
Điểm tiếp nối ngắn: [PROGRESS.md](PROGRESS.md).

Trạng thái hiện tại, điểm gián đoạn và kết quả kiểm tra khôi phục nằm tại
[implementation status](docs/implementation-status.md). Các mục evidence Phase 1
và tài liệu baseline Phase 0 bên dưới là lịch sử, không thay trạng thái hiện tại.

## Build and test

Use JDK 21 for Maven **and** both NetBeans child projects. From the repository root:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21.0.12'
.\mvnw.cmd -B -ntp verify
```

The ordinary build runs HTTP/security/presenter tests without requiring a SQL login.
These tests use test-scope HTTP fixtures; the application itself always calls real services.
SQL integration is a separate required acceptance step, not a simulated pass.

Flyway V1–V11 chỉ tạo schema/data nền. Sau migration, quản trị viên chạy các script
grant bảng tương ứng 03–11 trong deploy/sql. Runtime không có schema CONTROL,
db_owner hay quyền DDL. Script 02 đã được thay thế, không chạy lại.

## SQL development setup

1. Open [deploy/sql/01_provision_development.template.sql](deploy/sql/01_provision_development.template.sql) in SSMS as a SQL administrator. Any existing 01_provision_development.sql is a private, Git-ignored local working copy.
2. Replace both password placeholders in the SSMS buffer with two different strong local passwords, run, then discard the edited buffer. Do not save them in the repository or send them in chat. The script refuses existing target logins and does not change mixed mode/TCP/TLS.
3. It creates ComicNetDev and separate comicnet_app/comicnet_migrator logins/users/roles. Migration permissions stay scoped to ComicNetDev DDL/DML; no permission-delegation, sa/sysadmin/db_owner/schema CONTROL is required. Runtime permissions are granted on named tables by the administrator deployment scripts.
4. In the **backend process only**, set DB_USER, DB_PASSWORD, DB_MIGRATION_USER, DB_MIGRATION_PASSWORD; see backend/.env.example for non-secret names. A .env file is not auto-loaded. Do not set DB secrets in shared parent/desktop launch settings.
5. On a fresh installation, build the JAR and start backend (-Mode Run). Flyway migrates/validates; Hibernate uses validate. Readiness remains DOWN until the administrator runs script 03 after the table exists. Then run -Mode Verify for real SQL integration. Missing credentials or failed migration stop startup. Development uses encrypted JDBC with trustServerCertificate=true only in profile local. This foundation is not a production deployment configuration.

With the real credentials set in the backend terminal, run:

Khởi tạo dịch vụ Development một lần (tải release chính thức có kiểm SHA-256):

```powershell
.\deploy\Start-LocalServices.ps1 -Install
```

RustFS 1.0.4: S3 localhost:9000, console localhost:9001, private bucket comicnet-dev.
Mailpit 1.32.0: SMTP localhost:1025, inbox http://127.0.0.1:8025. Chỉ dùng Development.
Binary, volume, khóa và thông tin truy cập local nằm trong .local/ đã Git-ignore.

Để chạy backend với mật khẩu nhập ẩn, mở PowerShell:

```powershell
.\deploy\Start-Backend.ps1
```

Enter the runtime password, then the migration password at the hidden prompts. This helper
requires JDK 21, runs the real SQL integration tests, and starts the backend only if they pass.
Keep that backend window open. Its process environment is restored when the helper exits;
no password file is written. Run Swing separately from NetBeans so it receives no SQL secrets.
Use -Mode Verify for tests only, or -Mode Run to launch an already-built backend.
Helper tạo bộ khóa RSA/mail local nếu chưa có và cấu hình SMTP/S3 cho riêng backend.
Nếu chủ động dùng bản provisioning private hiện có, thêm -UseLocalConfiguration;
mật khẩu SQL chỉ được chuyển qua môi trường tiến trình, không in hoặc sao chép ra file mới.

Tài khoản demo local: trong màn Đăng ký của Swing, tạo `admin` với mật khẩu
`admin`, tên hiển thị ít nhất 3 ký tự; email và số điện thoại có thể để trống.
Sau đó mở `deploy/sql/05_assign_local_demo_roles.sql` trong SSMS và chạy trên
ComicNetDev bằng tài khoản SQL có quyền cập nhật dbo.UserRole và dbo.[User].
Script chỉ gán ADMINISTRATOR cho `admin`; không tạo hoặc cấp CREATOR cho tài khoản
khác. Đăng nhập lại `admin`, mở Quản trị → Tài khoản để chọn vai trò hệ thống
cho các tài khoản đã đăng ký. Có thể đăng ký `creator` / `creator` và cấp
READER, CREATOR tại đây. Tài khoản và mật khẩu không có hạn 15 phút; phiên đăng
nhập vẫn có thời hạn. Để Creator xuất bản trong một team, Admin cần tạo team và
gán Creator làm thành viên qua màn quản trị team.

Alternatively, when the four backend variables are already configured:

```powershell
.\mvnw.cmd -B -ntp -pl backend -Psql-integration verify
```

This starts real Tomcat/SQL, checks SQL major version 17, ComicNetDev marker, runtime privilege limits, ping/readiness/firewall. Do not run against a different database.

## Run/debug in NetBeans

Open this directory as the Maven parent and open its two modules. Select JDK 21 in NetBeans Java Platforms/project properties; the parent has no main class.

- **Backend**: bấm phải **Comic Net Backend → Reload Project** sau khi cập nhật mapping, rồi **Run**. `backend/nbactions.xml` gọi `deploy/Start-Backend.ps1 -Mode Run -FromSource -UseLocalConfiguration` qua Maven: script chỉ nạp mật khẩu từ file provisioning private đang có vào môi trường tiến trình backend, đặt `DB_USER=comicnet_app` và các biến SQL/S3/SMTP, rồi `spring-boot:run` biên dịch source hiện tại. Không thêm mật khẩu vào NetBeans Properties/POM/source. `backend/.env.example` chỉ là mẫu, không tự load. **Debug Project** mở JDWP ở `localhost:5005`; tiếp tục NetBeans → Debug → Attach Debugger → Java JPDA → SocketAttach → localhost:5005. Cổng chỉ bind loopback; Swing không nhận biến SQL. Nếu không có file private, chạy helper từ PowerShell **không** có `-UseLocalConfiguration` để nhập mật khẩu ẩn; NetBeans Run cần cấu hình private local như README mục SQL setup.
- **Desktop Swing**: Run/Debug child trong NetBeans. Main: com.comicplatform.desktop.DesktopApplication. COMICNET_API_BASE_URL mặc định http://localhost:8080/api/v1; COMICNET_MEDIA_HOSTS là danh sách host ảnh bổ sung khi triển khai. Client ảnh riêng không gửi cookie/token và không theo redirect. Checklist nghiệm thu 19 story, chuột/bàn phím/DPI/dialog ở [docs/testing/phase-2-swing-manual-checklist.md](docs/testing/phase-2-swing-manual-checklist.md).

Run backend first, desktop second. There are two JVMs. The UI does not start SQL/Spring or connect to JDBC. Stop backend and retry to verify the real unavailable state; restart backend and retry to recover. The screen distinguishes REST connectivity from database readiness. Alt+K checks, Alt+H cancels; tab/focus and resize are native Swing.

For command-line packaged launch (JDK 21):

```powershell
java -jar backend/target/comic-net-backend-0.1.0-SNAPSHOT.jar
# In another terminal WITHOUT SQL secrets:
java -jar desktop-swing/target/comic-net-desktop-swing-0.1.0-SNAPSHOT.jar
```

Keep desktop-swing/target/lib beside the desktop JAR. Packaging copies its runtime JSON libraries; desktop has no Spring/JPA/JDBC dependency. Neither Maven nor NetBeans should be launched from a process containing DB secrets when opening/running the desktop child.

## UI and scope

Swing chuyển Home/Manga/Details từ Figma thành desktop navigation, danh sách/chi tiết,
editor và reader native. Giữ action #61BFAD, artwork #54BAB9, text #1F1F39,
surface #F4F3FD, radius và Poppins đã đóng gói cùng OFL. Dùng font Unicode native
dự phòng cho tiếng Việt vì bộ Poppins nguồn thiếu glyph. Cửa sổ đề xuất 1280×800,
minimum 1024×640; toolbar xuống dòng, bảng có paging, HTTP/decode ngoài EDT.

Full scope remains in [prj_app.md](prj_app.md) and [docs](docs/README.md).
Backup/restore là quy trình người vận hành đã duyệt; Swing có runbook đóng gói và
nhật ký/jobs thật. Không xem trạng thái health hoặc một test SQL đơn lẻ là bằng chứng restore.

## Evidence — 2026-10-27

- Maven Wrapper 3.10.0 / Java 21.0.12: verify SUCCESS; 9 backend + 7 desktop tests, zero failures/errors/skips.
- NetBeans bundled Maven 3.9.16 with the same POM/JDK 21: verify SUCCESS. Model cache refreshed after resolving Maven 3.10 repository-origin metadata through the IDE Maven; no version downgrade.
- NetBeans Debug attached through JPDA and opened the Swing window on its bundled JDK 21.0.12; the session closed normally. Backend Run/Debug directly in the IDE with its own secret environment is still pending verification.
- Initial packaged Swing/NetBeans Run tests verified the unavailable-backend state. After the runtime table grant was applied, the existing packaged Swing window retried real REST and displayed Spring Boot connected / SQL Server ready with a server request ID.
- Packaged backend startup without DB variables fails before SQL login with a sanitized configuration error; no generated default-password login.
- Final root reactor verify with -Psql-integration: SUCCESS, 9 backend + 2 real SQL integration + 7 desktop tests; zero failures/errors/skips. SQL Server 2025 / ComicNetDev / Flyway V1 and runtime privilege limits verified. Live ping/readiness both UP; live Swing-to-REST-to-SQL verified. DPI/resize checks remain pending. No auth/catalog/reader business acceptance is claimed.
- Permission fix: V1 does not delegate privileges; administrator script 03 grants SELECT on dbo.SystemFoundation only. No schema CONTROL was granted. Script 02 is superseded. A public sanitized provisioning template is provided; the private SSMS working copy is Git-ignored.
- Build logs: build-verify.log, netbeans-build-check.log, backend/target/sql-integration-run.log and backend/target/backend-live.log (ignored); JUnit reports in each child target/surefire-reports and backend/target/failsafe-reports.

#   C o m i c - N e t  
 