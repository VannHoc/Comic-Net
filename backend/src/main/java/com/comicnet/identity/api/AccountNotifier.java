package com.comicnet.identity.api;

/** Queues a protected transactional email for an account through the durable mail job table. */
public interface AccountNotifier {
  void queueMail(String recipient, String subject, String body);
}
