CREATE TABLE dbo.AccountEmailOtp (
 UserId bigint NOT NULL REFERENCES dbo.[User](Id),
 Purpose varchar(30) NOT NULL CHECK (Purpose IN ('VERIFY_EMAIL','RESET_PASSWORD')),
 CodeHash char(64) COLLATE Latin1_General_100_BIN2 NOT NULL,
 ExpiresAt datetime2(3) NOT NULL,
 Attempts int NOT NULL DEFAULT 0 CHECK (Attempts BETWEEN 0 AND 5),
 ConsumedAt datetime2(3) NULL,
 SentAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 CONSTRAINT PK_AccountEmailOtp PRIMARY KEY (UserId,Purpose)
);
