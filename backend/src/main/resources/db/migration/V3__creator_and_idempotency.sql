CREATE TABLE dbo.CreatorTeam (
 Id bigint IDENTITY PRIMARY KEY, OwnerUserId bigint NOT NULL REFERENCES dbo.[User](Id),
 Name nvarchar(200) NOT NULL, Slug varchar(200) NOT NULL UNIQUE,
 Status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK(Status IN ('ACTIVE','DISABLED')),
 RowVersion rowversion NOT NULL
);
DECLARE @AuditMetadataDefault sysname;
SELECT @AuditMetadataDefault=d.name FROM sys.default_constraints d
 JOIN sys.columns c ON c.object_id=d.parent_object_id AND c.column_id=d.parent_column_id
 WHERE d.parent_object_id=OBJECT_ID(N'dbo.AuditLog') AND c.name=N'SafeMetadata';
IF @AuditMetadataDefault IS NOT NULL
BEGIN
 DECLARE @DropAuditMetadataDefault nvarchar(400)=N'ALTER TABLE dbo.AuditLog DROP CONSTRAINT '+QUOTENAME(@AuditMetadataDefault);
 EXEC sys.sp_executesql @DropAuditMetadataDefault;
END;
ALTER TABLE dbo.AuditLog ALTER COLUMN SafeMetadata nvarchar(max) NOT NULL;
ALTER TABLE dbo.AuditLog ADD CONSTRAINT DF_AuditLog_SafeMetadata DEFAULT '{}' FOR SafeMetadata;
CREATE TABLE dbo.TeamMember (
 Id bigint IDENTITY PRIMARY KEY, TeamId bigint NOT NULL REFERENCES dbo.CreatorTeam(Id),
 UserId bigint NOT NULL REFERENCES dbo.[User](Id), Status varchar(20) NOT NULL CHECK(Status IN ('ACTIVE','REMOVED')),
 JoinedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(), RowVersion rowversion NOT NULL,
 CONSTRAINT UQ_TeamMember UNIQUE(TeamId,UserId)
);
CREATE TABLE dbo.TeamRole (Id bigint IDENTITY PRIMARY KEY, Code varchar(30) NOT NULL UNIQUE);
CREATE TABLE dbo.TeamMemberRole (MemberId bigint NOT NULL REFERENCES dbo.TeamMember(Id), TeamRoleId bigint NOT NULL REFERENCES dbo.TeamRole(Id), PRIMARY KEY(MemberId,TeamRoleId));
INSERT dbo.TeamRole(Code) VALUES('OWNER'),('CREATOR'),('TRANSLATOR'),('EDITOR'),('PROOFREADER'),('UPLOADER');
CREATE TABLE dbo.IdempotencyRecord (
 Id bigint IDENTITY PRIMARY KEY, UserId bigint NOT NULL REFERENCES dbo.[User](Id), Operation varchar(80) NOT NULL,
 ScopeId varchar(100) NOT NULL, RequestKey uniqueidentifier NOT NULL, PayloadHash char(64) NOT NULL,
 ResourceId bigint NOT NULL, CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 CONSTRAINT UQ_IdempotencyRecord UNIQUE(UserId,Operation,ScopeId,RequestKey)
);
GO
