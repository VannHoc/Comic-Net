# COMIC NET — DESIGN SYSTEM V1.0

**Định hướng:** Modern Editorial Manga Library — giao diện desktop Java Swing sáng, gọn, giàu hình ảnh truyện, typography rõ; màu coral là điểm nhấn có kiểm soát. Thiết kế desktop cho ứng dụng Comic Net trên Windows 11 / NetBeans 31, không phải HTML/CSS hay React. Dùng dữ liệu thật từ backend; ảnh minh hoạ chỉ là visual reference.

## 1. Design principles

1. **Content first:** Bìa truyện, tên truyện, trạng thái đọc và hành động đọc phải nổi bật hơn nút/khung trang trí.
2. **Consistent rhythm:** Một hệ spacing, bo góc, chiều cao điều khiển, typography, khoảng cách thống nhất.
3. **Quiet chrome:** Sidebar và header giản dị; để ảnh bìa và nội dung tạo sự hấp dẫn.
4. **Practical desktop:** Layout thích ứng khi resize, cuộn đúng chỗ, không block Swing EDT.
5. **Truthful UI:** Không tự sinh truyện, số lượng đọc, đánh giá, cấp, ảnh bìa, danh hiệu từ dữ liệu giả rồi hiển thị như thật. Thiếu dữ liệu -> empty/loading/error state chuyên nghiệp.
6. **Light-only:** Chỉ một theme sáng. Không thêm nút chuyển dark mode.

## 2. Foundations / Tokens

### Color

| Token | Hex | Sử dụng |
|---|---|---|
| `color.brand.primary` | `#F65D4E` | CTA chính, thanh active, điểm nhấn |
| `color.brand.hover` | `#E84A3D` | Hover CTA |
| `color.brand.tint` | `#FFF0ED` | Background active nhẹ, soft badge |
| `color.ink.primary` | `#172033` | Tiêu đề, text chính |
| `color.ink.secondary` | `#66758A` | Mô tả, metadata |
| `color.ink.muted` | `#98A3B3` | Hint/placeholder |
| `color.canvas` | `#F6F8FB` | Nền nội dung |
| `color.surface` | `#FFFFFF` | Card, header, sidebar |
| `color.border` | `#E6EAF0` | Stroke nhẹ |
| `color.success` | `#1D9B75` | Xác nhận/thành công |
| `color.warning` | `#E6A340` | Cảnh báo |
| `color.danger` | `#DC4F52` | Hành động nguy hiểm |
| `color.reward` | `#F2B84A` | EXP/xu/badge đặc biệt |

### Typography

- Font ưu tiên **Segoe UI** (tương thích Windows, dấu tiếng Việt), fallback `SansSerif`; có thể dùng Inter nếu font thực sự được đóng gói hợp lệ. Không ép font khi hệ thống thiếu glyph.
- H1 / page title: **28px semibold/bold**.
- H2 / section heading: **20px semibold**.
- H3 / card heading: **16px semibold**.
- Body: **14px regular**, line height khoảng 1.4.
- Label / navigation: **14px medium**.
- Metadata/helper: **12–13px regular**.
- Không viết tất cả các tiêu đề bằng IN HOA.

### Spacing / Size

- Spacing scale: `4, 8, 12, 16, 20, 24, 32, 40` px (scale-aware ở DPI cao).
- Sidebar rộng chuẩn **228px**; thu gọn hoặc scroll theo khả năng resize.
- Top bar cao tham chiếu **72px**.
- Content padding tham chiếu **28px**; card padding **20–24px**.
- Gap section **28–32px**; card gap **16–20px**.
- Button/input height **40–44px**, padding ngang 14–18px.
- Card radius **14–16px**; control radius **10–12px**; badge pill.
- Card border 1px, shadow cực nhẹ nếu implementation hỗ trợ; không lạm dụng đổ bóng.
- Window tham chiếu **1536×960**, phải hỗ trợ tối thiểu 1180×720 nếu không tràn; dưới ngưỡng có scroll/column adaptation phù hợp.

### Icons / Images

- Bộ icon nét đơn consistent (Lucide-like), 18–20px; sử dụng resource/Icon/SVG renderer hợp lệ trong Swing, không dùng emoji thay icon.
- Cover ratio **2:3**, aspect preserved; ưu tiên cover image thật, background placeholder tinh tế khi không có dữ liệu ảnh.
- Không lấy ảnh Internet ngẫu nhiên gán thành bìa truyện thực; ảnh tham khảo thiết kế không đại diện dữ liệu hệ thống.

## 3. Shared components

- `AppSidebar`: danh sách item icon + label, active = coral tint + marker coral mảnh; brand ở đầu; status tài khoản dưới cùng; không trùng menu vô ích.
- `AppTopBar`: một ô tìm kiếm có icon, các icon button thông báo, ví, avatar; sizing nhất quán.
- `AppButton`: primary (coral fill), secondary (white + border), subtle/text, destructive. Hover/pressed/disabled/loading states; focus keyboard.
- `AppTextField` / `AppComboBox`: nhất quán height, radius, border, typography; validation messages cạnh field.
- `AppCard`: white + light border, radius14–16, internal padding 20–24.
- `ComicCard`: ảnh cover 2:3, tên tối đa 2 dòng, thể loại + chapter/status + progress/rating nếu dữ liệu thật. Cả card click đúng chi tiết, không phải nút tràn khung.
- `SectionHeader`: title trái, link/action phải.
- `StatusChip`, `MetaRow`, `EmptyState`, `LoadingSkeleton`, `ErrorState`, `Toast`, `ConfirmDialog`, `ProfileAvatar`, `ProgressBar`, `StatCard`, `TitleBadge`.
- Tương tác: hover, keyboard focus, loading, disabled. Mọi CTA cần behavior rõ ràng.

## 4. Desktop layout — Khám phá (Explore)

1. **Shell:** 228px left sidebar + 72px topbar + responsive content area; sidebar và header nhất quán cho các trang.
2. **Introduction:** eyebrow `KHÁM PHÁ` nhỏ + H1 `Hôm nay đọc gì?` + subcopy 1 dòng.
3. **Featured story:** hero nổi bật tỷ lệ rộng với cover đứng bên trái (tránh chiếm toàn chiều ngang), bên phải có title, tags, mô tả tối đa 2–3 dòng, 2 CTA `Đọc ngay` / `Chi tiết`. Chỉ xuất hiện khi có truyện thật; không kéo fullwidth nút.
4. **Discovery controls:** thanh search/filter trên một dòng, giảm bớt controls trực tiếp; dropdown `Thể loại`, `Trạng thái`, `Sắp xếp`; phần filter nâng cao có popover/panel.
5. **Grid:** 4–5 comic cards trên width đủ lớn, column resize theo kích thước; cùng tỉ lệ cover, cùng padding/gap. Có nút `Xem thêm`/pagination.
6. **Right rail:** optional 260–280px trên màn rộng: `Đang đọc dở` / `Top thịnh hành` / `Nhiệm vụ hôm nay` khi có data; màn nhỏ xếp xuống dưới, không bị squeeze.
7. **Empty:** Thông báo `Chưa có truyện để hiển thị` + hướng dẫn hợp lý nếu data rỗng; không lặp 3 card cùng một truyện để lấp chỗ trống.

## 5. Desktop layout — Tài khoản (Account)

1. Giữ shell chung.
2. `Hồ sơ của bạn` H1 + subtitle ngắn.
3. **Profile header card:** avatar tròn, display name, username, vai trò dạng label thân thiện (vd `Độc giả`), danh hiệu đang sử dụng nếu dữ liệu có, Level + thanh EXP thực; nút `Chỉnh sửa hồ sơ`.
4. **Body two columns:** main ~2/3 (tối đa ~620px cho form controls) + side ~1/3 (stat cards, danh hiệu, thành tựu nếu có). Form không kéo dài 100% màn.
5. **Tabs:** `Hồ sơ` / `Bảo mật` / `Phiên đăng nhập` / `Danh hiệu` (tab conditional nếu nghiệp vụ đã triển khai); tab rõ active state.
6. **Profile form:** tên hiển thị, email khôi phục, các thông tin có thể sửa; tách username khi không cho đổi; explicit Save, cancel, validation. Không render nguyên JSON list role dạng `["READER"]`.
7. **Account actions:** `Đăng xuất` ở cuối trang, destructive tone vừa phải, không ngang hàng với hành động lưu.
8. Level/danh hiệu chưa có từ API -> empty state riêng, không fabricate số liệu.

## 6. Swing implementation requirements

- Kiểm tra source code trước, tận dụng FlatLaf **nếu hiện có**; nếu thiếu, đề xuất dependency gọn và tương thích Java 21, chỉ thêm khi có lợi thực tế.
- Tách theme tokens/shared components, không chỉnh từng pixel bằng `null layout`, `setBounds` cố định. Dùng BorderLayout, GridBagLayout, BoxLayout hoặc MigLayout nếu dự án đã có dependency.
- Swing EDT chỉ render; HTTP/ảnh I/O qua async/SwingWorker; resize không lag; lazy image loading và cache đúng yêu cầu.
- Không động tới business logic, Spring Security Session Cookie + CSRF, REST endpoint, SQL schema/migrations, phân quyền/entitlement, wallet logic.
- Dùng `CardLayout` cho chuyển tab/trang, explicit scroll container; tránh nested scrollbars và scroll toàn bộ blank card.
- Nút/search/filter/tab phải giữ behavior thực; screen states loading, empty, error, permission, unavailable.
- Đồng bộ theme giữa trang Khám phá và Tài khoản, rồi mở rộng toàn app khi hai màn đã verified.

## 7. Acceptance checklist

- [ ] Typography hỗ trợ tiếng Việt, không lỗi dấu/ô vuông.
- [ ] Sidebar/header đồng nhất; căn lề, khoảng cách, radius nhất quán.
- [ ] Trang Explore không còn hero trống khổng lồ hay nút CTA dài bất thường.
- [ ] Comic grid có cover 2:3, title/meta/thao tác rõ; thiếu truyện -> empty state, không dữ liệu giả.
- [ ] Trang Account có avatar/name/level/title hợp dữ liệu thật; form max width hợp lý.
- [ ] Không trùng chức năng tài khoản/notification vô ích trong cả sidebar và header (nếu có shortcut thì chủ đích khác).
- [ ] Resize 1536×960, 1366×768, 1180×720; Windows DPI 100%/125% không clip/tràn.
- [ ] Focus, keyboard, hover, disabled, loading, validation hoạt động.
- [ ] Không phát sinh regression trong đăng nhập/session+CSRF, search, navigation, save profile, logout, đọc truyện.
- [ ] Build/run Java 21 NetBeans 31; báo cáo file thay đổi và test thực tế.
