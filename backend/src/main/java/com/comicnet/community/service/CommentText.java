package com.comicnet.community.service;

import com.comicnet.common.web.DomainException;

final class CommentText {
  private CommentText() {}

  static String clean(String value, int limit) {
    if (value == null || value.isBlank()) throw invalid();
    String text = value.strip();
    if (text.length() > limit
        || text.indexOf('<') >= 0
        || text.indexOf('>') >= 0
        || text.codePoints().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\t'))
      throw invalid();
    return text;
  }

  private static DomainException invalid() {
    return new DomainException(
        422, "INVALID_COMMENT_TEXT", "Nội dung phải là văn bản thuần, đúng giới hạn ký tự.");
  }
}
