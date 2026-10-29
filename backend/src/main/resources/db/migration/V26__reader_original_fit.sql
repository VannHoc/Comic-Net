DECLARE @fitConstraint sysname;
SELECT TOP (1) @fitConstraint = name
FROM sys.check_constraints
WHERE parent_object_id = OBJECT_ID(N'dbo.ReadingPreference')
  AND definition LIKE N'%Fit%';

IF @fitConstraint IS NULL
  THROW 51000, 'ReadingPreference fit constraint not found.', 1;

DECLARE @dropSql nvarchar(max) = N'ALTER TABLE dbo.ReadingPreference DROP CONSTRAINT ' + QUOTENAME(@fitConstraint);
EXEC(@dropSql);
ALTER TABLE dbo.ReadingPreference
  ADD CONSTRAINT CK_ReadingPreference_Fit
  CHECK (Fit IN ('WIDTH', 'HEIGHT', 'ORIGINAL'));
