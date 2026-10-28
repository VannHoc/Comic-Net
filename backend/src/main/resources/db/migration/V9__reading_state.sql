CREATE TABLE dbo.ReadingHistoryState (
 UserId bigint NOT NULL PRIMARY KEY REFERENCES dbo.[User](Id),HistoryGeneration bigint NOT NULL DEFAULT 0 CHECK(HistoryGeneration>=0),RowVersion rowversion NOT NULL
);
CREATE TABLE dbo.ReadingProgress (
 Id bigint IDENTITY PRIMARY KEY,UserId bigint NOT NULL REFERENCES dbo.[User](Id),ChapterId bigint NOT NULL REFERENCES dbo.Chapter(Id),
 VersionId bigint NULL,PageId bigint NULL,RelativeOffset decimal(10,8) NOT NULL CHECK(RelativeOffset BETWEEN 0 AND 1),Completed bit NOT NULL,
 LastReadAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),Revision bigint NOT NULL CHECK(Revision>0),LastMutationId uniqueidentifier NOT NULL,
 CONSTRAINT UQ_ReadingProgress UNIQUE(UserId,ChapterId),
 CONSTRAINT FK_Progress_Version FOREIGN KEY(ChapterId,VersionId) REFERENCES dbo.ChapterVersion(ChapterId,Id),
 CONSTRAINT FK_Progress_Page FOREIGN KEY(ChapterId,PageId) REFERENCES dbo.ChapterPage(ChapterId,Id)
);
CREATE INDEX IX_Progress_History ON dbo.ReadingProgress(UserId,LastReadAt DESC,Id DESC);
CREATE TABLE dbo.ProgressMutation (
 UserId bigint NOT NULL REFERENCES dbo.[User](Id),MutationId uniqueidentifier NOT NULL,ChapterId bigint NULL REFERENCES dbo.Chapter(Id),
 HistoryGeneration bigint NOT NULL,PayloadHash char(64) NOT NULL,ResultRevision bigint NOT NULL,ResultSnapshot nvarchar(max) NULL,
 State varchar(20) NOT NULL DEFAULT 'APPLIED' CHECK(State IN ('APPLIED','INVALIDATED')),
 ExpiresAt datetime2(3) NOT NULL DEFAULT DATEADD(day,30,SYSUTCDATETIME()),PRIMARY KEY(UserId,MutationId)
);
CREATE TABLE dbo.ReadingPreference (
 UserId bigint NOT NULL PRIMARY KEY REFERENCES dbo.[User](Id),Mode varchar(10) NOT NULL DEFAULT 'SCROLL' CHECK(Mode IN ('SCROLL','PAGED')),
 Direction varchar(3) NOT NULL DEFAULT 'LTR' CHECK(Direction IN ('LTR','RTL')),Fit varchar(10) NOT NULL DEFAULT 'WIDTH' CHECK(Fit IN ('WIDTH','HEIGHT')),RowVersion rowversion NOT NULL
);
GO
