/* Run in SSMS as a SQL administrator. Do not save filled passwords or commit this buffer.
   This script is development-only. It never changes mixed mode, ports or TLS settings.
   Choose two different strong passwords locally, execute, then discard the edited buffer.
   Refuses to alter existing logins/users. Review before running against an existing DB. */
USE master;
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @RuntimePassword nvarchar(128) = N'REPLACE_LOCALLY_RUNTIME_PASSWORD';
DECLARE @MigrationPassword nvarchar(128) = N'REPLACE_LOCALLY_MIGRATION_PASSWORD';

IF @RuntimePassword LIKE N'REPLACE_%' OR @MigrationPassword LIKE N'REPLACE_%'
    THROW 51000, 'Replace both password placeholders locally in SSMS; do not send passwords in chat.', 1;
IF LEN(@RuntimePassword) < 20 OR LEN(@MigrationPassword) < 20 OR @RuntimePassword = @MigrationPassword
    THROW 51001, 'Use two different passwords, each at least 20 characters.', 1;
IF CONVERT(int, SERVERPROPERTY('IsIntegratedSecurityOnly')) = 1
    THROW 51002, 'SQL Authentication is disabled. Review server authentication settings manually.', 1;
IF SUSER_ID(N'comicnet_app') IS NOT NULL OR SUSER_ID(N'comicnet_migrator') IS NOT NULL
    THROW 51003, 'A target login exists. Stop and review; this script does not reset existing accounts.', 1;

IF DB_ID(N'ComicNetDev') IS NULL EXEC(N'CREATE DATABASE [ComicNetDev]');

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @Sql nvarchar(max);
    SET @Sql = N'CREATE LOGIN [comicnet_app] WITH PASSWORD=N' + QUOTENAME(@RuntimePassword, '''')
        + N', CHECK_POLICY=ON, CHECK_EXPIRATION=OFF, DEFAULT_DATABASE=[ComicNetDev];';
    EXEC sys.sp_executesql @Sql;
    SET @Sql = N'CREATE LOGIN [comicnet_migrator] WITH PASSWORD=N' + QUOTENAME(@MigrationPassword, '''')
        + N', CHECK_POLICY=ON, CHECK_EXPIRATION=OFF, DEFAULT_DATABASE=[ComicNetDev];';
    EXEC sys.sp_executesql @Sql;
    /* Keep database context inside this dynamic batch. No GO may bypass a failed guard. */
    EXEC(N'USE [ComicNetDev];
        IF DATABASE_PRINCIPAL_ID(N''comicnet_app'') IS NOT NULL
           OR DATABASE_PRINCIPAL_ID(N''comicnet_migrator'') IS NOT NULL
           OR DATABASE_PRINCIPAL_ID(N''comicnet_runtime'') IS NOT NULL
           OR DATABASE_PRINCIPAL_ID(N''comicnet_migration'') IS NOT NULL
            THROW 51004, ''A target database principal exists. Stop and review.'', 1;
        CREATE USER [comicnet_app] FOR LOGIN [comicnet_app];
        CREATE USER [comicnet_migrator] FOR LOGIN [comicnet_migrator];
        CREATE ROLE [comicnet_runtime] AUTHORIZATION dbo;
        CREATE ROLE [comicnet_migration] AUTHORIZATION dbo;
        ALTER ROLE [comicnet_runtime] ADD MEMBER [comicnet_app];
        ALTER ROLE [comicnet_migration] ADD MEMBER [comicnet_migrator];
        GRANT CONNECT TO [comicnet_runtime];
        GRANT CONNECT TO [comicnet_migration];
        GRANT CREATE TABLE TO [comicnet_migration];
        GRANT ALTER, REFERENCES, INSERT, UPDATE, DELETE ON SCHEMA::dbo TO [comicnet_migration];
        GRANT SELECT ON SCHEMA::dbo TO [comicnet_migration];
        GRANT VIEW DEFINITION TO [comicnet_migration];');
    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
/* Runtime has no DDL or broad schema DML. After Flyway V1, an administrator runs
   03_grant_foundation_runtime.sql to grant SELECT on its single foundation table.
   A newly created empty database can remain if later provisioning fails; no existing DB is dropped. */
