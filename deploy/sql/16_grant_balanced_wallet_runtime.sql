/* Run as ComicNetDev administrator after Flyway V20; object-scoped permissions only. */
USE [ComicNetDev];
GO
IF OBJECT_ID(N'dbo.WalletAccount',N'U') IS NULL OR OBJECT_ID(N'dbo.LedgerEntry',N'U') IS NULL
 THROW 51061,'Run Flyway V20 before balanced wallet grants.',1;
GRANT SELECT ON OBJECT::dbo.CurrencyDefinition TO [comicnet_runtime];
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.WalletAccount TO [comicnet_runtime];
GRANT SELECT,INSERT ON OBJECT::dbo.LedgerEntry TO [comicnet_runtime];
GO
