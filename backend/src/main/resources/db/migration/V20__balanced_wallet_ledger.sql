CREATE TABLE dbo.CurrencyDefinition (
 Code varchar(12) NOT NULL PRIMARY KEY,
 Precision tinyint NOT NULL CONSTRAINT CK_CurrencyDefinition_Precision CHECK (Precision = 0)
);
INSERT dbo.CurrencyDefinition(Code,Precision) VALUES ('PAID',0),('REWARD',0);

CREATE TABLE dbo.WalletAccount (
 Id bigint IDENTITY PRIMARY KEY,
 UserId bigint NULL REFERENCES dbo.Wallet(UserId),
 SystemAccountCode varchar(40) NULL,
 CurrencyCode varchar(12) NOT NULL REFERENCES dbo.CurrencyDefinition(Code),
 BalanceCache bigint NOT NULL DEFAULT 0,
 CONSTRAINT CK_WalletAccount_Owner CHECK (
  (UserId IS NOT NULL AND SystemAccountCode IS NULL AND BalanceCache >= 0)
  OR (UserId IS NULL AND SystemAccountCode IS NOT NULL))
);
CREATE UNIQUE INDEX UX_WalletAccount_UserCurrency ON dbo.WalletAccount(UserId,CurrencyCode)
 WHERE UserId IS NOT NULL;
CREATE UNIQUE INDEX UX_WalletAccount_SystemCurrency ON dbo.WalletAccount(SystemAccountCode,CurrencyCode)
 WHERE SystemAccountCode IS NOT NULL;

INSERT dbo.WalletAccount(SystemAccountCode,CurrencyCode,BalanceCache)
 SELECT 'PLATFORM_CLEARING','PAID',-COALESCE(SUM(PaidBalance),0) FROM dbo.Wallet;
INSERT dbo.WalletAccount(SystemAccountCode,CurrencyCode,BalanceCache)
 SELECT 'PLATFORM_CLEARING','REWARD',-COALESCE(SUM(RewardBalance),0) FROM dbo.Wallet;
INSERT dbo.WalletAccount(UserId,CurrencyCode,BalanceCache)
 SELECT UserId,'PAID',PaidBalance FROM dbo.Wallet;
INSERT dbo.WalletAccount(UserId,CurrencyCode,BalanceCache)
 SELECT UserId,'REWARD',RewardBalance FROM dbo.Wallet;

CREATE TABLE dbo.LedgerEntry (
 Id bigint IDENTITY PRIMARY KEY,
 TransactionId bigint NOT NULL REFERENCES dbo.WalletTransaction(Id),
 AccountId bigint NOT NULL REFERENCES dbo.WalletAccount(Id),
 Side varchar(6) NOT NULL CONSTRAINT CK_LedgerEntry_Side CHECK (Side IN ('DEBIT','CREDIT')),
 Amount bigint NOT NULL CONSTRAINT CK_LedgerEntry_Positive CHECK (Amount > 0),
 CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 CONSTRAINT UQ_LedgerEntry_TransactionAccount UNIQUE(TransactionId,AccountId)
);
CREATE INDEX IX_LedgerEntry_Account ON dbo.LedgerEntry(AccountId,Id DESC);

INSERT dbo.LedgerEntry(TransactionId,AccountId,Side,Amount)
 SELECT t.Id,a.Id,CASE WHEN t.Delta > 0 THEN 'CREDIT' ELSE 'DEBIT' END,ABS(t.Delta)
 FROM dbo.WalletTransaction t JOIN dbo.WalletAccount a
  ON a.UserId=t.UserId AND a.CurrencyCode=t.TokenType
 WHERE t.Status='COMPLETED';
INSERT dbo.LedgerEntry(TransactionId,AccountId,Side,Amount)
 SELECT t.Id,a.Id,CASE WHEN t.Delta > 0 THEN 'DEBIT' ELSE 'CREDIT' END,ABS(t.Delta)
 FROM dbo.WalletTransaction t JOIN dbo.WalletAccount a
  ON a.SystemAccountCode='PLATFORM_CLEARING' AND a.CurrencyCode=t.TokenType
 WHERE t.Status='COMPLETED';
