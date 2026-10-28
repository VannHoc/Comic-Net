CREATE TABLE dbo.LibraryCollection (
 Id bigint IDENTITY PRIMARY KEY,UserId bigint NOT NULL REFERENCES dbo.[User](Id),Name nvarchar(100) NOT NULL,
 IsSystem bit NOT NULL,SystemKey varchar(40) NULL,RowVersion rowversion NOT NULL,
 CONSTRAINT CK_Collection_System CHECK((IsSystem=1 AND SystemKey IS NOT NULL) OR (IsSystem=0 AND SystemKey IS NULL))
);
CREATE UNIQUE INDEX UX_Collection_SystemKey ON dbo.LibraryCollection(UserId,SystemKey) WHERE SystemKey IS NOT NULL;
CREATE INDEX IX_Collection_User ON dbo.LibraryCollection(UserId,IsSystem,Id);
CREATE TABLE dbo.CollectionItem (
 CollectionId bigint NOT NULL REFERENCES dbo.LibraryCollection(Id),ComicId bigint NOT NULL REFERENCES dbo.Comic(Id),
 ReadingStatus varchar(20) NULL CHECK(ReadingStatus IN ('WANT_TO_READ','READING','COMPLETED','REREAD')),IsFavorite bit NULL,
 AddedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),RowVersion rowversion NOT NULL,PRIMARY KEY(CollectionId,ComicId)
);
CREATE TABLE dbo.ComicFollow (
 UserId bigint NOT NULL REFERENCES dbo.[User](Id),ComicId bigint NOT NULL REFERENCES dbo.Comic(Id),
 CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),PRIMARY KEY(UserId,ComicId)
);
CREATE INDEX IX_ComicFollow_Subscribers ON dbo.ComicFollow(ComicId,UserId);
CREATE TABLE dbo.Bookmark (
 Id bigint IDENTITY PRIMARY KEY,UserId bigint NOT NULL REFERENCES dbo.[User](Id),ChapterId bigint NOT NULL REFERENCES dbo.Chapter(Id),
 VersionId bigint NULL,PageId bigint NULL,RelativeOffset decimal(10,8) NOT NULL CHECK(RelativeOffset BETWEEN 0 AND 1),
 Label nvarchar(200) NULL,RowVersion rowversion NOT NULL,
 CONSTRAINT FK_Bookmark_Version FOREIGN KEY(ChapterId,VersionId) REFERENCES dbo.ChapterVersion(ChapterId,Id),
 CONSTRAINT FK_Bookmark_Page FOREIGN KEY(ChapterId,PageId) REFERENCES dbo.ChapterPage(ChapterId,Id)
);
CREATE INDEX IX_Bookmark_User ON dbo.Bookmark(UserId,Id);
INSERT dbo.RolePermission(RoleId,PermissionId) SELECT r.Id,p.Id FROM dbo.[Role] r CROSS JOIN dbo.Permission p
 WHERE r.Code='ADMINISTRATOR' AND NOT EXISTS(SELECT 1 FROM dbo.RolePermission rp WHERE rp.RoleId=r.Id AND rp.PermissionId=p.Id);
GO
