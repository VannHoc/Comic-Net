package com.comicnet.search.controller;

import com.comicnet.catalog.api.CatalogDtos.ComicSummary;
import com.comicnet.catalog.api.CatalogQueries;
import com.comicnet.common.web.PageResult;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {
  private final CatalogQueries catalog;

  public SearchController(CatalogQueries catalog) {
    this.catalog = catalog;
  }

  public record Suggestion(String id, String title, String type) {}

  @GetMapping("/suggestions")
  public java.util.List<Suggestion> suggestions(
      @RequestParam(defaultValue = "") @Size(max = 200) String q) {
    String query = q.strip();
    if (!query.isEmpty())
      query = com.comicnet.search.service.SearchHistoryService.normalize(query);
    return catalog
        .list(
            new CatalogQueries.Filter(
                query, null, null, null, null, query.isEmpty() ? "POPULARITY_DESC" : "TITLE_ASC"),
            0,
            8)
        .items()
        .stream()
        .map(comic -> new Suggestion(comic.id(), comic.title(), comic.type()))
        .toList();
  }

  @GetMapping("/comics")
  public PageResult<ComicSummary> search(
      @RequestParam @NotBlank @Size(max = 200) String q,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String genreId,
      @RequestParam(required = false) String tagId,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "UPDATED_DESC") String sort) {
    return catalog.list(
        new CatalogQueries.Filter(q, type, genreId, tagId, status, sort), page, size);
  }
}
