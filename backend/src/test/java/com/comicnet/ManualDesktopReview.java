package com.comicnet;

import java.nio.file.*;
import org.springframework.boot.SpringApplication;
import tools.jackson.databind.json.JsonMapper;

/** Optional local QA fixture. Test sources only: never packaged in the application JAR. */
public final class ManualDesktopReview {
  public static void main(String[] args) throws Exception {
    String configured = System.getenv("COMICNET_REVIEW_DIRECTORY");
    if (configured == null)
      throw new IllegalStateException("Set the private QA directory explicitly.");
    Path directory = Path.of(configured).toAbsolutePath().normalize();
    if (!directory.endsWith(Path.of(".local", "qa"))
        || !Files.isRegularFile(directory.getParent().getParent().resolve("prj_app.md")))
      throw new IllegalStateException(
          "QA output must stay inside the project .local/qa directory.");
    Files.createDirectories(directory);
    Path stop = directory.resolve("finish.flag");
    if (Files.exists(stop))
      throw new IllegalStateException(
          "Remove the previous QA finish marker before starting a new review.");
    int minutes = Integer.parseInt(System.getenv().getOrDefault("COMICNET_QA_MINUTES", "30"));
    if (minutes < 5 || minutes > 120)
      throw new IllegalArgumentException("QA duration must be 5–120 minutes.");
    var context =
        SpringApplication.run(
            ComicNetApplication.class,
            "--server.port=8081",
            "--comicnet.mail.worker-enabled=false",
            "--comicnet.upload.worker-enabled=true");
    ReadingConcurrencyIT fixture = new ReadingConcurrencyIT();
    context.getAutowireCapableBeanFactory().autowireBean(fixture);
    try {
      fixture.committedFixture();
      Files.writeString(
          directory.resolve("review.json"),
          JsonMapper.builder().build().writeValueAsString(fixture.reviewSnapshot()));
      System.out.println(
          "Local SQL/S3 desktop review fixture ready for "
              + minutes
              + " minutes. No credential printed. Finish with the private QA marker.");
      long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(minutes);
      while (!Files.exists(stop) && System.nanoTime() < deadline) Thread.sleep(500);
    } finally {
      try {
        fixture.removeOnlyThisCommittedFixture();
      } finally {
        context.close();
      }
    }
  }
}
