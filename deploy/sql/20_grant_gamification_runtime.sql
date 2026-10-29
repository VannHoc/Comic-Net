/* Run as ComicNetDev administrator after Flyway V28. */
USE [ComicNetDev];
GO
GRANT SELECT, INSERT, UPDATE, DELETE ON OBJECT::dbo.UserGamification TO [comicnet_runtime];
GRANT SELECT, INSERT, UPDATE, DELETE ON OBJECT::dbo.CheckInHistory TO [comicnet_runtime];
GRANT SELECT, INSERT, UPDATE, DELETE ON OBJECT::dbo.LuckyWheelHistory TO [comicnet_runtime];
GO
