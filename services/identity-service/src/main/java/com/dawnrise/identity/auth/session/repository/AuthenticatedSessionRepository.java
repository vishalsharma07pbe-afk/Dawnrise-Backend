package com.dawnrise.identity.auth.session.repository;

import com.dawnrise.identity.auth.session.entity.AuthenticatedSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthenticatedSessionRepository
        extends JpaRepository<AuthenticatedSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AuthenticatedSession session where session.id = :id")
    Optional<AuthenticatedSession> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AuthenticatedSession session where session.userId = :userId order by session.id")
    List<AuthenticatedSession> findAllByUserIdForUpdate(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from AuthenticatedSession session where session.platformUserId = :userId order by session.id")
    List<AuthenticatedSession> findAllByPlatformUserIdForUpdate(@Param("userId") Long userId);
}
