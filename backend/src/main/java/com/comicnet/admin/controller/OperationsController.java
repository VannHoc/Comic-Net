package com.comicnet.admin.controller;

import com.comicnet.identity.api.*;
import com.comicnet.common.web.PageResult;
import com.comicnet.observability.api.AuditQueries;
import com.comicnet.publishing.api.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class OperationsController {
  private final IdentityService identities;
  private final AuditQueries audit;
  private final UploadJobs jobs;

  public OperationsController(IdentityService identities, AuditQueries audit, UploadJobs jobs) {
    this.identities = identities;
    this.audit = audit;
    this.jobs = jobs;
  }

  @GetMapping("/audit-logs")
  public PageResult<AuditQueries.Entry> audit(
      @AuthenticationPrincipal Identity actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String targetType,
      @RequestParam(required = false) String targetId) {
    identities.requireAdministrator(actor);
    return audit.list(page, size, targetType, targetId);
  }

  @GetMapping("/upload-jobs")
  public PageResult<PublishingDtos.UploadJob> jobs(
      @AuthenticationPrincipal Identity actor,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return jobs.list(actor, page, size);
  }
}
