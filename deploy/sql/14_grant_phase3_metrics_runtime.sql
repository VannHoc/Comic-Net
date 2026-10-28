/* Run in SSMS as the existing database administrator after migrations V14/V15/V16.
   Grants only the new Phase 3 analytics, notification and scheduling tables.
   No schema-wide, DDL or permission-delegation rights are added. */
USE [ComicNetDev];
GO
IF OBJECT_ID(N'dbo.ComicView',N'U') IS NULL OR OBJECT_ID(N'dbo.Notification',N'U') IS NULL
 THROW 51050,'Run Flyway V14/V15 before granting Phase 3 metrics runtime permissions.',1;
GRANT SELECT,INSERT ON OBJECT::dbo.ComicView TO [comicnet_runtime];
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.ComicStatDaily TO [comicnet_runtime];
GRANT SELECT,INSERT ON OBJECT::dbo.ChapterCompletion TO [comicnet_runtime];
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.Notification TO [comicnet_runtime];
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.NotificationPreference TO [comicnet_runtime];
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.Announcement TO [comicnet_runtime];
/* The notification worker consumes outbox rows; it never deletes them. */
GRANT UPDATE ON OBJECT::dbo.OutboxEvent TO [comicnet_runtime];
GO
