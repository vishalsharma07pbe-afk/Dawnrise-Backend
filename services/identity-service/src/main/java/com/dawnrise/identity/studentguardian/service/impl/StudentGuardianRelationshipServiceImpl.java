package com.dawnrise.identity.studentguardian.service.impl;

import com.dawnrise.identity.auth.security.AuthorizationContext;
import com.dawnrise.identity.common.exception.ResourceNotFoundException;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditAction;
import com.dawnrise.identity.securityaudit.enums.SecurityAuditOutcome;
import com.dawnrise.identity.securityaudit.service.SecurityAuditService;
import com.dawnrise.identity.studentguardian.config.StudentGuardianProperties;
import com.dawnrise.identity.studentguardian.dto.CreateStudentGuardianRelationshipRequest;
import com.dawnrise.identity.studentguardian.dto.EndStudentGuardianRelationshipRequest;
import com.dawnrise.identity.studentguardian.dto.StudentGuardianRelationshipResponse;
import com.dawnrise.identity.studentguardian.entity.StudentGuardianRelationship;
import com.dawnrise.identity.studentguardian.enums.StudentGuardianRelationshipStatus;
import com.dawnrise.identity.studentguardian.exception.InvalidStudentGuardianRelationshipException;
import com.dawnrise.identity.studentguardian.exception.StudentGuardianRelationshipAccessDeniedException;
import com.dawnrise.identity.studentguardian.exception.StudentGuardianRelationshipConflictException;
import com.dawnrise.identity.studentguardian.exception.StudentGuardianRelationshipNotFoundException;
import com.dawnrise.identity.studentguardian.mapper.StudentGuardianRelationshipMapper;
import com.dawnrise.identity.studentguardian.repository.StudentGuardianRelationshipRepository;
import com.dawnrise.identity.studentguardian.repository.StudentGuardianRelationshipView;
import com.dawnrise.identity.studentguardian.service.StudentGuardianRelationshipService;
import com.dawnrise.identity.user.entity.User;
import com.dawnrise.identity.user.enums.UserRole;
import com.dawnrise.identity.user.enums.UserStatus;
import com.dawnrise.identity.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class StudentGuardianRelationshipServiceImpl
        implements StudentGuardianRelationshipService {

    private static final Set<UserStatus> ELIGIBLE_STATUSES =
            Set.of(
                    UserStatus.ACTIVE,
                    UserStatus.PENDING_ACTIVATION,
                    UserStatus.LOCKED
            );

    private final StudentGuardianRelationshipRepository repository;
    private final UserRepository userRepository;
    private final StudentGuardianProperties properties;
    private final StudentGuardianRelationshipMapper mapper;
    private final SecurityAuditService auditService;

    public StudentGuardianRelationshipServiceImpl(
            StudentGuardianRelationshipRepository repository,
            UserRepository userRepository,
            StudentGuardianProperties properties,
            StudentGuardianRelationshipMapper mapper,
            SecurityAuditService auditService
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.mapper = mapper;
        this.auditService = auditService;
    }

    @Override
    public StudentGuardianRelationshipResponse create(
            Long organizationId,
            Long studentUserId,
            AuthorizationContext authorizationContext,
            CreateStudentGuardianRelationshipRequest request
    ) {
        User student = lockStudent(organizationId, studentUserId);
        User guardian = findUser(
                organizationId,
                request.getGuardianUserId(),
                "Guardian user not found"
        );

        validateStudent(student);
        validateGuardian(guardian);

        if (student.getId().equals(guardian.getId())) {
            throw new InvalidStudentGuardianRelationshipException(
                    "Student and guardian cannot be the same user"
            );
        }

        long activeCount =
                repository.countByOrganizationIdAndStudentUserIdAndStatus(
                        organizationId,
                        studentUserId,
                        StudentGuardianRelationshipStatus.ACTIVE
                );

        if (activeCount >= properties
                .getMaxActiveGuardiansPerStudent()) {
            throw new StudentGuardianRelationshipConflictException(
                    "Student already has the maximum active guardians"
            );
        }

        if (repository
                .existsByOrganizationIdAndStudentUserIdAndGuardianUserIdAndStatus(
                        organizationId,
                        studentUserId,
                        guardian.getId(),
                        StudentGuardianRelationshipStatus.ACTIVE
                )) {
            throw new StudentGuardianRelationshipConflictException(
                    "Student and guardian already have an active relationship"
            );
        }

        boolean requestedPrimary = Boolean.TRUE.equals(
                request.getPrimaryGuardian()
        );
        boolean primary = activeCount == 0 || requestedPrimary;

        if (primary && activeCount > 0) {
            demoteActivePrimaries(organizationId, studentUserId);
            repository.flush();
        }

        StudentGuardianRelationship relationship =
                new StudentGuardianRelationship(
                        organizationId,
                        studentUserId,
                        guardian.getId(),
                        request.getRelationshipType(),
                        primary,
                        authorizationContext.getUserId(),
                        OffsetDateTime.now()
                );

        StudentGuardianRelationship saved = saveAndFlushOrConflict(
                relationship
        );

        // Audit remains REQUIRES_NEW; flush first so known database
        // constraints are exercised before recording a success event.
        auditService.record(
                organizationId,
                authorizationContext.getUserId(),
                SecurityAuditAction.STUDENT_GUARDIAN_RELATIONSHIP_CREATE,
                SecurityAuditOutcome.SUCCESS,
                "STUDENT_GUARDIAN_RELATIONSHIP",
                saved.getId(),
                Map.of(
                        "studentUserId", studentUserId,
                        "guardianUserId", guardian.getId(),
                        "primaryGuardian", primary
                )
        );

        return findViewByStudentAndId(
                organizationId,
                studentUserId,
                saved.getId()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentGuardianRelationshipResponse> getActiveForStudent(
            Long organizationId,
            Long studentUserId
    ) {
        requireExistingStudent(organizationId, studentUserId);
        return repository.findActiveViewsByStudent(
                        organizationId,
                        studentUserId,
                        StudentGuardianRelationshipStatus.ACTIVE
                )
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentGuardianRelationshipResponse> getHistoryForStudent(
            Long organizationId,
            Long studentUserId
    ) {
        requireExistingStudent(organizationId, studentUserId);
        return repository.findHistoryViewsByStudent(
                        organizationId,
                        studentUserId
                )
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Override
    public StudentGuardianRelationshipResponse setPrimary(
            Long organizationId,
            Long studentUserId,
            Long relationshipId,
            AuthorizationContext authorizationContext
    ) {
        lockStudent(organizationId, studentUserId);
        StudentGuardianRelationship target =
                findActiveRelationship(
                        organizationId,
                        studentUserId,
                        relationshipId
                );

        if (target.isPrimaryGuardian()) {
            return findViewByStudentAndId(
                    organizationId,
                    studentUserId,
                    target.getId()
            );
        }

        demoteActivePrimaries(organizationId, studentUserId);
        repository.flush();
        target.makePrimary();
        repository.flush();

        // Audit remains REQUIRES_NEW; flush first so known database
        // constraints are exercised before recording a success event.
        auditService.record(
                organizationId,
                authorizationContext.getUserId(),
                SecurityAuditAction.STUDENT_GUARDIAN_RELATIONSHIP_SET_PRIMARY,
                SecurityAuditOutcome.SUCCESS,
                "STUDENT_GUARDIAN_RELATIONSHIP",
                target.getId(),
                Map.of("studentUserId", studentUserId)
        );

        return findViewByStudentAndId(
                organizationId,
                studentUserId,
                target.getId()
        );
    }

    @Override
    public StudentGuardianRelationshipResponse end(
            Long organizationId,
            Long studentUserId,
            Long relationshipId,
            AuthorizationContext authorizationContext,
            EndStudentGuardianRelationshipRequest request
    ) {
        lockStudent(organizationId, studentUserId);
        StudentGuardianRelationship target =
                repository.findByOrganizationIdAndStudentUserIdAndId(
                                organizationId,
                                studentUserId,
                                relationshipId
                        )
                        .orElseThrow(() ->
                                new StudentGuardianRelationshipNotFoundException(
                                        "Relationship not found"
                                ));

        if (target.getStatus()
                == StudentGuardianRelationshipStatus.ENDED) {
            throw new StudentGuardianRelationshipConflictException(
                    "Relationship is already ended"
            );
        }

        List<StudentGuardianRelationship> activeRelationships =
                repository
                        .findAllByOrganizationIdAndStudentUserIdAndStatusOrderByPrimaryGuardianDescStartedAtAscIdAsc(
                                organizationId,
                                studentUserId,
                                StudentGuardianRelationshipStatus.ACTIVE
                        );

        boolean otherActiveExists = activeRelationships
                .stream()
                .anyMatch(relationship ->
                        !relationship.getId().equals(target.getId())
                );

        if (target.isPrimaryGuardian() && otherActiveExists) {
            Long replacementId =
                    request.getReplacementPrimaryRelationshipId();

            if (replacementId == null) {
                throw new StudentGuardianRelationshipConflictException(
                        "Ending a primary relationship requires a replacement primary"
                );
            }

            StudentGuardianRelationship replacement =
                    activeRelationships.stream()
                            .filter(relationship ->
                                    relationship.getId().equals(
                                            replacementId
                                    )
                            )
                            .findFirst()
                            .orElseThrow(() ->
                                    new StudentGuardianRelationshipConflictException(
                                            "Replacement primary relationship is not active for this student"
                                    ));

            if (replacement.getId().equals(target.getId())) {
                throw new StudentGuardianRelationshipConflictException(
                        "Replacement primary must be another active relationship"
                );
            }

            target.end(
                    authorizationContext.getUserId(),
                    request.getEndReason(),
                    OffsetDateTime.now()
            );
            repository.flush();

            replacement.makePrimary();
            repository.flush();
        } else {
            target.end(
                    authorizationContext.getUserId(),
                    request.getEndReason(),
                    OffsetDateTime.now()
            );
            repository.flush();
        }

        // Audit remains REQUIRES_NEW; flush first so known database
        // constraints are exercised before recording a success event.
        auditService.record(
                organizationId,
                authorizationContext.getUserId(),
                SecurityAuditAction.STUDENT_GUARDIAN_RELATIONSHIP_END,
                SecurityAuditOutcome.SUCCESS,
                "STUDENT_GUARDIAN_RELATIONSHIP",
                target.getId(),
                Map.of("studentUserId", studentUserId)
        );

        return findViewByStudentAndId(
                organizationId,
                studentUserId,
                target.getId()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentGuardianRelationshipResponse>
    getLinkedStudentsForParent(
            Long organizationId,
            AuthorizationContext authorizationContext
    ) {
        User parent = findUser(
                organizationId,
                authorizationContext.getUserId(),
                "Parent user not found"
        );

        if (!parent.getRoles().contains(UserRole.PARENT)) {
            throw new StudentGuardianRelationshipAccessDeniedException(
                    "Only a parent can view linked students"
            );
        }

        if (parent.getStatus() != UserStatus.ACTIVE) {
            throw new StudentGuardianRelationshipAccessDeniedException(
                    "Parent account is not active"
            );
        }

        return repository.findActiveViewsByGuardian(
                        organizationId,
                        parent.getId(),
                        StudentGuardianRelationshipStatus.ACTIVE
                )
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    private void demoteActivePrimaries(
            Long organizationId,
            Long studentUserId
    ) {
        repository
                .findAllByOrganizationIdAndStudentUserIdAndStatusOrderByPrimaryGuardianDescStartedAtAscIdAsc(
                        organizationId,
                        studentUserId,
                        StudentGuardianRelationshipStatus.ACTIVE
                )
                .stream()
                .filter(StudentGuardianRelationship::isPrimaryGuardian)
                .forEach(StudentGuardianRelationship::demotePrimary);
    }

    private StudentGuardianRelationshipResponse findViewByStudentAndId(
            Long organizationId,
            Long studentUserId,
            Long relationshipId
    ) {
        return repository.findHistoryViewsByStudent(
                        organizationId,
                        studentUserId
                )
                .stream()
                .filter(view ->
                        view.relationship().getId().equals(relationshipId)
                )
                .findFirst()
                .map(mapper::toResponse)
                .orElseThrow(() ->
                        new StudentGuardianRelationshipNotFoundException(
                                "Relationship not found"
                        ));
    }

    private StudentGuardianRelationship findActiveRelationship(
            Long organizationId,
            Long studentUserId,
            Long relationshipId
    ) {
        StudentGuardianRelationship relationship =
                repository.findByOrganizationIdAndStudentUserIdAndId(
                                organizationId,
                                studentUserId,
                                relationshipId
                        )
                        .orElseThrow(() ->
                                new StudentGuardianRelationshipNotFoundException(
                                        "Relationship not found"
                                ));

        if (relationship.getStatus()
                != StudentGuardianRelationshipStatus.ACTIVE) {
            throw new StudentGuardianRelationshipConflictException(
                    "Relationship is not active"
            );
        }

        return relationship;
    }

    private User lockStudent(Long organizationId, Long studentUserId) {
        User student = userRepository
                .findByOrganizationIdAndIdForUpdate(
                        organizationId,
                        studentUserId
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Student user not found"
                        ));

        validateStudent(student);
        return student;
    }

    private void requireExistingStudent(
            Long organizationId,
            Long studentUserId
    ) {
        User student = findUser(
                organizationId,
                studentUserId,
                "Student user not found"
        );
        validateStudent(student);
    }

    private User findUser(
            Long organizationId,
            Long userId,
            String notFoundMessage
    ) {
        return userRepository.findByOrganizationIdAndId(
                        organizationId,
                        userId
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(notFoundMessage)
                );
    }

    private void validateStudent(User student) {
        validateEligibility(
                student,
                UserRole.STUDENT,
                "Student user must have STUDENT role"
        );
    }

    private void validateGuardian(User guardian) {
        validateEligibility(
                guardian,
                UserRole.PARENT,
                "Guardian user must have PARENT role"
        );
    }

    private void validateEligibility(
            User user,
            UserRole requiredRole,
            String roleMessage
    ) {
        if (!user.getRoles().contains(requiredRole)) {
            throw new InvalidStudentGuardianRelationshipException(
                    roleMessage
            );
        }

        if (!ELIGIBLE_STATUSES.contains(user.getStatus())) {
            throw new InvalidStudentGuardianRelationshipException(
                    "User status is not eligible for guardian relationships"
            );
        }
    }

    private StudentGuardianRelationship saveAndFlushOrConflict(
            StudentGuardianRelationship relationship
    ) {
        try {
            StudentGuardianRelationship saved = repository.save(relationship);
            repository.flush();
            return saved;
        } catch (DataIntegrityViolationException exception) {
            throw new StudentGuardianRelationshipConflictException(
                    "Relationship conflicts with existing data"
            );
        }
    }
}
