-- Migration V29: Reading Chapter EXP Reward & History
CREATE TABLE dbo.ReadingExpHistory (
    Id bigint IDENTITY(1,1) NOT NULL PRIMARY KEY,
    UserId bigint NOT NULL REFERENCES dbo.[User](Id),
    ChapterId bigint NOT NULL REFERENCES dbo.Chapter(Id),
    ExpAwarded int NOT NULL,
    CreatedAt datetime2(3) NOT NULL CONSTRAINT DF_ReadingExpHistory_CreatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT UQ_ReadingExp_UserChapter UNIQUE (UserId, ChapterId)
);

CREATE INDEX IX_ReadingExpHistory_User ON dbo.ReadingExpHistory(UserId, Id DESC);
