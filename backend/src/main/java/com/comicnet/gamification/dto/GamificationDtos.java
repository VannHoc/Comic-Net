package com.comicnet.gamification.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class GamificationDtos {
  private GamificationDtos() {}

  public record Profile(
      long userId,
      long exp,
      int level,
      long currentLevelExp,
      long nextLevelExp,
      String title,
      LocalDate lastCheckInDate,
      int checkInStreak,
      int totalCheckIns,
      boolean canCheckInToday,
      int freeSpinsAvailable,
      long secondsUntilNextFreeSpin,
      long paidCoin,
      long rewardCoin) {}

  public record CheckInResult(
      LocalDate checkInDate,
      int dayInStreak,
      int expAwarded,
      int rewardCoinAwarded,
      long totalExp,
      int level,
      String title,
      int streak,
      String message) {}

  public record WheelPrize(
      int index,
      String rewardType,
      int amount,
      String label,
      int probabilityPercent) {}

  public record LuckyWheelInfo(
      List<WheelPrize> prizes,
      boolean hasFreeSpin,
      int spinCostRewardCoin,
      long secondsUntilNextFreeSpin) {}

  public record SpinResult(
      int prizeIndex,
      String rewardType,
      int amount,
      String label,
      int costCoin,
      long totalExp,
      int level,
      String title,
      long paidCoin,
      long rewardCoin,
      String message) {}

  public record CheckInItem(
      long id,
      LocalDate checkInDate,
      int dayInStreak,
      int expAwarded,
      int rewardCoinAwarded,
      Instant createdAt) {}

  public record SpinItem(
      long id,
      int prizeIndex,
      String rewardType,
      int amount,
      String label,
      int costCoin,
      Instant createdAt) {}

  public record ReadingExpInput(Long chapterId) {}

  public record ReadingExpResult(
      boolean awarded,
      int expAwarded,
      long totalExp,
      int level,
      String title,
      String message) {}

  public record ReadingExpItem(
      long id,
      long chapterId,
      int expAwarded,
      Instant createdAt) {}

  public record GamificationHistory(
      List<CheckInItem> checkIns,
      List<SpinItem> spins,
      List<ReadingExpItem> readingExp) {}
}
