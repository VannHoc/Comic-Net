/* Named object permissions after Flyway V4. Run with the existing setup administrator. */
USE [ComicNetDev];
IF USER_NAME()<>N'dbo' OR @@TRANCOUNT<>0 OR XACT_STATE()=-1
 THROW 51012,'Use a new administrator query with no pending transaction.',1;
IF OBJECT_ID(N'dbo.AssetDerivative',N'U') IS NULL THROW 51013,'Apply Flyway V4 first.',1;
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.Asset TO comicnet_runtime;
GRANT SELECT,INSERT ON OBJECT::dbo.AssetDerivative TO comicnet_runtime;
SELECT N'CONFIGURED' AS StorageRuntimePermissions;
