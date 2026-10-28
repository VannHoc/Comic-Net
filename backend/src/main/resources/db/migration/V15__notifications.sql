-- Phase 3: in-app notifications, preferences, announcements (M13).
CREATE TABLE dbo.Notification (
 Id bigint IDENTITY PRIMARY KEY,
 UserId bigint NOT NULL REFERENCES dbo.[User](Id),
 Type varchar(40) NOT NULL CHECK(Type IN ('NEW_CHAPTER','COMMENT_REPLY','COMMENT_REACTION','SYSTEM_ANNOUNCEMENT')),
 Title nvarchar(200) NOT NULL,
 Body nvarchar(1000) NOT NULL,
 LinkType varchar(30) NULL CHECK(LinkType IS NULL OR LinkType IN ('COMIC','CHAPTER','COMMENT')),
 LinkId bigint NULL,
 DedupKey varchar(160) NOT NULL,
 ReadAt datetime2(3) NULL,
 CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 CONSTRAINT UQ_Notification_Dedup UNIQUE(UserId,Type,DedupKey),
 CONSTRAINT CK_Notification_Link CHECK((LinkType IS NULL AND LinkId IS NULL) OR (LinkType IS NOT NULL AND LinkId IS NOT NULL))
);
CREATE INDEX IX_Notification_Inbox ON dbo.Notification(UserId,Id DESC) INCLUDE(ReadAt,Type);
CREATE INDEX IX_Notification_Unread ON dbo.Notification(UserId,Id DESC) WHERE ReadAt IS NULL;
CREATE TABLE dbo.NotificationPreference (
 UserId bigint NOT NULL REFERENCES dbo.[User](Id),
 Type varchar(40) NOT NULL CHECK(Type IN ('NEW_CHAPTER','COMMENT_REPLY','COMMENT_REACTION','SYSTEM_ANNOUNCEMENT')),
 InAppEnabled bit NOT NULL DEFAULT 1,
 EmailEnabled bit NOT NULL DEFAULT 0,
 UpdatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 CONSTRAINT PK_NotificationPreference PRIMARY KEY(UserId,Type)
);
CREATE TABLE dbo.Announcement (
 Id bigint IDENTITY PRIMARY KEY,
 Title nvarchar(200) NOT NULL,
 Body nvarchar(2000) NOT NULL,
 CreatedBy bigint NOT NULL REFERENCES dbo.[User](Id),
 CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 RecipientCount int NOT NULL DEFAULT 0 CHECK(RecipientCount>=0)
);
ALTER TABLE dbo.OutboxEvent ADD ErrorCode varchar(100) NULL;
CREATE INDEX IX_OutboxEvent_Consume ON dbo.OutboxEvent(Type,NextAttemptAt,Id) WHERE PublishedAt IS NULL;
INSERT dbo.Permission(Code) VALUES('notification.self'),('notification.broadcast');
INSERT dbo.RolePermission(RoleId,PermissionId) SELECT r.Id,p.Id FROM dbo.[Role] r CROSS JOIN dbo.Permission p
 WHERE (p.Code='notification.self' AND r.Code IN ('READER','CREATOR','MODERATOR','ADMINISTRATOR'))
 OR (p.Code='notification.broadcast' AND r.Code='ADMINISTRATOR');
GO
