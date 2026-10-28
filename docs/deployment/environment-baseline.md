# Technology & environment baseline — Phase 0 revision 2

Ngày xác minh: 2026-10-27 (Asia/Saigon). Chỉ đọc metadata/tài liệu chính thức và probe TCP, không cài dependency, không scaffold, không SQL login hoặc đổi cấu hình server.

Revision3 Swing-first: evidence dưới đây được giữ từ lần kiểm tra trước, **không có runtime/build kiểm tra mới trong lượt sửa tài liệu**. Active MVP toolchain là JDK21/java.desktop/java.net.http, Maven, NetBeans, Boot backend/SQL. React/Node/Vite versions giữ làm thông tin Web extension, không điều kiện dựng/chạy MVP. Root Maven parent đề xuất2children backend/desktop-swing; chưa có POM thực tế.

## Mức độ xác minh

`LOCAL-RUN`: đã chạy lệnh phiên bản. `LOCAL-METADATA`: file/registry có thông tin, chưa chứng minh ứng dụng/service chạy. `DOC-COMPATIBLE`: nguồn chính thức mô tả engine/peer/support phù hợp; chưa build tích hợp. `PENDING-USER`: cần quyết định của người dùng. Không đồng nhất bốn trạng thái này.

## Môi trường thực tế

| Thành phần | Evidence đọc được | Trạng thái / giới hạn |
|---|---|---|
| Java project | `C:\Program Files\Java\jdk-21.0.12`; gọi trực tiếp java/javac cho kết quả 21.0.12 | LOCAL-RUN; Java21 giữ nguyên. Shim `...Oracle\Java\javapath` không cho output đáng tin, không dùng làm bằng chứng JDK Maven/IDE |
| Maven | JAR metadata3.10.0; chạy trực tiếp Maven --version với JAVA_HOME JDK21 trong process kiểm tra trả Maven3.10.0/Java21.0.12 | LOCAL-RUN sau sandbox retry có quyền đọc phù hợp; không đổi JAVA_HOME toàn máy. NetBeans Maven project run vẫn chưa kiểm tra |
| Node / npm | `node --version`: v24.22.0; `npm --version`: 11.19.1 | LOCAL-RUN; không npm install/view, không lockfile |
| NetBeans | `C:\Program Files\Apache NetBeans`; netbeans.conf trỏ userdir/cache 31 | LOCAL-METADATA; chưa mở About hoặc chạy Maven từ IDE. NB31 hỗ trợ chạy với JDK21 theo tài liệu Apache |
| SQL Server | Registry `MSSQLSERVER → MSSQL17.MSSQLSERVER`, Setup Version 17.0.1000.7, Standard Developer Edition | User đã chọn `(local)` default instance. Registry là metadata, chưa SQL login để xác minh @@VERSION |
| SQL TCP/TLS | TCP Enabled=1, IPAll TcpPort=1433, dynamic port trống; ForceEncryption=0 | Probe socket localhost1433 thành công với quyền đọc phù hợp sau lần sandbox deny; chứng minh TCP listener, chưa chứng minh SQL/TLS handshake. User chọn JDBC encryption mandatory, không suy từ ForceEncryption |
| SSMS | Có thư mục Microsoft SQL Server Management Studio 22; SQLCMD ODBC18 có trên PATH | LOCAL-METADATA; chưa kiểm tra About/build hoặc login |
| Windows | Registry DisplayVersion=25H2/Build26200, legacy ProductName Windows10Pro; Maven runtime báo OS windows11/amd64 | OS family Windows11 được runtime corroborate; edition/About chưa kiểm tra; CIM vẫn Access denied |

Service Control Manager/CIM không đọc được với quyền hiện tại. Không sửa PATH/JAVA_HOME/registry, không chạy service hay yêu cầu nâng quyền để vượt hạn chế. Các kiểm tra runtime còn thiếu là gate P1-preflight sau khi có xác nhận, không được báo pass trước.

## Backend baseline đã được user duyệt

User đã cho phép Boot4.1.2/JUnit6, sau đó đổi UI chính sang Swing và giữ React/Web ngoài MVP; source revision3 phản ánh cả hai quyết định. Java21/SQL2025/Maven/NetBeans và23module/243features không đổi. Backend DOC-COMPATIBLE:

| Dependency | Target version / rule |
|---|---|
| Spring Boot | 4.1.2, một BOM duy nhất |
| Java / Maven | Java21; Maven3.10.0 đang có >=3.6.3 upstream requirement, CLI/runtime còn gate |
| JUnit Jupiter / Mockito | 6.0.3 / 5.23.0 theo Boot BOM |
| Spring Security / Spring Data JPA | 7.1.2 / 4.1.2 theo BOM |
| Hibernate ORM | 7.4.13.Final theo BOM, không tự pin dialect ngoài BOM |
| Flyway core + sqlserver | Cùng12.4.0 theo BOM; thêm module SQL Server riêng |
| Microsoft JDBC | 13.4.0.jre11 theo BOM; Microsoft xác nhận Java21 support |
| springdoc-openapi | 3.2.0; upstream matrix Boot4.1 với springdoc3.1–3.2 |

Nguồn phiên bản: [Boot4.1 requirements](https://docs.spring.io/spring-boot/system-requirements.html), [Boot4.1 BOM](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html), [springdoc matrix](https://springdoc.org/). Không bật MCP/AI integration của springdoc chỉ vì thư viện cung cấp; chỉ chọn WebMVC/OpenAPI cần thiết.

### Các nhánh đã so sánh trước quyết định (history, không dùng cấu hình)

| Bộ tương thích | Phiên bản kiểm chứng từ nguồn chính thức | Kết luận |
|---|---|---|
| Giữ JUnit5 theo đặc tả | Spring Boot 3.5.16; Java21; Maven >=3.6.3; BOM: JUnit Jupiter5.12.2, Mockito5.17.0, Flyway core/sqlserver11.7.2, JDBC12.10.2.jre11 | DOC-COMPATIBLE theo BOM; **Boot3.5 đã hết OSS support**, không tự khóa cho dự án |
| Nhánh Boot còn duy trì | Boot4.1.2 + Java21; BOM dùng JUnit6.0.3 và Flyway12.4.0 | Chỉ là phương án so sánh; JUnit6 khác yêu cầu bắt buộc, chưa được phép thay |
| OpenAPI cho Boot3.5 | springdoc-openapi2.10.0; upstream matrix cho Boot3.5 với springdoc2.8–2.10 | Candidate khi người dùng giữ Boot3.5; không dùng starter2.x nếu chọn Boot4 |
| JDBC update candidate | Microsoft JDBC13.6.0.jre11 hỗ trợ Java21 | Không override BOM tự động; cần chọn cùng backend baseline và kiểm tra driver/SQL trong P1 |
| SQL Server migration | Flyway có SQL Server2025 trong support matrix hiện hành; `org.flywaydb:flyway-sqlserver` là dependency riêng | Matrix hiện hành không chứng minh riêng Flyway11.7.2 + SQL17 đã chạy. P1 phải migrate/validate trên SQL2025; nếu cần nâng Flyway thì core/sqlserver cùng version, ghi quyết định |

**Q-STACK — RESOLVED:** user chọn Boot4.1.2/JUnit6. Không dùng Boot3.5 hoặc springdoc2.x cho baseline mới, không downgrade để giữ JUnit5. Việc build/migrate/test vẫn chưa chạy, không đồng nhất DOC-COMPATIBLE với runtime Verified.

Nguồn: [Boot3.5 requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [Boot3.5 BOM](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html), [Spring thông báo hết OSS support](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/), [Boot4.1 BOM](https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html), [springdoc matrix](https://springdoc.org/v2/), [Microsoft JDBC requirements](https://learn.microsoft.com/en-us/sql/connect/jdbc/system-requirements-for-the-jdbc-driver), [Flyway SQL Server](https://documentation.red-gate.com/fd/sql-server-database-277579330.html), [Flyway support matrix](https://documentation.red-gate.com/fd/supported-databases-and-versions-143754067.html), [NetBeans31](https://netbeans.apache.org/front/main/download/nb31/).

## Web extension reference versions — không phải MVP gate

| Package/runtime | Candidate | Evidence/compatibility |
|---|---|---|
| Node/npm | 24.22.0 / 11.19.1 đang có | Node đáp ứng Vite engines; không đổi toolchain toàn máy |
| React + react-dom | 19.3.1 cùng phiên bản | Release chính thức14/10/2026, không dùng canary |
| Vite | 8.3.4 | Tag nguồn đã xác minh; engines `^20.19.0 || >=22.12.0`, Node24.22 đáp ứng |
| @vitejs/plugin-react | 6.1.2 | Tag `plugin-react@6.1.2` đã xác minh, peer Vite^8, Node engines phù hợp; không bật React Compiler experimental |
| TypeScript | 5.9.3 | Tag chính thức có version; lựa chọn ổn định đã kiểm tra metadata, không bắt dùng compiler native7 mới chỉ vì latest |
| Tailwind + @tailwindcss/vite | 4.3.3 cùng phiên bản | Tag plugin chấp nhận Vite8; không thêm PostCSS plugin thứ hai cho cùng việc |
| React Router | 7.18.4, declarative SPA mode | Tag chấp nhận React>=18, Node>=20; không chuyển sang framework SSR |

Nguồn: [React19.3.1 release](https://github.com/react/react/releases/tag/v19.3.1), [Vite release policy](https://vite.dev/releases), [Vite8.3.4 manifest](https://raw.githubusercontent.com/vitejs/vite/v8.3.4/packages/vite/package.json), [React plugin6.1.2 tag](https://raw.githubusercontent.com/vitejs/vite-plugin-react/plugin-react@6.1.2/packages/plugin-react/package.json), [TypeScript5.9.3 manifest](https://raw.githubusercontent.com/microsoft/TypeScript/v5.9.3/package.json), [Tailwind Vite4.3.3 manifest](https://raw.githubusercontent.com/tailwindlabs/tailwindcss/v4.3.3/packages/@tailwindcss-vite/package.json), [Router7.18.4 manifest](https://raw.githubusercontent.com/remix-run/react-router/react-router@7.18.4/packages/react-router/package.json).

Đây là reference set metadata-compatible đã khảo sát trước khi user chuyển Swing, chưa build/runtime pass. Không npm install/package-lock/React/Vite/type/peer gate trong P1/P2. Khi WEB-EXT được xác nhận sau Swing MVP, revalidate support/security/peers và khóa resolved versions; TanStack Query/RHF/Zod/Context/Fetch vẫn là lựa chọn Web lúc cần, không bê sang Swing hoặc thêm Zustand vô cớ.

## Swing target baseline

Java21 Swing/AWT từ java.desktop; HttpClient từ java.net.http, CookieManager từ JDK; không Swing framework/LAF ngoài hoặc WebView mặc định. JSON mapper version theo dependency management được kiểm tra P1, chỉ desktop runtime deps cần thiết, không Spring Boot/JPA/JDBC. ImageIO/codec JPEG/PNG delivery và backend WebP decoder/derivative cần test P2; không tuyên bố JDK đọc mọi format. JUnit6 presenter/core tests; Windows real desktop cho GUI/Robot test, không headless skip rồi claim pass. Native desktop run độc lập backend JVM và không phụ thuộc npm.

## Configuration contract (chỉ tài liệu, không tạo .env)

| Key dự kiến | Giá trị / nguồn quyết định | Gate |
|---|---|---|
| JAVA_HOME | Đường dẫn JDK21 đã thấy; user/IDE xác nhận lựa chọn | Xác minh Maven và NetBeans cùng compile release21 |
| APP_PROFILE | local hoặc packaged; không tự chọn security giảm cấp | P1 không có profile production đầy đủ |
| APP_HOST / APP_PORT | HTTP localhost:8080 được user duyệt; bind loopback | Không tự bind0.0.0.0 |
| COMICNET_API_BASE_URL (Swing) | http://localhost:8080/api/v1 | Chỉ API URL trong desktop config; không DB secret, desktop không chạyHTTP server5173 |
| FRONTEND_ORIGIN (future Web) | http://localhost:5173 là origin đã duyệt trước, reserved WEB-EXT | Native không giả Origin; CORS allowlist Web bật khi triển khai Web, không wildcard |
| DB_HOST / DB_INSTANCE / DB_PORT | SSMS `(local)` default instance; JDBC `localhost:1433` | TCP Enabled+registry1433+socket open đã kiểm tra; không cần instanceName khi dùng direct port |
| DB_NAME | ComicNetDev — user đã chọn | Chưa tạo DB; không dùng database khác hoặc master làm schema app |
| DB_AUTH_MODE | SQL Server Authentication — user đã chọn | Không chuyển Windows integrated; không cần native auth DLL |
| DB_USER / DB_PASSWORD | Tài khoản ứng dụng riêng chưa provision; secret chỉ local | `sa` chỉ là tài khoản hiện có do user thông báo, không dùng cho runtime hoặc lưu mật khẩu; role tối thiểu trên ComicNetDev |
| DB_ENCRYPT / DB_TRUST_SERVER_CERTIFICATE | true / true chỉ Development — user đã duyệt | Mã hóa nhưng không xác minh certificate; production true/false và CA/truststore/hostname hợp lệ |
| DB_TRUSTSTORE_PATH / password | Chưa cần dev khi user chấp nhận trustServerCertificate=true | Chứng chỉ hiện tại chưa biết tự ký/CA; production phải xác minh trước deploy |
| JWT_ISSUER / JWT_AUDIENCE / JWT_KEY_PATH / JWT_KID | TTL/session policy approved; key/issuer/audience cụ thể chốt trong P1/P2 | Không tạo key trong P0, không default secret; xem foundation.md |

**Q-DB, Q-TLS, Q-AUTH, Q-DBNAME — RESOLVED** theo câu trả lời trực tiếp trong chat. User duyệt access10 phút, refresh session absolute30 ngày, rotation/revocation/CSRF và HTTP localhost. Registry ForceEncryption=0 không mâu thuẫn với lựa chọn client Mandatory: JDBC encrypt=true vẫn yêu cầu mã hóa; chưa xác minh server certificate tự ký hay CA và chưa cần xác minh CA cho dev đã chọn bỏ validation. [Microsoft encryption semantics](https://learn.microsoft.com/en-us/sql/connect/jdbc/connecting-with-ssl-encryption).

Swing giữ session/cookie trong process memory, không persistence/remember-me MVP;30 ngày là trần server session. Không thay approved cookie protocol bằng raw refresh JSON tự phát. Production Swing→backend HTTPS validation và SQL TLS đúng như policy. JDBC encryption exception chỉ backend dev, không tạo trust-all TLS client.

Backend-only DB/JWT/SETUP env được đưa vào **Run config backend child**, không dùng shared launch config/env file có secrets cho Swing hoặc root process rồi coi bí mật không sang desktop. Desktop đọc API/UI config riêng, không import backend application-local.yml hoặc .env DB. Maven parent chỉ quản versions/plugins, không phát secret qua system properties sang mọi child.

Không thử SQL login bằng Windows principal hoặc sa; chưa có secret/tài khoản riêng, không yêu cầu password trong chat. Không thay đổi service/TCP/certificate. Phase1-preflight sau xác nhận dùng cấu hình đã chọn để kiểm tra SQL/TLS/login; provision ComicNetDev và SQL login riêng chỉ khi bắt đầu Phase1 được duyệt. Đã chọn tên DB không phải bằng chứng DB hoặc login đã tồn tại.

Registry query LoginMode không trả giá trị xác nhận được; không kết luận SQL Authentication/mixed mode đã hoạt động chỉ vì user có account sa. P1 preflight phải xác minh bằng kết nối/quyền thật trước provisioning, không tự đổi server authentication mode ở P0.
