/* M20-F09: Purchase refund, entitlement revocation, and repurchase support */

DECLARE @statusConstraint sysname;
DECLARE @sql nvarchar(max);
SELECT TOP (1) @statusConstraint = name FROM sys.check_constraints
WHERE parent_object_id = OBJECT_ID(N'dbo.Purchase') AND definition LIKE N'%Status%';
IF @statusConstraint IS NOT NULL
BEGIN
  SET @sql = N'ALTER TABLE dbo.Purchase DROP CONSTRAINT ' + QUOTENAME(@statusConstraint);
  EXEC(@sql);
END
ALTER TABLE dbo.Purchase ADD CONSTRAINT CK_Purchase_StatusV27
  CHECK(Status IN ('PENDING','COMPLETED','REFUNDED'));

IF EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = N'UQ_Purchase_UserChapter' AND parent_object_id = OBJECT_ID(N'dbo.Purchase'))
BEGIN
  ALTER TABLE dbo.Purchase DROP CONSTRAINT UQ_Purchase_UserChapter;
END

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UX_Purchase_ActiveUserChapter' AND object_id = OBJECT_ID(N'dbo.Purchase'))
BEGIN
  CREATE UNIQUE INDEX UX_Purchase_ActiveUserChapter ON dbo.Purchase(UserId, ChapterId)
    WHERE Status IN ('PENDING', 'COMPLETED');
END

IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UX_UserEntitlement_Purchase' AND object_id = OBJECT_ID(N'dbo.UserEntitlement'))
BEGIN
  DROP INDEX UX_UserEntitlement_Purchase ON dbo.UserEntitlement;
END

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'UX_UserEntitlement_ActivePurchase' AND object_id = OBJECT_ID(N'dbo.UserEntitlement'))
BEGIN
  CREATE UNIQUE INDEX UX_UserEntitlement_ActivePurchase ON dbo.UserEntitlement(UserId, ChapterId)
    WHERE Kind='PURCHASE' AND State='ACTIVE';
END

IF COL_LENGTH(N'dbo.Purchase', N'RevokedAt') IS NULL
  ALTER TABLE dbo.Purchase ADD RevokedAt datetime2(3) NULL;

IF COL_LENGTH(N'dbo.Purchase', N'RevokedByUserId') IS NULL
  ALTER TABLE dbo.Purchase ADD RevokedByUserId bigint NULL REFERENCES dbo.[User](Id);

IF COL_LENGTH(N'dbo.Purchase', N'RevokeReason') IS NULL
  ALTER TABLE dbo.Purchase ADD RevokeReason nvarchar(500) NULL;

IF COL_LENGTH(N'dbo.Purchase', N'RefundTransactionId') IS NULL
  ALTER TABLE dbo.Purchase ADD RefundTransactionId bigint NULL REFERENCES dbo.WalletTransaction(Id);

IF COL_LENGTH(N'dbo.EntitlementRevocation', N'ActorUserId') IS NULL
  ALTER TABLE dbo.EntitlementRevocation ADD ActorUserId bigint NULL REFERENCES dbo.[User](Id);
