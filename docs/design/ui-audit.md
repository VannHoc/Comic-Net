# UI audit và screen-to-module mapping

Revision3: đây là audit **nguồn mobile Figma**, dùng làm tham khảo thiết kế Swing desktop; không coi viewport/mobile navigation là đặc tả desktop. [Swing screen map](swing-screen-map.md) là inventory mới phủ19stories MVP. React Web giữ hướng phát triển sau MVP, không là UI chính hiện tại. Không có lần ghi/sửa Figma hoặc desktop frame mới trong lượt cập nhật tài liệu này.

Nguồn: https://www.figma.com/design/mfsGdBIMuvjeMnsgAd6fgd/Web-Comic-App-Design--Community-?node-id=101-277

Evidence: lần kiểm tra trước trong cùng chat đã gọi metadata top-level pages, toàn `0:1`, chi tiết `5:269`, screenshot của `1:95`, `3:335`, `5:269`. Lần hoàn thiện Phase 0 ngày 2026-10-27 đã gọi `get_variable_defs` cho cả ba frame, tiếp tục session `53ae013d-72ac-46ed-980f-eb4ed0cd5512`. Không sửa file Figma, không tạo thiết kế thay thế.

## Inventory xác minh

| Page/frame | Thực tế | Component/cấu trúc | Module/UI data |
|---|---|---|---|
| Page 1 `0:1` | File trả về một page | Ba màn nghiệp vụ + Thumbnail | Không có reader/admin/auth screen riêng trong page đã kiểm tra |
| Home `1:95` | 375×812 | Avatar/greeting, search, trending cover cards, tabs, comic rows, bottom nav | M01 profile; M02 catalog; M06 search; M07/10 trending/gợi ý |
| Manga `3:335` | 375×812 | Header, category dropdown, list rows, bottom nav | M02/M06; title/cover/author/chapter count động |
| Manga details `5:269` | 375×812 | Hero, rating, description, chapter rows, lock/read icons, favorite/read CTA | M02/03/04/08/10/20 |
| Thumbnail `101:274` | 1600×1200 | Mockup `101:275`, `101:277`, `101:279` | Marketing/preview, không tính ba màn nghiệp vụ mới |

Component instances quan sát qua metadata: `Search`, `Footer/on`, `Footer/off`, `tab/on`, `tab/off`, `button/primary`, `button/secondary`, `icon/Favorites/off`, `icon/search`, `icon/arrow/down`, status/navigation bar. Có instances nhưng chưa xác minh toàn bộ main components/variants và nguồn library; không tự coi danh sách này là component library hoàn chỉnh.

## Tokens/styles đã lấy từ tool

| Tên nguồn Figma | Giá trị xác minh | Lưu ý mapping |
|---|---|---|
| white | #FFFFFF | Surface |
| primary | #61BFAD | Màu nhấn chính ở cả ba frame |
| Primary | #54BAB9 | Có ở Home/Manga; tên khác chữ hoa, không tự gộp thành cùng token |
| text 1 | #1F1F39 | Text mạnh |
| text 2 | #858597 | Text phụ |
| text 3 / text/body 3 | #B8B8D2 | Text nhạt, cần contrast check |
| text 4 | #F4F3FD | Surface nhạt, tên gốc không phản ánh semantic hoàn toàn |
| tertiary | #EAEAFF | Màu phụ |
| secondary | #FFEBF0 | Favorite/status surface |
| orange | #FF6905 | Rating/chapter accent |
| Gray 5 | #E0E0E0 | Home/Manga |
| Paragraph/4 | Poppins Medium, 11px, weight 500, line height 14px, letter spacing 0 | Một text style đã xác minh, chưa phải toàn typography scale |
| Card shadow | #B8B8D233, offset (0,8), blur/radius 12, spread -4 | Home |
| drop shadow | #00000026, offset (0,8), blur/radius 24, spread 0 | Home/Manga |

Spacing chứng cứ từ metadata: viewport width 375, margins/list x≈20/21, card width 335/height96, cover thumbnail 68×68, search 335×48; đây là measurements của node, chưa suy ra spacing scale toàn hệ thống. Revision2 đã đọc typography/radius trực tiếp như bảng dưới, không đo bằng mắt để ghi thành token đã xác minh.

## Revision 2 — normalized source-backed primitives

Ngày 2026-10-27 dùng `figma-use` read-only trên ba frame. Lần trả toàn bộ tree vượt output limit; lần truy vấn compact tiếp theo thành công, không có canvas mutation. `getStyledTextSegments` đọc cả mixed-style text: 91 text nodes tổng, 75 nodes có nội dung, 16 tổ hợp typography. Bao gồm layer ẩn/kế thừa; không coi mọi tổ hợp là style UI public. Đã xác minh **cả Poppins và Inter**, không thay tất cả bằng Poppins.

| Semantic alias dự kiến (tài liệu, chưa CSS/code) | Giá trị nguồn | Node/property chứng minh |
|---|---|---|
| color.action.primary | #61BFAD | CTA `5:341` fill, tab active `2:554` fill |
| color.artwork.teal | #54BAB9 | Illustration Vector `1:414`/`3:426`; một số navigation glyph kế thừa cũng dùng màu này |
| color.surface.default / search | #FFFFFF / #F4F3FD | Card `2:564` / Search `2:503` |
| color.surface.favorite | #FFEBF0 | Secondary button `5:343` |
| color.text.strong / secondary / muted | #1F1F39 / #858597 / #B8B8D2 | Style definitions + các text layer đã kiểm tra |
| radius.card / search | 12px / 12px | `2:564` / `2:503` |
| radius.cover.thumbnail | 8px | `2:565` 68×68 |
| radius.button.primary / secondary | 10px / 8px | `5:341` / `5:343`; không ép cùng radius |
| radius.tab.active | 20px | `2:554` 73×28; capsule render theo bounds |
| elevation.card | offset0,8; blur12; spread-4; #B8B8D2 alpha20% | `2:564` effects |

Không gộp hai màu `primary`/`Primary` theo tên. Semantic alias là tên triển khai dự kiến để tránh collision; giá trị và phạm vi source giữ nguyên, không chỉnh source Figma. Icon có fill khác phải dùng asset gốc, không tự recolor tất cả bằng action.primary.

| Usage đã quan sát | Exact typography | Ví dụ node |
|---|---|---|
| Header greeting name | Poppins SemiBold22, line-height AUTO, tracking0 | `1:398`, `3:408` |
| Comic detail title/rating | Poppins SemiBold20, AUTO, tracking0 | `5:359`, `5:361` |
| Comic row title | Poppins Medium14, AUTO, tracking0 | `2:566`, `2:579` |
| Comic row author | Poppins Regular12, AUTO, tracking0 | `2:567`, `2:580` |
| Search/tab labels | Poppins Regular14, line-height20px, tracking0 | `I2:503;44:1901`, `I2:554;53:222` |
| Bottom navigation | Poppins Medium11, line-height14px, tracking0px | `2:521`, `I2:524;262:1452` |
| Primary CTA | Poppins Medium16, AUTO, tracking0 | `I5:341;7:329` |
| Chapter number | Poppins Medium24, AUTO, tracking0 | `5:473`, `5:487` |
| Small chip | Poppins Regular10, AUTO, tracking0 | `2:574`, `2:587` |
| Chapter metadata | Poppins Medium12, line-height20px, tracking0 | `5:477`, `5:478` |
| Trending title | Inter Semi Bold14, AUTO, tracking0px, variable wght600/slnt0 | `2:456`, `2:475` |
| Trending author | Inter Regular10, AUTO, tracking0px, wght400/slnt0 | `2:457`, `2:471` |

Các tổ hợp khác được đọc: Poppins Regular14/AUTO cho greeting, Poppins SemiBold16/AUTO trên giá kế thừa, Poppins ExtraBold20/AUTO trong hero layer cũ, Inter Medium10/AUTO trong hero metadata. Chỉ đưa vào component thật khi layer nguồn được dùng. AUTO không chuyển thành giá trị pixel tự đoán; font files và browser metrics phải xác minh trước pixel comparison.

## Phase 1 Swing shell được giới hạn

Shell là JFrame+layout desktop/navigation tối thiểu, async ping thật/status/retry/backend-down; không React/WebView/Node. Status ping là màn kỹ thuật mới có nhãn, không tự nhận có Figma frame. Giữ colors/fonts khi asset hợp lệ, áp native controls/resizable/DPI; source CSS px không tự trở thành Swing font points/bounds. Không dựng Home trending/rating/locked chapter bằng data fake, không implement Reader/Admin/Auth nghiệp vụ ở P1. Native hover/focus/disabled styles cần review, không gọi là variants Figma đã có.

Font delivery/licensing và contrast là gate trước render UI identity-sensitive; nếu native system font hoặc tăng size11px của nguồn để đọc tốt trên desktop thì ghi adaptation và review, không tự claim dùng đúng Poppins/Inter khi chưa load được. Shell config/build/API có thể kiểm chứng độc lập pixel matching. Không yêu cầu desktop feature screens P3–6 hoặc Maven/Vite/React để nghiệm thu foundation.

## Missing screens và states

| ID | Màn cần bổ sung | Module / story | Phase |
|---|---|---|---|
| UI01 | Register/verify/login/forgot/reset | M01 / US01–03 | 2 |
| UI02 | Search results/filter/empty | M06 / US12 | 2 |
| UI03 | Reader + loading/error/expired access + settings | M04/20 / US09–11 | 2 |
| UI04 | Library/collections/follow/history/bookmark | M08 / US13–14 | 2 |
| UI05 | Comic/translation/volume/chapter editor | M02/03/14 / US05 | 2 |
| UI06 | Upload progress/failure/retry/reorder/version preview | M03/05 / US06–07 | 2 |
| UI07 | Review queue/approve/reject/publish/hide | M03/15 / US08 | 2 |
| UI08 | Admin users/roles/team seed/audit/jobs | M15/21 / US15–16 | 2 |
| UI09 | Comment/rating/report/moderation, notifications/ranking | M07/09/10/13 | 3 |
| UI10 | Wallet/top-up/purchase/VIP/transactions/reconcile | M11/12/20/23 | 4 |
| UI11 | Team/invites/tasks/dashboard/revenue/payout | M14/23 | 5 |
| UI12 | EXP/achievement/check-in/game/cards/inventory | M16/17 | 5 |
| UI13 | Offline/download/quota; AI review/advanced analytics | M18/19/07 | 6 |
| UI14 | Security/devices/MFA/SSO/privacy/export/delete | M01/22 | 3,6–7 |

Danh sách trên giữ nhóm yêu cầu màn hình, delivery MVP là Swing views SW00..18, không React pages. Mỗi màn cần normal/loading/empty/error/401/403/backend-unavailable/cancel/resize-DPI; full offline Reader giữ P6. Rating/view chưa có nguồn thật hiển thị “—/Chưa có dữ liệu”, không biến số mẫu Figma thành nghiệp vụ.

## Navigation và responsive proposed

Swing: sidebar/menu Catalog/Search/Library/Publishing/Admin và header account/status, content chuyển CardLayout; detail/chapter table → reader panel/window. Luồng Home/list/search/detail vẫn giữ nghĩa; không bắt giữ bottom nav mobile. Reader previous/next trong cùng translation. Prototype links và desktop wireframe chưa được duyệt, không giả prototype Figma đã có luồng mới.

MVP Swing kiểm tra desktop proposed1280×800/min1024×640, Windows DPI100/125/150%, resize/focus/keyboard và text clipping, không mobile viewport equality. Web extension sau MVP mới dùng source375×812, tablet768×1024/desktop1440×900 và mobile320 overflow tests; breakpoints được chốt lúc thiết kế Web. Không render fake phone status bar hoặc kéo giãn một mobile screenshot thành desktop.

## Gate còn mở

Trước code Swing feature: review wireframe desktop, font/assets availability/license, native component states và contrast; EDT/codec/native-cookie flow được test. Revision2 typography/radius/color evidence vẫn giữ, semantic palette không gộp primary/Primary. P1 chỉ Swing shell kỹ thuật sau xác nhận; chưa có desktop UI source/visual pass. React/mobile pixel comparison vẫn future WEB-EXT, không gate MVP.
