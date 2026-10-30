package com.dawnrise.identity.notification.securityevent;

import com.dawnrise.identity.auth.exception.AccountLockedException;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.repository.UserRepository;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Aspect
@Component
public class AccountLockoutNotificationAspect {
    private final LockoutPersistence persistence;
    public AccountLockoutNotificationAspect(LockoutPersistence persistence) { this.persistence=persistence; }

    @AfterThrowing(pointcut="execution(* com.dawnrise.identity.auth.lockout.LoginLockoutService.recordFailedLogin(com.dawnrise.identity.user.entity.User,..)) && args(user,..)", throwing="failure")
    public void locked(User user,AccountLockedException failure) { persistence.persist(user); }

    @Component
    static class LockoutPersistence {
        private final UserRepository users; private final ApplicationEventPublisher events;
        LockoutPersistence(UserRepository users,ApplicationEventPublisher events){this.users=users;this.events=events;}
        @Transactional(propagation=Propagation.REQUIRES_NEW)
        public void persist(User user) {
            users.saveAndFlush(user);
            events.publishEvent(new IdentitySecurityNotificationEvent(user.getOrganizationId(),user.getId(),
                    IdentitySecurityNotificationEvent.Kind.ACCOUNT_LOCKED,
                    "account-locked:"+user.getId()+":"+user.getLockedUntil()));
        }
    }
}
