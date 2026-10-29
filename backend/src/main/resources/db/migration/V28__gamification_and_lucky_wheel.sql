-- Migration V28: Gamification, Daily Check-In, Leveling & Lucky Wheel
CREATE TABLE dbo.UserGamification (
    UserId bigint NOT NULL PRIMARY KEY REFERENCES dbo.[User](Id),
    Exp bigint NOT NULL CONSTRAINT DF_UserGamification_Exp DEFAULT 0,
    Level int NOT NULL CONSTRAINT DF_UserGamification_Level DEFAULT 1,
    Title nvarchar(60) NOT NULL CONSTRAINT DF_UserGamification_Title DEFAULT N'Tân thủ đọc truyện',
    LastCheckInDate date NULL,
    CheckInStreak int NOT NULL CONSTRAINT DF_UserGamification_Streak DEFAULT 0,
    TotalCheckIns int NOT NULL CONSTRAINT DF_UserGamification_Total DEFAULT 0,
    LastFreeSpinDate date NULL,
    UpdatedAt datetime2(3) NOT NULL CONSTRAINT DF_UserGamification_UpdatedAt DEFAULT SYSUTCDATETIME()
);

CREATE TABLE dbo.CheckInHistory (
    Id bigint IDENTITY(1,1) NOT NULL PRIMARY KEY,
    UserId bigint NOT NULL REFERENCES dbo.[User](Id),
    CheckInDate date NOT NULL,
    DayInStreak int NOT NULL,
    ExpAwarded int NOT NULL,
    RewardCoinAwarded int NOT NULL,
    CreatedAt datetime2(3) NOT NULL CONSTRAINT DF_CheckInHistory_CreatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT UQ_CheckIn_UserDate UNIQUE (UserId, CheckInDate)
);

CREATE TABLE dbo.LuckyWheelHistory (
    Id bigint IDENTITY(1,1) NOT NULL PRIMARY KEY,
    UserId bigint NOT NULL REFERENCES dbo.[User](Id),
    PrizeIndex int NOT NULL,
    RewardType varchar(20) NOT NULL,
    RewardAmount int NOT NULL,
    RewardLabel nvarchar(100) NOT NULL,
    CostCoin int NOT NULL CONSTRAINT DF_LuckyWheelHistory_Cost DEFAULT 0,
    CreatedAt datetime2(3) NOT NULL CONSTRAINT DF_LuckyWheelHistory_CreatedAt DEFAULT SYSUTCDATETIME()
);

CREATE INDEX IX_CheckInHistory_User ON dbo.CheckInHistory(UserId, CheckInDate DESC);
CREATE INDEX IX_LuckyWheelHistory_User ON dbo.LuckyWheelHistory(UserId, Id DESC);

-- Initialize gamification for existing users
INSERT INTO dbo.UserGamification (UserId, Exp, Level, Title, CheckInStreak, TotalCheckIns)
SELECT u.Id, 0, 1, N'Tân thủ đọc truyện', 0, 0
FROM dbo.[User] u
WHERE NOT EXISTS (SELECT 1 FROM dbo.UserGamification g WHERE g.UserId = u.Id);
