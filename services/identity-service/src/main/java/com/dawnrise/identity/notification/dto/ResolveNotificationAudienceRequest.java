package com.dawnrise.identity.notification.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
public record ResolveNotificationAudienceRequest(@NotEmpty List<@Valid Audience> audiences) {
    public record Audience(@NotNull Type type,String key) {}
    public enum Type { WHOLE_SCHOOL,ROLE,STAFF,STUDENT,ACTIVE_GUARDIAN }
}
