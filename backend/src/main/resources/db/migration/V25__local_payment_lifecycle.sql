/* Keep order snapshots unchanged when a package is edited. */
ALTER TABLE dbo.PaymentOrder ADD ExpiresAt datetime2(3) NULL;
GO
UPDATE dbo.PaymentOrder SET ExpiresAt=DATEADD(MINUTE,30,CreatedAt);
ALTER TABLE dbo.PaymentOrder ALTER COLUMN ExpiresAt datetime2(3) NOT NULL;
ALTER TABLE dbo.PaymentOrder ADD CONSTRAINT DF_PaymentOrder_ExpiresAt
 DEFAULT DATEADD(MINUTE,30,SYSUTCDATETIME()) FOR ExpiresAt;

DECLARE @constraint sysname;
DECLARE @sql nvarchar(max);
SELECT @constraint = cc.name FROM sys.check_constraints cc
 WHERE cc.parent_object_id=OBJECT_ID(N'dbo.PaymentOrder')
 AND cc.parent_column_id=COLUMNPROPERTY(OBJECT_ID(N'dbo.PaymentOrder'),N'Status','ColumnId');
IF @constraint IS NULL THROW 51080, 'PaymentOrder status constraint not found', 1;
SET @sql=N'ALTER TABLE dbo.PaymentOrder DROP CONSTRAINT ' + QUOTENAME(@constraint);
EXEC(@sql);
ALTER TABLE dbo.PaymentOrder ADD CONSTRAINT CK_PaymentOrder_StatusV25
 CHECK(Status IN ('PENDING','COMPLETED','FAILED','EXPIRED','REFUNDED'));

CREATE TABLE dbo.PaymentOrderEvent (
 Id bigint IDENTITY PRIMARY KEY,
 OrderId bigint NOT NULL REFERENCES dbo.PaymentOrder(Id),
 ActorUserId bigint NULL REFERENCES dbo.[User](Id),
 EventType varchar(30) NOT NULL CHECK(EventType IN ('FAILED','EXPIRED','REFUNDED')),
 Reason nvarchar(500) NULL,
 CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 CONSTRAINT UQ_PaymentOrderEvent_Type UNIQUE(OrderId,EventType)
);
