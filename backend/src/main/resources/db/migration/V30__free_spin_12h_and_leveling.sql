-- Migration V30: 12-hour free spin interval and updated leveling system
ALTER TABLE dbo.UserGamification ADD LastFreeSpinAt datetime2(3) NULL;
GO

-- Populate LastFreeSpinAt from LastFreeSpinDate if available
UPDATE dbo.UserGamification
SET LastFreeSpinAt = CAST(LastFreeSpinDate AS datetime2(3))
WHERE LastFreeSpinDate IS NOT NULL AND LastFreeSpinAt IS NULL;
GO

-- Update existing user titles to match new milestone titles
UPDATE dbo.UserGamification SET Title = N'Tân thủ' WHERE Level < 5;
UPDATE dbo.UserGamification SET Title = N'Nhập môn' WHERE Level >= 5 AND Level < 10;
UPDATE dbo.UserGamification SET Title = N'Độc giả' WHERE Level >= 10 AND Level < 20;
UPDATE dbo.UserGamification SET Title = N'Kỳ cựu' WHERE Level >= 20 AND Level < 40;
UPDATE dbo.UserGamification SET Title = N'Lần Theo Nét Mực' WHERE Level >= 40 AND Level < 50;
UPDATE dbo.UserGamification SET Title = N'Vượt Qua Hồi Kết' WHERE Level >= 50 AND Level < 60;
UPDATE dbo.UserGamification SET Title = N'Toàn trí độc giả' WHERE Level >= 60 AND Level < 80;
UPDATE dbo.UserGamification SET Title = N'Nhìn thấu hồng trần' WHERE Level >= 80 AND Level < 100;
UPDATE dbo.UserGamification SET Title = N'Đấng toàn năng' WHERE Level >= 100;
GO
