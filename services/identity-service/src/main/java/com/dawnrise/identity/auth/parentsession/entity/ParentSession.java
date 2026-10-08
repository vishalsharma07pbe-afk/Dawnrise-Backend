package com.dawnrise.identity.auth.parentsession.entity;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity
@Table(name = "parent_sessions")
public class ParentSession {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "authenticated_at", nullable = false) private OffsetDateTime authenticatedAt;
    @Column(name = "last_activity_at", nullable = false) private OffsetDateTime lastActivityAt;
    @Column(name = "locked_at") private OffsetDateTime lockedAt;
    @Version private Long version;
    protected ParentSession() {}
    public ParentSession(UUID id, Long userId, OffsetDateTime now) { this.id=id; this.userId=userId; authenticatedAt=now; lastActivityAt=now; }
    public UUID getId(){return id;} public Long getUserId(){return userId;}
    public OffsetDateTime getAuthenticatedAt(){return authenticatedAt;} public OffsetDateTime getLastActivityAt(){return lastActivityAt;}
    public boolean isLocked(){return lockedAt!=null;} public void touch(OffsetDateTime now){lastActivityAt=now;}
    public void lock(OffsetDateTime now){if(lockedAt==null)lockedAt=now;}
}
