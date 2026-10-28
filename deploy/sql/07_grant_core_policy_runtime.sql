/* Named object permissions for the ERD policy tables after Flyway V5. */
USE [ComicNetDev];
IF USER_NAME()<>N'dbo' OR @@TRANCOUNT<>0 OR XACT_STATE()=-1
 THROW 51012,'Use a new administrator query with no pending transaction.',1;
IF OBJECT_ID(N'dbo.AssetAccessPolicy',N'U') IS NULL THROW 51013,'Apply Flyway V5 first.',1;
GRANT SELECT ON OBJECT::dbo.TeamRolePermission TO comicnet_runtime;
GRANT SELECT,INSERT ON OBJECT::dbo.AssetAccessPolicy TO comicnet_runtime;
SELECT N'CONFIGURED' AS CorePolicyRuntimePermissions;
