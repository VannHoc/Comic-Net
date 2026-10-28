/* Execute as the existing ComicNetDev setup administrator after Flyway V3. */
USE [ComicNetDev];
IF USER_NAME()<>N'dbo' OR @@TRANCOUNT<>0 OR XACT_STATE()=-1
 THROW 51012,'Use a new administrator query with no pending transaction.',1;
IF OBJECT_ID(N'dbo.CreatorTeam',N'U') IS NULL THROW 51013,'Apply Flyway V3 first.',1;
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.CreatorTeam TO comicnet_runtime;
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.TeamMember TO comicnet_runtime;
GRANT SELECT ON OBJECT::dbo.TeamRole TO comicnet_runtime;
GRANT SELECT,INSERT,DELETE ON OBJECT::dbo.TeamMemberRole TO comicnet_runtime;
GRANT SELECT,INSERT ON OBJECT::dbo.IdempotencyRecord TO comicnet_runtime;
SELECT N'CONFIGURED' AS CreatorRuntimePermissions;
