package com.dawnrise.identity.studentguardian.service;

import com.dawnrise.identity.auth.security.AuthorizationContext;
import com.dawnrise.identity.studentguardian.dto.CreateStudentGuardianRelationshipRequest;
import com.dawnrise.identity.studentguardian.dto.EndStudentGuardianRelationshipRequest;
import com.dawnrise.identity.studentguardian.dto.StudentGuardianRelationshipResponse;

import java.util.List;

public interface StudentGuardianRelationshipService {

    StudentGuardianRelationshipResponse create(
            Long organizationId,
            Long studentUserId,
            AuthorizationContext authorizationContext,
            CreateStudentGuardianRelationshipRequest request
    );

    List<StudentGuardianRelationshipResponse> getActiveForStudent(
            Long organizationId,
            Long studentUserId
    );

    List<StudentGuardianRelationshipResponse> getHistoryForStudent(
            Long organizationId,
            Long studentUserId
    );

    StudentGuardianRelationshipResponse setPrimary(
            Long organizationId,
            Long studentUserId,
            Long relationshipId,
            AuthorizationContext authorizationContext
    );

    StudentGuardianRelationshipResponse end(
            Long organizationId,
            Long studentUserId,
            Long relationshipId,
            AuthorizationContext authorizationContext,
            EndStudentGuardianRelationshipRequest request
    );

    List<StudentGuardianRelationshipResponse> getLinkedStudentsForParent(
            Long organizationId,
            AuthorizationContext authorizationContext
    );
}
