package com.dawnrise.identity.auth.parentsession.repository;
import com.dawnrise.identity.auth.parentsession.entity.ParentSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface ParentSessionRepository extends JpaRepository<ParentSession,UUID>{
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from ParentSession s where s.id=:id")
 Optional<ParentSession> findByIdForUpdate(@Param("id") UUID id);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from ParentSession s where s.userId=:userId order by s.id")
 List<ParentSession> findAllByUserIdForUpdate(@Param("userId") Long userId);
}
