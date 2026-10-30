package com.dawnrise.school.announcement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;
public final class AnnouncementDtos { private AnnouncementDtos(){}
 public enum Severity { NORMAL,IMPORTANT,URGENT } public enum AudienceType { WHOLE_SCHOOL,ROLE,WING,GRADE,SECTION,STAFF,STUDENT,ACTIVE_GUARDIAN }
 public record Audience(@NotNull AudienceType type,String key){}
 public record Create(@NotBlank @Size(max=120) String title,@NotBlank @Size(max=4000) String body,@NotNull Severity severity,@NotEmpty List<@Valid Audience> audiences,OffsetDateTime scheduledAt,OffsetDateTime expiresAt){}
 public record Version(@PositiveOrZero long version){}
 public record Schedule(@PositiveOrZero long version,@NotNull OffsetDateTime scheduledAt,OffsetDateTime expiresAt){}
 public record View(long id,String title,String body,Severity severity,String status,List<Audience> audiences,OffsetDateTime scheduledAt,OffsetDateTime publishedAt,OffsetDateTime expiresAt,long version){}
 public record Audit(String action,long actorUserId,OffsetDateTime occurredAt){}
}
