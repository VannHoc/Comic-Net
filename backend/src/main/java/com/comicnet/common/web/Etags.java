package com.comicnet.common.web;

import java.util.Base64;

public final class Etags {
  private Etags() {}

  public static String of(byte[] version) {
    return "\"" + Base64.getUrlEncoder().withoutPadding().encodeToString(version) + "\"";
  }

  public static void require(String supplied, byte[] current) {
    if (supplied == null || supplied.isBlank())
      throw new DomainException(
          428, "IF_MATCH_REQUIRED", "Cần tải phiên bản dữ liệu trước khi sửa.");
    if (!of(current).equals(supplied))
      throw new DomainException(412, "STALE_VERSION", "Dữ liệu đã thay đổi. Hãy tải lại.");
  }
}
