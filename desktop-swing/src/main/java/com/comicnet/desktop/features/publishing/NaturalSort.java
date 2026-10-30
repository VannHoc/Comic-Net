package com.comicnet.desktop.features.publishing;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NaturalSort {
  private static final Pattern CHUNK = Pattern.compile("(\\d+)|(\\D+)");

  public static final Comparator<String> COMPARATOR = (s1, s2) -> {
    if (s1 == null && s2 == null) return 0;
    if (s1 == null) return -1;
    if (s2 == null) return 1;

    Matcher m1 = CHUNK.matcher(s1);
    Matcher m2 = CHUNK.matcher(s2);

    while (m1.find()) {
      if (!m2.find()) return 1;

      String chunk1 = m1.group();
      String chunk2 = m2.group();

      int result;
      if (Character.isDigit(chunk1.charAt(0)) && Character.isDigit(chunk2.charAt(0))) {
        // Compare numerically
        try {
          long n1 = Long.parseLong(chunk1);
          long n2 = Long.parseLong(chunk2);
          result = Long.compare(n1, n2);
        } catch (NumberFormatException e) {
          result = chunk1.compareTo(chunk2);
        }
      } else {
        result = chunk1.compareToIgnoreCase(chunk2);
      }
      if (result != 0) return result;
    }
    return m2.find() ? -1 : 0;
  };

  private NaturalSort() {}
}
