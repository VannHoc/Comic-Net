/* Run in SSMS as the existing database administrator after migrations V12/V13.
   Grants only the new Phase 3 tables; does not grant schema-wide or DDL rights. */
USE [ComicNetDev];
GO
IF OBJECT_ID(N'dbo.ComicRating',N'U') IS NULL OR OBJECT_ID(N'dbo.Comment',N'U') IS NULL
 THROW 51040,'Run Flyway V12/V13 before granting Phase 3 runtime permissions.',1;
GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.ComicRating TO [comicnet_runtime];
GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.Comment TO [comicnet_runtime];
GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.CommentReaction TO [comicnet_runtime];
GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.CommentReport TO [comicnet_runtime];
GRANT SELECT,INSERT,UPDATE,DELETE ON OBJECT::dbo.UserBlock TO [comicnet_runtime];
GO
