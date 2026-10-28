CREATE TABLE dbo.Asset (
 Id bigint IDENTITY PRIMARY KEY, UploadedBy bigint NOT NULL REFERENCES dbo.[User](Id),
 Kind varchar(30) NOT NULL CHECK(Kind IN ('COVER','BANNER','CHAPTER_PAGE')),
 SourceKey varchar(300) NOT NULL UNIQUE, SourceHash char(64) NOT NULL,
 MimeType varchar(100) NOT NULL, ByteSize bigint NOT NULL CHECK(ByteSize>0),
 Width int NOT NULL CHECK(Width>0), Height int NOT NULL CHECK(Height>0),
 Status varchar(20) NOT NULL CHECK(Status IN ('STAGING','READY','FAILED','RETIRED')),
 CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME()
);
CREATE TABLE dbo.AssetDerivative (
 Id bigint IDENTITY PRIMARY KEY, AssetId bigint NOT NULL REFERENCES dbo.Asset(Id),
 Format varchar(10) NOT NULL CHECK(Format IN ('JPEG','PNG')), ObjectKey varchar(300) NOT NULL UNIQUE,
 Width int NOT NULL, Height int NOT NULL, MimeType varchar(100) NOT NULL, ByteSize bigint NOT NULL,
 CONSTRAINT UQ_AssetDerivative UNIQUE(AssetId,Format)
);
GO
