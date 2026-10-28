package com.comicnet.catalog.repository;

import com.comicnet.catalog.entity.ComicEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ComicRepository extends JpaRepository<ComicEntity, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from ComicEntity c where c.id=:id and c.deletedAt is null")
  Optional<ComicEntity> lockById(@Param("id") long id);
}
