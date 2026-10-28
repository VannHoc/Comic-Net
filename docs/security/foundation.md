# Security Foundation — Phase 0 contract cho Phase 1

Giữ Spring Security, JWT access/refresh và backend authorization theo `prj_app.md` đã cập nhật Boot4.1.2/JUnit6 theo user cho phép. Nội dung dưới đây là baseline thiết kế Phase 0; security code và key Development đã được triển khai ở P1/P2. Evidence hiện tại và phần còn thiếu tại [implementation status](../implementation-status.md). [Permission matrix](permission-matrix.md) mô tả quyền nghiệp vụ P2; [environment baseline](../deployment/environment-baseline.md) ghi cấu hình user đã duyệt và bằng chứng thực tế.

Revision3: **Swing là UI MVP**, React/browser là Web extension. Server authorization/TTL/SQL policy giữ nguyên; cookie/CORS/Origin behavior được phân biệt native và browser dưới đây. Không thêm desktop DB credential hoặc local SQL fallback.

## Phân ranh Phase 1 / Phase 2

| Area | Phase1 foundation | Phase2 business auth |
|---|---|---|
| Routes | GET `/api/v1/system/ping`, bare health/liveness/readiness; Swing shell gọi API | Auth/setup endpoints, profile, resource permission; chưa có React/static SPA |
| Default access | Chỉ allowlist public cụ thể; non-public API deny; không generated default password/form login/httpBasic | Bearer validation + active user/session + role/resource checks |
| Users | Chưa User/Session model và chưa login UI nghiệp vụ | US01–03 và US00 Swing setup wizard/US18 team provision |
| JWT | Boot4.1.2/Security7.1.2 config contract và wiring tests; không phát token giả | Issue/verify/revoke thật theo policy user đã duyệt; chưa có key không mở protected APIs |
| CSRF | Define cookie-auth chain, native no-Origin + valid CSRF tests; không tắt global | Cùng protocol trên Swing và browser tương lai; setup capability có exact matcher riêng |
| Testing | Filter-chain tests với fixture chỉ trong test scope, public/denied route checks | Login/rotation/replay/suspension/IDOR/concurrency với SQL thật |

Không tạo endpoint admin/setup/auth mock để demo Phase1. Test-only authentication không được báo thành login đã chạy. Khi không có business route, request unauthenticated vào non-public `/api/v1/**` vẫn bị firewall chặn401; fixture thiếu quyền403. Không mở actuator details cho Swing shell hoặc trao DB credential cho UI để kiểm tra readiness.

## Route/response policy

- Public P1: GET ping và health status UP/DOWN; readiness có thể DOWN/503 khi SQL không sẵn sàng, liveness không phụ thuộc SQL. Không trả host/user/DB error message.
- Deny public truy cập metrics/env/configprops/beans/loggers/audit/details. P1 chưa có operator identity thì các endpoint đó disabled hoặc inaccessible; P2 mới cấp permission ops/admin. OpenAPI/Swagger nếu bật trong local chỉ expose subset công khai có chủ đích; packaged profile mặc định không mở admin documentation ra Internet.
- Browser OPTIONS xử lý bởi CORS trước auth; allowlist khi Web extension được bật, không wildcard+credentials. MVP không cần CORS để Swing gọi REST; Swing HttpClient không bị browser CORS áp đặt và **không giả Origin5173**. Origin nếu có phải được kiểm tra; Origin không có không làm bỏ qua auth/permission/CSRF. Future Web expose ETag/Location/X-Request-ID/Retry-After khi bật CORS.
- Errors thống nhất `application/problem+json`, không HTML login redirect/stacktrace. 401 chưa authenticated;403 authenticated thiếu permission hoặc CSRF invalid;404 private resource khi đã biết user không được biết tồn tại;429 Retry-After. Request ID do server tạo/validate, không log dữ liệu bí mật.

## Chính sách auth user đã duyệt Q-AUTH

User đã đồng ý access10 phút trong memory; refresh random opaque single-use hash ở DB, session absolute30 ngày, rotation/revocation/CSRF; không localStorage. Không đổi tuổi session/remember-me âm thầm. Roles từ DB/principal; token claim không cho frontend sửa quyền. Suspend/reset/role change phải invalidate session hoặc authVersion trong P2; access filter kiểm tra trạng thái session, không chỉ verify chữ ký.

Thiết kế kỹ thuật JWT: RS256 với key RSA>=2048, `kid`, issuer/audience cố định từ config; allowlist thuật toán, kiểm tra exp/nbf/iat/jti/sub/sid, clock skew tối đa30s. Không tự viết crypto; dùng Spring Security JOSE/Nimbus qua starter theo backend version đã duyệt. Private key ngoài repo qua path/secret reference, public key tương ứng; thiếu/không khớp key fail closed khi auth được bật. Key rotation giữ public key cũ đến token cũ hết TTL, không đưa private key vào frontend/Swagger/log. Chưa tạo khóa hoặc authorization server riêng.

Password technical candidate: DelegatingPasswordEncoder với PBKDF2-HMAC-SHA256 (random salt, versioned encoder id); không bcrypt mặc định khi policy cho phép128 ký tự mà chưa giải quyết giới hạn byte. Work factor benchmark ở P1 được phép trước khi có users; hash one-way, raw password không trim/cắt/ghi log. Proposed password12–128 ký tự và TTL verify/reset cần user review trước P2; không thay đổi quy tắc tài khoản đã duyệt. [Spring password storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html).

## Client profiles sau thay đổi Swing-first

| Profile | Refresh cookie | CSRF cookie/header | Constraint |
|---|---|---|---|
| Swing MVP HTTP localhost | Per-session Java CookieManager/CookieStore trong memory; cookie backend vẫn host-only/HttpOnly/path /api/v1/auth, Secure=false chỉ local | GET csrf → nonce body/cookie, header X-CSRF-Token, cookie từ cùng store; login/refresh/logout đều validate | Không global CookieHandler, không serialize cookie/secret; API URL localhost8080, không browser5173 prerequisite |
| Web extension HTTP local tương lai | `comic_refresh`; HttpOnly; Secure=false chỉ profile local; SameSite=Lax; Path=/api/v1/auth; host-only | `comic_csrf`; không HttpOnly; Path=/; SameSite=Lax; Secure=false local; X-CSRF-Token | Origin5173/backend8080 là cấu hình đã duyệt trước, chỉ dùng khi Web được triển khai; không MVP prerequisite |
| HTTPS local/packaged tương lai nếu cần | Refresh Secure=true, HttpOnly, host-only, path như trên | CSRF Secure=true; cùng exact header name | Chưa cần HTTPS local; không suy ra profile này đã provision certificate |
| Production tương lai | HTTPS bắt buộc, Secure cookies; deployment topology review riêng | Same-origin ưu tiên; cross-site cần thiết kế lại SameSite/CSRF | Không suy ra production từ local; không sử dụng profile local |

CSRF dùng Spring Security CookieCsrfTokenRepository, name/header thống nhất; GET `/auth/csrf` trả nonce/body và no-store. Từ P2 login/refresh/logout luôn cần đúng cookie nonce + X-CSRF-Token. **Nếu Origin hiện diện** phải khớp allowlist, kể cả bearer request; nếu vắng Origin (native client) vẫn kiểm tra CSRF, login credential, cookie/session thật và resource permission. Không trust X-Client-Type/User-Agent làm bypass, không require client gửi Origin giả để qua filter. Origin=null không tự allow. Browser origin5173 là cấu hình Web tương lai được duyệt trước đây, không CORS allow rộng khi Web chưa bật.

Register/reset/verify/resend/forgot không dùng cookie làm auth có exact route/rate/token rules, reject disallowed Origin khi có; không blanket exception /auth/**. Bearer-only APIs chỉ exempt CSRF khi không chấp nhận ambient cookie credential. [Spring CSRF reference](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html).

CSRF reload sau login/logout, Swing single-flight refresh; revoked family không recover bằng old cookie. Native client không dựa vào HttpOnly/SameSite như ranh giới chống người kiểm soát JVM đọc token: chúng là browser flags, backend vẫn kiểm tra credential/state. Memory-only baseline không auto-login sau restart; window close xóa state, best-effort logout không được chặn EDT. Mất mạng thì backend session chưa chắc đã revoke, chỉ expiry/admin revoke theo thực tế. Asset/media HttpClient không Cookie/Authorization, không follow redirect credential. Redact Cookie/Authorization/X-Setup-Token/reset token/signed URL query.

## US-00 local one-time setup (đã được user đồng ý)

POST `/api/v1/setup/administrator` chỉ có khi profile local + SETUP_ENABLED=true, actual remote peer loopback, setup token backend từ env có entropy tối thiểu256bit; không trust X-Forwarded-For/Origin/User-Agent. Default disabled; production không expose. Token nhập bằng secret field ở Swing wizard, không DB/admin SQL password, không store file/Preferences/log. Proposed TTL15 phút kể từ enable/start; thông số review trước P2, không tạo secret trong P0.

Exact route dùng X-Setup-Token capability, không cookie authentication nên exact CSRF exemption được mô tả riêng, không mở rộng sang login/refresh/logout. Reject browser Origin, sai/missing token401, disabled/nonlocal404, initialized409 hoặc404 khi đã tắt; rate limit. Không cấp Admin bằng register hoặc client role field. Body chỉ email/password/displayName.

Transaction lock BootstrapState singleton→check chưa initialized/chưa có Admin→create verified first User+Admin role→AuditLog system actor→mark InitializedAt→commit. Unique/lock chống hai requests; token chỉ consume khi commit. Không tự reset state khi Admin suspend; không public reset setup. Request mất response sau commit dẫn login hoặc initialized message, không tạo thêm Admin; không hứa retry trả201 giống lần đầu. Response201 User, không access token/cookie; wizard chuyển login chuẩn. Admin thứ hai/Moderator được đăng ký/xác minh/gán role qua GUI chuẩn, không tự cấp role trên client.

## SQL/environment security

User chọn SQL Authentication, default `(local)` instance, ComicNetDev, JDBC `encrypt=true;trustServerCertificate=true` chỉ Development. Port1433 có registry/TCP listener evidence; SQL login/TLS handshake chưa chạy. Application login riêng, không dùng sa; tài khoản/mật khẩu mới chưa được tạo. Hibernate `ddl-auto=validate` khi schema có, không update/create-drop trên DB thật. P1 có migration smoke độc lập, không tạo toàn bộ ERD. Migration privilege chỉ ComicNetDev, runtime DML tối thiểu. Secrets qua local env/secret reference; `.env.example` sau này chỉ placeholder; không lấy secrets hiện có của máy. Production bắt buộc HTTPS và xác minh certificate SQL (`trustServerCertificate=false`), không kế thừa dev exception.

## Acceptance checks trước nghiệm thu Phase1

SEC-P1-01: public ping200 và không rò config; SEC-P1-02: non-public unauth401, fixture thiếu quyền403; SEC-P1-03: Origin không được phép không nhận CORS allowance; SEC-P1-04: CSRF thiếu/sai trên cookie-auth test fixture bị403; SEC-P1-05: actuator sensitive không public; SEC-P1-06: missing auth key fail closed, no default-password login; SEC-P1-07: error/requestId redaction; SEC-P1-08: cookie flags đúng profile đã được user chọn; SEC-P1-09: SQL migration/runtime secret không ở tracked files. Chưa chạy các kiểm tra này vì Phase1 chưa bắt đầu.

Revision3 bổ sung SEC-P1-10: native no-Origin với credential/CSRF đúng qua test fixture, thiếu CSRF vẫn403; giả X-Client-Type không bypass; media client không bearer/cookie. Bootstrap thực tế kiểm thử ở P2: disabled/default, token expiry, nonloopback, wrong token, parallel requests/commit rollback/audit và login sau setup. Phase1 không có endpoint setup thật hoặc giả.
