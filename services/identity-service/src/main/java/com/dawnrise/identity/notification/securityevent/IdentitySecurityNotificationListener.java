package com.dawnrise.identity.notification.securityevent;

import com.dawnrise.identity.notification.dto.IngestNotificationRequest;
import com.dawnrise.identity.notification.dto.NotificationAction;
import com.dawnrise.identity.notification.model.NotificationActionType;
import com.dawnrise.identity.notification.model.NotificationCategory;
import com.dawnrise.identity.notification.model.NotificationEventType;
import com.dawnrise.identity.notification.model.NotificationImportance;
import com.dawnrise.identity.notification.service.NotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.OffsetDateTime;
import java.util.Set;

@Component
public class IdentitySecurityNotificationListener {
    private final NotificationService notifications;
    public IdentitySecurityNotificationListener(NotificationService notifications) { this.notifications=notifications; }

    @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
    public void on(IdentitySecurityNotificationEvent event) {
        String heading=switch(event.kind()) {
            case PASSWORD_CHANGED -> "Your Dawnrise password was changed";
            case PASSWORD_RESET_COMPLETED -> "Your Dawnrise password was reset";
            case ACCOUNT_ACTIVATED -> "Welcome to Dawnrise";
            case ACCOUNT_LOCKED -> "Your Dawnrise account was locked";
            case ACCOUNT_STATUS_CHANGED -> "Your Dawnrise account status changed";
        };
        String context=switch(event.kind()) {
            case ACCOUNT_ACTIVATED -> "Your account is active. You can now sign in.";
            case ACCOUNT_STATUS_CHANGED -> "Contact your school if this change is unexpected.";
            default -> "Review account security if you did not make this change.";
        };
        NotificationEventType eventType=switch(event.kind()) {
            case ACCOUNT_LOCKED -> NotificationEventType.ACCOUNT_LOCKED;
            case ACCOUNT_STATUS_CHANGED -> NotificationEventType.ACCOUNT_STATUS_CHANGED;
            default -> NotificationEventType.SECURITY_ALERT;
        };
        notifications.ingest("identity-service",new IngestNotificationRequest(event.organizationId(),
                event.sourceEventId(), eventType, NotificationCategory.SECURITY,
                NotificationImportance.IMPORTANT,heading,context,
                new NotificationAction(NotificationActionType.ACCOUNT_SECURITY,null,null,null),null,
                OffsetDateTime.now(), Set.of(event.userId())));
    }
}
