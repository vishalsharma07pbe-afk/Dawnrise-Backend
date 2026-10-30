package com.dawnrise.school.announcement;
import jakarta.validation.constraints.*;import java.time.OffsetDateTime;import java.util.*;
public final class ReleaseDtos {private ReleaseDtos(){}
 public record Create(@NotBlank @Size(max=120)String title,@NotBlank @Size(max=500)String summary,@NotBlank @Size(max=4000)String details,boolean allActiveOrganizations,Set<@Positive Long> organizationIds,OffsetDateTime scheduledAt,OffsetDateTime expiresAt){}
 public record View(long id,String title,String summary,String details,String status,Set<Long> organizationIds,OffsetDateTime scheduledAt,OffsetDateTime publishedAt,OffsetDateTime expiresAt,long version){}
}
