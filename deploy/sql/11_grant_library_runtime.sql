/* Exact library tables, after Flyway V11. */
USE [ComicNetDev];
IF USER_NAME()<>N'dbo' OR @@TRANCOUNT<>0 OR XACT_STATE()=-1
 THROW 51012,'Use a new administrator query with no pending transaction.',1;
IF OBJECT_ID(N'dbo.LibraryCollection',N'U') IS NULL THROW 51013,'Apply Flyway V11 first.',1;
GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.LibraryCollection TO comicnet_runtime;
GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.CollectionItem TO comicnet_runtime;
GRANT SELECT,INSERT,DELETE ON OBJECT::dbo.ComicFollow TO comicnet_runtime;
GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.Bookmark TO comicnet_runtime;
SELECT N'CONFIGURED' AS LibraryRuntimePermissions;
