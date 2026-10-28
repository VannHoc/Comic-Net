CREATE TABLE dbo.Comment (
 Id bigint IDENTITY PRIMARY KEY,ComicId bigint NOT NULL REFERENCES dbo.Comic(Id),
 ChapterId bigint NULL REFERENCES dbo.Chapter(Id),PageId bigint NULL,RelativeOffset decimal(10,8) NULL,
 ParentId bigint NULL,AuthorId bigint NOT NULL REFERENCES dbo.[User](Id),Body nvarchar(4000) NULL,
 Spoiler bit NOT NULL DEFAULT 0,IsHidden bit NOT NULL DEFAULT 0,DeletedAt datetime2(3) NULL,
 CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),UpdatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 RowVersion rowversion NOT NULL,CONSTRAINT UQ_Comment_Comic UNIQUE(Id,ComicId),
 CONSTRAINT FK_Comment_Parent FOREIGN KEY(ParentId,ComicId) REFERENCES dbo.Comment(Id,ComicId),
 CONSTRAINT FK_Comment_Page FOREIGN KEY(ChapterId,PageId) REFERENCES dbo.ChapterPage(ChapterId,Id),
 CONSTRAINT CK_Comment_Anchor CHECK((PageId IS NULL AND RelativeOffset IS NULL) OR
  (PageId IS NOT NULL AND ChapterId IS NOT NULL AND RelativeOffset IS NOT NULL AND RelativeOffset BETWEEN 0 AND 1)),
 CONSTRAINT CK_Comment_Body CHECK(DeletedAt IS NOT NULL OR Body IS NOT NULL)
);
CREATE INDEX IX_Comment_Context ON dbo.Comment(ComicId,ChapterId,PageId,Id);
CREATE INDEX IX_Comment_AuthorTime ON dbo.Comment(AuthorId,CreatedAt);
CREATE TABLE dbo.CommentReaction (
 CommentId bigint NOT NULL REFERENCES dbo.Comment(Id),UserId bigint NOT NULL REFERENCES dbo.[User](Id),
 Value smallint NOT NULL CHECK(Value IN (-1,1)),PRIMARY KEY(CommentId,UserId)
);
CREATE TABLE dbo.CommentReport (
 Id bigint IDENTITY PRIMARY KEY,CommentId bigint NOT NULL REFERENCES dbo.Comment(Id),
 ReporterId bigint NOT NULL REFERENCES dbo.[User](Id),Reason nvarchar(1000) NOT NULL,
 CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),ResolvedAt datetime2(3) NULL,
 ResolvedBy bigint NULL REFERENCES dbo.[User](Id),Resolution nvarchar(1000) NULL,
 CONSTRAINT UQ_CommentReport_User UNIQUE(CommentId,ReporterId)
);
CREATE INDEX IX_CommentReport_Open ON dbo.CommentReport(ResolvedAt,Id);
CREATE TABLE dbo.UserBlock (
 BlockerId bigint NOT NULL REFERENCES dbo.[User](Id),BlockedId bigint NOT NULL REFERENCES dbo.[User](Id),
 PRIMARY KEY(BlockerId,BlockedId),CONSTRAINT CK_UserBlock_Self CHECK(BlockerId<>BlockedId)
);
INSERT dbo.Permission(Code) VALUES('community.self'),('community.moderate');
INSERT dbo.RolePermission(RoleId,PermissionId) SELECT r.Id,p.Id FROM dbo.[Role] r CROSS JOIN dbo.Permission p
 WHERE (p.Code='community.self' AND r.Code IN ('READER','CREATOR','MODERATOR','ADMINISTRATOR'))
 OR (p.Code='community.moderate' AND r.Code IN ('MODERATOR','ADMINISTRATOR'));
GO
