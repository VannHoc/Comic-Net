# MVP API contract

Nguồn máy đọc: [openapi-mvp.json](openapi-mvp.json), OpenAPI 3.0.3. Đây là contract được đối chiếu với controller bằng test. Kết quả runtime và phần chưa nghiệm thu nằm tại [implementation status](../implementation-status.md). Mỗi operation có `operationId`, `x-story`, `x-permission`, `x-resource-scope`, `x-transaction`; schema DTO và status được khai báo. URL localhost là cấu hình Development đã được duyệt; tình trạng runtime được xác nhận riêng trong checkpoint.

Revision3: Swing là client MVP, React Web giữ ngoài MVP. Backend-only authorization/SQL/transaction, DTO không chứa Swing widget hoặc JPA entity.Contract hiện có 71 paths, 93 operations và 81 schemas: bổ sung getReaderChapter và deleteComic vào baseline setup/image-format. Số lượng route không thay thế acceptance test GUI. Server localhost8080 được user duyệt cho dev, không cần Vite5173.

## Quy ước chung

- Base `/api/v1`, JSON UTF-8; ID là string khớp `^[1-9][0-9]{0,18}$` và <= signed BIGINT max. Swing DTO giữ string IDs hoặc parse checked Long khi thật sự cần; Web tương lai không dùng JavaScript Number làm authoritative ID.
- Datetime UTC ISO-8601. `GET` không có side effect nghiệp vụ; pagination `page` zero-based, `size` mặc định 20, max 100. Sort enum allowlist, tie-breaker Id. Không hứa snapshot pagination khi dữ liệu đang thay đổi.
- Public API chỉ expose visible data; chapters list không có raw object key/signed URL. Manifest/preview response `Cache-Control: private, no-store`, URL expiresAt. Tránh query token trong application logs.
- Error `application/problem+json`: type/title/status/code/detail/instance/requestId/errors; không stack trace. 400 validation, 401 auth, 403 forbidden, 404 absent/private, 409 domain conflict, 412 stale ETag, 413 too large, 415 wrong media, 422 content invalid, 428 missing If-Match, 429 limit (Retry-After), 503 dependency unavailable. 500 cho lỗi không dự kiến, requestId để tra log.
- Mutable GET/POST/PATCH response trả ETag. PATCH/DELETE và transition trên version/chapter yêu cầu `If-Match` từ GET; thiếu 428, stale 412. JSON Patch không dùng; partial DTO explicit với field allowlist. PATCH không hỗ trợ null để xóa trừ schema ghi nullable; response full snapshot. GET ETag dựa đúng resource (version có token riêng với chapter).
- POST create/retry-sensitive upload có `Idempotency-Key` UUID; server scopes theo user+operation+parent, cùng key/payload trả cùng resource/status, khác payload 409. Bản ghi 24 giờ minimum; sau TTL request mới có thể tạo resource mới, trừ unique invariant vẫn còn. Upload key giữ theo job lâu hơn khi job chưa terminal.
- PUT follow/collection item là idempotent theo natural key; DELETE missing own follow/item trả 204. Concurrency update item yêu cầu ETag khi đã tồn tại; create dùng `If-None-Match: *`, conflict 412. Progress dùng revision body thay ETag.
- Default collection `SystemKey=LIBRARY` là nguồn ReadingStatus/IsFavorite. Custom collection chỉ nhóm truyện; PUT body `{}`, response không có status/favorite. PUT default item nhận status/favorite; create mặc định WANT_TO_READ/false. Cập nhật trạng thái qua default item, không giữ bản sao authoritative trong custom item. Team membership PUT cũng dùng If-Match cho update hoặc If-None-Match:* cho create; không đổi owner qua endpoint này.

## Nhóm operation MVP

| Group | Operations / hành vi | Quyền |
|---|---|---|
| Local setup | POST `/setup/administrator`, wizard Swing, US00 | Setup token+local enable+loopback+one-time, không public signup/RBAC bypass |
| Foundation | GET `/system/ping`; Actuator liveness/readiness ngoài business base | Public ping; health details protected |
| Auth | POST register, verify-email, resend-verification, login, refresh, logout, forgot-password, reset-password; GET csrf | Public có rate limit; refresh/logout cookie+CSRF |
| Profile | GET/PATCH `/me`; GET/PUT `/me/reading-preferences` | Self; email/role không editable qua profile |
| Catalog | GET `/comics`, `/comics/{comicId}`, `/comics/by-slug/{slug}`, `/comics/{comicId}/translations`, `/translations/{translationId}/chapters`; GET `/metadata`, `/search/comics` | Public filtered, tuổi/visibility |
| Content management | POST/PATCH comic, translation, volume, chapter; upload cover/banner; GET managed resources | Scoped Creator hoặc Admin; metadata lookup mutation Admin |
| Publishing | Create/get version; upload pages; get/retry job; reorder; remove binding; submit/review; publish/unpublish; preview | Team permissions; independent reviewer; Admin/Moderator theo action |
| Reader | GET `/chapters/{chapterId}/access`, `/chapters/{chapterId}/manifest` | Public request nhưng backend access check; signed URL sau allow |
| Progress/history | GET/PUT `/me/progress/{chapterId}`; GET/DELETE `/me/history` | Self; expectedRevision/mutationId/historyGeneration |
| Library | Collections CRUD, collection item PUT/DELETE, follows GET/PUT/DELETE, bookmarks CRUD | Self, parent scope validation |
| Admin | Users list/status/roles; teams/members setup; catalog visibility; jobs/audit read | Administrator; last-admin và audit rules |

Các endpoint chi tiết và DTO authoritative trong JSON, không tự thêm API thương mại/game/AI vào MVP. Publishing review API dùng permission riêng, không gộp toàn bộ thành `/admin`.

## Auth protocol

1. Register `{email,password,displayName}` → generic 202, email job. Resend/forgot luôn generic 202 bất kể tồn tại.
2. Verify/reset `{token,...}` → 204; invalid/expired trả 400 generic, không phân biệt email.
3. Login `{email,password,deviceLabel?}` sau GET csrf, gửi nonce cookie+X-CSRF-Token; validate Origin khi hiện diện, Swing không giả Origin.200 SessionTokens no-store+Set-Cookie refresh. Unverified/suspended/invalid credential generic401; Swing có resend dialog. Access10m/server session absolute30d giữ nguyên; runtime được kiểm chứng trong các integration test auth.
4. GET csrf → 200 `{csrfToken}` và set nonce cookie; endpoint same-origin/CORS allowlist, no-store.
5. Refresh/logout không nhận refreshToken trong body; cookie+`X-CSRF-Token`. Refresh trả access token và rotate cookie; logout revoke session, xóa cookie, trả 204. Replay refresh revoke family.

Swing dùng HttpClient/CookieManager per-session in-memory, nhận nonce body/cookie; không global cookie store/persistence/password Preferences. HTTP/decode/upload async ngoài EDT, error/requestId/ETag/Retry-After centralized; retry mutation chỉ khi idempotent. Native không chịu browser CORS nhưng không được bypass CSRF/token/RBAC. Browser Web extension mới cần credentials/CORS Origin allowlist; Origin có nhưng sai vẫn reject, thiếu Origin không là bằng chứng caller trusted. View generation/session guard bỏ response cũ sau logout; close best-effort logout không chặn EDT.

## Setup wizard và vận hành

setupAdministrator body email/password/displayName, header X-Setup-Token từ secret user nhập; không SQL password, roles hoặc ownerUserId. Server default disabled, chỉ local+explicit flag+loopback, reject browser Origin. Transaction BootstrapState/User/Admin+Reader/Audit; response201 User không JWT/cookie, đăng nhập chuẩn tiếp. Retry/lost response có thể initialized409/disabled404 nhưng không tạo Admin thứ hai; không public reset setup. Chi tiết threat/expiry ở security foundation. Chỉ triển khai P2 sau auth schema, không mock route này ở P1.

US16: user giữ backup/restore là ops runbook/verification, Swing hiển thị hướng dẫn versioned và jobs/audit thực; không có API SQL BACKUP/RESTORE hoặc desktop credential. Tất cả chức năng interactive MVP còn lại có Swing screen, không dependency React.

## Publishing protocol và limits đề xuất

Chapter create thuộc TranslationId; version create `{copyFromVersionId?}`. Tạo chapter đồng thời initial DRAFT version hoặc explicit create version? **Contract chọn explicit create:** Chapter POST trả Chapter; client POST versions sau đó. Một working copy/chapter; xung đột 409. ChapterPublished pointer NULL đến publish đầu tiên.

Upload `multipart/form-data`: `file` (một ZIP hoặc ảnh), `pageId?` chỉ khi thay đúng một page trong draft. Nếu không pageId, append trang mới; response 202 UploadJob. Archive sorting natural filename order và path normalized, sau đó user xác nhận thứ tự; không dùng filename làm identity. Không replace ngầm theo filename. Append nhiều upload serialize theo version; upload yêu cầu If-Match version, version ETag đổi khi job accepted, worker hoàn tất tiếp tục đổi version token. Client fetch lại version trước reorder/submit.

Proposed D07 limits: 100 MiB compressed/request, 500 MiB expanded archive, 500 pages/version, 20 MiB/image, max 40 megapixels/image; ZIP compression ratio tối đa 100:1; không nested ZIP, không path absolute/`..`/symlink; chỉ JPEG/PNG/WebP được decode thật cho MVP. AVIF output/input mở rộng Phase 3 sau kiểm tra codec. Limits server config, không hardcode trong UI. Reject 413/415/422 trước public exposure; failed job không publish và không mất version public cũ.

Reorder `{pageIds:[...]}` là permutation toàn bộ page bindings hiện tại, không duplicate/missing/external ID. Remove page chỉ draft và không physical delete ChapterPage. Submit yêu cầu >=1 page, assets READY và không active upload job; review snapshot immutable. Review `{decision:APPROVE|REJECT,reason?}`; REJECT cần reason, không self review. Publish `{versionId}` trên Chapter resource, check approved version cùng Chapter; If-Match Chapter và version state revalidated trong tx. Preview chỉ team/reviewer, không permanent content entitlement.

## Xóa mềm truyện — deleteComic

DELETE `/comics/{comicId}` yêu cầu đăng nhập, Creator sở hữu Comic hoặc Administrator và If-Match hiện tại. Trả 204; thiếu ETag 428, stale 412, không đúng scope/đã xóa 404. Cập nhật DeletedAt + Visibility=ARCHIVED và audit COMIC_SOFT_DELETED trong cùng transaction. Không cascade delete chapter/version/page/assets/progress/bookmark. API public/search/reader ngừng trả nội dung và cấp signed URL mới; URL đã cấp còn hiệu lực đến TTL như chính sách nền. Worker đang chờ xử lý sẽ fail an toàn khi parent đã xóa; trusted cleanup vẫn khóa được parent để kết thúc lease. Swing yêu cầu xác nhận trước DELETE. Không có hard delete hoặc restore nội dung trong thao tác này.

## Progress và history

GET progress trả `{chapterId,versionId,pageId,relativeOffset,completed,revision,historyGeneration,lastReadAt}` hoặc 404 PROGRESS_NOT_FOUND, GET history luôn trả generation. PUT thêm `expectedRevision`, `mutationId`, `historyGeneration`, không nhận `userId` hay thời gian authoritative. Version/page thuộc Chapter, offset [0,1]. Revision 0 chỉ tạo, retry key cùng payload no-op. Conflict 409 kèm current trong extension của Problem; client refetch và yêu cầu user chọn nếu có rewind conflict. Không tự chọn vị trí xa nhất.

Clear history DELETE tăng generation trong cùng tx xóa rows, trả 200 `{historyGeneration}`; previous requests không tạo lại history bằng retry. Bookmark còn giữ theo lựa chọn người dùng, không xóa theo history mặc định. ReadingPreference GET trả defaults và ETag kể cả chưa có row; PUT tạo/upsert qua đúng token.

## Response contracts và transactions

- ComicDetail trả metadata tác phẩm; bản dịch lấy qua listTranslations hoặc listManagedTranslations riêng, không có translations summary nhúng trong schema hiện tại. Rating/views null khi chưa có analytics/rating thật; UI ghi chưa có số liệu, không seed fake để coi là kết quả đo.
- ManagedChapter gồm RowVersion ETag và publishedVersionId; ManagedVersion có page bindings và per-page asset processing status; không leak bucket/raw object key.
- Manifest gồm chapterId,versionId,pages[{pageId,position,url,expiresAt,width,height}], navigation previous/next chỉ tới chapter visible cùng translation. Access response `{allowed,reason,requiresLogin}` không tiết lộ paid entitlement của user khác.
- Upload/job/status query đọc projection, không chạy lại job. Retry chỉ terminal FAILED + valid owner, giữ cùng job identity và pages dedup. Job info chỉ safe errorCode, không filesystem path.
- DB create/update/transition và audit là một transaction. Read manifest kiểm tra policy và published pointer ở nhất quán snapshot rồi ký URL; race hide sau cấp URL có TTL residual, recheck khi renew. Không hứa thu hồi tức thời object URL đã cấp.

## Ví dụ lỗi

```json
{
  "type": "urn:comic-net:problem:progress-conflict",
  "title": "Tiến độ đã thay đổi trên thiết bị khác",
  "status": 409,
  "code": "PROGRESS_CONFLICT",
  "detail": "Tải lại tiến độ trước khi ghi tiếp.",
  "instance": "/api/v1/me/progress/123",
  "requestId": "example-request-id",
  "errors": []
}
```

Ví dụ dùng ID minh họa, không giả làm dữ liệu runtime. Error `current` có thể đi kèm Progress schema như OpenAPI khai báo.

Revision3 giữ90 operation cũ, thêm setupAdministrator của US00 thành91; US18/15 share provisioning endpoints. GET managed metadata dùng chapter.preview+scope; hidden/private trả404. Đây là số liệu thiết kế revision3 trước triển khai; contract hiện tại theo JSON và checkpoint. [Security foundation](../security/foundation.md) giữ quy tắc phân quyền.

## Image delivery contract cho Swing

Phase2 bổ sung `getReaderChapter`: GET `/chapters/{chapterId}` trả metadata public (comic/translation ID, tên truyện/chương, số chương và accessPolicy) cho Reader/history/bookmark. Không có URL ảnh, vẫn lọc visibility/All-age. Cùng deleteComic (xóa mềm do user duyệt), tổng contract hiện tại 93 operations; phạm vi 23 module/243 chức năng không đổi.

getChapterManifest/previewChapterVersion nhận optional `displayFormat=JPEG|PNG`, mặc địnhJPEG. Mỗi ManifestPage có `mimeType` image/jpeg hoặc image/png, URL/expiry/size/position/PageId như trước; server trả Content-Type khớp. Missing/failed compatible derivative không publish/serve success giả. Cover/banner URLs ở ComicSummary/ComicDetail cũng phải compatible, nullable khi chưa có ảnh. Source upload WebP không mặc định client ImageIO đọc được; backend decode/derive kiểm chứng ở P2. Nhiều kích thước và WebP/AVIF output đầy đủ vẫn P3, không bị loại bỏ.

Client media request không Cookie/Authorization và không tự chuyển credential sang redirect/storage host; check MIME/decoded image, error UI rõ. Không dùng WebView/browser hoặc raw SQL/object keys làm workaround. Private decoded cache chỉ memory ở MVP, không coi là offline license/download P6. Web extension tái sử dụng default JPEG/PNG; khi hỗ trợ thêm format phải version/test contract riêng.

## Bổ sung Phase 3 — community và recommendation

OpenAPI hiện có 113 operations: giữ 93 operations MVP, thêm 20 operations Phase 3. Không đổi contract auth/ETag/ID đã có. Schema và từng route chính thức nằm trong openapi-mvp.json.

- Comic rating/review: GET /comics/{comic}/rating-summary, GET /comics/{comic}/reviews, GET /me/ratings/{comic}, PUT/DELETE /comics/{comic}/rating. Điểm 1–5, một bản hiện hành mỗi user/comic; tạo dùng If-None-Match: *, cập nhật/xóa dùng If-Match. Tổng hợp chỉ tính user ACTIVE đã xác minh; comic ẩn/xóa/giới hạn tuổi không được công khai.
- Community: /community/comments và /community/comments/{comment}; reply, PageId/relativeOffset, spoiler, tombstone khi xóa; /vote, /reports, /moderation. Tạo dùng Idempotency-Key, sửa/xóa/moderate dùng If-Match; bình luận bị ẩn không lộ body cho người đọc. /me/blocks và /me/blocks/{user} phục vụ block/unblock; /community/moderation/reports chỉ cho capability community.moderate. Chặn người dùng không được làm mất quyền moderator kiểm tra bình luận.
- Content luôn là văn bản thuần; HTML/control bị từ chối; giới hạn 10 comment/phút/user. Bộ từ ngữ cấm cấu hình qua comicnet.community.blocked-terms; mặc định chưa áp chính sách từ ngữ khi danh sách rỗng.
- GET /recommendations?size=20 dùng rating hợp lệ trong 30 ngày và fallback catalog công khai khi cold start. GET /comics/{comic}/similar?size=20 ưu tiên genre/tag/type, loại truyện nguồn và nội dung không công khai. Đây là recommendation cơ bản, không tuyên bố đã triển khai collaborative/personalization nâng cao.
