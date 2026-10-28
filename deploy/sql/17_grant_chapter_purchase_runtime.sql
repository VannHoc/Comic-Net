/* Run as ComicNetDev administrator after Flyway V21. */
USE [ComicNetDev];
GO
IF OBJECT_ID(N'dbo.ChapterPrice',N'U') IS NULL OR OBJECT_ID(N'dbo.UserEntitlement',N'U') IS NULL
 THROW 51062,'Run Flyway V21 before chapter purchase grants.',1;
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.ChapterPrice TO [comicnet_runtime];
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.Purchase TO [comicnet_runtime];
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.UserEntitlement TO [comicnet_runtime];
GRANT SELECT,INSERT ON OBJECT::dbo.EntitlementGrant TO [comicnet_runtime];
GRANT SELECT,INSERT ON OBJECT::dbo.EntitlementRevocation TO [comicnet_runtime];
GO
