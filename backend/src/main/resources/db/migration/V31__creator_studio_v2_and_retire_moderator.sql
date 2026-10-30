-- V31__creator_studio_v2_and_retire_moderator.sql
-- Creator Studio V2: Add NEEDS_CHANGES state to ChapterVersion working copy states
-- Retire MODERATOR role from active permissions

ALTER TABLE dbo.ChapterVersion DROP CONSTRAINT CK_ChapterVersion_WorkingState;

ALTER TABLE dbo.ChapterVersion ADD CONSTRAINT CK_ChapterVersion_WorkingState CHECK(
  (IsWorkingCopy=1 AND State IN ('DRAFT','UPLOADING','PROCESSING','READY_FOR_REVIEW','NEEDS_CHANGES','APPROVED','SCHEDULED','FAILED')) OR
  (IsWorkingCopy=0 AND State IN ('REJECTED','PUBLISHED','UNPUBLISHED','ARCHIVED'))
);

-- Safely remove active RolePermission for MODERATOR role
DELETE FROM dbo.RolePermission WHERE RoleId IN (SELECT Id FROM dbo.[Role] WHERE Code = 'MODERATOR');
