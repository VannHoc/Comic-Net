-- Phase 3: valid read counting, ranking and analytics (M07).
-- A valid read is one authenticated open of a published chapter, deduplicated per user/chapter/day.
CREATE TABLE dbo.ComicView (
 Id bigint IDENTITY PRIMARY KEY,
 ComicId bigint NOT NULL REFERENCES dbo.Comic(Id),
 ChapterId bigint NOT NULL REFERENCES dbo.Chapter(Id),
 UserId bigint NOT NULL REFERENCES dbo.[User](Id),
 ViewedOn date NOT NULL,
 ViewedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 CONSTRAINT UQ_ComicView_UserChapterDay UNIQUE(UserId,ChapterId,ViewedOn)
);
CREATE INDEX IX_ComicView_ComicDay ON dbo.ComicView(ComicId,ViewedOn) INCLUDE(ViewedAt);
CREATE TABLE dbo.ComicStatDaily (
 ComicId bigint NOT NULL REFERENCES dbo.Comic(Id),
 StatDate date NOT NULL,
 Views int NOT NULL DEFAULT 0 CHECK(Views>=0),
 Completions int NOT NULL DEFAULT 0 CHECK(Completions>=0),
 CONSTRAINT PK_ComicStatDaily PRIMARY KEY(ComicId,StatDate)
);
CREATE INDEX IX_ComicStatDaily_Period ON dbo.ComicStatDaily(StatDate,ComicId) INCLUDE(Views,Completions);
CREATE TABLE dbo.ChapterCompletion (
 UserId bigint NOT NULL REFERENCES dbo.[User](Id),
 ChapterId bigint NOT NULL REFERENCES dbo.Chapter(Id),
 ComicId bigint NOT NULL REFERENCES dbo.Comic(Id),
 CompletedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 CONSTRAINT PK_ChapterCompletion PRIMARY KEY(UserId,ChapterId)
);
CREATE INDEX IX_ChapterCompletion_Chapter ON dbo.ChapterCompletion(ChapterId);
INSERT dbo.Permission(Code) VALUES('analytics.self'),('analytics.read');
INSERT dbo.RolePermission(RoleId,PermissionId) SELECT r.Id,p.Id FROM dbo.[Role] r CROSS JOIN dbo.Permission p
 WHERE (p.Code='analytics.self' AND r.Code IN ('READER','CREATOR','MODERATOR','ADMINISTRATOR'))
 OR (p.Code='analytics.read' AND r.Code IN ('CREATOR','MODERATOR','ADMINISTRATOR'));
GO
