package com.dawnrise.identity.studentguardian.dto;

import java.util.List;

public record ActiveGuardianRecipientsResponse(List<Long> guardianUserIds) {
}
