/* Lookup codes are Unicode strings in the approved API contract. */
DECLARE @LookupTable sysname,@CodeConstraint sysname,@LookupSql nvarchar(max);
DECLARE LookupTables CURSOR LOCAL FAST_FORWARD FOR SELECT name FROM sys.tables
 WHERE schema_id=SCHEMA_ID(N'dbo') AND name IN (N'Author',N'Publisher',N'Genre',N'Tag',N'ContentWarning');
OPEN LookupTables;
FETCH NEXT FROM LookupTables INTO @LookupTable;
WHILE @@FETCH_STATUS=0
BEGIN
 SET @CodeConstraint=NULL;
 SELECT @CodeConstraint=kc.name FROM sys.key_constraints kc
 JOIN sys.index_columns ic ON ic.object_id=kc.parent_object_id AND ic.index_id=kc.unique_index_id
 JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
 WHERE kc.parent_object_id=OBJECT_ID(N'dbo.'+QUOTENAME(@LookupTable)) AND kc.type='UQ' AND c.name=N'Code';
 IF @CodeConstraint IS NULL THROW 51030,'Expected lookup Code unique constraint is missing.',1;
 SET @LookupSql=N'ALTER TABLE dbo.'+QUOTENAME(@LookupTable)+N' DROP CONSTRAINT '+QUOTENAME(@CodeConstraint)+N'; '
  +N'ALTER TABLE dbo.'+QUOTENAME(@LookupTable)+N' ALTER COLUMN Code nvarchar(50) NOT NULL; '
  +N'ALTER TABLE dbo.'+QUOTENAME(@LookupTable)+N' ADD CONSTRAINT '+QUOTENAME(N'UQ_'+@LookupTable+N'_Code')+N' UNIQUE(Code);';
 EXEC sys.sp_executesql @LookupSql;
 FETCH NEXT FROM LookupTables INTO @LookupTable;
END;
CLOSE LookupTables;
DEALLOCATE LookupTables;
ALTER TABLE dbo.AccountMailJob ADD LeaseOwner uniqueidentifier NULL;
GO
