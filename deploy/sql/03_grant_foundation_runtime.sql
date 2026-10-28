/* Run after Flyway V1 creates dbo.SystemFoundation, as the setup administrator.
   Grants SELECT on this single table to the runtime role. No schema-wide grant,
   GRANT OPTION, role membership, login, password or server setting is changed. */
USE [ComicNetDev];
SET NOCOUNT ON;

-- XACT_STATE can be 1 inside an autocommit statement while @@TRANCOUNT is 0.
-- Reject explicit pending transactions or a doomed transaction, not normal autocommit.
IF DB_NAME() <> N'ComicNetDev' OR USER_NAME() <> N'dbo' OR @@TRANCOUNT <> 0 OR XACT_STATE() = -1
    THROW 51012, 'Open a NEW administrator query connection to ComicNetDev with no pending transaction.', 1;
IF OBJECT_ID(N'dbo.SystemFoundation', N'U') IS NULL
    THROW 51013, 'Flyway V1 must create dbo.SystemFoundation before applying this grant.', 1;
IF DATABASE_PRINCIPAL_ID(N'comicnet_runtime') IS NULL
    THROW 51011, 'Runtime role is missing. No changes were made.', 1;

GRANT SELECT ON OBJECT::dbo.SystemFoundation TO [comicnet_runtime];

SELECT N'CONFIGURED' AS FoundationRuntimeSelect;
