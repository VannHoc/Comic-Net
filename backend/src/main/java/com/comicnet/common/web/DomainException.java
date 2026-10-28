package com.comicnet.common.web;

public class DomainException extends RuntimeException {
  private final int status;
  private final String code;
  private Object current;

  public DomainException(int status, String code, String detail) {
    super(detail);
    this.status = status;
    this.code = code;
  }

  public int status() {
    return status;
  }

  public String code() {
    return code;
  }

  public Object current() {
    return current;
  }

  public DomainException withCurrent(Object value) {
    current = value;
    return this;
  }

  public static DomainException forbidden() {
    return new DomainException(403, "ACCESS_DENIED", "Bạn không có quyền thực hiện thao tác này.");
  }

  public static DomainException missing() {
    return new DomainException(404, "NOT_FOUND", "Không tìm thấy tài nguyên.");
  }

  public static DomainException unauthorized() {
    return new DomainException(
        401, "AUTH_FAILED", "Thông tin xác thực không hợp lệ hoặc phiên đã kết thúc.");
  }
}
