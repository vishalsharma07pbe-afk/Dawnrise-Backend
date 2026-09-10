package com.dawnrise.identity.studentguardian.repository;

import com.dawnrise.identity.studentguardian.entity.StudentGuardianRelationship;
import com.dawnrise.identity.user.entity.User;

public record StudentGuardianRelationshipView(
        StudentGuardianRelationship relationship,
        User student,
        User guardian
) {
}
