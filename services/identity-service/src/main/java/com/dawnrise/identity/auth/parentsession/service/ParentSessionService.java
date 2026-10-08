package com.dawnrise.identity.auth.parentsession.service;
import com.dawnrise.identity.auth.exception.InvalidCredentialsException;
import com.dawnrise.identity.auth.parentsession.config.ParentSessionProperties;
import com.dawnrise.identity.auth.parentsession.dto.ParentSessionValidationResponse;
import com.dawnrise.identity.auth.parentsession.entity.ParentSession;
import com.dawnrise.identity.auth.parentsession.exception.*;
import com.dawnrise.identity.auth.parentsession.repository.ParentSessionRepository;
import com.dawnrise.identity.common.exception.ResourceNotFoundException;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.time.OffsetDateTime;
import java.util.UUID;
@Service
public class ParentSessionService {
 private final ParentSessionRepository sessions; private final UserRepository users; private final PasswordEncoder encoder; private final ParentSessionProperties properties;
 @Autowired public ParentSessionService(ObjectProvider<ParentSessionRepository> sessions,UserRepository users,PasswordEncoder encoder,ParentSessionProperties properties){this.sessions=sessions.getIfAvailable();this.users=users;this.encoder=encoder;this.properties=properties;}
 public ParentSessionService(ParentSessionRepository sessions,UserRepository users,PasswordEncoder encoder,ParentSessionProperties properties){this.sessions=sessions;this.users=users;this.encoder=encoder;this.properties=properties;}
 @Transactional public ParentSession create(UUID id,Long userId,OffsetDateTime now){return sessions.save(new ParentSession(id,userId,now));}
 @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ParentSessionLockedException.class) public ParentSessionValidationResponse validateAndTouch(UUID id,Long userId){ParentSession s=owned(id,userId);OffsetDateTime now=now();if(s.isLocked()||!s.getLastActivityAt().plus(properties.getInactivityTimeout()).isAfter(now)){s.lock(now);throw new ParentSessionLockedException("Parent session is locked");}s.touch(now);return new ParentSessionValidationResponse(true,s.getAuthenticatedAt());}
 @Transactional(readOnly=true) public void requireRecentAuthentication(UUID id,Long userId){ParentSession s=sessions.findById(id).filter(v->v.getUserId().equals(userId)).orElseThrow(()->new ParentSessionLockedException("Parent session is unavailable"));if(s.isLocked()||s.getAuthenticatedAt().plus(properties.getRecentAuthentication()).isBefore(now()))throw new RecentParentAuthenticationRequiredException("Recent parent authentication is required");}
 @Transactional(readOnly=true) public OffsetDateTime verifyPassword(UUID id,Long userId,String password){sessions.findById(id).filter(v->v.getUserId().equals(userId)).orElseThrow(()->new ParentSessionLockedException("Parent session is unavailable"));User u=users.findById(userId).orElseThrow(()->new ResourceNotFoundException("User not found"));if(u.getPasswordHash()==null||!encoder.matches(password,u.getPasswordHash()))throw new InvalidCredentialsException("Password is incorrect");return now();}
 OffsetDateTime now(){return OffsetDateTime.now();}
 private ParentSession owned(UUID id,Long userId){if(sessions==null)throw new ParentSessionLockedException("Parent session is unavailable");return sessions.findByIdForUpdate(id).filter(v->v.getUserId().equals(userId)).orElseThrow(()->new ParentSessionLockedException("Parent session is unavailable"));}
}
