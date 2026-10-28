/* Operator-only development sample. Run in SSMS with backup/restore privileges.
   First stop application writes and capture the matching object-storage snapshot per
   desktop-swing/src/main/resources/operations/backup-restore-v1.txt.
   Creates a COPY_ONLY backup and a NEW verification database. No REPLACE, DROP,
   login changes or runtime permission changes. Keep outputs in the private QA record.
   Do not treat SQL success alone as a complete database + asset restore PASS. */
USE [master];
SET NOCOUNT ON;
SET XACT_ABORT ON;
IF @@TRANCOUNT <> 0
    THROW 51205, 'Use a new operator query with no pending transaction.', 1;

IF CAST(SERVERPROPERTY('ProductMajorVersion') AS int) <> 17
    THROW 51200, 'This sample requires the agreed SQL Server 2025 instance.', 1;
IF DB_ID(N'ComicNetDev') IS NULL
    THROW 51201, 'Expected ComicNetDev database is missing.', 1;
IF (SELECT COUNT(*) FROM sys.master_files WHERE database_id=DB_ID(N'ComicNetDev')) <> 2
    THROW 51202, 'Review logical files manually: this sample expects one data and one log file.', 1;

DECLARE @Suffix nvarchar(32) = LOWER(REPLACE(CONVERT(nvarchar(36), NEWID()), N'-', N''));
DECLARE @RestoreName sysname = N'ComicNetRestoreQA_' + LEFT(@Suffix, 12);
DECLARE @BackupRoot nvarchar(4000) = CONVERT(nvarchar(4000), SERVERPROPERTY('InstanceDefaultBackupPath'));
DECLARE @DataRoot nvarchar(4000) = CONVERT(nvarchar(4000), SERVERPROPERTY('InstanceDefaultDataPath'));
DECLARE @LogRoot nvarchar(4000) = CONVERT(nvarchar(4000), SERVERPROPERTY('InstanceDefaultLogPath'));
IF @BackupRoot IS NULL OR @DataRoot IS NULL OR @LogRoot IS NULL
    THROW 51203, 'Default SQL service directories unavailable; operator must choose paths.', 1;
IF RIGHT(@BackupRoot, 1) <> N'\' SET @BackupRoot += N'\';
IF RIGHT(@DataRoot, 1) <> N'\' SET @DataRoot += N'\';
IF RIGHT(@LogRoot, 1) <> N'\' SET @LogRoot += N'\';
DECLARE @Backup nvarchar(4000) = @BackupRoot + N'ComicNetDev_QA_' + @Suffix + N'.bak';
DECLARE @DataFile nvarchar(4000) = @DataRoot + @RestoreName + N'.mdf';
DECLARE @LogFile nvarchar(4000) = @LogRoot + @RestoreName + N'_log.ldf';
DECLARE @DataLogical sysname = (SELECT name FROM sys.master_files WHERE database_id=DB_ID(N'ComicNetDev') AND type=0);
DECLARE @LogLogical sysname = (SELECT name FROM sys.master_files WHERE database_id=DB_ID(N'ComicNetDev') AND type=1);
IF @DataLogical IS NULL OR @LogLogical IS NULL OR DB_ID(@RestoreName) IS NOT NULL
    THROW 51204, 'Restore target guard failed.', 1;

BACKUP DATABASE [ComicNetDev] TO DISK=@Backup WITH COPY_ONLY, CHECKSUM, COMPRESSION;
RESTORE VERIFYONLY FROM DISK=@Backup WITH CHECKSUM;
DECLARE @RestoreSql nvarchar(max) = N'RESTORE DATABASE ' + QUOTENAME(@RestoreName)
    + N' FROM DISK=@b WITH MOVE @dl TO @df, MOVE @ll TO @lf, CHECKSUM, RECOVERY;';
EXEC sys.sp_executesql @RestoreSql,
    N'@b nvarchar(4000), @dl sysname, @df nvarchar(4000), @ll sysname, @lf nvarchar(4000)',
    @b=@Backup, @dl=@DataLogical, @df=@DataFile, @ll=@LogLogical, @lf=@LogFile;
DBCC CHECKDB(@RestoreName) WITH NO_INFOMSGS;
SELECT @RestoreName AS VerificationDatabase, @Backup AS BackupFile,
       SYSUTCDATETIME() AS CheckedAtUtc,
       N'SQL restored; verify matching asset snapshot and application reads before overall PASS.' AS NextCheck;
