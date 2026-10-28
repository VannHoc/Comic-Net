/* Named reading-state objects after Flyway V9. */
USE [ComicNetDev];
IF USER_NAME()<>N'dbo' OR @@TRANCOUNT<>0 OR XACT_STATE()=-1
 THROW 51012,'Use a new administrator query with no pending transaction.',1;
IF OBJECT_ID(N'dbo.ReadingHistoryState',N'U') IS NULL THROW 51013,'Apply Flyway V9 first.',1;
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.ReadingHistoryState TO comicnet_runtime;
GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.ReadingProgress TO comicnet_runtime;
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.ProgressMutation TO comicnet_runtime;
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.ReadingPreference TO comicnet_runtime;
SELECT N'CONFIGURED' AS ReadingRuntimePermissions;
