package com.comicnet.architecture;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ModuleBoundariesTest {
  private static final Map<String, Set<String>> DEPENDENCIES =
      Map.ofEntries(
          Map.entry("observability", Set.of()),
          Map.entry("identity", Set.of("observability")),
          Map.entry("storage", Set.of("identity", "observability")),
          Map.entry("creator", Set.of("identity", "observability")),
          Map.entry("catalog", Set.of("identity", "creator", "storage", "observability")),
          Map.entry("publishing", Set.of("identity", "catalog", "creator", "storage", "observability")),
          Map.entry("entitlement", Set.of("identity", "publishing", "observability")),
          Map.entry(
              "reader", Set.of("identity", "publishing", "storage", "entitlement", "observability")),
          Map.entry("library", Set.of("identity", "catalog", "reader", "observability")),
          Map.entry("search", Set.of("catalog", "publishing", "observability")),
          Map.entry("analytics", Set.of("identity", "catalog", "publishing", "observability")),
          Map.entry(
              "community", Set.of("identity", "catalog", "publishing", "entitlement", "observability")),
          Map.entry("recommendation", Set.of("identity", "catalog", "analytics", "observability")),
          Map.entry("wallet", Set.of("identity", "observability")),
          Map.entry(
              "monetization",
              Set.of("identity", "publishing", "wallet", "entitlement", "observability")),
          Map.entry(
              "finance",
              Set.of("identity", "monetization", "wallet", "entitlement", "creator", "observability")),
          Map.entry("notification", Set.of("identity", "library", "observability")),
          Map.entry("gamification", Set.of("identity", "wallet", "entitlement", "observability")),
          Map.entry(
              "admin",
              Set.of(
                  "identity",
                  "catalog",
                  "creator",
                  "publishing",
                  "community",
                  "monetization",
                  "finance",
                  "observability")));

  @Test
  void modulesUseApprovedPublicApisWithoutReverseDependencies() throws Exception {
    Path root = Path.of("src/main/java/com/comicnet");
    List<String> violations = new ArrayList<>();
    Pattern imports =
        Pattern.compile("import\\s+(?:static\\s+)?com\\.comicnet\\.([a-z]+)\\.([^;]+);");
    try (var paths = Files.walk(root)) {
      for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
        String source = root.relativize(file).getName(0).toString();
        if (!DEPENDENCIES.containsKey(source) && !source.equals("common")) continue;
        var matcher = imports.matcher(Files.readString(file));
        while (matcher.find()) {
          String target = matcher.group(1), member = matcher.group(2);
          if (source.equals(target) || target.equals("common")) continue;
          if (!DEPENDENCIES.getOrDefault(source, Set.of()).contains(target)
              || !member.startsWith("api."))
            violations.add(root.relativize(file) + " imports " + target + "." + member);
        }
      }
    }
    assertThat(violations).as("Module dependencies and private implementation imports").isEmpty();
  }

  @Test
  void approvedGraphHasOnlyRealModulesAndNoCycle() {
    assertThat(DEPENDENCIES).hasSize(19);
    Set<String> complete = new HashSet<>();
    for (String module : DEPENDENCIES.keySet()) visit(module, new HashSet<>(), complete);
  }

  private void visit(String module, Set<String> active, Set<String> complete) {
    if (complete.contains(module)) return;
    assertThat(active.add(module)).as("Circular dependency at " + module).isTrue();
    for (String dependency : DEPENDENCIES.get(module)) visit(dependency, active, complete);
    active.remove(module);
    complete.add(module);
  }
}
