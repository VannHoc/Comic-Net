-- Preserve existing email identities. New accounts may authenticate by a unique username.
DECLARE @emailConstraint sysname;
SELECT TOP (1) @emailConstraint = kc.name
FROM sys.key_constraints kc
JOIN sys.index_columns ic ON ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id
JOIN sys.columns c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
WHERE kc.parent_object_id = OBJECT_ID(N'dbo.[User]') AND kc.type = 'UQ'
  AND c.name = N'NormalizedEmail';
IF @emailConstraint IS NOT NULL
BEGIN
  DECLARE @dropConstraint nvarchar(max) = N'ALTER TABLE dbo.[User] DROP CONSTRAINT '
      + QUOTENAME(@emailConstraint);
  EXEC sys.sp_executesql @dropConstraint;
END;

ALTER TABLE dbo.[User] ALTER COLUMN Email nvarchar(320) NULL;
ALTER TABLE dbo.[User] ALTER COLUMN NormalizedEmail nvarchar(320)
  COLLATE Latin1_General_100_BIN2 NULL;
CREATE UNIQUE INDEX UX_User_Email_WhenPresent ON dbo.[User](NormalizedEmail)
  WHERE NormalizedEmail IS NOT NULL;

ALTER TABLE dbo.[User] ADD Username nvarchar(12) NULL,
  NormalizedUsername nvarchar(12) COLLATE Latin1_General_100_BIN2 NULL,
  RecoveryPhone nvarchar(20) NULL;
EXEC sys.sp_executesql N'CREATE UNIQUE INDEX UX_User_Username_WhenPresent
  ON dbo.[User](NormalizedUsername) WHERE NormalizedUsername IS NOT NULL';
