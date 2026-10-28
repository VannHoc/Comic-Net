package com.comicnet.common.web;

import java.util.List;

public record PageResult<T>(List<T> items, PageInfo page) {
  public record PageInfo(int page, int size, long totalElements, int totalPages) {}

  public static void validate(int page, int size) {
    if (page < 0 || size < 1 || size > 100)
      throw new DomainException(400, "INVALID_PAGE", "Phân trang không hợp lệ.");
  }

  public static <T> PageResult<T> of(List<T> items, int page, int size, long count) {
    validate(page, size);
    return new PageResult<>(
        items,
        new PageInfo(
            page, size, count, (int) Math.min(Integer.MAX_VALUE, (count + size - 1) / size)));
  }

  public static long offset(int page, int size) {
    validate(page, size);
    return (long) page * size;
  }
}
