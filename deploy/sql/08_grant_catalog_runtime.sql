/* Named catalog tables only, after Flyway V6. */
USE [ComicNetDev];
IF USER_NAME()<>N'dbo' OR @@TRANCOUNT<>0 OR XACT_STATE()=-1
 THROW 51012,'Use a new administrator query with no pending transaction.',1;
IF OBJECT_ID(N'dbo.ComicTranslation',N'U') IS NULL THROW 51013,'Apply Flyway V6 first.',1;
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.Comic TO comicnet_runtime;
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.ComicTranslation TO comicnet_runtime;
GRANT SELECT,INSERT ON OBJECT::dbo.Author TO comicnet_runtime;
GRANT SELECT,INSERT ON OBJECT::dbo.Publisher TO comicnet_runtime;
GRANT SELECT,INSERT ON OBJECT::dbo.Genre TO comicnet_runtime;
GRANT SELECT,INSERT ON OBJECT::dbo.Tag TO comicnet_runtime;
GRANT SELECT,INSERT ON OBJECT::dbo.ContentWarning TO comicnet_runtime;
GRANT SELECT,INSERT,DELETE ON OBJECT::dbo.ComicAlternativeTitle TO comicnet_runtime;
GRANT SELECT,INSERT,DELETE ON OBJECT::dbo.ComicAuthor TO comicnet_runtime;
GRANT SELECT,INSERT,DELETE ON OBJECT::dbo.ComicPublisher TO comicnet_runtime;
GRANT SELECT,INSERT,DELETE ON OBJECT::dbo.ComicGenre TO comicnet_runtime;
GRANT SELECT,INSERT,DELETE ON OBJECT::dbo.ComicTag TO comicnet_runtime;
GRANT SELECT,INSERT,DELETE ON OBJECT::dbo.ComicContentWarning TO comicnet_runtime;
SELECT N'CONFIGURED' AS CatalogRuntimePermissions;
