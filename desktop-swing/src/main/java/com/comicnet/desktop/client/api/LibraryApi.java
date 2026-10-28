package com.comicnet.desktop.client.api;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class LibraryApi {
  private final ApiClient api;

  public LibraryApi(ApiClient api) {
    this.api = api;
  }

  public CompletableFuture<ApiClient.Reply> saveDefault(String comic, Map<String, ?> changes) {
    return api.get("/me/collections?size=1")
        .thenCompose(
            collections -> {
              String id = collections.body().path("items").get(0).path("id").asText();
              String path = "/me/collections/" + id + "/items/" + comic;
              return api.get(path)
                  .handle(
                      (item, error) -> {
                        if (error == null) return item;
                        Throwable cause = ApiClient.unwrap(error);
                        if (cause instanceof ApiClient.ApiFailure failure
                            && failure.status() == 404) return null;
                        throw new java.util.concurrent.CompletionException(cause);
                      })
                  .thenCompose(
                      item ->
                          api.request(
                              "PUT",
                              path,
                              changes,
                              item == null ? null : item.etag(),
                              item == null ? Map.of("If-None-Match", "*") : Map.of()));
            });
  }
}
