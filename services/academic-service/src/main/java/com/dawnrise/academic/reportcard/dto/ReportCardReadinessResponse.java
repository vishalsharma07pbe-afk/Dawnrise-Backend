package com.dawnrise.academic.reportcard.dto;

import java.util.List;

public record ReportCardReadinessResponse(long publicationId, boolean ready, Long activePolicyId,
                                          String activePolicyName, int studentCount, int existingCardCount,
                                          List<String> blockers) {
}
