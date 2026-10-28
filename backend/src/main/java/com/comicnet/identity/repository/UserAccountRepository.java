package com.comicnet.identity.repository;

import com.comicnet.identity.entity.UserAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
  Optional<UserAccount> findByNormalizedEmail(String email);
  Optional<UserAccount> findByNormalizedUsername(String username);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from UserAccount u where u.id=:id")
  Optional<UserAccount> lockById(@Param("id") Long id);
}
