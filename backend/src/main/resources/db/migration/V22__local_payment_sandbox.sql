CREATE TABLE dbo.CoinPackage (
 Code varchar(30) NOT NULL PRIMARY KEY,
 PaidCoin bigint NOT NULL CHECK(PaidCoin > 0),
 AmountVnd bigint NOT NULL CHECK(AmountVnd > 0),
 Active bit NOT NULL DEFAULT 1
);
INSERT dbo.CoinPackage(Code,PaidCoin,AmountVnd) VALUES
 ('DEMO_100',100,10000),('DEMO_500',500,45000),('DEMO_1000',1000,80000);

CREATE TABLE dbo.PaymentOrder (
 Id bigint IDENTITY PRIMARY KEY,
 UserId bigint NOT NULL REFERENCES dbo.[User](Id),
 PackageCode varchar(30) NOT NULL REFERENCES dbo.CoinPackage(Code),
 PaidCoinSnapshot bigint NOT NULL CHECK(PaidCoinSnapshot > 0),
 AmountVndSnapshot bigint NOT NULL CHECK(AmountVndSnapshot > 0),
 IdempotencyKey varchar(100) NOT NULL,
 Status varchar(20) NOT NULL CHECK(Status IN ('PENDING','COMPLETED','FAILED')),
 CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 SettledAt datetime2(3) NULL,
 CONSTRAINT UQ_PaymentOrder_Idempotency UNIQUE(UserId,IdempotencyKey)
);
CREATE INDEX IX_PaymentOrder_UserTime ON dbo.PaymentOrder(UserId,Id DESC);

CREATE TABLE dbo.PaymentTransaction (
 Id bigint IDENTITY PRIMARY KEY,
 OrderId bigint NOT NULL UNIQUE REFERENCES dbo.PaymentOrder(Id),
 Provider varchar(30) NOT NULL,
 ProviderReference varchar(100) NOT NULL UNIQUE,
 AmountVnd bigint NOT NULL CHECK(AmountVnd > 0),
 WalletTransactionId bigint NOT NULL UNIQUE REFERENCES dbo.WalletTransaction(Id),
 CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME()
);
CREATE TABLE dbo.WebhookInbox (
 Id bigint IDENTITY PRIMARY KEY,
 Provider varchar(30) NOT NULL,
 EventId varchar(100) NOT NULL,
 OrderId bigint NOT NULL REFERENCES dbo.PaymentOrder(Id),
 Status varchar(20) NOT NULL CHECK(Status IN ('PROCESSED','REJECTED')),
 ReceivedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 CONSTRAINT UQ_WebhookInbox_Event UNIQUE(Provider,EventId)
);
CREATE TABLE dbo.ReconciliationRecord (
 Id bigint IDENTITY PRIMARY KEY,
 Provider varchar(30) NOT NULL,
 CheckedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 PendingOrders bigint NOT NULL,
 MismatchCount bigint NOT NULL,
 CheckedBy bigint NOT NULL REFERENCES dbo.[User](Id)
);
