package com.dawnrise.identity.auth.parentsession.service;
import com.dawnrise.identity.auth.parentsession.config.ParentSessionProperties;
import com.dawnrise.identity.auth.parentsession.entity.ParentSession;
import com.dawnrise.identity.auth.parentsession.exception.*;
import com.dawnrise.identity.auth.parentsession.repository.ParentSessionRepository;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.repository.UserRepository;
import org.junit.jupiter.api.*;import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.*;import java.util.*;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class ParentSessionServiceTest {
 private ParentSessionRepository sessions;private UserRepository users;private PasswordEncoder encoder;private ParentSessionProperties properties;
 private final OffsetDateTime initial=OffsetDateTime.parse("2026-10-07T10:00:00+05:30");
 @BeforeEach void setup(){sessions=mock(ParentSessionRepository.class);users=mock(UserRepository.class);encoder=mock(PasswordEncoder.class);properties=new ParentSessionProperties();properties.setInactivityTimeout(Duration.ofMinutes(15));properties.setRecentAuthentication(Duration.ofMinutes(10));}
 private ParentSessionService serviceAt(OffsetDateTime now){ParentSessionService service=spy(new ParentSessionService(sessions,users,encoder,properties));doReturn(now).when(service).now();return service;}
 @Test void locksAfterInactivity(){UUID id=UUID.randomUUID();ParentSession session=new ParentSession(id,7L,initial);when(sessions.findByIdForUpdate(id)).thenReturn(Optional.of(session));assertThrows(ParentSessionLockedException.class,()->serviceAt(initial.plusMinutes(16)).validateAndTouch(id,7L));assertTrue(session.isLocked());}
 @Test void requiresRecentAuthenticationForSensitiveAction(){UUID id=UUID.randomUUID();ParentSession session=new ParentSession(id,7L,initial);when(sessions.findById(id)).thenReturn(Optional.of(session));assertThrows(RecentParentAuthenticationRequiredException.class,()->serviceAt(initial.plusMinutes(11)).requireRecentAuthentication(id,7L));}
 @Test void activeSessionCanReauthenticateBeforeInactivityLock(){UUID id=UUID.randomUUID();ParentSession session=new ParentSession(id,7L,initial);session.touch(initial.plusMinutes(9));User user=mock(User.class);when(user.getPasswordHash()).thenReturn("hash");when(encoder.matches("secret","hash")).thenReturn(true);when(sessions.findById(id)).thenReturn(Optional.of(session));when(users.findById(7L)).thenReturn(Optional.of(user));ParentSessionService service=serviceAt(initial.plusMinutes(11));assertThrows(RecentParentAuthenticationRequiredException.class,()->service.requireRecentAuthentication(id,7L));assertEquals(initial.plusMinutes(11),service.verifyPassword(id,7L,"secret"));assertFalse(session.isLocked());}
 @Test void verifiesPasswordForLockedSessionWithoutReactivatingOldToken(){UUID id=UUID.randomUUID();ParentSession session=new ParentSession(id,7L,initial);session.lock(initial.plusMinutes(16));User user=mock(User.class);when(user.getPasswordHash()).thenReturn("hash");when(encoder.matches("secret","hash")).thenReturn(true);when(sessions.findById(id)).thenReturn(Optional.of(session));when(users.findById(7L)).thenReturn(Optional.of(user));ParentSessionService service=serviceAt(initial.plusMinutes(17));service.verifyPassword(id,7L,"secret");assertTrue(session.isLocked());when(sessions.findByIdForUpdate(id)).thenReturn(Optional.of(session));assertThrows(ParentSessionLockedException.class,()->service.validateAndTouch(id,7L));}
 @Test void rejectsSessionOwnedByDifferentIdentity(){UUID id=UUID.randomUUID();ParentSession session=new ParentSession(id,7L,initial);when(sessions.findByIdForUpdate(id)).thenReturn(Optional.of(session));assertThrows(ParentSessionLockedException.class,()->serviceAt(initial.plusMinutes(1)).validateAndTouch(id,8L));}
}
