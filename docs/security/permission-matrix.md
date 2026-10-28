# Permission matrix và security baseline

Proposed baseline. Mọi quyết định quyền do backend kiểm tra; frontend route guards chỉ phục vụ UX. Deny by default. Role union không vượt qua account suspension, content visibility, age policy hoặc resource scope. Guest là trạng thái chưa đăng nhập, không lưu như role của User.

Revision2: [Security Foundation](foundation.md) xác định phần nào có ở P1 và P2. User đã duyệt TTL/session, HTTP localhost, SQL auth và dev certificate exception; không đồng nghĩa khóa/key/account hoặc auth runtime đã có. Publishing metadata **read-only** dùng `chapter.preview`; `catalog.manage` chỉ dùng cho mutation/owner metadata, không bắt Translator/Proofreader có quyền sửa catalog để preview.

Revision3: cùng matrix áp dụng backend cho Swing trước, Web sau. Không có role đặc biệt desktop vượt quyền; control Swing hide/disable không là authorization. US00 setup là capability local một lần đã được user duyệt, không quyền Guest công khai hoặc role thứ10. User đã giữ backup/restore là ops procedure; Swing có runbook/log viewer, không runtime SQL credential.

## Ký hiệu và role scope

- `P`: dữ liệu public, phù hợp visibility/age. `O`: chỉ dữ liệu của chính user.
- `T`: chỉ team có membership active và permission tương ứng trên bản dịch/chapter. Có role Creator toàn hệ thống chưa đủ để thao tác mọi team.
- `M`: kiểm duyệt trong phạm vi được giao. `A`: quyền toàn hệ thống, phải audit. `—`: không được phép theo role đó.
- Reader là nhóm quyền tài khoản xác minh cơ bản; tài khoản có role khác vẫn phải được cấp quyền Reader hoặc permission tương đương. Không hardcode phép kế thừa tùy tiện.
- Translator, Editor, Proofreader, Uploader là role theo team. Một người có nhiều role; creator team owner là membership có quyền manage team, không thêm actor thứ mười.

## Ma trận chín actor

| Action / permission | Guest | Reader | Creator | Translator | Editor | Proofreader | Uploader | Moderator | Administrator |
|---|---|---|---|---|---|---|---|---|---|
| Xem/tìm catalog public `catalog.read` | P | P | P | P | P | P | P | P | P |
| Đọc chapter `chapter.read` | P* | P* | P* | P* | P* | P* | P* | P* | P* |
| Profile/preferences/session của mình `profile.self` | — | O | O | O | O | O | O | O | O |
| Library/progress/history/bookmark `library.self` | — | O | O | O | O | O | O | O | O |
| Comment/rating/report `community.write` (P3) | — | O | O | O | O | O | O | O | O |
| Ví/mua/VIP `wallet.self` (P4) | — | O | O | O | O | O | O | O | O |
| Game/reward/offline (P5/6) | — | O* | O* | O* | O* | O* | O* | O* | O* |
| Tạo comic `catalog.create` | — | — | T | — | — | — | — | — | A |
| Sửa metadata comic/bản dịch `catalog.manage` | — | — | T | — | — | — | — | — | A |
| Xóa mềm comic `catalog.manage` | — | — | O | — | — | — | — | — | A |
| Tạo volume/chapter/version `chapter.draft` | — | — | T | T | T | — | T | — | A |
| Gán/chỉnh nội dung bản dịch `chapter.translate` | — | — | T | T | — | — | — | — | A |
| Upload/reorder/replace page `chapter.upload` | — | — | T | — | T | — | T | — | A |
| Preview draft `chapter.preview` | — | — | T | T | T | T | T | M | A |
| Ghi kết quả proofreading `chapter.proofread` (P5) | — | — | T | — | T | T | — | — | A |
| Submit review `chapter.submit` | — | — | T | — | T | — | T | — | A |
| Approve/reject `chapter.review` | — | — | — | — | — | — | — | M | A |
| Publish version đã approved `chapter.publish` | — | — | T | — | — | — | — | M | A |
| Unpublish/hide nội dung `content.hide` | — | — | — | — | — | — | — | M | A |
| Tạo/quản lý team/mời thành viên `team.manage` | — | — | T** | — | — | — | — | — | A |
| Xem analytics doanh thu team (P5) | — | — | T** | — | — | — | — | — | A |
| Yêu cầu payout (P5) | — | — | T** | — | — | — | — | — | A*** |
| Kiểm duyệt comment/report `moderation.manage` | — | — | — | — | — | — | — | M | A |
| Suspend/restore account `users.suspend` | — | — | — | — | — | — | — | — | A |
| Gán system roles `roles.manage` | — | — | — | — | — | — | — | — | A |
| Price/plan/voucher config (P4) | — | — | — | — | — | — | — | — | A |
| Reconcile/refund/payout approve (P4/5) | — | — | — | — | — | — | — | — | A*** |
| Audit/system settings/ops health | — | — | — | — | — | — | — | — | A |
| Export/delete own data (P6/7) | — | O | O | O | O | O | O | O | O |

`*` Quyền đọc/game/offline vẫn phải qua policy/grant/age/expiry; Moderator/Admin không tự động sở hữu quyền mua. Preview có watermark/context audit riêng, không phải public read bypass. Guest MVP chỉ đọc public All-age FREE; nội dung 13+/16+/18+ chờ chính sách tuổi được review trước bật.

`**` Chỉ team owner hoặc membership có permission cụ thể; Creator thông thường không tự ý sửa tỷ lệ/payout nếu chưa có permission.

`***` Không phê duyệt payout/refund do chính mình yêu cầu. Admin có thể tạo yêu cầu hỗ trợ nhưng không tự approve. Không cung cấp endpoint sửa balance trực tiếp; adjustment bắt buộc ledger và lý do.

## Quy tắc resource-level bắt buộc

1. Route luôn load resource từ ID → kiểm tra parent ownership/team/status → thực thi. Không tin `ownerUserId`, `teamId`, `roles`, `balance` từ request.
2. Comic có thể có nhiều bản dịch/team. `catalog.manage` trên comic yêu cầu chủ sở hữu Comic; quản lý bản dịch chỉ yêu cầu team chủ bản dịch. Được quyền một bản dịch không có nghĩa được sửa metadata tác phẩm hoặc bản dịch của nhóm khác.
3. Chapter thuộc một Translation. Version/Page/UploadJob phải cùng chapter/team; request trộn ID hợp lệ khác team bị từ chối. Reviewer không được duyệt version có `createdBy/submittedBy = self`; admin khởi tạo thứ hai/reviewer riêng cho demo, không âm thầm bỏ rule.
4. Approved version không sửa trực tiếp. Thay trang tạo draft mới. Publish chỉ thực hiện khi approved snapshot còn nguyên và assets READY.
5. Email chưa verify chỉ được verify/resend/logout và endpoint auth public; không cấp normal access token trước verify. Suspended user bị chặn mọi protected request, refresh và worker action dưới danh tính user đó. Access JWT kiểm tra session/user status tại backend để suspension có hiệu lực ngay, không chờ JWT hết hạn.
6. Trả 404 thay vì 403 cho private resource không được phép biết tồn tại. 403 dùng cho operation bị cấm trên resource đã được phép nhìn thấy. Tài nguyên bị ẩn không rò qua search/ranking/URL ký.
7. Không đọc ví/history/profile của user khác bằng cách truyền ID; đường dẫn `/me` resolve từ authentication principal. Admin xem dữ liệu nhạy cảm qua API riêng, projection hạn chế và audit.
8. User không đổi role hoặc team owner qua profile PATCH. Không xóa/khóa last active administrator. Team owner transfer có workflow riêng Phase 5.

## Token, validation và threats

- D06 đã được user duyệt: access JWT10 phút trong memory; refresh opaque random hash ở DB, cookie HttpOnly/SameSite=Lax/path `/api/v1/auth`; session absolute30 ngày. Development HTTP localhost tắt Secure chỉ profile local; production HTTPS Secure=true. Không localStorage cho tokens.
- Refresh single-use rotation, reuse detection revoke token family/session. DB transaction serialize cùng token để hai refresh không thành công song song. Frontend single-flight refresh; nếu response rotation mất mạng, yêu cầu login lại thay vì chấp nhận replay.
- Login/refresh/logout P2: cookie nonce + X-CSRF-Token luôn được server validate. Native Swing không cần/không giả Origin; Origin khi hiện diện phải allowlist, thiếu Origin không bỏ auth/CSRF. Dùng CookieCsrfTokenRepository; nonce GET /auth/csrf no-store, reload sau login/logout. Cookie/refresh trong Swing memory-only; Web JS không đọc refresh cookie; không CORS wildcard+credentials.
- Access bearer APIs validate token/session/resource. Password technical candidate DelegatingPasswordEncoder/PBKDF2-HMAC-SHA256 trong foundation.md, cost đo ở Phase1 sau xác nhận; không crypto tự viết hoặc cắt password theo byte. Verification/reset token hash, one-time, TTL, response không tiết lộ email tồn tại. Rate limits riêng login/reset/verify/upload.
- Password proposed 12–128 ký tự, không cắt ngầm; email normalized unique; HTTPS production. Log không có secret/token/password/raw URL ký.
- Upload chống path traversal/ZIP slip, ZIP bomb, file giả MIME, image decompression bomb, oversized dimensions, nested archives; file name không thành object key do client quyết định. Limits cụ thể trong API guide và review trước Phase 2.
- SQL parameter binding, output encoding/XSS sanitation cho rich text, CSP theo asset policy, least-privilege SQL login, secrets env. Audit lưu actor/action/target/result/requestId, không dump toàn request.

## MVP permissions → API

API contract dùng `x-permission`, `x-resource-scope`, `x-transaction`. Admin endpoints `/admin/**` cần role/permission admin tương ứng; moderation chapter dùng `/publishing/**` với reviewer permission, không yêu cầu Moderator trở thành Admin. Tất cả bearer operation phải có 401; action resource-private có 403/404; versioned mutation có 412. Auth public operation có rate limit và generic error.

Test matrix: mỗi role × allowed/denied; same-team vs other-team; same-owner vs other-owner; mixed IDs; disabled membership; suspended session; last admin; self review. Quyền Phase 4–6 là thiết kế phạm vi, chưa phải API MVP đã có.
