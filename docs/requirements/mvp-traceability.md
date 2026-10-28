# MVP feature traceability — revision 3 Swing

Mỗi feature có phần thực hiện ở Phase2 được liên kết cụ thể tới story/operation/table/test family. Mapping một feature tới nhóm operations không thay thế acceptance criteria chi tiết của story. Non-MVP features vẫn giữ nguyên trong feature-register.md với epic/phase, không bị loại bỏ. Không coi các test family là kết quả đã chạy.

| Feature | Phần MVP | Story / AC | OperationId liên quan | Tables / evidence | Test family | Swing view |
|---|---|---|---|---|---|---|
| M01-F01 | 2 | US-01 | register, verifyEmail, resendVerification | User/Role/Session/RefreshToken/AccountActionToken | TC-AUTH-01..05; TC-SEC-01..05 | SW01 |
| M01-F02 | 2 | US-02 | getCsrf, login, refresh, logout, getMe, updateMe | User/Role/Session/RefreshToken/AccountActionToken | TC-AUTH-01..05; TC-SEC-01..05 | SW02 |
| M01-F03 | 2 | US-01 | register, verifyEmail, resendVerification | User/Role/Session/RefreshToken/AccountActionToken | TC-AUTH-01..05; TC-SEC-01..05 | SW01 |
| M01-F04 | 2 | US-03 | forgotPassword, resetPassword | User/Role/Session/RefreshToken/AccountActionToken | TC-AUTH-01..05; TC-SEC-01..05 | SW03 |
| M01-F07 | 2 | US-02 | getCsrf, login, refresh, logout, getMe, updateMe | User/Role/Session/RefreshToken/AccountActionToken | TC-AUTH-01..05; TC-SEC-01..05 | SW02 |
| M01-F08 | 2 | US-02 | getCsrf, login, refresh, logout, getMe, updateMe | User/Role/Session/RefreshToken/AccountActionToken | TC-AUTH-01..05; TC-SEC-01..05 | SW02 |
| M01-F12 | 2 | US-15, US-18 | listUsers, getManagedUser, setUserStatus, setUserRoles, listTeams, createTeam, getManagedTeam, setTeamMember (các operation còn lại theo x-story trong OpenAPI) | User/Role/Session/RefreshToken/AccountActionToken | TC-AUTH-01..05; TC-SEC-01..05 | SW15, SW18 |
| M02-F01 | 2 | US-04, US-05 | listComics, createComic, deleteComic, getMetadata, getComic, updateComic, getComicBySlug, listTranslations, createTranslation (các operation còn lại theo x-story trong OpenAPI) | Comic/Translation/metadata joins | TC-DB-01..04; TC-SEC-01..05 | SW04, SW05 |
| M02-F02 | 2 | US-04, US-05 | listComics, createComic, getMetadata, getComic, updateComic, getComicBySlug, listTranslations, createTranslation (các operation còn lại theo x-story trong OpenAPI) | Comic/Translation/metadata joins | TC-DB-01..04; TC-SEC-01..05 | SW04, SW05 |
| M02-F03 | 2 | US-04, US-05 | listComics, createComic, getMetadata, getComic, updateComic, getComicBySlug, listTranslations, createTranslation (các operation còn lại theo x-story trong OpenAPI) | Comic/Translation/metadata joins | TC-DB-01..04; TC-SEC-01..05 | SW04, SW05 |
| M02-F04 | 2 | US-04, US-05 | listComics, createComic, getMetadata, getComic, updateComic, getComicBySlug, listTranslations, createTranslation (các operation còn lại theo x-story trong OpenAPI) | Comic/Translation/metadata joins | TC-DB-01..04; TC-SEC-01..05 | SW04, SW05 |
| M02-F05 | 2 | US-04, US-05 | listComics, createComic, getMetadata, getComic, updateComic, getComicBySlug, listTranslations, createTranslation (các operation còn lại theo x-story trong OpenAPI) | Comic/Translation/metadata joins | TC-DB-01..04; TC-SEC-01..05 | SW04, SW05 |
| M02-F06 | 2 | US-04, US-05 | listComics, createComic, getMetadata, getComic, updateComic, getComicBySlug, listTranslations, createTranslation (các operation còn lại theo x-story trong OpenAPI) | Comic/Translation/metadata joins | TC-DB-01..04; TC-SEC-01..05 | SW04, SW05 |
| M02-F07 | 2 | US-04, US-05 | listComics, createComic, getMetadata, getComic, updateComic, getComicBySlug, listTranslations, createTranslation (các operation còn lại theo x-story trong OpenAPI) | Comic/Translation/metadata joins | TC-DB-01..04; TC-SEC-01..05 | SW04, SW05 |
| M02-F08 | 2 | US-04, US-05 | listComics, createComic, getMetadata, getComic, updateComic, getComicBySlug, listTranslations, createTranslation (các operation còn lại theo x-story trong OpenAPI) | Comic/Translation/metadata joins | TC-DB-01..04; TC-SEC-01..05 | SW04, SW05 |
| M02-F09 | 2 | US-04, US-05 | listComics, createComic, getMetadata, getComic, updateComic, getComicBySlug, listTranslations, createTranslation (các operation còn lại theo x-story trong OpenAPI) | Comic/Translation/metadata joins | TC-DB-01..04; TC-SEC-01..05 | SW04, SW05 |
| M02-F10 | 2 | US-04, US-05 | listComics, createComic, getMetadata, getComic, updateComic, getComicBySlug, listTranslations, createTranslation (các operation còn lại theo x-story trong OpenAPI) | Comic/Translation/metadata joins | TC-DB-01..04; TC-SEC-01..05 | SW04, SW05 |
| M02-F11 | 2 | US-04, US-05 | listComics, createComic, getMetadata, getComic, updateComic, getComicBySlug, listTranslations, createTranslation (các operation còn lại theo x-story trong OpenAPI) | Comic/Translation/metadata joins | TC-DB-01..04; TC-SEC-01..05 | SW04, SW05 |
| M03-F01 | 2 | US-05, US-06 | createComic, updateComic, createTranslation, createChapter, createVolume, listManagedComics, getManagedComic, getManagedTranslation (các operation còn lại theo x-story trong OpenAPI) | Chapter/Version/Page/VersionPage/UploadJob | TC-UP-01..05; TC-VER-01..04 | SW05, SW06 |
| M03-F02 | 2 | US-06 | createChapter, listManagedChapters, getManagedChapter, uploadChapterPages, getUploadJob, retryUploadJob | Chapter/Version/Page/VersionPage/UploadJob | TC-UP-01..05; TC-VER-01..04 | SW06 |
| M03-F03 | 2 | US-06 | createChapter, listManagedChapters, getManagedChapter, uploadChapterPages, getUploadJob, retryUploadJob | Chapter/Version/Page/VersionPage/UploadJob | TC-UP-01..05; TC-VER-01..04 | SW06 |
| M03-F04 | 2 | US-07 | updateChapter, createChapterVersion, listChapterVersions, getChapterVersion, reorderChapterPages, removeChapterPage, previewChapterVersion, listReviewQueue | Chapter/Version/Page/VersionPage/UploadJob | TC-UP-01..05; TC-VER-01..04 | SW07 |
| M03-F05 | 2 | US-06 | createChapter, listManagedChapters, getManagedChapter, uploadChapterPages, getUploadJob, retryUploadJob | Chapter/Version/Page/VersionPage/UploadJob | TC-UP-01..05; TC-VER-01..04 | SW06 |
| M03-F08 | 2 | US-07 | updateChapter, createChapterVersion, listChapterVersions, getChapterVersion, reorderChapterPages, removeChapterPage, previewChapterVersion, listReviewQueue | Chapter/Version/Page/VersionPage/UploadJob | TC-UP-01..05; TC-VER-01..04 | SW07 |
| M03-F09 | 2 | US-07 | updateChapter, createChapterVersion, listChapterVersions, getChapterVersion, reorderChapterPages, removeChapterPage, previewChapterVersion, listReviewQueue | Chapter/Version/Page/VersionPage/UploadJob | TC-UP-01..05; TC-VER-01..04 | SW07 |
| M03-F10 | 2 | US-08 | submitChapterVersion, reviewChapterVersion, publishChapter, unpublishChapter, setComicVisibility | Chapter/Version/Page/VersionPage/UploadJob | TC-UP-01..05; TC-VER-01..04 | SW08 |
| M03-F12 | 2 upload/job recovery; 3 published-version rollback | US-06 | createChapter, listManagedChapters, getManagedChapter, uploadChapterPages, getUploadJob, retryUploadJob | Chapter/Version/Page/VersionPage/UploadJob | TC-UP-01..05; TC-VER-01..04 | SW06 |
| M04-F01 | 2 | US-09 | getChapterAccess, getChapterManifest | ReadingProgress/Preference/HistoryState/ProgressMutation | TC-READ-01..04; TC-SYNC-01..05 | SW09 |
| M04-F02 | 2 | US-09 | getChapterAccess, getChapterManifest | ReadingProgress/Preference/HistoryState/ProgressMutation | TC-READ-01..04; TC-SYNC-01..05 | SW09 |
| M04-F03 | 2 | US-09 | getChapterAccess, getChapterManifest | ReadingProgress/Preference/HistoryState/ProgressMutation | TC-READ-01..04; TC-SYNC-01..05 | SW09 |
| M04-F04 | 2 | US-11 | getReadingPreferences, putReadingPreferences | ReadingProgress/Preference/HistoryState/ProgressMutation | TC-READ-01..04; TC-SYNC-01..05 | SW11 |
| M04-F08 | 2 | US-11 | getReadingPreferences, putReadingPreferences | ReadingProgress/Preference/HistoryState/ProgressMutation | TC-READ-01..04; TC-SYNC-01..05 | SW11 |
| M04-F09 | 2 | US-09 | getChapterAccess, getChapterManifest | ReadingProgress/Preference/HistoryState/ProgressMutation | TC-READ-01..04; TC-SYNC-01..05 | SW09 |
| M04-F10 | 2 | US-09 | getChapterAccess, getChapterManifest | ReadingProgress/Preference/HistoryState/ProgressMutation | TC-READ-01..04; TC-SYNC-01..05 | SW09 |
| M04-F12 | 2 | US-10 | getReadingProgress, putReadingProgress, getReadingHistory | ReadingProgress/Preference/HistoryState/ProgressMutation | TC-READ-01..04; TC-SYNC-01..05 | SW10 |
| M04-F13 | 2 | US-10 | getReadingProgress, putReadingProgress, getReadingHistory | ReadingProgress/Preference/HistoryState/ProgressMutation | TC-READ-01..04; TC-SYNC-01..05 | SW10 |
| M04-F14 | 2 | US-10 | getReadingProgress, putReadingProgress, getReadingHistory | ReadingProgress/Preference/HistoryState/ProgressMutation | TC-READ-01..04; TC-SYNC-01..05 | SW10 |
| M05-F01 | 2 | US-06, US-16 | systemPing, createChapter, listManagedChapters, getManagedChapter, uploadChapterPages, getUploadJob, retryUploadJob, listAuditLogs (các operation còn lại theo x-story trong OpenAPI) | DigitalAsset/AssetVariant/UploadJob | TC-UP-01..05; TC-OPS-01..04 | SW06, SW16 |
| M05-F02 | 2 | US-05, US-06 | createComic, updateComic, createTranslation, createChapter, createVolume, listManagedComics, getManagedComic, getManagedTranslation (các operation còn lại theo x-story trong OpenAPI) | DigitalAsset/AssetVariant/UploadJob | TC-UP-01..05; TC-OPS-01..04 | SW05, SW06 |
| M05-F04 | 2 | US-09 | getChapterAccess, getChapterManifest | DigitalAsset/AssetVariant/UploadJob | TC-UP-01..05; TC-OPS-01..04 | SW09 |
| M05-F05 | 2 | US-09 | getChapterAccess, getChapterManifest | DigitalAsset/AssetVariant/UploadJob | TC-UP-01..05; TC-OPS-01..04 | SW09 |
| M05-F06 | 2 | US-09 | getChapterAccess, getChapterManifest | DigitalAsset/AssetVariant/UploadJob | TC-UP-01..05; TC-OPS-01..04 | SW09 |
| M05-F09 | 2 staging/orphan recovery; 3 retention cleanup nâng cao | US-06, US-16 | systemPing, createChapter, listManagedChapters, getManagedChapter, uploadChapterPages, getUploadJob, retryUploadJob, listAuditLogs (các operation còn lại theo x-story trong OpenAPI) | DigitalAsset/AssetVariant/UploadJob | TC-UP-01..05; TC-OPS-01..04 | SW06, SW16 |
| M05-F11 | 2 | US-06, US-16 | systemPing, createChapter, listManagedChapters, getManagedChapter, uploadChapterPages, getUploadJob, retryUploadJob, listAuditLogs (các operation còn lại theo x-story trong OpenAPI) | DigitalAsset/AssetVariant/UploadJob | TC-UP-01..05; TC-OPS-01..04 | SW06, SW16 |
| M06-F05 | 2 | US-12 | searchComics | Catalog search projection/indexes | TC-UI-01..04; TC-SEC-01..05 | SW12 |
| M06-F06 | 2 updated date; 3 views/rating/popularity | US-12 | searchComics | Catalog search projection/indexes | TC-UI-01..04; TC-SEC-01..05 | SW12 |
| M06-F07 | 2 | US-12 | searchComics | Catalog search projection/indexes | TC-UI-01..04; TC-SEC-01..05 | SW12 |
| M08-F01 | 2 | US-13 | listCollections, createCollection, getCollection, updateCollection, deleteCollection, listCollectionItems, getCollectionItem, putCollectionItem (các operation còn lại theo x-story trong OpenAPI) | Collection/Item/Follow/Bookmark + reader progress | TC-LIB-01..03; TC-SYNC-01..05 | SW13 |
| M08-F02 | 2 | US-13 | listCollections, createCollection, getCollection, updateCollection, deleteCollection, listCollectionItems, getCollectionItem, putCollectionItem (các operation còn lại theo x-story trong OpenAPI) | Collection/Item/Follow/Bookmark + reader progress | TC-LIB-01..03; TC-SYNC-01..05 | SW13 |
| M08-F03 | 2 | US-10 | getReadingProgress, putReadingProgress, getReadingHistory | Collection/Item/Follow/Bookmark + reader progress | TC-LIB-01..03; TC-SYNC-01..05 | SW10 |
| M08-F04 | 2 | US-13 | listCollections, createCollection, getCollection, updateCollection, deleteCollection, listCollectionItems, getCollectionItem, putCollectionItem (các operation còn lại theo x-story trong OpenAPI) | Collection/Item/Follow/Bookmark + reader progress | TC-LIB-01..03; TC-SYNC-01..05 | SW13 |
| M08-F05 | 2 | US-13 | listCollections, createCollection, getCollection, updateCollection, deleteCollection, listCollectionItems, getCollectionItem, putCollectionItem (các operation còn lại theo x-story trong OpenAPI) | Collection/Item/Follow/Bookmark + reader progress | TC-LIB-01..03; TC-SYNC-01..05 | SW13 |
| M08-F06 | 2 | US-10 | getReadingProgress, putReadingProgress, getReadingHistory | Collection/Item/Follow/Bookmark + reader progress | TC-LIB-01..03; TC-SYNC-01..05 | SW10 |
| M08-F07 | 2 | US-14 | clearReadingHistory, listBookmarks, createBookmark, getBookmark, updateBookmark, deleteBookmark | Collection/Item/Follow/Bookmark + reader progress | TC-LIB-01..03; TC-SYNC-01..05 | SW14 |
| M08-F08 | 2 | US-10 | getReadingProgress, putReadingProgress, getReadingHistory | Collection/Item/Follow/Bookmark + reader progress | TC-LIB-01..03; TC-SYNC-01..05 | SW10 |
| M14-F01 | 2 nền; 5 đầy đủ | US-18 | getManagedUser, setUserRoles, listTeams, createTeam, getManagedTeam, setTeamMember, getTeamMember | CreatorTeam/TeamMember/TeamRole | TC-BOOT-01..04; TC-SEC-01..05 | SW18 |
| M14-F02 | 2 nền; 5 đầy đủ | US-18 | getManagedUser, setUserRoles, listTeams, createTeam, getManagedTeam, setTeamMember, getTeamMember | CreatorTeam/TeamMember/TeamRole | TC-BOOT-01..04; TC-SEC-01..05 | SW18 |
| M14-F03 | 2 nền; 5 đầy đủ | US-18 | getManagedUser, setUserRoles, listTeams, createTeam, getManagedTeam, setTeamMember, getTeamMember | CreatorTeam/TeamMember/TeamRole | TC-BOOT-01..04; TC-SEC-01..05 | SW18 |
| M14-F06 | 2 publishing API cơ bản; 5 creator portal đầy đủ | US-06, US-08 | createChapter, listManagedChapters, getManagedChapter, uploadChapterPages, getUploadJob, retryUploadJob, submitChapterVersion, reviewChapterVersion (các operation còn lại theo x-story trong OpenAPI) | CreatorTeam/TeamMember/TeamRole | TC-BOOT-01..04; TC-SEC-01..05 | SW06, SW08 |
| M15-F01 | 2 | US-15 | listUsers, getManagedUser, setUserStatus, setUserRoles, listMyTeams | UserRole/TeamMember/AuditLog | TC-SEC-01..05; TC-BOOT-01..04 | SW15 |
| M15-F02 | 2 | US-15 | listUsers, getManagedUser, setUserStatus, setUserRoles, listMyTeams | UserRole/TeamMember/AuditLog | TC-SEC-01..05; TC-BOOT-01..04 | SW15 |
| M15-F03 | 2 | US-00, US-15, US-18 | setupAdministrator, setUserRoles, getManagedUser, setTeamMember | UserRole/BootstrapState/TeamMember/AuditLog | TC-SEC-01..05; TC-BOOT-01..04 | SW00, SW15, SW18 |
| M15-F04 | 2 | US-08 | submitChapterVersion, reviewChapterVersion, publishChapter, unpublishChapter, setComicVisibility | UserRole/TeamMember/AuditLog | TC-SEC-01..05; TC-BOOT-01..04 | SW08 |
| M15-F07 | 2 nền; 4 tài chính; 7 đầy đủ | US-15 | listUsers, getManagedUser, setUserStatus, setUserRoles, listMyTeams | UserRole/TeamMember/AuditLog | TC-SEC-01..05; TC-BOOT-01..04 | SW15 |
| M15-F08 | 2 | US-16 | systemPing, listAuditLogs, listUploadJobs | UserRole/TeamMember/AuditLog | TC-SEC-01..05; TC-BOOT-01..04 | SW16 |
| M15-F10 | 2 | US-08 | submitChapterVersion, reviewChapterVersion, publishChapter, unpublishChapter, setComicVisibility | UserRole/TeamMember/AuditLog | TC-SEC-01..05; TC-BOOT-01..04 | SW08 |
| M15-F11 | 2 | US-15 | listUsers, getManagedUser, setUserStatus, setUserRoles, listMyTeams | UserRole/TeamMember/AuditLog | TC-SEC-01..05; TC-BOOT-01..04 | SW15 |
| M20-F07 | 2 FREE/RESTRICTED; 4 thương mại | US-09 | getChapterAccess, getChapterManifest | Chapter.AccessPolicy + access service (grant tables P4) | TC-READ-01..04 | SW09 |
| M21-F09 | 2 | US-16 | systemPing, listAuditLogs, listUploadJobs | AuditLog/BackgroundJob/Outbox + logs | TC-OPS-01..04 | SW16 |
| M21-F10 | 2 | US-16 | systemPing, listAuditLogs, listUploadJobs | AuditLog/BackgroundJob/Outbox + logs | TC-OPS-01..04 | SW16 |
| M21-F11 | 2 | US-16 | systemPing, listAuditLogs, listUploadJobs | AuditLog/BackgroundJob/Outbox + logs | TC-OPS-01..04 | SW16 |
| M22-F02 | 2 mẫu; 7 đầy đủ | US-16 | systemPing, listAuditLogs, listUploadJobs | Backup manifest/config/restore evidence | TC-OPS-01..04 | SW16 |
| M22-F07 | 2 mẫu; 7 đầy đủ | US-16 | systemPing, listAuditLogs, listUploadJobs | Backup manifest/config/restore evidence | TC-OPS-01..04 | SW16 |
| M22-F08 | 2 mẫu; 7 đầy đủ | US-16 | systemPing, listAuditLogs, listUploadJobs | Backup manifest/config/restore evidence | TC-OPS-01..04 | SW16 |

**74 features có phần MVP**. US-00 bootstrap qua wizard Swing SW00 và setupAdministrator capability-gated local REST đã được user duyệt. US-18 sở hữu initial team/role provisioning; setUserRoles/getManagedUser là operations dùng chung US18/15. US-17 Swing cross-cutting có TC-UI/TC-SW và screen map; không business endpoint riêng. Foundation Phase1: M21-F01/F02/F04 → P1-04 + SEC-P1-01/05/07; M22-F01/F09 → P1-03/09 + SEC-P1-09.

Mỗi operation được gán x-story/x-stories trong OpenAPI; schema/permission/transaction của operation là nguồn contract chi tiết. Phần workflow/AC chi tiết không chỉ dựa feature count.

Tất cả74MVP feature rows có Swing view mapping;19stories phủ SW00..18 tại design/swing-screen-map.md.91operations design, gồm setupAdministrator; source243features/23modules không đổi. US16 ops backup/restore procedure theo user đã đồng ý, không UI SQL executor. Không Node/Vite/React gate.
