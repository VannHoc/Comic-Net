/* Run as ComicNetDev administrator after Flyway V22. */
USE [ComicNetDev];
GO
IF OBJECT_ID(N'dbo.PaymentOrder',N'U') IS NULL OR OBJECT_ID(N'dbo.CoinPackage',N'U') IS NULL
 THROW 51063,'Run Flyway V22 before local payment grants.',1;
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.CoinPackage TO [comicnet_runtime];
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.PaymentOrder TO [comicnet_runtime];
GRANT SELECT,INSERT ON OBJECT::dbo.PaymentTransaction TO [comicnet_runtime];
GRANT SELECT,INSERT ON OBJECT::dbo.WebhookInbox TO [comicnet_runtime];
GRANT SELECT,INSERT ON OBJECT::dbo.ReconciliationRecord TO [comicnet_runtime];
IF OBJECT_ID(N'dbo.PaymentOrderEvent',N'U') IS NOT NULL
 GRANT SELECT,INSERT ON OBJECT::dbo.PaymentOrderEvent TO [comicnet_runtime];
GO
