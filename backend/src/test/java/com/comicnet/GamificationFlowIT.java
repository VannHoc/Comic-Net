package com.comicnet;

import static org.assertj.core.api.Assertions.*;

import com.comicnet.common.web.DomainException;
import com.comicnet.gamification.service.GamificationService;
import com.comicnet.identity.api.Identity;
import com.comicnet.wallet.api.WalletOperations;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class GamificationFlowIT {
  @Autowired private GamificationService gamificationService;
  @Autowired private WalletOperations walletService;
  @Autowired private JdbcTemplate jdbc;

  private Identity testUser;

  @BeforeEach
  void setUp() {
    // Find or create active user for gamification testing
    Long userId = jdbc.queryForObject(
        "SELECT TOP 1 Id FROM dbo.[User] WHERE Status = 'ACTIVE' ORDER BY Id ASC",
        Long.class);
    assertThat(userId).isNotNull();
    testUser = new Identity(userId, 1L, 1L, Set.of("READER"));

    // Clean up previous test check-ins/spins/reading exp for this user within transaction
    jdbc.update("DELETE FROM dbo.CheckInHistory WHERE UserId = ?", userId);
    jdbc.update("DELETE FROM dbo.LuckyWheelHistory WHERE UserId = ?", userId);
    jdbc.update("DELETE FROM dbo.ReadingExpHistory WHERE UserId = ?", userId);
    jdbc.update(
        "UPDATE dbo.UserGamification SET Exp = 0, Level = 1, Title = N'Tân thủ', "
            + "LastCheckInDate = NULL, CheckInStreak = 0, TotalCheckIns = 0, LastFreeSpinDate = NULL, LastFreeSpinAt = NULL "
            + "WHERE UserId = ?",
        userId);
  }

  @Test
  void initialProfileHasLevelOneAndAvailableCheckInAndFreeSpin() {
    var profile = gamificationService.getProfile(testUser);
    assertThat(profile.userId()).isEqualTo(testUser.userId());
    assertThat(profile.level()).isEqualTo(1);
    assertThat(profile.exp()).isEqualTo(0);
    assertThat(profile.title()).isEqualTo("Tân thủ");
    assertThat(profile.canCheckInToday()).isTrue();
    assertThat(profile.freeSpinsAvailable()).isEqualTo(1);
    assertThat(profile.secondsUntilNextFreeSpin()).isEqualTo(0);
  }

  @Test
  void dailyCheckInAwardsExpAndCoinsAndPreventsDoubleCheckIn() {
    long initialReward = walletService.balance(testUser.userId()).rewardCoin();

    var result = gamificationService.checkIn(testUser);
    assertThat(result.dayInStreak()).isEqualTo(1);
    assertThat(result.expAwarded()).isEqualTo(15);
    assertThat(result.rewardCoinAwarded()).isEqualTo(5);
    assertThat(result.streak()).isEqualTo(1);

    // Verify wallet received Reward Coins
    long newReward = walletService.balance(testUser.userId()).rewardCoin();
    assertThat(newReward).isEqualTo(initialReward + 5);

    // Verify profile updated
    var profile = gamificationService.getProfile(testUser);
    assertThat(profile.canCheckInToday()).isFalse();
    assertThat(profile.exp()).isEqualTo(15);

    // Verify double check-in in same day throws 409 Conflict
    assertThatThrownBy(() -> gamificationService.checkIn(testUser))
        .isInstanceOf(DomainException.class)
        .hasMessageContaining("đã điểm danh");
  }

  @Test
  void luckyWheelProvidesFreeSpinAndRequiresRewardCoinsForSubsequentSpins() {
    // Top up test user with reward coins to ensure funds for extra spin
    walletService.apply(testUser.userId(), "REWARD", 50, "TEST_SETUP", "setup:" + testUser.userId());

    // Spin 1: Free Daily Spin (cost = 0)
    var spin1 = gamificationService.spin(testUser);
    assertThat(spin1.costCoin()).isEqualTo(0);
    assertThat(spin1.prizeIndex()).isBetween(0, 7);
    assertThat(spin1.label()).isNotBlank();

    // Spin 2: Paid Spin (cost = 5 Reward Coin)
    long beforeSpin2 = walletService.balance(testUser.userId()).rewardCoin();
    var spin2 = gamificationService.spin(testUser);
    assertThat(spin2.costCoin()).isEqualTo(5);
    assertThat(spin2.prizeIndex()).isBetween(0, 7);

    // Verify free spin resets after 12 hours
    jdbc.update(
        "UPDATE dbo.UserGamification SET LastFreeSpinAt = DATEADD(hour, -13, SYSUTCDATETIME()) WHERE UserId = ?",
        testUser.userId());
    var wheelInfo = gamificationService.getWheelInfo(testUser);
    assertThat(wheelInfo.hasFreeSpin()).isTrue();
    assertThat(wheelInfo.secondsUntilNextFreeSpin()).isEqualTo(0);

    var spin3 = gamificationService.spin(testUser);
    assertThat(spin3.costCoin()).isEqualTo(0);

    // Verify history recorded
    var history = gamificationService.getHistory(testUser, 10);
    assertThat(history.spins()).hasSize(3);
  }

  @Test
  void expProgressionLevelsUpAndPromotesTitle() {
    gamificationService.addReadingExp(testUser.userId(), 150);

    var profile = gamificationService.getProfile(testUser);
    assertThat(profile.exp()).isEqualTo(150);
    assertThat(profile.level()).isEqualTo(2);
    assertThat(profile.title()).isEqualTo("Tân thủ");

    // Reaching 400 EXP levels up to 5 -> Nhập môn
    gamificationService.addReadingExp(testUser.userId(), 250); // total 400: Lv1 (100) + Lv2 (100) + Lv3 (100) + Lv4 (100) -> Lv5
    var profileLv5 = gamificationService.getProfile(testUser);
    assertThat(profileLv5.exp()).isEqualTo(400);
    assertThat(profileLv5.level()).isEqualTo(5);
    assertThat(profileLv5.title()).isEqualTo("Nhập môn");
  }

  @Test
  void readingChapterAwardsExpOnceAndPreventsDuplicateFarming() {
    long chapterId = 228L;

    // First completion of chapter 228 awards 20 EXP
    var res1 = gamificationService.awardReadingExp(testUser, chapterId);
    assertThat(res1.awarded()).isTrue();
    assertThat(res1.expAwarded()).isEqualTo(20);
    assertThat(res1.totalExp()).isEqualTo(20);

    // Second completion of the same chapter should not award duplicate EXP
    var res2 = gamificationService.awardReadingExp(testUser, chapterId);
    assertThat(res2.awarded()).isFalse();
    assertThat(res2.expAwarded()).isEqualTo(0);
    assertThat(res2.totalExp()).isEqualTo(20);

    // Reading a different chapter (230) awards another 20 EXP
    var res3 = gamificationService.awardReadingExp(testUser, 230L);
    assertThat(res3.awarded()).isTrue();
    assertThat(res3.expAwarded()).isEqualTo(20);
    assertThat(res3.totalExp()).isEqualTo(40);

    // Verify history includes reading exp items
    var history = gamificationService.getHistory(testUser, 10);
    assertThat(history.readingExp()).hasSize(2);
  }
}
