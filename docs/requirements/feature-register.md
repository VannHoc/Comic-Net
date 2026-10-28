# Feature register — 23 module

Revision3 Swing-first, ngày2026-10-27. Mỗi bullet dưới `### Chức năng` trong prj_app.md được giữ nguyên, gán ID và phase đề xuất. Business rules và conditional qualifiers của đặc tả vẫn có hiệu lực. Source line là dòng one-based. Mỗi feature thuộc epic Mxx; feature MVP ánh xạ user story tương ứng trong mvp-backlog.md. Trạng thái đã được đối chiếu checkpoint P1/P2; Implemented (phần MVP) chỉ ghi phần code hiện có, không khẳng định feature nâng cao hoặc AC native đã Verified. Phần foundation P1 đã được user nghiệm thu; phase sau vẫn Not Started.

## M01 — IDENTITY, AUTHENTICATION & ACCESS CONTROL

| ID | Chức năng nguyên văn | Phase | Trạng thái | Dòng nguồn |
|---|---|---|---|---|
| M01-F01 | Đăng ký tài khoản. | 2 | Implemented (phần MVP); chưa Verified | 358 |
| M01-F02 | Đăng nhập / đăng xuất. | 2 | Implemented (phần MVP); chưa Verified | 359 |
| M01-F03 | Email verification. | 2 | Implemented (phần MVP); chưa Verified | 360 |
| M01-F04 | Quên và đặt lại mật khẩu. | 2 | Implemented (phần MVP); chưa Verified | 361 |
| M01-F05 | Google / Facebook / Apple SSO. | 6 | Not Started | 362 |
| M01-F06 | OTP / Passwordless Authentication. | 6 | Not Started | 363 |
| M01-F07 | JWT Access Token và Refresh Token. | 2 | Implemented (phần MVP); chưa Verified | 364 |
| M01-F08 | Refresh Token Rotation và Token Revocation. | 2 | Implemented (phần MVP); chưa Verified | 365 |
| M01-F09 | MFA / TOTP. | 3 | Not Started | 366 |
| M01-F10 | Quản lý thiết bị và phiên đăng nhập. | 3 | Not Started | 367 |
| M01-F11 | Account Recovery. | 3 | Not Started | 368 |
| M01-F12 | RBAC và phân quyền theo phạm vi tài nguyên. | 2 | Implemented (phần MVP); chưa Verified | 369 |
## M02 — COMIC CATALOG & METADATA MANAGEMENT

| ID | Chức năng nguyên văn | Phase | Trạng thái | Dòng nguồn |
|---|---|---|---|---|
| M02-F01 | CRUD truyện tranh. | 2 | Implemented (phần MVP); chưa Verified | 402 |
| M02-F02 | Quản lý tên chính, tên thay thế, slug. | 2 | Implemented (phần MVP); chưa Verified | 403 |
| M02-F03 | Tác giả, họa sĩ, nhà xuất bản nếu có. | 2 | Implemented (phần MVP); chưa Verified | 404 |
| M02-F04 | Scanlation Team. | 2 | Implemented (phần MVP); chưa Verified | 405 |
| M02-F05 | Quốc gia và loại truyện: Manga, Manhwa, Manhua, Comic. | 2 | Implemented (phần MVP); chưa Verified | 406 |
| M02-F06 | Genre, Tags và Content Warnings. | 2 | Implemented (phần MVP); chưa Verified | 407 |
| M02-F07 | Age Rating: All-age, 13+, 16+, 18+. | 2 | Implemented (phần MVP); chưa Verified | 408 |
| M02-F08 | Trạng thái: Ongoing, Completed, Hiatus, Dropped. | 2 | Implemented (phần MVP); chưa Verified | 409 |
| M02-F09 | Cover, Banner, Description. | 2 | Implemented (phần MVP); chưa Verified | 410 |
| M02-F10 | Quản lý nhiều bản dịch/ngôn ngữ cho cùng tác phẩm. | 2 | Implemented (phần MVP); chưa Verified | 411 |
| M02-F11 | Trạng thái hiển thị và khả năng tìm kiếm. | 2 | Implemented (phần MVP); chưa Verified | 412 |
## M03 — CHAPTER MANAGEMENT & PUBLISHING PIPELINE

| ID | Chức năng nguyên văn | Phase | Trạng thái | Dòng nguồn |
|---|---|---|---|---|
| M03-F01 | Tạo và chỉnh sửa Chapter / Volume. | 2 | Implemented (phần MVP); chưa Verified | 428 |
| M03-F02 | Bulk Upload ZIP. | 2 | Implemented (phần MVP); chưa Verified | 429 |
| M03-F03 | Kiểm tra định dạng và giới hạn upload. | 2 | Implemented (phần MVP); chưa Verified | 430 |
| M03-F04 | Sắp xếp lại trang. | 2 | Implemented (phần MVP); chưa Verified | 431 |
| M03-F05 | Tự động tối ưu hóa ảnh. | 2 | Implemented (phần MVP); chưa Verified | 432 |
| M03-F06 | Chuyển đổi WebP / AVIF. | 3 | Not Started | 433 |
| M03-F07 | Tạo nhiều kích thước ảnh. | 3 | Not Started | 434 |
| M03-F08 | Chapter Versioning. | 2 | Implemented (phần MVP); chưa Verified | 435 |
| M03-F09 | Draft và Preview. | 2 | Implemented (phần MVP); chưa Verified | 436 |
| M03-F10 | Content Review. | 2 | Implemented (phần MVP); chưa Verified | 437 |
| M03-F11 | Scheduled Publishing. | 3 | Implemented (Phase 3); chưa Verified | 438 |
| M03-F12 | Rollback và Recovery khi xử lý lỗi. | 2 upload/job recovery; 3 published-version rollback | Implemented (upload/job recovery + published-version rollback); chưa Verified | 439 |
## M04 — READER ENGINE & READING EXPERIENCE

| ID | Chức năng nguyên văn | Phase | Trạng thái | Dòng nguồn |
|---|---|---|---|---|
| M04-F01 | Vertical Scroll. | 2 | Implemented (phần MVP); chưa Verified | 461 |
| M04-F02 | Page-by-Page. | 2 | Implemented (phần MVP); chưa Verified | 462 |
| M04-F03 | RTL / LTR. | 2 | Implemented (phần MVP); chưa Verified | 463 |
| M04-F04 | Fit Width / Height. | 2 | Implemented (phần MVP); chưa Verified | 464 |
| M04-F05 | Fullscreen. | 3 | Not Started | 465 |
| M04-F06 | Auto Scroll. | 3 | Not Started | 466 |
| M04-F07 | Brightness Control trong phạm vi trình duyệt cho phép. | 3 | Not Started | 467 |
| M04-F08 | Reading Preferences. | 2 | Implemented (phần MVP); chưa Verified | 468 |
| M04-F09 | Preloading và Lazy Loading. | 2 | Implemented (phần MVP); chưa Verified | 469 |
| M04-F10 | Image Loading States. | 2 | Implemented (phần MVP); chưa Verified | 470 |
| M04-F11 | Dynamic Memory Management. | 6 | Not Started | 471 |
| M04-F12 | Auto Bookmark. | 2 | Implemented (phần MVP); chưa Verified | 472 |
| M04-F13 | Resume Reading. | 2 | Implemented (phần MVP); chưa Verified | 473 |
| M04-F14 | Reading Progress Synchronization. | 2 | Implemented (phần MVP); chưa Verified | 474 |
## M05 — DIGITAL ASSET STORAGE & CONTENT DELIVERY

| ID | Chức năng nguyên văn | Phase | Trạng thái | Dòng nguồn |
|---|---|---|---|---|
| M05-F01 | S3-compatible Object Storage. | 2 | Implemented (phần MVP); chưa Verified | 490 |
| M05-F02 | Quản lý Cover, Banner, Chapter Images. | 2 | Implemented (phần MVP); chưa Verified | 491 |
| M05-F03 | CDN Integration. | 3 | Not Started | 492 |
| M05-F04 | Signed URLs. | 2 | Implemented (phần MVP); chưa Verified | 493 |
| M05-F05 | Temporary Access Tokens. | 2 | Implemented (phần MVP); chưa Verified | 494 |
| M05-F06 | Rate Limiting. | 2 | Implemented (phần MVP); chưa Verified | 495 |
| M05-F07 | Anti-Hotlinking. | 3 | Not Started | 496 |
| M05-F08 | Image Watermark tùy chính sách. | 3 | Not Started | 497 |
| M05-F09 | Storage Cleanup. | 2 staging/orphan recovery; 3 retention cleanup nâng cao | Implemented (phần MVP); chưa Verified | 498 |
| M05-F10 | Cache Invalidation. | 3 | Not Started | 499 |
| M05-F11 | Asset Metadata và Versioning. | 2 | Implemented (phần MVP); chưa Verified | 500 |
## M06 — SEARCH & DISCOVERY

| ID | Chức năng nguyên văn | Phase | Trạng thái | Dòng nguồn |
|---|---|---|---|---|
| M06-F01 | Full-Text Search. | 3 | Implemented conditional (V18); BLOCKED verification: SQL Server FullTextInstalled=0 | 518 |
| M06-F02 | Search Autocomplete. | 3 | Implemented Swing SearchAssist; GUI chưa Verified | 519 |
| M06-F03 | Search Suggestions. | 3 | Implemented, SearchFlowIT Tested; GUI chưa Verified | 520 |
| M06-F04 | Search History. | 3 | Implemented, SearchFlowIT Tested; GUI chưa Verified | 521 |
| M06-F05 | Multi-Faceted Filtering. | 2 | Implemented (phần MVP); chưa Verified | 522 |
| M06-F06 | Sort by Updated Date / Views / Rating / Popularity. | 2 updated date; 3 views/rating/popularity | Implemented (updated date + views/rating/popularity); chưa Verified | 523 |
| M06-F07 | Search theo tên thay thế, tác giả, thể loại. | 2 | Implemented (phần MVP); chưa Verified | 524 |
| M06-F08 | Reverse Image Search bằng AI Embeddings, nếu được triển khai. | 6 | Not Started | 525 |
| M06-F09 | Index Synchronization. | 3 | Implemented conditional (SQL CHANGE_TRACKING AUTO); BLOCKED verification: FullTextInstalled=0 | 526 |
| M06-F10 | Search Fallback. | 3 | Implemented; SearchFlowIT Tested with SQL fallback; full-text outage chưa Verified | 527 |
## M07 — RANKING, VIEW COUNTING & ANALYTICS

| ID | Chức năng nguyên văn | Phase | Trạng thái | Dòng nguồn |
|---|---|---|---|---|
| M07-F01 | Đếm lượt đọc hợp lệ. | 3 | Implemented (Phase 3); chưa Verified | 542 |
| M07-F02 | Daily / Weekly / Monthly Ranking. | 3 | Implemented (Phase 3); chưa Verified | 543 |
| M07-F03 | Trending. | 3 | Implemented (Phase 3); chưa Verified | 544 |
| M07-F04 | Redis Sorted Sets. | 6 | Not Started | 545 |
| M07-F05 | Chống spam view. | 3 | Implemented (Phase 3); chưa Verified | 546 |
| M07-F06 | Analytics theo thời gian. | 3 | Implemented (Phase 3); chưa Verified | 547 |
| M07-F07 | Chapter Completion Rate. | 3 | Implemented (Phase 3); chưa Verified | 548 |
| M07-F08 | Reading Drop-off. | 6 | Not Started | 549 |
| M07-F09 | Peak Traffic. | 6 | Not Started | 550 |
| M07-F10 | Creator Analytics. | 5 | Not Started | 551 |
## M08 — PERSONAL LIBRARY & CROSS-DEVICE SYNC

| ID | Chức năng nguyên văn | Phase | Trạng thái | Dòng nguồn |
|---|---|---|---|---|
| M08-F01 | Favorites. | 2 | Implemented (phần MVP); chưa Verified | 567 |
| M08-F02 | Followed Comics. | 2 | Implemented (phần MVP); chưa Verified | 568 |
| M08-F03 | Reading History. | 2 | Implemented (phần MVP); chưa Verified | 569 |
| M08-F04 | Custom Collections. | 2 | Implemented (phần MVP); chưa Verified | 570 |
| M08-F05 | Want to Read / Reading / Completed / Re-read. | 2 | Implemented (phần MVP); chưa Verified | 571 |
| M08-F06 | Last Read Chapter. | 2 | Implemented (phần MVP); chưa Verified | 572 |
| M08-F07 | Bookmark. | 2 | Implemented (phần MVP); chưa Verified | 573 |
| M08-F08 | Cross-Device Synchronization. | 2 | Implemented (phần MVP); chưa Verified | 574 |
| M08-F09 | Đồng bộ theo thời gian gần thực. | 6 | Not Started | 575 |
## M09 — COMMUNITY & COMMENT SYSTEM

| ID | Chức năng nguyên văn | Phase | Trạng thái | Dòng nguồn |
|---|---|---|---|---|
| M09-F01 | Chapter Comments. | 3 | Implemented; backend kiểm thử một phần; GUI chưa Verified | 590 |
| M09-F02 | Comic Comments. | 3 | Implemented; backend kiểm thử một phần; GUI chưa Verified | 591 |
| M09-F03 | Image-Level Comments. | 3 | Implemented; backend kiểm thử một phần; GUI chưa Verified | 592 |
| M09-F04 | Nested Replies. | 3 | Implemented; backend kiểm thử một phần; GUI chưa Verified | 593 |
| M09-F05 | Upvote / Downvote. | 3 | Implemented; backend kiểm thử một phần; GUI chưa Verified | 594 |
| M09-F06 | Emoji / GIF / Sticker. | 5 | Not Started | 595 |
| M09-F07 | Spoiler Tags. | 3 | Implemented; backend kiểm thử một phần; GUI chưa Verified | 596 |
| M09-F08 | Report Comment. | 3 | Implemented; backend kiểm thử một phần; GUI chưa Verified | 597 |
| M09-F09 | Edit / Delete Comment. | 3 | Implemented; backend kiểm thử một phần; GUI chưa Verified | 598 |
| M09-F10 | User Blocking. | 3 | Implemented; backend kiểm thử một phần; GUI chưa Verified | 599 |
| M09-F11 | Moderation Queue. | 3 | Implemented; backend kiểm thử một phần; GUI chưa Verified | 600 |
| M09-F12 | Anti-Spam và Profanity Filtering. | 3 | Implemented (rate limit + filter cấu hình); policy từ ngữ chưa chốt; chưa Verified | 601 |
## M10 — RATINGS & RECOMMENDATION ENGINE

| ID | Chức năng nguyên văn | Phase | Trạng thái | Dòng nguồn |
|---|---|---|---|---|
| M10-F01 | Rating theo thang điểm thống nhất. | 3 | Implemented; backend kiểm thử một phần; GUI chưa Verified | 617 |
| M10-F02 | User Reviews. | 3 | Implemented; backend kiểm thử một phần; GUI chưa Verified | 618 |
| M10-F03 | Rating Aggregation. | 3 | Implemented; backend kiểm thử một phần; GUI chưa Verified | 619 |
| M10-F04 | Similar Comics. | 3 | Implemented; backend kiểm thử một phần; GUI chưa Verified | 620 |
| M10-F05 | Personalized Recommendations. | 6 | Not Started | 621 |
| M10-F06 | Content-Based Filtering. | 3 | Implemented; backend kiểm thử một phần; GUI chưa Verified | 622 |
| M10-F07 | Collaborative Filtering. | 6 | Not Started | 623 |
| M10-F08 | Trending Recommendations. | 3 | Implemented; backend kiểm thử một phần; GUI chưa Verified | 624 |
| M10-F09 | Cold-Start Recommendation Strategy. | 3 | Implemented; backend kiểm thử một phần; GUI chưa Verified | 625 |
## M11 — DUAL-TOKEN WALLET & VIRTUAL ECONOMY

| ID | Chức năng nguyên văn | Phase | Trạng thái | Dòng nguồn |
|---|---|---|---|---|
| M11-F01 | Quản lý ví. | 4 | Implemented phần số dư/lịch sử; SQL Tested; Swing chưa Verified | 646 |
| M11-F02 | Nạp Paid Coin. | 4 | Implemented sandbox local; SQL Tested; Swing chưa Verified; provider thật chưa có | 647 |
| M11-F03 | Nhận Reward Coin. | 4 | Not Started | 648 |
| M11-F04 | Kiểm tra số dư. | 4 | Implemented; SQL Tested; Swing chưa Verified | 649 |
| M11-F05 | Ledger Transactions. | 4 | Implemented balanced debit/credit; SQL Tested; đối soát nâng cao còn thiếu | 650 |
| M11-F06 | Purchase / Spend. | 4 | Implemented mua chương; SQL Tested; Swing chưa Verified | 651 |
| M11-F07 | Refund / Adjustment. | 4 | Not Started | 652 |
| M11-F08 | Transaction History. | 4 | Implemented; SQL Tested; Swing chưa Verified | 653 |
| M11-F09 | Idempotency. | 4 | Implemented cho WalletService; SQL Tested | 654 |
| M11-F10 | Anti-Fraud. | 4 | Not Started | 655 |
| M11-F11 | Reward Expiration Rules nếu được áp dụng. | 4 | Not Started | 656 |
## M12 — MONETIZATION & COMMERCIAL CONTENT

| ID | Chức năng nguyên văn | Phase | Trạng thái | Dòng nguồn |
|---|---|---|---|---|
| M12-F01 | Pay-per-Chapter. | 4 | Implemented; SQL Tested; Swing chưa Verified | 685 |
| M12-F02 | Chapter Pricing. | 4 | Implemented giá hiện hành PAID/REWARD; SQL Tested; Swing chưa Verified | 686 |
| M12-F03 | Fast Pass. | 4 | Not Started | 687 |
| M12-F04 | VIP Subscription. | 4 | Not Started | 688 |
| M12-F05 | Membership Benefits. | 4 | Not Started | 689 |
| M12-F06 | Subscription Plans. | 4 | Not Started | 690 |
| M12-F07 | Rewarded Ads. | 4 | Not Started | 691 |
| M12-F08 | Ad Campaign Rules. | 4 | Not Started | 692 |
| M12-F09 | Promotion / Voucher. | 4 | Not Started | 693 |
| M12-F10 | Access Product Catalog. | 4 | Not Started | 694 |
## M13 — NOTIFICATION SYSTEM

| ID | Chức năng nguyên văn | Phase | Trạng thái | Dòng nguồn |
|---|---|---|---|---|
| M13-F01 | In-App Notifications. | 3 | Implemented (Phase 3); chưa Verified | 715 |
| M13-F02 | New Chapter Alerts. | 3 | Implemented (Phase 3); chưa Verified | 716 |
| M13-F03 | Reply Notifications. | 3 | Implemented (Phase 3); chưa Verified | 717 |
| M13-F04 | Reaction Notifications. | 3 | Implemented (Phase 3); chưa Verified | 718 |
| M13-F05 | System Announcements. | 3 | Implemented (Phase 3); chưa Verified | 719 |
| M13-F06 | Email Notifications. | 3 | Implemented (Phase 3); chưa Verified | 720 |
| M13-F07 | Push Notification Integration. | 6 | Not Started | 721 |
| M13-F08 | Notification Preferences. | 3 | Implemented (Phase 3); chưa Verified | 722 |
| M13-F09 | Mark as Read / Unread. | 3 | Implemented (Phase 3); chưa Verified | 723 |
| M13-F10 | Batch Notifications. | 3 | Implemented (Phase 3); chưa Verified | 724 |
## M14 — CREATOR & SCANLATION TEAM PORTAL

| ID | Chức năng nguyên văn | Phase | Trạng thái | Dòng nguồn |
|---|---|---|---|---|
| M14-F01 | Tạo nhóm. | 2 nền; 5 đầy đủ | Implemented (phần MVP); chưa Verified | 740 |
| M14-F02 | Mời / Xóa thành viên. | 2 nền; 5 đầy đủ | Implemented (phần MVP); chưa Verified | 741 |
| M14-F03 | Phân quyền trong nhóm. | 2 nền; 5 đầy đủ | Implemented (phần MVP); chưa Verified | 742 |
| M14-F04 | Phân công Translation / Editing / Proofreading / Upload. | 5 | Not Started | 743 |
| M14-F05 | Creator Dashboard. | 5 | Not Started | 744 |
| M14-F06 | Chapter Publishing Management. | 2 publishing API cơ bản; 5 creator portal đầy đủ | Implemented (phần MVP); chưa Verified | 745 |
| M14-F07 | Revenue Sharing Configuration. | 5 | Not Started | 746 |
| M14-F08 | Team Analytics. | 5 | Not Started | 747 |
| M14-F09 | Payout Requests. | 5 | Not Started | 748 |
| M14-F10 | Approval Workflow. | 5 | Not Started | 749 |
## M15 — ADMIN CONTROL CENTER

| ID | Chức năng nguyên văn | Phase | Trạng thái | Dòng nguồn |
|---|---|---|---|---|
| M15-F01 | System Dashboard. | 2 | Implemented (phần MVP); chưa Verified | 765 |
| M15-F02 | User Management. | 2 | Implemented (phần MVP); chưa Verified | 766 |
| M15-F03 | Role Assignment. | 2 | Implemented (phần MVP); chưa Verified | 767 |
| M15-F04 | Comic / Chapter Moderation. | 2 | Implemented (phần MVP); chưa Verified | 768 |
| M15-F05 | Report Management. | 3 | Not Started | 769 |
| M15-F06 | Banner Management. | 3 | Not Started | 770 |
| M15-F07 | System Configuration. | 2 nền; 4 tài chính; 7 đầy đủ | Implemented (phần MVP); chưa Verified | 771 |
| M15-F08 | Audit Logs. | 2 | Implemented (phần MVP); chưa Verified | 772 |
| M15-F09 | Feature Flags. | 3 | Not Started | 773 |
| M15-F10 | Content Visibility Management. | 2 | Implemented (phần MVP); chưa Verified | 774 |
| M15-F11 | Account Suspension / Restoration. | 2 | Implemented (phần MVP); chưa Verified | 775 |
## M16 — GAMIFICATION & LEVELING

| ID | Chức năng nguyên văn | Phase | Trạng thái | Dòng nguồn |
|---|---|---|---|---|
| M16-F01 | EXP System. | 5 | Not Started | 791 |
| M16-F02 | User Level. | 5 | Not Started | 792 |
| M16-F03 | Achievement System. | 5 | Not Started | 793 |
| M16-F04 | Daily Check-In. | 5 | Not Started | 794 |
| M16-F05 | Reading Rewards. | 5 | Not Started | 795 |
| M16-F06 | Comment / Review Rewards. | 5 | Not Started | 796 |
| M16-F07 | Cultivation Titles. | 5 | Not Started | 797 |
| M16-F08 | Avatar Frames. | 5 | Not Started | 798 |
| M16-F09 | Special Badges. | 5 | Not Started | 799 |
| M16-F10 | Unlockable Perks. | 5 | Not Started | 800 |
## M17 — MINI-GAME & LOYALTY HUB

| ID | Chức năng nguyên văn | Phase | Trạng thái | Dòng nguồn |
|---|---|---|---|---|
| M17-F01 | Daily Lucky Wheel. | 5 | Not Started | 824 |
| M17-F02 | Reward Coin Spending. | 5 | Not Started | 825 |
| M17-F03 | Free Daily Spin. | 5 | Not Started | 826 |
| M17-F04 | Character Card Collection. | 5 | Not Started | 827 |
| M17-F05 | Gacha Collection. | 5 | Not Started | 828 |
| M17-F06 | Manga Trivia. | 5 | Not Started | 829 |
| M17-F07 | Quiz Rewards. | 5 | Not Started | 830 |
| M17-F08 | Voucher & Perk Redemption. | 5 | Not Started | 831 |
| M17-F09 | Reward Inventory. | 5 | Not Started | 832 |
| M17-F10 | Mini-Game History. | 5 | Not Started | 833 |
## M18 — AI AUTOMATION & CONTENT INTELLIGENCE

| ID | Chức năng nguyên văn | Phase | Trạng thái | Dòng nguồn |
|---|---|---|---|---|
| M18-F01 | AI-assisted Content Moderation. | 6 | Not Started | 853 |
| M18-F02 | NSFW Image Classification. | 6 | Not Started | 854 |
| M18-F03 | Spam Detection. | 6 | Not Started | 855 |
| M18-F04 | Abuse Detection. | 6 | Not Started | 856 |
| M18-F05 | Content Tag Suggestions. | 6 | Not Started | 857 |
| M18-F06 | Embedding Generation. | 6 | Not Started | 858 |
| M18-F07 | Semantic Search Support. | 6 | Not Started | 859 |
| M18-F08 | Recommendation Feature Extraction. | 6 | Not Started | 860 |
| M18-F09 | AI Processing Queue. | 6 | Not Started | 861 |
| M18-F10 | Human Review Support. | 6 | Not Started | 862 |
## M19 — OFFLINE READER & SECURE STORAGE

| ID | Chức năng nguyên văn | Phase | Trạng thái | Dòng nguồn |
|---|---|---|---|---|
| M19-F01 | Download Chapter for Offline Reading. | 6 | Not Started | 879 |
| M19-F02 | Offline Content Index. | 6 | Not Started | 880 |
| M19-F03 | Offline Reading Progress. | 6 | Not Started | 881 |
| M19-F04 | Sync After Reconnection. | 6 | Not Started | 882 |
| M19-F05 | Download Queue. | 6 | Not Started | 883 |
| M19-F06 | Offline Content Expiration. | 6 | Not Started | 884 |
| M19-F07 | Encrypted Storage khi phù hợp với nền tảng. | 6 | Not Started | 885 |
| M19-F08 | Storage Quota Management. | 6 | Not Started | 886 |
| M19-F09 | Remove Downloaded Content. | 6 | Not Started | 887 |
## M20 — DIGITAL ENTITLEMENT MANAGEMENT

| ID | Chức năng nguyên văn | Phase | Trạng thái | Dòng nguồn |
|---|---|---|---|---|
| M20-F01 | Permanent Chapter Unlock. | 4 | Implemented; SQL Tested; Swing chưa Verified | 908 |
| M20-F02 | Temporary Chapter Unlock. | 4 | Not Started | 909 |
| M20-F03 | VIP Entitlement. | 4 | Not Started | 910 |
| M20-F04 | Fast Pass Entitlement. | 4 | Not Started | 911 |
| M20-F05 | Promotional Access. | 4 | Not Started | 912 |
| M20-F06 | Reward-Based Unlock. | 4 | Not Started | 913 |
| M20-F07 | Access Validation. | 2 FREE/RESTRICTED; 4 thương mại | Implemented FREE/entitlement; SQL Tested; Swing chưa Verified | 914 |
| M20-F08 | Entitlement History. | 4 | Not Started | 915 |
| M20-F09 | Access Expiration / Revocation. | 4 | Not Started | 916 |
| M20-F10 | Cross-Device Access Synchronization. | 4; 6 realtime | Not Started | 917 |
## M21 — SYSTEM OBSERVABILITY & RELIABILITY

| ID | Chức năng nguyên văn | Phase | Trạng thái | Dòng nguồn |
|---|---|---|---|---|
| M21-F01 | Application Logging. | 1 | Verified (foundation P1; phần mở rộng chưa bắt đầu) | 934 |
| M21-F02 | Structured Logs. | 1 | Verified (foundation P1; phần mở rộng chưa bắt đầu) | 935 |
| M21-F03 | Error Tracking. | 7 | Not Started | 936 |
| M21-F04 | Health Checks. | 1 | Verified (foundation P1; phần mở rộng chưa bắt đầu) | 937 |
| M21-F05 | API Metrics. | 3 | Not Started | 938 |
| M21-F06 | Performance Monitoring. | 7 | Not Started | 939 |
| M21-F07 | Tracing. | 7 | Not Started | 940 |
| M21-F08 | Alerting. | 7 | Not Started | 941 |
| M21-F09 | Background Job Monitoring. | 2 | Implemented (phần MVP); chưa Verified | 942 |
| M21-F10 | Error Recovery. | 2 | Implemented (phần MVP); chưa Verified | 943 |
| M21-F11 | Retry / Dead Letter Handling. | 2 | Implemented (phần MVP); chưa Verified | 944 |
## M22 — DATA PROTECTION, BACKUP & DISASTER RECOVERY

| ID | Chức năng nguyên văn | Phase | Trạng thái | Dòng nguồn |
|---|---|---|---|---|
| M22-F01 | Backup Strategy. | 1 | Verified (foundation P1; phần mở rộng chưa bắt đầu) | 960 |
| M22-F02 | Restore Procedure. | 2 mẫu; 7 đầy đủ | Implemented (phần MVP); chưa Verified | 961 |
| M22-F03 | Data Export. | 6 | Not Started | 962 |
| M22-F04 | User Data Deletion. | 6 | Not Started | 963 |
| M22-F05 | Data Retention. | 7 | Not Started | 964 |
| M22-F06 | Privacy Preferences. | 6 | Not Started | 965 |
| M22-F07 | Database Recovery. | 2 mẫu; 7 đầy đủ | Implemented (phần MVP); chưa Verified | 966 |
| M22-F08 | Backup Verification. | 2 mẫu; 7 đầy đủ | Implemented (phần MVP); chưa Verified | 967 |
| M22-F09 | Sensitive Data Protection. | 1 | Verified (foundation P1; phần mở rộng chưa bắt đầu) | 968 |
| M22-F10 | Disaster Recovery Documentation. | 7 | Not Started | 969 |
## M23 — FINANCIAL RECONCILIATION & SUBSCRIPTION LIFECYCLE

| ID | Chức năng nguyên văn | Phase | Trạng thái | Dòng nguồn |
|---|---|---|---|---|
| M23-F01 | Payment Reconciliation. | 4 | Implemented snapshot đối soát sandbox local; SQL Tested; provider thật chưa có | 985 |
| M23-F02 | Payment Webhooks. | 4 | Implemented inbox event sandbox nội bộ; webhook nhà cung cấp chưa có | 986 |
| M23-F03 | Subscription Activation. | 4 | Not Started | 987 |
| M23-F04 | Renewal. | 4 | Not Started | 988 |
| M23-F05 | Cancellation. | 4 | Not Started | 989 |
| M23-F06 | Expiration. | 4 | Not Started | 990 |
| M23-F07 | Refund. | 4 | Not Started | 991 |
| M23-F08 | Chargeback. | 4 | Not Started | 992 |
| M23-F09 | Failed Payment Handling. | 4 | Not Started | 993 |
| M23-F10 | Payout Reconciliation. | 4 finance foundation; 5 payout reconciliation đầy đủ | Not Started | 994 |
| M23-F11 | Transaction Dispute Tracking. | 4 | Not Started | 995 |
Tổng **23 module, 243 bullet chức năng**. Phase đầu là lần triển khai đầu tiên; chức năng nhiều phần tiếp tục mở rộng theo scope-roadmap.md.

Revision3: toàn bộ bullet/module source giữ nguyên; source line refs đã tính lại sau khi cập nhật UI/run/SRS sections. Phase2 delivery là Swing, React Web giữ WEB-EXT sau MVP. Browser-specific wording nguồn không bị xóa; platform mapping trong srs.md.
