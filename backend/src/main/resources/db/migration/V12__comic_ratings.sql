CREATE TABLE dbo.ComicRating (
 ComicId bigint NOT NULL REFERENCES dbo.Comic(Id),
 UserId bigint NOT NULL REFERENCES dbo.[User](Id),
 Score tinyint NOT NULL CHECK(Score BETWEEN 1 AND 5),
 Review nvarchar(4000) NULL,
 UpdatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 RowVersion rowversion NOT NULL,
 CONSTRAINT PK_ComicRating PRIMARY KEY(ComicId,UserId)
);
CREATE INDEX IX_ComicRating_Recent ON dbo.ComicRating(ComicId,UpdatedAt DESC,UserId DESC)
 INCLUDE(Score);
INSERT dbo.Permission(Code) VALUES('rating.self');
INSERT dbo.RolePermission(RoleId,PermissionId)
 SELECT r.Id,p.Id FROM dbo.[Role] r CROSS JOIN dbo.Permission p
 WHERE p.Code='rating.self' AND r.Code IN ('READER','CREATOR','MODERATOR','ADMINISTRATOR');
GO
