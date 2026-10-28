package com.comicnet.common.web;

import java.util.List;

public record ApiProblem(
    String type,
    String title,
    int status,
    String code,
    String detail,
    String instance,
    String requestId,
    List<FieldError> errors,
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        Object current) {
  public ApiProblem(
      String type,
      String title,
      int status,
      String code,
      String detail,
      String instance,
      String requestId,
      List<FieldError> errors) {
    this(type, title, status, code, detail, instance, requestId, errors, null);
  }

  public ApiProblem withCurrent(Object value) {
    return new ApiProblem(type, title, status, code, detail, instance, requestId, errors, value);
  }

  public record FieldError(String field, String message) {}
}
