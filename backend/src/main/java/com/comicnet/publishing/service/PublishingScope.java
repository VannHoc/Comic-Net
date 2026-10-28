package com.comicnet.publishing.service;

import com.comicnet.identity.api.Identity;
import com.comicnet.catalog.api.CatalogAccess;
import com.comicnet.common.web.*;
import com.comicnet.publishing.repository.PublishingRepository;
import com.comicnet.publishing.api.PublishingChapterAccess;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Every mutation takes locks in Comic -> Translation -> Chapter -> Version order. */
@Component
public class PublishingScope implements PublishingChapterAccess {
  private final JdbcTemplate jdbc;
  private final CatalogAccess catalog;
  private final PublishingRepository repository;

  public PublishingScope(
      JdbcTemplate jdbc, CatalogAccess catalog, PublishingRepository repository) {
    this.jdbc = jdbc;
    this.catalog = catalog;
    this.repository = repository;
  }

  public CatalogAccess.TranslationScope chapter(
      Identity actor, long id, String permission, boolean lock) {
    var chapter = repository.chapter(id);
    var scope =
        catalog.scopedTranslation(actor, Ids.parse(chapter.translationId()), permission, lock);
    if (lock)
      jdbc.queryForMap(
          "SELECT Id FROM dbo.Chapter WITH (UPDLOCK,HOLDLOCK) WHERE Id=? AND DeletedAt IS NULL",
          id);
    return scope;
  }

  @Override
  public void requireChapterEditor(Identity actor, long chapterId) {
    chapter(actor, chapterId, "chapter.draft", true);
  }

  public CatalogAccess.TranslationScope version(
      Identity actor, long id, String permission, boolean lock) {
    var parents =
        jdbc.queryForList("SELECT ChapterId FROM dbo.ChapterVersion WHERE Id=?", Long.class, id);
    if (parents.isEmpty()) throw DomainException.missing();
    var scope = chapter(actor, parents.getFirst(), permission, lock);
    if (lock)
      jdbc.queryForMap("SELECT Id FROM dbo.ChapterVersion WITH (UPDLOCK,HOLDLOCK) WHERE Id=?", id);
    return scope;
  }

  public void reviewer(Identity actor) {
    if (actor == null || (!actor.administrator() && !actor.roles().contains("MODERATOR")))
      throw DomainException.forbidden();
  }

  public void lockForJob(long version) {
    var parents =
        jdbc.queryForList(
            "SELECT ChapterId FROM dbo.ChapterVersion WHERE Id=?", Long.class, version);
    if (parents.isEmpty()) throw DomainException.missing();
    var chapter = repository.chapter(parents.getFirst());
    catalog.lockForJob(Ids.parse(chapter.translationId()));
    jdbc.queryForMap(
        "SELECT Id FROM dbo.Chapter WITH (UPDLOCK,HOLDLOCK) WHERE Id=?", Ids.parse(chapter.id()));
    jdbc.queryForMap(
        "SELECT Id FROM dbo.ChapterVersion WITH (UPDLOCK,HOLDLOCK) WHERE Id=?", version);
  }
}
