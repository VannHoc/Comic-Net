package com.comicnet.common.web;

public final class Ids {
  private Ids() {}

  public static long parse(String value) {
    try {
      long id = Long.parseLong(value);
      if (id <= 0 || !value.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException();
      return id;
    } catch (Exception e) {
      throw new DomainException(400, "INVALID_ID", "Mã tài nguyên không hợp lệ.");
    }
  }
}
