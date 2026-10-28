package com.comicnet.publishing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Releases approved snapshots whose schedule is due. Enabled by default, can be disabled for QA. */
@Service
public class ScheduledPublishWorker {
  private static final Logger log = LoggerFactory.getLogger(ScheduledPublishWorker.class);
  private final PublishingService publishing;
  private final Environment env;

  public ScheduledPublishWorker(PublishingService publishing, Environment env) {
    this.publishing = publishing;
    this.env = env;
  }

  @Scheduled(fixedDelayString = "${comicnet.publish.schedule-poll-delay:5000}")
  public void releaseDue() {
    if (!env.getProperty("comicnet.publish.schedule-enabled", Boolean.class, true)) return;
    try {
      for (int released = 0; released < 20; released++)
        if (!publishing.releaseNextScheduled()) return;
    } catch (RuntimeException error) {
      log.warn("Scheduled publish run failed: {}", error.toString());
    }
  }
}
