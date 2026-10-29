package com.comicnet.gamification.service;

import com.comicnet.common.web.DomainException;
import com.comicnet.gamification.dto.GamificationDtos.*;
import com.comicnet.identity.api.Identity;
import com.comicnet.wallet.api.WalletOperations;
import java.security.SecureRandom;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GamificationService {
  private static final int SPIN_COST_REWARD_COIN = 5;
  public static final long FREE_SPIN_INTERVAL_SECONDS = 12 * 3600; // 12 hours free spin interval

  private static final int[] CHECK_IN_COIN_REWARDS = {5, 10, 20, 30, 50, 70, 100};
  private static final int[] CHECK_IN_EXP_REWARDS = {15, 20, 30, 40, 60, 80, 120};

  private static final List<WheelPrize> PRIZES = List.of(
      new WheelPrize(0, "COIN_REWARD", 10, "+10 Xu thưởng", 25),
      new WheelPrize(1, "EXP", 50, "+50 EXP", 25),
      new WheelPrize(2, "COIN_REWARD", 20, "+20 Xu thưởng", 15),
      new WheelPrize(3, "EXP", 100, "+100 EXP", 15),
      new WheelPrize(4, "COIN_REWARD", 50, "+50 Xu thưởng", 10),
      new WheelPrize(5, "EXP", 200, "+200 EXP", 5),
      new WheelPrize(6, "COIN_PAID", 5, "Jackpot: +5 Xu mua!", 2),
      new WheelPrize(7, "EXP", 5, "May mắn lần sau (+5 EXP)", 3)
  );

  private final JdbcTemplate jdbc;
  private final WalletOperations wallet;
  private final SecureRandom random = new SecureRandom();

  public GamificationService(JdbcTemplate jdbc, WalletOperations wallet) {
    this.jdbc = jdbc;
    this.wallet = wallet;
  }

  public static int expToNextLevel(int level) {
    if (level < 1) return 100;
    if (level < 5) return 100; // Lv. 1..4: 100 EXP/cấp
    if (level < 10) return 200; // Lv. 5..9: 200 EXP/cấp
    if (level < 20) return 500; // Lv. 10..19: 500 EXP/cấp
    if (level < 40) return 1000; // Lv. 20..39: 1000 EXP/cấp
    if (level < 50) return 1200; // Lv. 40..49: 1200 EXP/cấp
    if (level < 60) return 1500; // Lv. 50..59: 1500 EXP/cấp
    if (level < 80) return 3000; // Lv. 60..79: 3000 EXP/cấp
    return 5000; // Lv. 80..100+: 5000 EXP/cấp
  }

  public static int calculateLevel(long totalExp) {
    if (totalExp <= 0) return 1;
    int level = 1;
    long exp = totalExp;
    while (true) {
      int required = expToNextLevel(level);
      if (exp >= required) {
        exp -= required;
        level++;
      } else {
        break;
      }
    }
    return level;
  }

  public static long cumulativeExpForLevel(int targetLevel) {
    long total = 0;
    for (int lvl = 1; lvl < targetLevel; lvl++) {
      total += expToNextLevel(lvl);
    }
    return total;
  }

  public static String titleForLevel(int level) {
    if (level < 5) return "Tân thủ";
    if (level < 10) return "Nhập môn";
    if (level < 20) return "Độc giả";
    if (level < 40) return "Kỳ cựu";
    if (level < 50) return "Lần Theo Nét Mực";
    if (level < 60) return "Vượt Qua Hồi Kết";
    if (level < 80) return "Toàn trí độc giả";
    if (level < 100) return "Nhìn thấu hồng trần";
    return "Đấng toàn năng";
  }

  @Transactional
  public Profile getProfile(Identity actor) {
    long userId = actor.userId();
    ensureGamificationRow(userId);

    return jdbc.queryForObject(
        "SELECT UserId, Exp, Level, Title, LastCheckInDate, CheckInStreak, TotalCheckIns, "
            + "DATEDIFF(second, LastFreeSpinAt, SYSUTCDATETIME()) AS SecSinceLastSpin "
            + "FROM dbo.UserGamification WHERE UserId = ?",
        (rs, rowNum) -> mapProfile(rs, userId),
        userId);
  }

  private Profile mapProfile(ResultSet rs, long userId) throws SQLException {
    long exp = rs.getLong("Exp");
    int level = calculateLevel(exp);
    String title = titleForLevel(level);
    Date lastCheckInSql = rs.getDate("LastCheckInDate");
    LocalDate lastCheckInDate = lastCheckInSql != null ? lastCheckInSql.toLocalDate() : null;
    int checkInStreak = rs.getInt("CheckInStreak");
    int totalCheckIns = rs.getInt("TotalCheckIns");

    Object secObj = rs.getObject("SecSinceLastSpin");
    boolean hasFreeSpin = secObj == null || ((Number) secObj).longValue() >= FREE_SPIN_INTERVAL_SECONDS;
    long secondsUntilNextFreeSpin = hasFreeSpin
        ? 0
        : Math.max(0, FREE_SPIN_INTERVAL_SECONDS - ((Number) secObj).longValue());
    int freeSpinsAvailable = hasFreeSpin ? 1 : 0;

    LocalDate today = LocalDate.now();
    boolean canCheckInToday = lastCheckInDate == null || !lastCheckInDate.equals(today);

    long levelBaseExp = cumulativeExpForLevel(level);
    long nextLevelExp = expToNextLevel(level);
    long currentLevelExp = Math.max(0, exp - levelBaseExp);

    var balance = wallet.balance(userId);

    return new Profile(
        userId,
        exp,
        level,
        currentLevelExp,
        nextLevelExp,
        title,
        lastCheckInDate,
        checkInStreak,
        totalCheckIns,
        canCheckInToday,
        freeSpinsAvailable,
        secondsUntilNextFreeSpin,
        balance.paidCoin(),
        balance.rewardCoin());
  }

  @Transactional
  public CheckInResult checkIn(Identity actor) {
    long userId = actor.userId();
    ensureGamificationRow(userId);

    // Lock gamification record
    var state = jdbc.queryForMap(
        "SELECT Exp, Level, Title, LastCheckInDate, CheckInStreak, TotalCheckIns "
            + "FROM dbo.UserGamification WITH (UPDLOCK, HOLDLOCK) WHERE UserId = ?",
        userId);

    LocalDate today = LocalDate.now();
    Date lastCheckInSql = (Date) state.get("LastCheckInDate");
    LocalDate lastCheckInDate = lastCheckInSql != null ? lastCheckInSql.toLocalDate() : null;

    if (lastCheckInDate != null && lastCheckInDate.equals(today)) {
      throw new DomainException(409, "ALREADY_CHECKED_IN", "Hôm nay bạn đã điểm danh rồi!");
    }

    int currentStreak = ((Number) state.get("CheckInStreak")).intValue();
    int newStreak;
    if (lastCheckInDate != null && lastCheckInDate.equals(today.minusDays(1))) {
      newStreak = currentStreak + 1;
    } else {
      newStreak = 1;
    }

    int dayInCycle = ((newStreak - 1) % 7) + 1;
    int expAwarded = CHECK_IN_EXP_REWARDS[dayInCycle - 1];
    int coinAwarded = CHECK_IN_COIN_REWARDS[dayInCycle - 1];

    // Apply reward coin to wallet
    wallet.apply(
        userId,
        "REWARD",
        coinAwarded,
        "DAILY_CHECKIN",
        "checkin:" + userId + ":" + today);

    // Record check-in history
    jdbc.update(
        "INSERT INTO dbo.CheckInHistory(UserId, CheckInDate, DayInStreak, ExpAwarded, RewardCoinAwarded) "
            + "VALUES (?, ?, ?, ?, ?)",
        userId,
        Date.valueOf(today),
        dayInCycle,
        expAwarded,
        coinAwarded);

    long currentExp = ((Number) state.get("Exp")).longValue();
    long newExp = currentExp + expAwarded;
    int newLevel = calculateLevel(newExp);
    String newTitle = titleForLevel(newLevel);

    // Update user gamification status
    jdbc.update(
        "UPDATE dbo.UserGamification "
            + "SET Exp = ?, Level = ?, Title = ?, LastCheckInDate = ?, CheckInStreak = ?, TotalCheckIns = TotalCheckIns + 1, UpdatedAt = SYSUTCDATETIME() "
            + "WHERE UserId = ?",
        newExp,
        newLevel,
        newTitle,
        Date.valueOf(today),
        newStreak,
        userId);

    String message = "Điểm danh Ngày " + dayInCycle + " thành công! Nhận +" + expAwarded + " EXP và +" + coinAwarded + " Xu thưởng.";
    if (newLevel > ((Number) state.get("Level")).intValue()) {
      message += " Chúc mừng bạn đã thăng cấp " + newLevel + " (" + newTitle + ")!";
    }

    return new CheckInResult(
        today,
        dayInCycle,
        expAwarded,
        coinAwarded,
        newExp,
        newLevel,
        newTitle,
        newStreak,
        message);
  }

  public LuckyWheelInfo getWheelInfo(Identity actor) {
    long userId = actor.userId();
    ensureGamificationRow(userId);

    Integer sec = jdbc.queryForObject(
        "SELECT DATEDIFF(second, LastFreeSpinAt, SYSUTCDATETIME()) FROM dbo.UserGamification WHERE UserId = ?",
        Integer.class,
        userId);
    boolean hasFreeSpin = sec == null || sec >= FREE_SPIN_INTERVAL_SECONDS;
    long secondsRemaining = hasFreeSpin
        ? 0
        : Math.max(0, FREE_SPIN_INTERVAL_SECONDS - sec);

    return new LuckyWheelInfo(PRIZES, hasFreeSpin, SPIN_COST_REWARD_COIN, secondsRemaining);
  }

  @Transactional
  public SpinResult spin(Identity actor) {
    long userId = actor.userId();
    ensureGamificationRow(userId);

    // Lock gamification record
    var state = jdbc.queryForMap(
        "SELECT Exp, Level, Title, DATEDIFF(second, LastFreeSpinAt, SYSUTCDATETIME()) AS SecSinceLastSpin "
            + "FROM dbo.UserGamification WITH (UPDLOCK, HOLDLOCK) WHERE UserId = ?",
        userId);

    Number secNum = (Number) state.get("SecSinceLastSpin");
    boolean isFreeSpin = secNum == null || secNum.longValue() >= FREE_SPIN_INTERVAL_SECONDS;
    int cost = isFreeSpin ? 0 : SPIN_COST_REWARD_COIN;

    if (!isFreeSpin) {
      var balance = wallet.balance(userId);
      if (balance.rewardCoin() < cost) {
        throw new DomainException(422, "INSUFFICIENT_REWARD_COIN",
            "Bạn không đủ Xu thưởng để quay (cần " + cost + " xu). Hãy điểm danh hoặc đọc truyện để nhận thêm!");
      }
      wallet.apply(
          userId,
          "REWARD",
          -cost,
          "LUCKY_SPIN_FEE",
          "spin-fee:" + userId + ":" + UUID.randomUUID());
    }

    // Pick prize according to probability weights
    WheelPrize prize = pickRandomPrize();

    long currentExp = ((Number) state.get("Exp")).longValue();
    long newExp = currentExp;

    if ("EXP".equals(prize.rewardType())) {
      newExp += prize.amount();
    } else if ("COIN_REWARD".equals(prize.rewardType()) && prize.amount() > 0) {
      wallet.apply(
          userId,
          "REWARD",
          prize.amount(),
          "LUCKY_SPIN",
          "spin-win:" + userId + ":" + UUID.randomUUID());
    } else if ("COIN_PAID".equals(prize.rewardType()) && prize.amount() > 0) {
      wallet.apply(
          userId,
          "PAID",
          prize.amount(),
          "LUCKY_SPIN_JACKPOT",
          "spin-jackpot:" + userId + ":" + UUID.randomUUID());
    }

    int newLevel = calculateLevel(newExp);
    String newTitle = titleForLevel(newLevel);

    // Record lucky wheel history
    jdbc.update(
        "INSERT INTO dbo.LuckyWheelHistory(UserId, PrizeIndex, RewardType, RewardAmount, RewardLabel, CostCoin) "
            + "VALUES (?, ?, ?, ?, ?, ?)",
        userId,
        prize.index(),
        prize.rewardType(),
        prize.amount(),
        prize.label(),
        cost);

    // Update free spin timestamp if it was a free spin
    if (isFreeSpin) {
      jdbc.update(
          "UPDATE dbo.UserGamification "
              + "SET Exp = ?, Level = ?, Title = ?, LastFreeSpinDate = CAST(SYSUTCDATETIME() AS date), LastFreeSpinAt = SYSUTCDATETIME(), UpdatedAt = SYSUTCDATETIME() "
              + "WHERE UserId = ?",
          newExp,
          newLevel,
          newTitle,
          userId);
    } else {
      jdbc.update(
          "UPDATE dbo.UserGamification "
              + "SET Exp = ?, Level = ?, Title = ?, UpdatedAt = SYSUTCDATETIME() "
              + "WHERE UserId = ?",
          newExp,
          newLevel,
          newTitle,
          userId);
    }

    var freshBalance = wallet.balance(userId);
    String message = "Quay trúng: " + prize.label() + "!";
    if (newLevel > ((Number) state.get("Level")).intValue()) {
      message += " Chúc mừng bạn đã thăng cấp " + newLevel + " (" + newTitle + ")!";
    }

    return new SpinResult(
        prize.index(),
        prize.rewardType(),
        prize.amount(),
        prize.label(),
        cost,
        newExp,
        newLevel,
        newTitle,
        freshBalance.paidCoin(),
        freshBalance.rewardCoin(),
        message);
  }

  @Transactional
  public void addReadingExp(long userId, int amount) {
    if (amount <= 0) return;
    ensureGamificationRow(userId);

    var state = jdbc.queryForMap(
        "SELECT Exp FROM dbo.UserGamification WITH (UPDLOCK, HOLDLOCK) WHERE UserId = ?",
        userId);

    long exp = ((Number) state.get("Exp")).longValue() + amount;
    int level = calculateLevel(exp);
    String title = titleForLevel(level);

    jdbc.update(
        "UPDATE dbo.UserGamification SET Exp = ?, Level = ?, Title = ?, UpdatedAt = SYSUTCDATETIME() WHERE UserId = ?",
        exp,
        level,
        title,
        userId);
  }

  @Transactional
  public ReadingExpResult awardReadingExp(Identity actor, long chapterId) {
    long userId = actor.userId();
    ensureGamificationRow(userId);

    var existing = jdbc.queryForList(
        "SELECT Id FROM dbo.ReadingExpHistory WHERE UserId = ? AND ChapterId = ?",
        Long.class, userId, chapterId);
    if (!existing.isEmpty()) {
      var profile = getProfile(actor);
      return new ReadingExpResult(
          false, 0, profile.exp(), profile.level(), profile.title(), "Bạn đã nhận thưởng EXP cho chương này trước đó.");
    }

    int expAwarded = 20;
    try {
      jdbc.update(
          "INSERT INTO dbo.ReadingExpHistory(UserId, ChapterId, ExpAwarded) VALUES (?, ?, ?)",
          userId, chapterId, expAwarded);
    } catch (org.springframework.dao.DuplicateKeyException duplicate) {
      var profile = getProfile(actor);
      return new ReadingExpResult(
          false, 0, profile.exp(), profile.level(), profile.title(), "Bạn đã nhận thưởng EXP cho chương này trước đó.");
    }

    addReadingExp(userId, expAwarded);
    var profile = getProfile(actor);
    String message = "Chúc mừng bạn nhận được +" + expAwarded + " EXP khi hoàn thành chương truyện!";
    return new ReadingExpResult(true, expAwarded, profile.exp(), profile.level(), profile.title(), message);
  }

  @Transactional(readOnly = true)
  public GamificationHistory getHistory(Identity actor, int limit) {
    long userId = actor.userId();
    int size = Math.max(1, Math.min(50, limit));

    var checkIns = jdbc.query(
        "SELECT TOP (" + size + ") Id, CheckInDate, DayInStreak, ExpAwarded, RewardCoinAwarded, CreatedAt "
            + "FROM dbo.CheckInHistory WHERE UserId = ? ORDER BY Id DESC",
        (rs, rowNum) -> new CheckInItem(
            rs.getLong("Id"),
            rs.getDate("CheckInDate").toLocalDate(),
            rs.getInt("DayInStreak"),
            rs.getInt("ExpAwarded"),
            rs.getInt("RewardCoinAwarded"),
            rs.getTimestamp("CreatedAt").toInstant()),
        userId);

    var spins = jdbc.query(
        "SELECT TOP (" + size + ") Id, PrizeIndex, RewardType, RewardAmount, RewardLabel, CostCoin, CreatedAt "
            + "FROM dbo.LuckyWheelHistory WHERE UserId = ? ORDER BY Id DESC",
        (rs, rowNum) -> new SpinItem(
            rs.getLong("Id"),
            rs.getInt("PrizeIndex"),
            rs.getString("RewardType"),
            rs.getInt("RewardAmount"),
            rs.getString("RewardLabel"),
            rs.getInt("CostCoin"),
            rs.getTimestamp("CreatedAt").toInstant()),
        userId);

    var readingExp = jdbc.query(
        "SELECT TOP (" + size + ") Id, ChapterId, ExpAwarded, CreatedAt "
            + "FROM dbo.ReadingExpHistory WHERE UserId = ? ORDER BY Id DESC",
        (rs, rowNum) -> new ReadingExpItem(
            rs.getLong("Id"),
            rs.getLong("ChapterId"),
            rs.getInt("ExpAwarded"),
            rs.getTimestamp("CreatedAt").toInstant()),
        userId);

    return new GamificationHistory(checkIns, spins, readingExp);
  }

  private WheelPrize pickRandomPrize() {
    int roll = random.nextInt(100); // 0..99
    int cumulative = 0;
    for (WheelPrize prize : PRIZES) {
      cumulative += prize.probabilityPercent();
      if (roll < cumulative) {
        return prize;
      }
    }
    return PRIZES.getFirst();
  }

  private void ensureGamificationRow(long userId) {
    jdbc.update(
        "IF NOT EXISTS (SELECT 1 FROM dbo.UserGamification WHERE UserId = ?) "
            + "BEGIN INSERT INTO dbo.UserGamification(UserId, Exp, Level, Title, CheckInStreak, TotalCheckIns) "
            + "VALUES (?, 0, 1, N'Tân thủ', 0, 0) END",
        userId,
        userId);
  }
}
