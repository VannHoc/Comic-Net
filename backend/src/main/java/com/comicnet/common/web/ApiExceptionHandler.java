package com.comicnet.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
  private final ProblemWriter problems;

  public ApiExceptionHandler(ProblemWriter problems) {
    this.problems = problems;
  }

  @ExceptionHandler(DomainException.class)
  ResponseEntity<ApiProblem> domain(DomainException error, HttpServletRequest request) {
    var response = response(request, error.status(), error.code(), error.getMessage());
    return ResponseEntity.status(response.getStatusCode())
        .headers(response.getHeaders())
        .body(response.getBody().withCurrent(error.current()));
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class,
    org.springframework.web.multipart.support.MissingServletRequestPartException.class,
    org.springframework.web.method.annotation.HandlerMethodValidationException.class,
    jakarta.validation.ConstraintViolationException.class
  })
  ResponseEntity<ApiProblem> invalid(HttpServletRequest request) {
    return response(
        request,
        400,
        "VALIDATION_FAILED",
        "Dữ liệu không hợp lệ. Vui lòng kiểm tra các trường nhập.");
  }

  @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
  ResponseEntity<ApiProblem> uploadTooLarge(HttpServletRequest request) {
    return response(request, 413, "UPLOAD_SIZE_LIMIT", "Tệp tải lên vượt giới hạn dung lượng.");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiProblem> conflict(HttpServletRequest request) {
    return response(request, 409, "DATA_CONFLICT", "Dữ liệu đã tồn tại hoặc đang được tham chiếu.");
  }

  @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
  ResponseEntity<ApiProblem> notFound(HttpServletRequest request) {
    return response(request, 404, "NOT_FOUND", "Resource not found.");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiProblem> unexpected(Exception exception, HttpServletRequest request) {
    // Deliberately omit exception messages, headers and query strings (may contain secrets).
    LOG.error("Unhandled request failure type={}", exception.getClass().getSimpleName());
    return response(request, 500, "INTERNAL_ERROR", "The request could not be completed.");
  }

  private ResponseEntity<ApiProblem> response(
      HttpServletRequest request, int status, String code, String detail) {
    return ResponseEntity.status(status)
        .header("Cache-Control", "no-store")
        .header("Content-Type", "application/problem+json")
        .body(problems.body(request, status, code, detail));
  }
}
