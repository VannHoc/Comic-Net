-- Phase 3: scheduled publishing (M03-F11). Due versions are released by the publish worker.
ALTER TABLE dbo.ChapterVersion ADD ScheduledAt datetime2(3) NULL;
CREATE INDEX IX_ChapterVersion_Due ON dbo.ChapterVersion(ScheduledAt,Id) WHERE State='SCHEDULED';
GO
