package com.comicnet.identity.service;

import com.comicnet.identity.api.AccountNotifier;
import com.comicnet.common.web.DomainException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AccountMail implements AccountNotifier {
  private final JdbcTemplate jdbc;
  private final JavaMailSender sender;
  private final Environment env;
  private final TransactionTemplate transactions;
  private volatile TextEncryptor encryptor;

  public AccountMail(
      JdbcTemplate jdbc, JavaMailSender sender, Environment env, TransactionTemplate transactions) {
    this.jdbc = jdbc;
    this.sender = sender;
    this.env = env;
    this.transactions = transactions;
  }

  private synchronized TextEncryptor cipher() {
    if (encryptor != null) return encryptor;
    try {
      String key =
          Files.readString(Path.of(env.getRequiredProperty("ACCOUNT_MAIL_KEY_PATH"))).strip();
      if (key.length() < 64) throw new IllegalArgumentException();
      encryptor = Encryptors.delux(key, "636f6d69636e6574");
      return encryptor;
    } catch (Exception e) {
      throw new DomainException(
          503, "MAIL_NOT_CONFIGURED", "Backend chưa cấu hình khóa bảo vệ email.");
    }
  }

  public void queue(String recipient, String purpose, String token) {
    String title =
        purpose.equals("VERIFY_EMAIL")
            ? "Comic Net — Xác minh email"
            : "Comic Net — Đặt lại mật khẩu";
    String body =
        "Mã thao tác Comic Net của bạn:\n\n"
            + token
            + "\n\nNhập mã này trong ứng dụng Comic Net. Không chia sẻ mã với người khác.\n"
            + (purpose.equals("VERIFY_EMAIL")
                ? "Mã hết hạn sau 24 giờ."
                : "Mã hết hạn sau 30 phút.");
    jdbc.update(
        "INSERT dbo.AccountMailJob(Recipient,Subject,EncryptedBody) VALUES(?,?,?)",
        recipient,
        title,
        cipher().encrypt(body));
  }

  /** Notification mail shares the same durable, encrypted, retried delivery queue. */
  @Override
  public void queueMail(String recipient, String subject, String body) {
    int stored =
        jdbc.update(
            "INSERT dbo.AccountMailJob(Recipient,Subject,EncryptedBody) VALUES(?,?,?)",
            recipient,
            subject.length() > 200 ? subject.substring(0, 200) : subject,
            cipher().encrypt(body));
    if (stored != 1) throw new IllegalStateException("Notification mail was not queued.");
  }

  @Scheduled(fixedDelayString = "${comicnet.mail.poll-delay:2000}")
  public void deliver() {
    if (!env.getProperty("comicnet.mail.worker-enabled", Boolean.class, true)) return;
    deliverNext();
  }

  public boolean deliverNext() {
    return deliver(null);
  }

  public boolean deliverJob(long id) {
    return deliver(id);
  }

  private boolean deliver(Long selected) {
    var job =
        transactions.execute(
            status -> {
              Object[] arguments = selected == null ? new Object[] {} : new Object[] {selected};
              String scope = selected == null ? "" : " AND Id=?";
              jdbc.update(
                  "UPDATE dbo.AccountMailJob SET"
                      + " State='FAILED',LeaseOwner=NULL,LeaseUntil=NULL,ErrorCode='SMTP_DELIVERY_EXHAUSTED'"
                      + " WHERE State='RUNNING' AND LeaseUntil<SYSUTCDATETIME() AND Attempts>=5"
                      + scope,
                  arguments);
              var rows =
                  jdbc.queryForList(
                      "SELECT TOP (1) Id,Recipient,Subject,EncryptedBody,Attempts FROM"
                          + " dbo.AccountMailJob WITH (UPDLOCK,READPAST,READCOMMITTEDLOCK) WHERE"
                          + " ((State IN ('QUEUED','RETRY_WAIT') AND"
                          + " NextAttemptAt<=SYSUTCDATETIME()) OR (State='RUNNING' AND"
                          + " LeaseUntil<SYSUTCDATETIME())) AND Attempts<5"
                          + scope
                          + " ORDER BY Id",
                      arguments);
              if (rows.isEmpty()) return null;
              var row = rows.getFirst();
              String lease = java.util.UUID.randomUUID().toString();
              row.put("LeaseOwner", lease);
              jdbc.update(
                  "UPDATE dbo.AccountMailJob SET"
                      + " State='RUNNING',Attempts=Attempts+1,LeaseOwner=?,LeaseUntil=DATEADD(second,30,SYSUTCDATETIME())"
                      + " WHERE Id=?",
                  lease,
                  row.get("Id"));
              return row;
            });
    if (job == null) return false;
    try {
      var message = new SimpleMailMessage();
      message.setFrom(env.getProperty("MAIL_FROM", "noreply@comicnet.local"));
      message.setTo((String) job.get("Recipient"));
      message.setSubject((String) job.get("Subject"));
      message.setText(cipher().decrypt((String) job.get("EncryptedBody")));
      sender.send(message);
      jdbc.update(
          "UPDATE dbo.AccountMailJob SET"
              + " State='SUCCEEDED',SentAt=SYSUTCDATETIME(),LeaseOwner=NULL,LeaseUntil=NULL,EncryptedBody='',ErrorCode=NULL"
              + " WHERE Id=? AND LeaseOwner=? AND State='RUNNING'",
          job.get("Id"),
          job.get("LeaseOwner"));
    } catch (Exception error) {
      int attempts = ((Number) job.get("Attempts")).intValue() + 1;
      jdbc.update(
          "UPDATE dbo.AccountMailJob SET"
              + " State=?,NextAttemptAt=?,LeaseOwner=NULL,LeaseUntil=NULL,ErrorCode='SMTP_DELIVERY_FAILED'"
              + " WHERE Id=? AND LeaseOwner=? AND State='RUNNING'",
          attempts >= 5 ? "FAILED" : "RETRY_WAIT",
          java.time.LocalDateTime.ofInstant(
              Instant.now().plusSeconds(Math.min(300, 10L * attempts)), java.time.ZoneOffset.UTC),
          job.get("Id"),
          job.get("LeaseOwner"));
    }
    return true;
  }
}
