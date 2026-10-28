CREATE TABLE dbo.Author(Id bigint IDENTITY PRIMARY KEY,Code varchar(50) NOT NULL UNIQUE,Name nvarchar(200) NOT NULL);
CREATE TABLE dbo.Publisher(Id bigint IDENTITY PRIMARY KEY,Code varchar(50) NOT NULL UNIQUE,Name nvarchar(200) NOT NULL);
CREATE TABLE dbo.Genre(Id bigint IDENTITY PRIMARY KEY,Code varchar(50) NOT NULL UNIQUE,Name nvarchar(200) NOT NULL);
CREATE TABLE dbo.Tag(Id bigint IDENTITY PRIMARY KEY,Code varchar(50) NOT NULL UNIQUE,Name nvarchar(200) NOT NULL);
CREATE TABLE dbo.ContentWarning(Id bigint IDENTITY PRIMARY KEY,Code varchar(50) NOT NULL UNIQUE,Name nvarchar(200) NOT NULL);
CREATE TABLE dbo.Comic (
 Id bigint IDENTITY PRIMARY KEY,OwnerUserId bigint NOT NULL REFERENCES dbo.[User](Id),
 Title nvarchar(300) NOT NULL,Slug varchar(200) NOT NULL UNIQUE,Description nvarchar(max) NOT NULL,
 Type varchar(20) NOT NULL CHECK(Type IN ('MANGA','MANHWA','MANHUA','COMIC')),
 CountryCode char(2) NOT NULL,Status varchar(20) NOT NULL CHECK(Status IN ('ONGOING','COMPLETED','HIATUS','DROPPED')),
 AgeRating varchar(20) NOT NULL CHECK(AgeRating IN ('ALL_AGE','13_PLUS','16_PLUS','18_PLUS')),
 Visibility varchar(20) NOT NULL DEFAULT 'HIDDEN' CHECK(Visibility IN ('PUBLIC','HIDDEN','ARCHIVED')),
 CoverAssetId bigint NULL REFERENCES dbo.DigitalAsset(Id),BannerAssetId bigint NULL REFERENCES dbo.DigitalAsset(Id),
 CreatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),UpdatedAt datetime2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
 DeletedAt datetime2(3) NULL,LockVersion bigint NOT NULL DEFAULT 0,RowVersion rowversion NOT NULL
);
CREATE INDEX IX_Comic_PublicUpdated ON dbo.Comic(Visibility,AgeRating,UpdatedAt DESC,Id) INCLUDE(Title,Type,Slug);
CREATE TABLE dbo.ComicAlternativeTitle (
 Id bigint IDENTITY PRIMARY KEY,ComicId bigint NOT NULL REFERENCES dbo.Comic(Id),Title nvarchar(300) NOT NULL,
 NormalizedTitle nvarchar(300) NOT NULL,LanguageTag varchar(35) NULL,
 CONSTRAINT UQ_ComicAlternativeTitle UNIQUE(ComicId,NormalizedTitle)
);
CREATE TABLE dbo.ComicAuthor(ComicId bigint NOT NULL REFERENCES dbo.Comic(Id),AuthorId bigint NOT NULL REFERENCES dbo.Author(Id),ContributionType varchar(10) NOT NULL CHECK(ContributionType IN ('AUTHOR','ARTIST')),PRIMARY KEY(ComicId,AuthorId,ContributionType));
CREATE TABLE dbo.ComicPublisher(ComicId bigint NOT NULL REFERENCES dbo.Comic(Id),PublisherId bigint NOT NULL REFERENCES dbo.Publisher(Id),PRIMARY KEY(ComicId,PublisherId));
CREATE TABLE dbo.ComicGenre(ComicId bigint NOT NULL REFERENCES dbo.Comic(Id),GenreId bigint NOT NULL REFERENCES dbo.Genre(Id),PRIMARY KEY(ComicId,GenreId));
CREATE TABLE dbo.ComicTag(ComicId bigint NOT NULL REFERENCES dbo.Comic(Id),TagId bigint NOT NULL REFERENCES dbo.Tag(Id),PRIMARY KEY(ComicId,TagId));
CREATE TABLE dbo.ComicContentWarning(ComicId bigint NOT NULL REFERENCES dbo.Comic(Id),ContentWarningId bigint NOT NULL REFERENCES dbo.ContentWarning(Id),PRIMARY KEY(ComicId,ContentWarningId));
CREATE TABLE dbo.ComicTranslation (
 Id bigint IDENTITY PRIMARY KEY,ComicId bigint NOT NULL REFERENCES dbo.Comic(Id),TeamId bigint NOT NULL REFERENCES dbo.CreatorTeam(Id),
 LanguageTag varchar(35) NOT NULL,EditionKey nvarchar(50) NOT NULL,TitleOverride nvarchar(300) NULL,
 Visibility varchar(20) NOT NULL DEFAULT 'HIDDEN' CHECK(Visibility IN ('PUBLIC','HIDDEN','ARCHIVED')),RowVersion rowversion NOT NULL,
 CONSTRAINT UQ_ComicTranslation UNIQUE(ComicId,TeamId,LanguageTag,EditionKey)
);
CREATE INDEX IX_ComicTranslation_Public ON dbo.ComicTranslation(ComicId,Visibility,Id);
GO
