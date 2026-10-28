-- SQL Server Full-Text is optional in Development. CatalogService retains the
-- parameterized CHARINDEX path when the feature is absent or temporarily unavailable.
IF FULLTEXTSERVICEPROPERTY('IsFullTextInstalled') = 1
BEGIN
  IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID(N'dbo.Comic') AND name = N'UX_Comic_SearchKey')
    CREATE UNIQUE INDEX UX_Comic_SearchKey ON dbo.Comic(Id);

  IF NOT EXISTS (SELECT 1 FROM sys.fulltext_catalogs WHERE name = N'ComicNetSearch')
    EXEC(N'CREATE FULLTEXT CATALOG ComicNetSearch');

  IF NOT EXISTS (SELECT 1 FROM sys.fulltext_indexes WHERE object_id = OBJECT_ID(N'dbo.Comic'))
    EXEC(N'CREATE FULLTEXT INDEX ON dbo.Comic (Title LANGUAGE 0, Description LANGUAGE 0)
      KEY INDEX UX_Comic_SearchKey ON ComicNetSearch WITH CHANGE_TRACKING AUTO');
END;
