CREATE TABLE dbo.SystemFoundation (
    Id tinyint NOT NULL CONSTRAINT PK_SystemFoundation PRIMARY KEY,
    SchemaVersion int NOT NULL,
    InstalledAt datetime2(3) NOT NULL CONSTRAINT DF_SystemFoundation_InstalledAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT CK_SystemFoundation_Singleton CHECK (Id = 1)
);
GO

INSERT INTO dbo.SystemFoundation (Id, SchemaVersion) VALUES (1, 1);
GO

-- Runtime object permissions are provisioned by the database administrator with
-- deploy/sql/03_grant_foundation_runtime.sql after the first migration.
-- The migration login needs DDL permissions, not schema-wide permission administration.
