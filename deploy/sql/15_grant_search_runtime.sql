USE [ComicNetDev];
GO
IF OBJECT_ID(N'dbo.SearchHistory',N'U') IS NULL
 THROW 51041,'Apply Flyway V17 before granting search-history permissions.',1;
GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.SearchHistory TO [comicnet_runtime];
