CREATE TABLE dbo.[User] (
 Id bigint IDENTITY PRIMARY KEY, Email nvarchar(320) NOT NULL,
 NormalizedEmail nvarchar(320) COLLATE Latin1_General_100_BIN2 NOT NULL UNIQUE,
 PasswordHash nvarchar(500) NULL, DisplayName nvarchar(100) NOT NULL,
 Status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK(Status IN ('ACTIVE','SUSPENDED','DELETED')),
 EmailVerifiedAt datetime2(3) NULL, AuthVersion bigint NOT NULL DEFAULT 0,
 LockVersion bigint NOT NULL DEFAULT 0, CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 RowVersion rowversion NOT NULL
);
CREATE TABLE dbo.[Role] (Id bigint IDENTITY PRIMARY KEY, Code varchar(40) NOT NULL UNIQUE);
CREATE TABLE dbo.Permission (Id bigint IDENTITY PRIMARY KEY, Code varchar(100) NOT NULL UNIQUE);
CREATE TABLE dbo.UserRole (UserId bigint NOT NULL REFERENCES dbo.[User](Id), RoleId bigint NOT NULL REFERENCES dbo.[Role](Id), PRIMARY KEY(UserId,RoleId));
CREATE TABLE dbo.RolePermission (RoleId bigint NOT NULL REFERENCES dbo.[Role](Id), PermissionId bigint NOT NULL REFERENCES dbo.Permission(Id), PRIMARY KEY(RoleId,PermissionId));
INSERT dbo.[Role](Code) VALUES ('READER'),('CREATOR'),('MODERATOR'),('ADMINISTRATOR');
INSERT dbo.Permission(Code) VALUES ('profile.self'),('library.self'),('catalog.create'),('catalog.manage'),('chapter.draft'),('chapter.upload'),('chapter.preview'),('chapter.submit'),('chapter.publish'),('chapter.review'),('content.hide'),('team.manage'),('users.suspend'),('roles.manage'),('ops.read');
INSERT dbo.RolePermission SELECT r.Id,p.Id FROM dbo.[Role] r CROSS JOIN dbo.Permission p
 WHERE r.Code='ADMINISTRATOR' OR (r.Code='READER' AND p.Code IN ('profile.self','library.self'))
 OR (r.Code='CREATOR' AND p.Code='catalog.create')
 OR (r.Code='MODERATOR' AND p.Code IN ('chapter.review','chapter.publish','chapter.preview','content.hide'));
CREATE TABLE dbo.[Session] (
 Id bigint IDENTITY PRIMARY KEY, UserId bigint NOT NULL REFERENCES dbo.[User](Id), DeviceLabel nvarchar(100) NOT NULL,
 ExpiresAt datetime2(3) NOT NULL, RevokedAt datetime2(3) NULL, LastSeenAt datetime2(3) NOT NULL,
 CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME()
);
CREATE INDEX IX_Session_User ON dbo.[Session](UserId,RevokedAt);
CREATE TABLE dbo.RefreshToken (
 Id bigint IDENTITY PRIMARY KEY, SessionId bigint NOT NULL REFERENCES dbo.[Session](Id),
 TokenHash char(64) COLLATE Latin1_General_100_BIN2 NOT NULL UNIQUE,
 ParentTokenId bigint NULL REFERENCES dbo.RefreshToken(Id), ExpiresAt datetime2(3) NOT NULL,
 UsedAt datetime2(3) NULL, RevokedAt datetime2(3) NULL
);
CREATE TABLE dbo.AccountActionToken (
 Id bigint IDENTITY PRIMARY KEY, UserId bigint NOT NULL REFERENCES dbo.[User](Id),
 Purpose varchar(30) NOT NULL CHECK(Purpose IN ('VERIFY_EMAIL','RESET_PASSWORD')),
 TokenHash char(64) COLLATE Latin1_General_100_BIN2 NOT NULL UNIQUE, ExpiresAt datetime2(3) NOT NULL,
 ConsumedAt datetime2(3) NULL, CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME()
);
CREATE TABLE dbo.BootstrapState (
 Id tinyint NOT NULL PRIMARY KEY CHECK(Id=1), InitializedAt datetime2(3) NULL,
 AdministratorUserId bigint NULL REFERENCES dbo.[User](Id), SetupGeneration bigint NOT NULL DEFAULT 0,
 RowVersion rowversion NOT NULL
);
INSERT dbo.BootstrapState(Id) VALUES(1);
CREATE TABLE dbo.AuditLog (
 Id bigint IDENTITY PRIMARY KEY, ActorUserId bigint NULL REFERENCES dbo.[User](Id),
 Action varchar(100) NOT NULL, TargetType varchar(50) NOT NULL, TargetId nvarchar(100) NULL,
 Outcome varchar(50) NOT NULL, RequestId varchar(100) NOT NULL, SafeMetadata nvarchar(2000) NOT NULL DEFAULT '{}',
 CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME()
);
CREATE INDEX IX_Audit_Target ON dbo.AuditLog(TargetType,TargetId,CreatedAt);
CREATE TABLE dbo.AccountMailJob (
 Id bigint IDENTITY PRIMARY KEY, Recipient nvarchar(320) NOT NULL, Subject nvarchar(200) NOT NULL,
 EncryptedBody nvarchar(max) NOT NULL, State varchar(20) NOT NULL DEFAULT 'QUEUED',
 Attempts int NOT NULL DEFAULT 0, LeaseUntil datetime2(3) NULL, NextAttemptAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 ErrorCode varchar(100) NULL, CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(), SentAt datetime2(3) NULL
);
CREATE INDEX IX_AccountMailJob_State ON dbo.AccountMailJob(State,NextAttemptAt);
GO
