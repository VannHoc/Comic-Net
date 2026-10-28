/* Run as ComicNetDev administrator after Flyway V19; object-scoped permissions only. */
USE [ComicNetDev];
GO
IF OBJECT_ID(N'dbo.Wallet',N'U') IS NULL OR OBJECT_ID(N'dbo.WalletTransaction',N'U') IS NULL
 THROW 51060,'Run Flyway V19 before wallet grants.',1;
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.Wallet TO [comicnet_runtime];
GRANT SELECT,INSERT ON OBJECT::dbo.WalletTransaction TO [comicnet_runtime];
GO
