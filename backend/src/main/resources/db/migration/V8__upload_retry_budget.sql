ALTER TABLE dbo.UploadJob ADD MaxAttempts int NOT NULL CONSTRAINT DF_UploadJob_MaxAttempts DEFAULT 3 CHECK(MaxAttempts>0);
GO
