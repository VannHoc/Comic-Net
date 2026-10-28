CREATE TABLE dbo.SearchHistory (
 UserId bigint NOT NULL REFERENCES dbo.[User](Id),
 Query nvarchar(200) NOT NULL,
 LastSearchedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 CONSTRAINT PK_SearchHistory PRIMARY KEY(UserId,Query)
);
CREATE INDEX IX_SearchHistory_Recent ON dbo.SearchHistory(UserId,LastSearchedAt DESC);
