ALTER TABLE dbo.AssetVariant DROP CONSTRAINT UQ_AssetDerivative;
ALTER TABLE dbo.AssetVariant ADD CONSTRAINT UQ_AssetVariant_Dimensions UNIQUE(AssetId,Format,Width,Height);
CREATE TABLE dbo.Volume (
 Id bigint IDENTITY PRIMARY KEY,TranslationId bigint NOT NULL REFERENCES dbo.ComicTranslation(Id),
 DisplayLabel nvarchar(100) NOT NULL,SortOrder decimal(12,4) NOT NULL CHECK(SortOrder>=0),RowVersion rowversion NOT NULL,
 CONSTRAINT UQ_Volume_Order UNIQUE(TranslationId,SortOrder),CONSTRAINT UQ_Volume_Parent UNIQUE(TranslationId,Id)
);
CREATE TABLE dbo.Chapter (
 Id bigint IDENTITY PRIMARY KEY,TranslationId bigint NOT NULL REFERENCES dbo.ComicTranslation(Id),VolumeId bigint NULL,
 DisplayNumber nvarchar(50) NOT NULL,Title nvarchar(300) NOT NULL,SortOrder decimal(12,4) NOT NULL CHECK(SortOrder>=0),
 Visibility varchar(20) NOT NULL DEFAULT 'HIDDEN' CHECK(Visibility IN ('PUBLIC','HIDDEN','ARCHIVED')),
 AccessPolicy varchar(20) NOT NULL CHECK(AccessPolicy IN ('FREE','RESTRICTED')),PublishedVersionId bigint NULL,
 PublishedAt datetime2(3) NULL,DeletedAt datetime2(3) NULL,RowVersion rowversion NOT NULL,
 CONSTRAINT UQ_Chapter_Order UNIQUE(TranslationId,SortOrder),
 CONSTRAINT FK_Chapter_Volume FOREIGN KEY(TranslationId,VolumeId) REFERENCES dbo.Volume(TranslationId,Id)
);
CREATE INDEX IX_Chapter_PublicOrder ON dbo.Chapter(TranslationId,Visibility,SortOrder,Id);
CREATE TABLE dbo.ChapterVersion (
 Id bigint IDENTITY PRIMARY KEY,ChapterId bigint NOT NULL REFERENCES dbo.Chapter(Id),VersionNumber int NOT NULL CHECK(VersionNumber>0),
 State varchar(30) NOT NULL,IsWorkingCopy bit NOT NULL,CreatedBy bigint NOT NULL REFERENCES dbo.[User](Id),
 SubmittedBy bigint NULL REFERENCES dbo.[User](Id),ReviewedBy bigint NULL REFERENCES dbo.[User](Id),ReviewedAt datetime2(3) NULL,ReviewReason nvarchar(2000) NULL,
 CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),RowVersion rowversion NOT NULL,
 CONSTRAINT UQ_ChapterVersion_Number UNIQUE(ChapterId,VersionNumber),CONSTRAINT UQ_ChapterVersion_Parent UNIQUE(ChapterId,Id),
 CONSTRAINT CK_ChapterVersion_WorkingState CHECK(
  (IsWorkingCopy=1 AND State IN ('DRAFT','UPLOADING','PROCESSING','READY_FOR_REVIEW','APPROVED','SCHEDULED','FAILED')) OR
  (IsWorkingCopy=0 AND State IN ('REJECTED','PUBLISHED','UNPUBLISHED','ARCHIVED')))
);
CREATE UNIQUE INDEX UX_ChapterVersion_WorkingCopy ON dbo.ChapterVersion(ChapterId) WHERE IsWorkingCopy=1;
ALTER TABLE dbo.Chapter ADD CONSTRAINT FK_Chapter_PublishedVersion FOREIGN KEY(Id,PublishedVersionId) REFERENCES dbo.ChapterVersion(ChapterId,Id);
CREATE TABLE dbo.ChapterPage (
 Id bigint IDENTITY PRIMARY KEY,ChapterId bigint NOT NULL REFERENCES dbo.Chapter(Id),
 CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),RetiredAt datetime2(3) NULL,
 CONSTRAINT UQ_ChapterPage_Parent UNIQUE(ChapterId,Id)
);
CREATE TABLE dbo.ChapterVersionPage (
 ChapterId bigint NOT NULL,VersionId bigint NOT NULL,PageId bigint NOT NULL,Position int NOT NULL CHECK(Position>0),
 AssetId bigint NOT NULL REFERENCES dbo.DigitalAsset(Id),PRIMARY KEY(VersionId,PageId),
 CONSTRAINT UQ_VersionPage_Position UNIQUE(VersionId,Position),
 CONSTRAINT FK_VersionPage_Version FOREIGN KEY(ChapterId,VersionId) REFERENCES dbo.ChapterVersion(ChapterId,Id),
 CONSTRAINT FK_VersionPage_Page FOREIGN KEY(ChapterId,PageId) REFERENCES dbo.ChapterPage(ChapterId,Id)
);
CREATE TABLE dbo.UploadJob (
 Id bigint IDENTITY PRIMARY KEY,ChapterId bigint NOT NULL,VersionId bigint NOT NULL,RequestedBy bigint NOT NULL REFERENCES dbo.[User](Id),
 ClientRequestKey uniqueidentifier NOT NULL,Status varchar(20) NOT NULL,IsActive bit NOT NULL,
 Attempts int NOT NULL DEFAULT 0,LeaseOwner uniqueidentifier NULL,LeaseUntil datetime2(3) NULL,
 NextAttemptAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),ErrorCode varchar(100) NULL,
 StagingKey varchar(300) NOT NULL,StagingReady bit NOT NULL DEFAULT 0,StagingPurged bit NOT NULL DEFAULT 0,
 ReplacePageId bigint NULL,SourceHash char(64) NOT NULL,SourceSize bigint NOT NULL,
 CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),UpdatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),RowVersion rowversion NOT NULL,
 CONSTRAINT UQ_UploadJob_Request UNIQUE(RequestedBy,ClientRequestKey),
 CONSTRAINT FK_UploadJob_Version FOREIGN KEY(ChapterId,VersionId) REFERENCES dbo.ChapterVersion(ChapterId,Id),
 CONSTRAINT FK_UploadJob_ReplacePage FOREIGN KEY(ChapterId,ReplacePageId) REFERENCES dbo.ChapterPage(ChapterId,Id),
 CONSTRAINT CK_UploadJob_State CHECK((IsActive=1 AND Status IN ('QUEUED','RUNNING','RETRY_WAIT')) OR (IsActive=0 AND Status IN ('SUCCEEDED','FAILED','CANCELLED')))
);
CREATE UNIQUE INDEX UX_UploadJob_ActiveVersion ON dbo.UploadJob(VersionId) WHERE IsActive=1;
CREATE INDEX IX_UploadJob_Claim ON dbo.UploadJob(Status,NextAttemptAt,Id) INCLUDE(StagingReady,LeaseUntil,Attempts);
CREATE TABLE dbo.UploadJobItem (
 JobId bigint NOT NULL REFERENCES dbo.UploadJob(Id),Ordinal int NOT NULL CHECK(Ordinal>0),
 EntryName nvarchar(400) NOT NULL,AssetId bigint NOT NULL REFERENCES dbo.DigitalAsset(Id),PageId bigint NULL REFERENCES dbo.ChapterPage(Id),
 PRIMARY KEY(JobId,Ordinal)
);
CREATE TABLE dbo.OutboxEvent (
 Id bigint IDENTITY PRIMARY KEY,EventId uniqueidentifier NOT NULL UNIQUE,Type varchar(100) NOT NULL,SchemaVersion int NOT NULL,
 AggregateId nvarchar(100) NOT NULL,Payload nvarchar(max) NOT NULL,CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 PublishedAt datetime2(3) NULL,Attempts int NOT NULL DEFAULT 0,NextAttemptAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME()
);
GO
