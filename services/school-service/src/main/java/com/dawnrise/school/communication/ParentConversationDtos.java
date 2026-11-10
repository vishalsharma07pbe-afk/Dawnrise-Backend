package com.dawnrise.school.communication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

public final class ParentConversationDtos {
    private ParentConversationDtos() {}
    public record Contact(long userId,String name,String detail,String category,String kind) {}
    public record Create(@Positive long studentUserId,@Positive long contactUserId,@NotBlank @Size(max=32) String category,@NotBlank @Size(max=160) String subject,@NotBlank @Size(max=4000) String message) {}
    public record CreateDirect(@Positive long recipientUserId,@NotBlank @Size(max=160) String subject,@NotBlank @Size(max=4000) String message) {}
    public record Reply(@NotBlank @Size(max=4000) String body) {}
    public record ContactInput(@Positive long userId,@NotBlank @Size(max=32) String category,@NotBlank @Size(max=24) String contactType,@NotBlank @Size(max=120) String displayLabel) {}
    public record PolicyInput(@Positive int maxOpenThreads,@Positive int maxNewThreads7Days,@Positive int headEscalationSchoolDays) {}
    public record Message(long id,long senderUserId,String body,OffsetDateTime createdAt) {}
    public record Thread(long id,long studentUserId,long contactUserId,String contactName,String category,String subject,String status,String escalationLevel,boolean headEscalationAvailable,int unreadCount,OffsetDateTime lastMessageAt,long version,List<Message> messages,String threadKind) {}
}
