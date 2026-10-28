/* Exact table permissions after Flyway V8. No new schema or server permission. */
USE [ComicNetDev];
IF USER_NAME()<>N'dbo' OR @@TRANCOUNT<>0 OR XACT_STATE()=-1
 THROW 51012,'Use a new administrator query with no pending transaction.',1;
IF OBJECT_ID(N'dbo.UploadJobItem',N'U') IS NULL THROW 51013,'Apply Flyway V7/V8 first.',1;
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.Volume TO comicnet_runtime;
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.Chapter TO comicnet_runtime;
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.ChapterVersion TO comicnet_runtime;
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.ChapterPage TO comicnet_runtime;
GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.ChapterVersionPage TO comicnet_runtime;
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.UploadJob TO comicnet_runtime;
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.UploadJobItem TO comicnet_runtime;
GRANT SELECT,INSERT ON OBJECT::dbo.OutboxEvent TO comicnet_runtime;
SELECT N'CONFIGURED' AS PublishingRuntimePermissions;
