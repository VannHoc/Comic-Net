/* Optional administrator setup after installing SQL Server Full-Text Search.
   Execute outside a user transaction. Existing metadata search remains available without it.
   No database rebuild, history repair, or data deletion is performed. */
USE [ComicNetDev];
GO
IF FULLTEXTSERVICEPROPERTY('IsFullTextInstalled') <> 1
 THROW 51042,'SQL Server Full-Text Search component is not installed. Basic search remains available.',1;
IF NOT EXISTS(SELECT 1 FROM sys.fulltext_catalogs WHERE name=N'ComicNetSearch')
 EXEC(N'CREATE FULLTEXT CATALOG ComicNetSearch WITH ACCENT_SENSITIVITY=OFF');
IF NOT EXISTS(SELECT 1 FROM sys.fulltext_indexes WHERE object_id=OBJECT_ID(N'dbo.Comic'))
BEGIN
 DECLARE @ComicKey sysname = (SELECT name FROM sys.indexes WHERE object_id=OBJECT_ID(N'dbo.Comic') AND is_primary_key=1);
 IF @ComicKey IS NULL THROW 51043,'Comic primary key is missing.',1;
 DECLARE @CreateIndex nvarchar(max) = N'CREATE FULLTEXT INDEX ON dbo.Comic(Title LANGUAGE 0,Description LANGUAGE 0) KEY INDEX '
   + QUOTENAME(@ComicKey) + N' ON ComicNetSearch WITH CHANGE_TRACKING AUTO';
 EXEC sys.sp_executesql @CreateIndex;
END;
GO
