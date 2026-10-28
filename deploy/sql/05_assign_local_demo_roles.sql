-- Run once in SSMS against ComicNetDev after registering username admin
-- in the Swing application. Other roles are managed in the app.
USE ComicNetDev;
GO
SET XACT_ABORT ON;
BEGIN TRANSACTION;

DECLARE @AdminId bigint = (SELECT Id FROM dbo.[User] WITH (UPDLOCK, HOLDLOCK)
    WHERE NormalizedUsername = N'admin' AND Status = 'ACTIVE');
IF @AdminId IS NULL
    THROW 51000, 'Register the active admin account first.', 1;

INSERT dbo.UserRole(UserId, RoleId)
SELECT @AdminId, r.Id FROM dbo.[Role] r
WHERE r.Code = 'ADMINISTRATOR'
  AND NOT EXISTS (SELECT 1 FROM dbo.UserRole ur WHERE ur.UserId = @AdminId AND ur.RoleId = r.Id);
UPDATE dbo.[User] SET AuthVersion = AuthVersion + 1, LockVersion = LockVersion + 1
WHERE Id = @AdminId;
COMMIT TRANSACTION;

SELECT u.Username, r.Code AS RoleCode, u.Status
FROM dbo.[User] u JOIN dbo.UserRole ur ON ur.UserId = u.Id
JOIN dbo.[Role] r ON r.Id = ur.RoleId
WHERE u.Id = @AdminId
ORDER BY u.Username, r.Code;
