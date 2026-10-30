package com.comicnet;

import static org.assertj.core.api.Assertions.*;

import com.comicnet.identity.api.*;
import com.comicnet.identity.service.AuthenticationService;
import com.comicnet.catalog.api.CatalogDtos.*;
import com.comicnet.catalog.service.CatalogService;
import com.comicnet.common.web.*;
import com.comicnet.community.dto.CommentDtos.*;
import com.comicnet.community.service.CommentService;
import com.comicnet.notification.dto.NotificationDtos.*;
import com.comicnet.notification.service.*;
import com.comicnet.support.SqlTestActors;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
      "comicnet.mail.worker-enabled=false",
      "comicnet.notification.worker-enabled=false",
      "comicnet.publish.schedule-enabled=false"
    })
@Transactional
class NotificationFlowIT {
  @Autowired AuthenticationService auth;
  @Autowired AccountAdministration accounts;
  @Autowired CatalogService catalog;
  @Autowired CommentService comments;
  @Autowired NotificationService notifications;
  @Autowired NotificationWorker worker;
  @Autowired JdbcTemplate jdbc;
  @Autowired jakarta.persistence.EntityManager entities;
  private SqlTestActors actors;
  private Identity admin, author, replier, third;
  private long comic;

  private String key() {
    return UUID.randomUUID().toString();
  }

  @BeforeEach
  void setup() {
    actors = new SqlTestActors(auth, accounts, jdbc, entities);
    admin = actors.administrator();
    author = actors.reader();
    replier = actors.reader();
    third = actors.reader();
    comic =
        Ids.parse(
            catalog
                .create(
                    admin,
                    new ComicInput(
                        "Thông báo",
                        "notify-" + key(),
                        "Notification fixture",
                        "MANGA",
                        "JP",
                        "ONGOING",
                        "ALL_AGE",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        null),
                    key())
                .id());
    catalog.visibility(
        admin, comic, new VisibilityPut("PUBLIC", "JUnit notification"), Etags.of(catalog.version(comic)));
  }

  private CommentView comment(Identity actor, String body, String parent) {
    return comments.create(
        actor, new CommentInput(comic + "", null, null, null, parent, body, false), key());
  }

  private void drain() {
    for (int processed = 0; processed < 20; processed++) if (!worker.processNext()) return;
  }

  private List<NotificationView> inbox(Identity actor) {
    return notifications.inbox(actor, 0, 50, false).items();
  }

  @Test
  void registrationAndFirstLoginCreateOneDurableWelcome() {
    assertThat(inbox(author))
        .filteredOn(n -> n.title().equals("Chào mừng bạn đến với Comic Net"))
        .hasSize(1);
    String email = jdbc.queryForObject("SELECT Email FROM dbo.[User] WHERE Id=?", String.class, author.userId());
    actors.login(email);
    actors.login(email);
    assertThat(inbox(author))
        .filteredOn(n -> n.title().equals("Chào mừng bạn đến với Comic Net"))
        .hasSize(1);
  }

  @Test
  void replyAndReactionNotificationsRespectOwnershipPreferencesAndDeduplication() {
    var parent = comment(author, "Bình luận gốc", null);
    comment(replier, "Trả lời lần một", parent.id());
    drain();

    var authorInbox = inbox(author);
    assertThat(authorInbox).filteredOn(n -> n.type().equals("COMMENT_REPLY")).hasSize(1);
    assertThat(notifications.summary(author).unread()).isEqualTo(2);
    var reply = authorInbox.getFirst();
    assertThat(reply.linkType()).isEqualTo("COMMENT");
    assertThat(reply.read()).isFalse();
    assertThat(inbox(replier)).filteredOn(n -> n.type().equals("COMMENT_REPLY")).isEmpty();

    notifications.read(author, Ids.parse(reply.id()), true);
    assertThat(notifications.summary(author).unread()).isEqualTo(1);
    notifications.read(author, Ids.parse(reply.id()), false);
    assertThat(notifications.summary(author).unread()).isEqualTo(2);

    comments.vote(third, Ids.parse(parent.id()), 1);
    drain();
    assertThat(inbox(author))
        .filteredOn(n -> n.type().equals("COMMENT_REACTION"))
        .hasSize(1);
    // A second vote toggle by the same actor never produces a second notification.
    comments.vote(third, Ids.parse(parent.id()), null);
    comments.vote(third, Ids.parse(parent.id()), 1);
    drain();
    assertThat(inbox(author))
        .filteredOn(n -> n.type().equals("COMMENT_REACTION"))
        .hasSize(1);

    notifications.preference(author, "COMMENT_REPLY", new PreferenceInput(false, false));
    comment(third, "Trả lời khi đã tắt thông báo", parent.id());
    drain();
    assertThat(inbox(author)).filteredOn(n -> n.type().equals("COMMENT_REPLY")).hasSize(1);
    assertThatThrownBy(
            () ->
                notifications.preference(
                    author, "COMMENT_REPLY", new PreferenceInput(false, true)))
        .isInstanceOfSatisfying(
            DomainException.class, e -> assertThat(e.code()).isEqualTo("INVALID_NOTIFICATION_PREFERENCE"));
  }

  @Test
  void announcementReachesActiveAccountsOnceAndReadAllClearsTheInbox() {
    var created = notifications.announce(admin, new AnnouncementInput("Bảo trì", "Hệ thống bảo trì 22:00."));
    assertThat(created.recipients()).isPositive();
    assertThat(inbox(author)).filteredOn(n -> n.type().equals("SYSTEM_ANNOUNCEMENT")).hasSize(2);
    assertThat(inbox(third)).filteredOn(n -> n.type().equals("SYSTEM_ANNOUNCEMENT")).hasSize(2);
    assertThat(notifications.announcements(admin, 0, 20).items()).hasSize(1);

    notifications.readAll(third);
    assertThat(notifications.summary(third).unread()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.Notification WHERE UserId=? AND Type='SYSTEM_ANNOUNCEMENT'",
                Integer.class,
                third.userId()))
        .isEqualTo(2);
  }
}
