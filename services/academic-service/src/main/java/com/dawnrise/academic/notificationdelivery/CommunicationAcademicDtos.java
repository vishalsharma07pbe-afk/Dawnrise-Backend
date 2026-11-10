package com.dawnrise.academic.notificationdelivery;

import java.util.List;
import java.util.Set;

public final class CommunicationAcademicDtos {
    private CommunicationAcademicDtos() {}
    public record Group(String type, long id, String label, String detail) {}
    public record Groups(List<Group> items) {}
    public record ResolveRequest(long actorUserId, boolean schoolWide, String type, long id) {}
    public record ScopeRequest(long actorUserId, boolean schoolWide) {}
    public record Ids(Set<Long> studentUserIds) {}
    public record ParentContact(long userId,String kind) {}
    public record ParentContacts(List<ParentContact> items) {}
}
