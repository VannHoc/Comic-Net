package com.comicnet.reader.service;

import com.comicnet.identity.api.*;
import com.comicnet.common.web.*;
import com.comicnet.entitlement.api.ReadingAccess;
import com.comicnet.publishing.api.PublishedChapters;
import com.comicnet.publishing.api.PublishingDtos.*;
import com.comicnet.reader.api.ReadingAnchors;
import com.comicnet.storage.api.AssetStore;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReaderService implements ReadingAnchors {
  private final PublishedChapters chapters;
  private final ReadingAccess access;
  private final AssetStore assets;

  public ReaderService(PublishedChapters chapters, ReadingAccess access, AssetStore assets) {
    this.chapters = chapters;
    this.access = access;
    this.assets = assets;
  }

  @Transactional(readOnly = true)
  public com.comicnet.reader.dto.ReadingDtos.ChapterContext context(long id) {
    var snapshot = chapters.current(id).orElseThrow(DomainException::missing);
    var chapter = snapshot.chapter();
    return new com.comicnet.reader.dto.ReadingDtos.ChapterContext(
        chapter.id(),
        snapshot.comicId() + "",
        chapter.translationId(),
        chapter.displayNumber(),
        chapter.title(),
        snapshot.comicTitle(),
        chapter.accessPolicy());
  }

  @Transactional(readOnly = true)
  public ReadingAccess.Decision access(Identity actor, long id) {
    return chapters
        .current(id)
        .map(snapshot -> access.decide(actor, id, snapshot.chapter().accessPolicy()))
        .orElse(new ReadingAccess.Decision(false, "UNAVAILABLE", false));
  }

  @Transactional(readOnly = true)
  public Manifest manifest(Identity actor, long id, String format) {
    var snapshot = chapters.current(id).orElseThrow(DomainException::missing);
    if (!access.decide(actor, id, snapshot.chapter().accessPolicy()).allowed())
      throw new DomainException(
          403, "READING_RESTRICTED", "Chương cần quyền đọc hoặc mở khóa.");
    var displays = assets.displayMany(
        snapshot.pages().stream().map(page -> Ids.parse(page.assetId())).toList(), format);
    List<ManifestPage> pages =
        snapshot.pages().stream()
            .map(
                page -> {
                  var image = displays.get(Ids.parse(page.assetId()));
                  return new ManifestPage(
                      page.pageId(),
                      page.position(),
                      image.url(),
                      image.expiresAt(),
                      image.width(),
                      image.height(),
                      image.mimeType());
                })
            .toList();
    return new Manifest(
        id + "",
        snapshot.chapter().publishedVersionId(),
        pages,
        snapshot.previousChapterId(),
        snapshot.nextChapterId());
  }

  @Transactional(readOnly = true)
  public void requireValid(Identity actor, long chapter, long version, long page) {
    var current = chapters.current(chapter).orElseThrow(DomainException::missing);
    if (!access.decide(actor, chapter, current.chapter().accessPolicy()).allowed())
      throw DomainException.forbidden();
    if (!chapters.historicalAnchor(chapter, version, page))
      throw new DomainException(
          422, "INVALID_READING_ANCHOR", "Trang và version không thuộc chapter đã xuất bản này.");
  }

  @Transactional(readOnly = true)
  public boolean available(Identity actor, long chapter, long version, long page) {
    var current = chapters.current(chapter);
    return current.isPresent()
        && access.decide(actor, chapter, current.get().chapter().accessPolicy()).allowed()
        && chapters.historicalAnchor(chapter, version, page)
        && current.get().pages().stream().anyMatch(binding -> Ids.parse(binding.pageId()) == page);
  }
}
