package com.dawnrise.school.communication;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;

public final class CommunicationDtos {
    private CommunicationDtos() {}
    public record Target(String type, String key, String label, String detail) {}
    public record Page(List<Target> items, int page, int size, long total) {}
    public record Selection(@NotNull Type type, String key) {}
    public record PreviewRequest(@NotEmpty @Size(max = 50) List<@Valid Selection> selections) {}
    public record Preview(List<Selection> selections, int recipientCount, String membershipResolution, String message) {}
    public enum Type { INDIVIDUAL, STAFF, ROLE, STUDENT, ACTIVE_GUARDIAN, SECTION, GRADE, WING, SECTION_FAMILIES, GRADE_FAMILIES, WING_FAMILIES, EVERYONE }
    public record CreateNotice(@NotNull @Size(min=1,max=120) String subject,@NotNull @Size(min=1,max=4000) String body,@NotEmpty @Size(max=50) List<@Valid Selection> selections) {}
    public record VersionMutation(@PositiveOrZero long version) {}
    public record ScheduleMutation(@PositiveOrZero long version,@NotNull LocalDateTime scheduledLocal) {}
    public record Notice(long id,String subject,String body,String status,String deliveryStatus,List<Selection> selections,int previewRecipientCount,Integer acceptedRecipientCount,OffsetDateTime scheduledAt,OffsetDateTime publishedAt,OffsetDateTime createdAt,long version,String lastError) {}
    public record NoticePage(List<Notice> items,int page,int size,long total) {}
}
