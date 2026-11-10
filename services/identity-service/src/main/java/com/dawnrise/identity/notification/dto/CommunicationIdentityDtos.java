package com.dawnrise.identity.notification.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;

public final class CommunicationIdentityDtos {
    private CommunicationIdentityDtos() {}

    public record SearchRequest(String query, int page, int size, Set<Long> allowedUserIds, Set<Long> excludedUserIds) {}
    public record User(long id, String displayName, List<String> roles) {}
    public record Group(String type, String key, String label, String detail) {}
    public record Groups(List<Group> items) {}
    public record Page(List<User> items, int page, int size, long total) {}
    public record ResolveUsersRequest(@NotEmpty @Size(max = 500) Set<Long> userIds) {}
    public record ResolveGuardiansRequest(@NotEmpty @Size(max = 5000) Set<Long> studentUserIds) {}
    public record Ids(Set<Long> userIds) {}
    public record Authority(boolean active, Set<String> permissions) {}
}
