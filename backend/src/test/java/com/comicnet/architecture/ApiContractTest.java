package com.comicnet.architecture;

import static org.assertj.core.api.Assertions.*;

import com.comicnet.catalog.api.CatalogDtos.ComicSummary;
import com.comicnet.library.dto.LibraryDtos.*;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ApiContractTest {
  private final JsonMapper json =
      JsonMapper.builder(
              tools.jackson.core.json.JsonFactory.builder()
                  .enable(tools.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                  .build())
          .build();

  @Test
  void everyApprovedOperationHasOneControllerRouteAndNoDuplicateJsonKeys() throws Exception {
    JsonNode contract = json.readTree(Files.readString(Path.of("../docs/api/openapi-mvp.json")));
    Set<String> expected = new TreeSet<>(), ids = new HashSet<>();
    for (var path : contract.path("paths").properties())
      for (var operation : path.getValue().properties()) {
        assertThat(operation.getValue().isObject()).as(path.getKey()).isTrue();
        assertThat(operation.getValue().path("operationId").asText()).isNotBlank();
        expected.add(operation.getKey().toUpperCase(Locale.ROOT) + " " + normalized(path.getKey()));
        assertThat(ids.add(operation.getValue().path("operationId").asText())).isTrue();
      }
    Set<String> actual = new TreeSet<>();
    Path root = Path.of("src/main/java");
    try (var paths = Files.walk(root)) {
      for (Path file :
          paths.filter(p -> p.getFileName().toString().endsWith("Controller.java")).toList()) {
        Class<?> type =
            Class.forName(
                root.relativize(file)
                    .toString()
                    .replace('\\', '.')
                    .replace('/', '.')
                    .replaceAll("\\.java$", ""));
        RequestMapping mapping = type.getAnnotation(RequestMapping.class);
        String base = mapping == null ? "" : mapping.value()[0];
        for (Method method : type.getDeclaredMethods()) {
          String verb = null, path = null;
          if (method.isAnnotationPresent(GetMapping.class)) {
            verb = "GET";
            path = first(method.getAnnotation(GetMapping.class).value());
          }
          if (method.isAnnotationPresent(PostMapping.class)) {
            verb = "POST";
            path = first(method.getAnnotation(PostMapping.class).value());
          }
          if (method.isAnnotationPresent(PutMapping.class)) {
            verb = "PUT";
            path = first(method.getAnnotation(PutMapping.class).value());
          }
          if (method.isAnnotationPresent(PatchMapping.class)) {
            verb = "PATCH";
            path = first(method.getAnnotation(PatchMapping.class).value());
          }
          if (method.isAnnotationPresent(DeleteMapping.class)) {
            verb = "DELETE";
            path = first(method.getAnnotation(DeleteMapping.class).value());
          }
          if (verb != null) {
            String full = (base + "/" + path).replaceAll("/{2,}", "/");
            if (full.endsWith("/")) full = full.substring(0, full.length() - 1);
            if (full.startsWith("/api/v1/"))
              assertThat(actual.add(verb + " " + normalized(full.substring(7))))
                  .as("Duplicate mapped method")
                  .isTrue();
          }
        }
      }
    }
    assertThat(expected).hasSize(174);
    assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
  }

  private String first(String[] values) {
    return values.length == 0 ? "" : values[0];
  }

  private String normalized(String path) {
    return path.replaceAll("\\{[^}]+}", "{id}");
  }

  @Test
  void librarySerializationKeepsTheSingleStatusSourceAndRequiredNullableFields() {
    var comic =
        new ComicSummary(
            "1", "Comic", "comic", "MANGA", "ALL_AGE", "PUBLIC", "PUBLISHED", null, Instant.EPOCH, null, null);
    JsonNode custom = json.valueToTree(new CollectionItem(comic, null, null, Instant.EPOCH));
    assertThat(custom.has("readingStatus")).isFalse();
    assertThat(custom.has("isFavorite")).isFalse();
    assertThat(custom.path("comic").has("coverUrl")).isTrue();
    JsonNode defaults = json.valueToTree(new CollectionView("1", "Library", true, "LIBRARY"));
    assertThat(defaults.path("isSystem").asBoolean()).isTrue();
    assertThat(defaults.has("system")).isFalse();
  }
}
