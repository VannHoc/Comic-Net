CREATE TABLE dbo.Wallet (
 UserId bigint NOT NULL PRIMARY KEY REFERENCES dbo.[User](Id),
 PaidBalance bigint NOT NULL CONSTRAINT DF_Wallet_Paid DEFAULT 0,
 RewardBalance bigint NOT NULL CONSTRAINT DF_Wallet_Reward DEFAULT 0,
 UpdatedAt datetime2(3) NOT NULL CONSTRAINT DF_Wallet_Updated DEFAULT SYSUTCDATETIME(),
 CONSTRAINT CK_Wallet_NonNegative CHECK (PaidBalance >= 0 AND RewardBalance >= 0)
);
CREATE TABLE dbo.WalletTransaction (
 Id bigint IDENTITY PRIMARY KEY,
 UserId bigint NOT NULL REFERENCES dbo.Wallet(UserId),
 TokenType varchar(10) NOT NULL CHECK (TokenType IN ('PAID','REWARD')),
 Delta bigint NOT NULL CHECK (Delta <> 0),
 BalanceAfter bigint NOT NULL CHECK (BalanceAfter >= 0),
 Source varchar(40) NOT NULL,
 ReferenceKey varchar(100) NOT NULL,
 Status varchar(12) NOT NULL CHECK (Status IN ('PENDING','COMPLETED','FAILED','REVERSED')),
 CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 CONSTRAINT UQ_WalletTransaction_Reference UNIQUE (UserId,ReferenceKey)
);
CREATE INDEX IX_WalletTransaction_UserTime ON dbo.WalletTransaction(UserId,Id DESC);
