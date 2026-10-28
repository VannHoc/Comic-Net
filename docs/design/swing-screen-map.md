# Swing screen inventory và MVP coverage

Revision3, 2026-10-27. Bảng là baseline thiết kế desktop; form/code Swing đã có, nhưng các nghiệm thu GUI còn thiếu được ghi tại [biên bản Phase 2](../testing/phase-2-acceptance.md). Chưa có Figma desktop frame. Figma audit giữ evidence nguồn trong [ui-audit.md](ui-audit.md); không gắn nhãn các màn dưới đây là đã có thiết kế hoàn chỉnh.

## Desktop composition

Main JFrame + navigation bên trái (Catalog/Search/Library/Publishing/Admin theo quyền), header user/backend status, CardLayout content. Không sidebar bọc trực tiếp một trang React/WebView. JSplitPane ở editor/reader khi cần; BorderLayout/GridBagLayout/BoxLayout thay absolute positioning/null layout. JTable có pagination cho catalog/chapter/admin; JScrollPane reader, control bar riêng, dialogs cho confirm/conflict/errors. Không đặt business logic trong NetBeans generated .form event handlers.

Kích thước đề xuất1280×800, minimum1024×640; test DPI100/125/150%, system font scaling, resize/maximize, keyboard/focus/accessible labels. Màu/fonts source-backed kế thừa identity, native Swing component metrics khác CSS phải ghi là adaptation. Không áp nguyên px/mobile breakpoint hoặc fake phone status bar lên desktop.

## All19 stories → Swing views

| View ID | Story | Swing view / thao tác thật | API/evidence |
|---|---|---|---|
| SW00 | US-00 | Wizard first-admin: endpoint local, token secret input, email/name/password, success chuyển login; không lưu token | setupAdministrator; disabled/already-initialized/retry states |
| SW01 | US-01 | Register/verify token/resend dialogs; không bắt mở React link | register, verifyEmail, resendVerification |
| SW02 | US-02 | Login/logout, current session/account; in-memory cookie/CSRF/JWT, expiry/refresh state | getCsrf, login, refresh, logout, getMe |
| SW03 | US-03 | Forgot/reset với token input và validation | forgotPassword, resetPassword |
| SW04 | US-04 | Catalog JTable/cards + detail/translations/chapter list | listComics/getComic/listTranslations/listChapters |
| SW05 | US-05 | Comic/translation/volume/chapter editor, metadata selections; ownership scope | create/update comic/translation/volume, createMetadata |
| SW06 | US-06 | JFileChooser ảnh/ZIP, progress bar/job status/cancel/retry; cancel HTTP không đồng nghĩa server job đã hủy | uploadChapterPages/getUploadJob/retryUploadJob |
| SW07 | US-07 | Version/page list + move-up/down/reorder, replace/remove, draft preview image | createChapterVersion/getChapterVersion/reorder/remove/preview |
| SW08 | US-08 | Review queue, approve/reject reason, publish/unpublish/visibility confirm | listReviewQueue/reviewChapterVersion/publishChapter/unpublishChapter |
| SW09 | US-09 | Reader scroll/paged/RTL/LTR/fit, page selector/loading/retry, previous/next | access/manifest + signed JPEG/PNG GET |
| SW10 | US-10 | Auto progress/resume + explicit conflict dialog chọn reload/reapply; hai session/client kiểm chứng | get/putReadingProgress, getReadingHistory |
| SW11 | US-11 | Reading preferences dialog, guest memory defaults, user server settings | get/putReadingPreferences |
| SW12 | US-12 | Search/filter panel, loading/empty/page/sort, result detail | searchComics/getMetadata |
| SW13 | US-13 | Default/custom collections, status/favorite/follow | collections/items/follows CRUD |
| SW14 | US-14 | Bookmark list/edit/delete, history clear confirmation/generation state | bookmarks CRUD/clearReadingHistory |
| SW15 | US-15 | Admin users/roles/status/membership, last-admin denial message | listUsers/getManagedUser/setUserStatus/setUserRoles/setTeamMember |
| SW16 | US-16 | Health/jobs/audit + bundled versioned backup/restore runbook; report chưa có phải ghi chưa kiểm chứng | systemPing/listUploadJobs/listAuditLogs; operator procedure user đã đồng ý |
| SW17 | US-17 | Shell/navigation/states/keyboard/resize/DPI phủ mọi screen | TC-SW + Swing E2E; không business API riêng |
| SW18 | US-18 | Admin create team + owner/member role setup trước catalog | createTeam/getManagedTeam/getTeamMember/setTeamMember |

US16 không có nút SQL BACKUP/RESTORE giả; user đã đồng ý procedure vận hành và Swing xem hướng dẫn/nhật ký. Các API/business functionality khác trong bảng phải usable trực tiếp từ Swing. API list trong bảng là tóm tắt; tên operationId authoritative ở OpenAPI/traceability.

## States và execution rules

Mỗi view cần initial/loading/empty/success/API error;401 chuyển session-expired;403 permission feedback;409 progress/idempotency conflict;412 reload ETag;413 file-too-large;429 cooldown/Retry-After;503 backend unavailable. Không tự retry POST mutation không idempotency. Request generation/cancellation bỏ kết quả cũ khi đổi comic/version/user; presenter/app state giữ data, view giữ display.

HTTP/upload/image decode/scale ngoài EDT. SwingWorker.done/process hoặc SwingUtilities.invokeLater cập nhật Swing. Không Future.get/join hoặc ImageIO.read mạng trên EDT. Reader prefetch có giới hạn theo viewport, evict decoded images khi rời chapter; auto-save progress debounce + flush best effort không block close. Server revision authoritative, không lấy offset pixel tuyệt đối hoặc client clock làm source of truth.

Public media/private signed URL qua media HttpClient không cookies/Authorization, chỉ hosts/schemes được cấu hình; kiểm MIME+decode, không tự follow redirect sang URL lạ. Backend tạo ảnh display JPEG/PNG, gồm cover/banner và manifest preview. Không WebView/browser dependency hoặc ImageIO WebP/AVIF support giả định. Stream upload thay đọc toàn ZIP100MiB vào heap; secrets/token không logging.

## Figma adaptation và trạng thái

Home1:95 → SW04 catalog/dashboard bố cục desktop; Manga3:335 → catalog/search table/grid; Details5:269 → detail+chapter table+reader CTA. Giữ palette/typography sources đã đọc; mobile footer chuyển desktop nav. Không có desktop frame cho reader/auth/admin/editor; cần review wireframe trước code feature P2. Phase1 chỉ status shell/ping, không claim Home/full UI implemented.

React Web giữ inventory mobile375×812, responsive/components và tests ở WEB-EXT sau MVP, không tạo npm project/embedded browser trong Swing để thỏa coverage.
