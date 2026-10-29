package com.comicnet.gamification.controller;

import com.comicnet.gamification.dto.GamificationDtos.*;
import com.comicnet.gamification.service.GamificationService;
import com.comicnet.identity.api.Identity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/gamification")
public class GamificationController {
  private final GamificationService gamificationService;

  public GamificationController(GamificationService gamificationService) {
    this.gamificationService = gamificationService;
  }

  @GetMapping("/profile")
  public Profile getProfile(@AuthenticationPrincipal Identity actor) {
    return gamificationService.getProfile(actor);
  }

  @PostMapping("/check-in")
  public CheckInResult checkIn(@AuthenticationPrincipal Identity actor) {
    return gamificationService.checkIn(actor);
  }

  @GetMapping("/lucky-wheel")
  public LuckyWheelInfo getWheelInfo(@AuthenticationPrincipal Identity actor) {
    return gamificationService.getWheelInfo(actor);
  }

  @PostMapping("/lucky-wheel/spin")
  public SpinResult spin(@AuthenticationPrincipal Identity actor) {
    return gamificationService.spin(actor);
  }

  @PostMapping("/reading-exp")
  public ReadingExpResult awardReadingExp(
      @AuthenticationPrincipal Identity actor,
      @RequestBody ReadingExpInput input) {
    long chapterId = input != null && input.chapterId() != null ? input.chapterId() : 0L;
    return gamificationService.awardReadingExp(actor, chapterId);
  }

  @GetMapping("/history")
  public GamificationHistory getHistory(
      @AuthenticationPrincipal Identity actor,
      @RequestParam(defaultValue = "20") int limit) {
    return gamificationService.getHistory(actor, limit);
  }
}
