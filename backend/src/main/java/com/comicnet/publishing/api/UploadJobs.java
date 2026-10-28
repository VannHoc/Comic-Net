package com.comicnet.publishing.api;

import com.comicnet.identity.api.Identity;
import com.comicnet.common.web.PageResult;

public interface UploadJobs {
  PageResult<PublishingDtos.UploadJob> list(Identity actor, int page, int size);
}
