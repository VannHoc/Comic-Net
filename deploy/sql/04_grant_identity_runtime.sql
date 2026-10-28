/* Phase 2 identity/runtime permissions. Run as setup administrator after Flyway V2.
   Only named tables; no schema-wide grant, CONTROL or GRANT OPTION. Audit is append-only. */
USE [ComicNetDev];
IF USER_NAME()<>N'dbo' OR @@TRANCOUNT<>0 OR XACT_STATE()=-1
    THROW 51012,'Open a new administrator query with no pending transaction.',1;
IF OBJECT_ID(N'dbo.BootstrapState',N'U') IS NULL
    THROW 51013,'Apply Flyway V2 before these runtime grants.',1;
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.[User] TO comicnet_runtime;
GRANT SELECT ON OBJECT::dbo.[Role] TO comicnet_runtime;
GRANT SELECT ON OBJECT::dbo.Permission TO comicnet_runtime;
GRANT SELECT ON OBJECT::dbo.RolePermission TO comicnet_runtime;
GRANT SELECT,INSERT,DELETE ON OBJECT::dbo.UserRole TO comicnet_runtime;
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.[Session] TO comicnet_runtime;
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.RefreshToken TO comicnet_runtime;
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.AccountActionToken TO comicnet_runtime;
GRANT SELECT,UPDATE ON OBJECT::dbo.BootstrapState TO comicnet_runtime;
GRANT SELECT,INSERT ON OBJECT::dbo.AuditLog TO comicnet_runtime;
GRANT SELECT,INSERT,UPDATE ON OBJECT::dbo.AccountMailJob TO comicnet_runtime;
SELECT N'CONFIGURED' AS IdentityRuntimePermissions;
