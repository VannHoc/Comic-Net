CREATE TABLE dbo.ChapterPrice (
 Id bigint IDENTITY PRIMARY KEY,
 ChapterId bigint NOT NULL REFERENCES dbo.Chapter(Id),
 CurrencyCode varchar(12) NOT NULL REFERENCES dbo.CurrencyDefinition(Code),
 Amount bigint NOT NULL CHECK(Amount > 0),
 ValidFrom datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 ValidTo datetime2(3) NULL,
 RowVersion rowversion NOT NULL,
 CONSTRAINT CK_ChapterPrice_Interval CHECK(ValidTo IS NULL OR ValidTo >= ValidFrom)
);
CREATE UNIQUE INDEX UX_ChapterPrice_Active ON dbo.ChapterPrice(ChapterId,CurrencyCode)
 WHERE ValidTo IS NULL;

CREATE TABLE dbo.Purchase (
 Id bigint IDENTITY PRIMARY KEY,
 UserId bigint NOT NULL REFERENCES dbo.[User](Id),
 ChapterId bigint NOT NULL REFERENCES dbo.Chapter(Id),
 PriceId bigint NOT NULL REFERENCES dbo.ChapterPrice(Id),
 PriceAmountSnapshot bigint NOT NULL CHECK(PriceAmountSnapshot > 0),
 CurrencyCode varchar(12) NOT NULL REFERENCES dbo.CurrencyDefinition(Code),
 WalletTransactionId bigint NULL REFERENCES dbo.WalletTransaction(Id),
 IdempotencyKey varchar(100) NOT NULL,
 Status varchar(12) NOT NULL CHECK(Status IN ('PENDING','COMPLETED')),
 CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 CONSTRAINT UQ_Purchase_UserChapter UNIQUE(UserId,ChapterId),
 CONSTRAINT UQ_Purchase_Idempotency UNIQUE(UserId,IdempotencyKey)
);

CREATE TABLE dbo.UserEntitlement (
 Id bigint IDENTITY PRIMARY KEY,
 UserId bigint NOT NULL REFERENCES dbo.[User](Id),
 ChapterId bigint NOT NULL REFERENCES dbo.Chapter(Id),
 Kind varchar(24) NOT NULL CHECK(Kind IN ('PURCHASE','SUBSCRIPTION','FAST_PASS','ADMIN_GRANT')),
 ValidFrom datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 ValidTo datetime2(3) NULL,
 State varchar(12) NOT NULL CHECK(State IN ('ACTIVE','REVOKED')),
 RowVersion rowversion NOT NULL,
 CONSTRAINT CK_UserEntitlement_Interval CHECK(ValidTo IS NULL OR ValidTo > ValidFrom)
);
CREATE UNIQUE INDEX UX_UserEntitlement_Purchase ON dbo.UserEntitlement(UserId,ChapterId)
 WHERE Kind='PURCHASE';
CREATE INDEX IX_UserEntitlement_Access ON dbo.UserEntitlement(UserId,ChapterId,State,ValidTo);

CREATE TABLE dbo.EntitlementGrant (
 Id bigint IDENTITY PRIMARY KEY,
 EntitlementId bigint NOT NULL REFERENCES dbo.UserEntitlement(Id),
 SourceType varchar(30) NOT NULL,
 SourceId bigint NOT NULL,
 CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 CONSTRAINT UQ_EntitlementGrant_Source UNIQUE(SourceType,SourceId,EntitlementId)
);
CREATE TABLE dbo.EntitlementRevocation (
 Id bigint IDENTITY PRIMARY KEY,
 EntitlementId bigint NOT NULL REFERENCES dbo.UserEntitlement(Id),
 Reason nvarchar(500) NOT NULL,
 CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME()
);
