package com.comicnet;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.comicnet.identity.api.*;
import com.comicnet.identity.service.AuthenticationService;
import com.comicnet.catalog.api.CatalogDtos.*;
import com.comicnet.catalog.service.CatalogService;
import com.comicnet.common.web.*;
import com.comicnet.community.dto.CommentDtos.*;
import com.comicnet.community.service.CommentService;
import com.comicnet.support.SqlTestActors;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    properties = {
      "comicnet.mail.worker-enabled=false",
      "comicnet.upload.worker-enabled=false",
      "comicnet.community.blocked-terms=forbiddenqa"
    })
@AutoConfigureMockMvc
@Transactional
class CommentFlowIT {
  @Autowired AuthenticationService auth;
  @Autowired AccountAdministration accounts;
  @Autowired CatalogService catalog;
  @Autowired CommentService comments;
  @Autowired JdbcTemplate jdbc;
  @Autowired jakarta.persistence.EntityManager entities;
  @Autowired MockMvc mvc;
  private Identity admin, reader;
  private long comic;

  @BeforeEach
  void setup() {
    var actors = new SqlTestActors(auth, accounts, jdbc, entities);
    admin = actors.administrator();
    reader = actors.reader();
    comic =
        Ids.parse(
            catalog
                .create(
                    admin,
                    new ComicInput(
                        "Community SQL",
                        "community-" + UUID.randomUUID(),
                        "Comment integration",
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
        admin,
        comic,
        new VisibilityPut("PUBLIC", "Community QA"),
        Etags.of(catalog.version(comic)));
  }

  private String key() {
    return UUID.randomUUID().toString();
  }

  private CommentInput input(String text, String parent) {
    return new CommentInput(comic + "", null, null, null, parent, text, false);
  }

  @Test
  void repliesVotesReplayAndDeletionPreserveThread() {
    String request = key();
    var parent = comments.create(reader, input("Bình luận đầu 😊", null), request);
    assertThat(comments.create(reader, input("Bình luận đầu 😊", null), request).id())
        .isEqualTo(parent.id());
    var reply = comments.create(admin, input("Trả lời", parent.id()), key());
    long id = Ids.parse(parent.id());
    comments.vote(admin, id, 1);
    comments.vote(admin, id, 1);
    assertThat(comments.get(null, id).votes()).isEqualTo(1);
    assertThat(comments.vote(admin, id, -1).votes()).isEqualTo(-1);
    comments.vote(admin, id, null);
    comments.delete(reader, id, comments.get(reader, id).etag());
    assertThat(comments.get(null, id).body()).isNull();
    assertThat(comments.get(null, id).deleted()).isTrue();
    assertThat(comments.get(null, Ids.parse(reply.id())).parentId()).isEqualTo(parent.id());
    assertThat(comments.list(null, comic, null, null, 0, 20).items()).hasSize(2);
  }

  @Test
  void ownershipEtagReportsModerationAndBlocksAreEnforced() {
    var comment = comments.create(reader, input("Nội dung cần duyệt", null), key());
    long id = Ids.parse(comment.id());
    assertThatThrownBy(
            () -> comments.edit(admin, id, new EditInput("Không phải chủ", false), comment.etag()))
        .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.status()).isEqualTo(404));
    comments.edit(reader, id, new EditInput("Đã sửa", true), comment.etag());
    assertThatThrownBy(
            () -> comments.edit(reader, id, new EditInput("Stale", false), comment.etag()))
        .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.status()).isEqualTo(412));
    comments.report(admin, id, "Report QA");
    comments.report(admin, id, "Repeat QA");
    assertThat(comments.reports(admin, 0, 20).items()).hasSize(1);
    assertThatThrownBy(() -> comments.reports(reader, 0, 20)).isInstanceOf(DomainException.class);
    comments.moderate(
        admin, id, new ModerationInput(true, "Ẩn nội dung QA"), comments.get(admin, id).etag());
    assertThat(comments.get(null, id).body()).isNull();
    assertThat(comments.reports(admin, 0, 20).items()).isEmpty();
    comments.block(admin, reader.userId(), true);
    assertThat(comments.list(admin, comic, null, null, 0, 20).items()).isEmpty();
    assertThat(comments.blocks(admin, 0, 20).items()).hasSize(1);
    comments.block(admin, reader.userId(), false);
    assertThat(comments.list(admin, comic, null, null, 0, 20).items()).hasSize(1);
    catalog.visibility(
        admin,
        comic,
        new VisibilityPut("HIDDEN", "Community hidden QA"),
        Etags.of(catalog.version(comic)));
    assertThatThrownBy(() -> comments.get(null, id)).isInstanceOf(DomainException.class);
  }

  @Test
  void executableContentSpamAndAnonymousMutationsAreRejected() throws Exception {
    mvc.perform(
            post("/api/v1/community/comments")
                .contentType("application/json")
                .content("{\"comicId\":\"" + comic + "\",\"body\":\"Hello\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/community/comments").param("comicId", comic + ""))
        .andExpect(status().isOk());
    assertThatThrownBy(
            () -> comments.create(reader, input("<script>alert(1)</script>", null), key()))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> comments.create(reader, input("forbiddenqa", null), key()))
        .isInstanceOf(DomainException.class);
    for (int i = 0; i < 10; i++) comments.create(reader, input("Message " + i, null), key());
    assertThatThrownBy(() -> comments.create(reader, input("Over limit", null), key()))
        .isInstanceOfSatisfying(DomainException.class, e -> assertThat(e.status()).isEqualTo(429));
  }
}
