/* Run as ComicNetDev administrator after Flyway V29. */
USE [ComicNetDev];
GO
GRANT SELECT, INSERT, UPDATE, DELETE ON OBJECT::dbo.ReadingExpHistory TO [comicnet_runtime];
GO
