/* Preserve the approved ERD names and ownership; additive migration after V4. */
EXEC sys.sp_rename N'dbo.Asset',N'DigitalAsset';
EXEC sys.sp_rename N'dbo.AssetDerivative',N'AssetVariant';
EXEC sys.sp_rename N'dbo.DigitalAsset.SourceKey',N'ObjectKey',N'COLUMN';
EXEC sys.sp_rename N'dbo.DigitalAsset.SourceHash',N'SHA256',N'COLUMN';
EXEC sys.sp_rename N'dbo.DigitalAsset.MimeType',N'DetectedMime',N'COLUMN';
ALTER TABLE dbo.DigitalAsset ADD Bucket varchar(63) NOT NULL CONSTRAINT DF_DigitalAsset_Bucket DEFAULT 'comicnet-dev', RowVersion rowversion NOT NULL;
CREATE TABLE dbo.AssetAccessPolicy (
 AssetId bigint NOT NULL PRIMARY KEY REFERENCES dbo.DigitalAsset(Id),
 Policy varchar(10) NOT NULL CHECK(Policy IN ('PRIVATE','PUBLIC')),
 UrlTtlSeconds int NOT NULL CHECK(UrlTtlSeconds>0)
);
INSERT dbo.AssetAccessPolicy(AssetId,Policy,UrlTtlSeconds) SELECT Id,'PRIVATE',120 FROM dbo.DigitalAsset;
INSERT dbo.Permission(Code) SELECT 'chapter.translate' WHERE NOT EXISTS(SELECT 1 FROM dbo.Permission WHERE Code='chapter.translate');
CREATE TABLE dbo.TeamRolePermission (
 TeamRoleId bigint NOT NULL REFERENCES dbo.TeamRole(Id), PermissionId bigint NOT NULL REFERENCES dbo.Permission(Id),
 PRIMARY KEY(TeamRoleId,PermissionId)
);
INSERT dbo.TeamRolePermission(TeamRoleId,PermissionId)
 SELECT r.Id,p.Id FROM dbo.TeamRole r CROSS JOIN dbo.Permission p WHERE
 (r.Code='OWNER' AND p.Code='team.manage') OR
 (r.Code IN ('OWNER','CREATOR') AND p.Code IN ('catalog.manage','chapter.draft','chapter.upload','chapter.preview','chapter.submit','chapter.publish','chapter.translate')) OR
 (r.Code='TRANSLATOR' AND p.Code IN ('chapter.draft','chapter.preview','chapter.translate')) OR
 (r.Code='EDITOR' AND p.Code IN ('chapter.draft','chapter.upload','chapter.preview','chapter.submit')) OR
 (r.Code='PROOFREADER' AND p.Code='chapter.preview') OR
 (r.Code='UPLOADER' AND p.Code IN ('chapter.draft','chapter.upload','chapter.preview','chapter.submit'));
EXEC sys.sp_rename N'dbo.IdempotencyRecord.UserId',N'ActorId',N'COLUMN';
EXEC sys.sp_rename N'dbo.IdempotencyRecord.PayloadHash',N'RequestHash',N'COLUMN';
ALTER TABLE dbo.IdempotencyRecord ADD State varchar(20) NOT NULL CONSTRAINT DF_Idempotency_State DEFAULT 'COMMITTED',
 ResponseRef nvarchar(300) NULL, ExpiresAt datetime2(3) NOT NULL CONSTRAINT DF_Idempotency_Expires DEFAULT DATEADD(day,1,SYSUTCDATETIME());
GO
