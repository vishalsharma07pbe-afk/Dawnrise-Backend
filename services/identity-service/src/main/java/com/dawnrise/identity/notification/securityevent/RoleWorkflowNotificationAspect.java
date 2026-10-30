package com.dawnrise.identity.notification.securityevent;

import com.dawnrise.identity.notification.dto.IngestNotificationRequest;
import com.dawnrise.identity.notification.dto.NotificationAction;
import com.dawnrise.identity.notification.model.*;
import com.dawnrise.identity.notification.service.NotificationService;
import com.dawnrise.identity.roleapproval.dto.*;
import com.dawnrise.identity.roleapproval.entity.RoleAssignmentRequest;
import com.dawnrise.identity.roleapproval.enums.ApprovalStatus;
import com.dawnrise.identity.roleapproval.policy.RoleApprovalPolicy;
import com.dawnrise.identity.roleapproval.repository.RoleAssignmentApprovalRepository;
import com.dawnrise.identity.roleapproval.repository.RoleAssignmentRequestRepository;
import com.dawnrise.identity.roleremoval.dto.RoleRemovalApprovalResponse;
import com.dawnrise.identity.roleremoval.dto.RoleRemovalRequestResponse;
import com.dawnrise.identity.roleremoval.entity.RoleRemovalRequest;
import com.dawnrise.identity.roleremoval.policy.RoleRemovalApprovalPolicy;
import com.dawnrise.identity.roleremoval.repository.RoleRemovalApprovalRepository;
import com.dawnrise.identity.roleremoval.repository.RoleRemovalRequestRepository;
import com.dawnrise.identity.user.enums.UserRole;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Aspect @Component
public class RoleWorkflowNotificationAspect {
    private final NotificationService notifications; private final NamedParameterJdbcTemplate jdbc;
    private final RoleApprovalPolicy assignmentPolicy; private final RoleRemovalApprovalPolicy removalPolicy;
    private final RoleAssignmentRequestRepository assignments; private final RoleRemovalRequestRepository removals;
    private final RoleAssignmentApprovalRepository assignmentApprovals; private final RoleRemovalApprovalRepository removalApprovals;
    public RoleWorkflowNotificationAspect(NotificationService notifications,NamedParameterJdbcTemplate jdbc,
            RoleApprovalPolicy assignmentPolicy,RoleRemovalApprovalPolicy removalPolicy,
            RoleAssignmentRequestRepository assignments,RoleRemovalRequestRepository removals,
            RoleAssignmentApprovalRepository assignmentApprovals,RoleRemovalApprovalRepository removalApprovals) {
        this.notifications=notifications;this.jdbc=jdbc;this.assignmentPolicy=assignmentPolicy;this.removalPolicy=removalPolicy;
        this.assignments=assignments;this.removals=removals;this.assignmentApprovals=assignmentApprovals;this.removalApprovals=removalApprovals;
    }

    @AfterReturning(pointcut="execution(* com.dawnrise.identity.roleapproval.service.RoleAssignmentRequestServiceImpl.createRequest(..))",returning="response")
    public void assignmentCreated(RoleAssignmentRequestResponse response){created(response.getOrganizationId(),response.getId(),response.getUserId(),response.getRequestedByUserId(),response.getRequestedRole(),false);}
    @AfterReturning(pointcut="execution(* com.dawnrise.identity.roleapproval.service.RoleAssignmentRequestServiceImpl.cancelRequest(..))",returning="response")
    public void assignmentCancelled(RoleAssignmentRequestResponse response){terminal(response.getOrganizationId(),response.getId(),response.getUserId(),false,ApprovalStatus.CANCELLED);}
    @AfterReturning(pointcut="execution(* com.dawnrise.identity.roleapproval.service.RoleAssignmentApprovalServiceImpl.recordDecision(..))",returning="response")
    public void assignmentDecision(RoleAssignmentApprovalResponse response){RoleAssignmentRequest r=assignments.findById(response.getRequestId()).orElseThrow();decision(r.getOrganizationId(),r.getId(),r.getUserId(),r.getRequestedByUserId(),r.getRequestedRole(),false,r.getStatus());}
    @AfterReturning(pointcut="execution(* com.dawnrise.identity.roleremoval.service.RoleRemovalRequestServiceImpl.createRequest(..))",returning="response")
    public void removalCreated(RoleRemovalRequestResponse response){created(response.getOrganizationId(),response.getId(),response.getUserId(),response.getRequestedByUserId(),response.getRequestedRole(),true);}
    @AfterReturning(pointcut="execution(* com.dawnrise.identity.roleremoval.service.RoleRemovalRequestServiceImpl.cancelRequest(..))",returning="response")
    public void removalCancelled(RoleRemovalRequestResponse response){terminal(response.getOrganizationId(),response.getId(),response.getUserId(),true,ApprovalStatus.CANCELLED);}
    @AfterReturning(pointcut="execution(* com.dawnrise.identity.roleremoval.service.RoleRemovalApprovalServiceImpl.recordDecision(..))",returning="response")
    public void removalDecision(RoleRemovalApprovalResponse response){RoleRemovalRequest r=removals.findById(response.getRequestId()).orElseThrow();decision(r.getOrganizationId(),r.getId(),r.getUserId(),r.getRequestedByUserId(),r.getRequestedRole(),true,r.getStatus());}

    private void created(long org,long id,long target,long requester,UserRole role,boolean removal){
        emit(org,id,(removal?NotificationEventType.ROLE_REMOVAL_REQUESTED:NotificationEventType.ROLE_ASSIGNMENT_REQUESTED),"Role change requested","A role change request was created for your account",Set.of(target),removal);
        approvalRequired(org,id,target,requester,role,removal);
    }
    private void decision(long org,long id,long target,long requester,UserRole role,boolean removal,ApprovalStatus status){
        if(status==ApprovalStatus.PENDING){approvalRequired(org,id,target,requester,role,removal);return;}
        terminal(org,id,target,removal,status);
    }
    private void terminal(long org,long id,long target,boolean removal,ApprovalStatus status){
        NotificationEventType type=switch(status){case APPROVED->removal?NotificationEventType.ROLE_REMOVAL_APPROVED:NotificationEventType.ROLE_ASSIGNMENT_APPROVED;case REJECTED->removal?NotificationEventType.ROLE_REMOVAL_REJECTED:NotificationEventType.ROLE_ASSIGNMENT_REJECTED;default->removal?NotificationEventType.ROLE_REMOVAL_CANCELLED:NotificationEventType.ROLE_ASSIGNMENT_CANCELLED;};
        emit(org,id,type,"Role request "+status.name().toLowerCase(),"Your role change request was updated",Set.of(target),removal);
        notifications.resolveGroup("identity-service",org,group(removal,id),source(type,id));
    }
    private void approvalRequired(long org,long id,long target,long requester,UserRole role,boolean removal){
        Set<UserRole> required=removal?removalPolicy.getRequiredApproverRoles(role):assignmentPolicy.getRequiredApproverRoles(role);
        Set<UserRole> satisfied=new HashSet<>();
        Set<Long> priorApprovers=new HashSet<>();
        if(removal) removalApprovals.findAllByRequestId(id).forEach(a->{priorApprovers.add(a.getApproverUserId());if(a.getDecision().name().equals("APPROVED"))satisfied.add(a.getApproverRole());});
        else assignmentApprovals.findAllByRequestId(id).forEach(a->{priorApprovers.add(a.getApproverUserId());if(a.getDecision().name().equals("APPROVED"))satisfied.add(a.getApproverRole());});
        required=new HashSet<>(required);required.removeAll(satisfied);if(required.isEmpty())return;
        Set<Long> recipients=new HashSet<>(jdbc.queryForList("SELECT DISTINCT u.id FROM users u JOIN user_roles r ON r.user_id=u.id WHERE u.organization_id=:org AND u.status='ACTIVE' AND r.role IN (:roles)",new MapSqlParameterSource("org",org).addValue("roles",required.stream().map(Enum::name).toList()),Long.class));
        recipients.remove(target);recipients.remove(requester);recipients.removeAll(priorApprovers);if(recipients.isEmpty())return;
        NotificationEventType type=removal?NotificationEventType.ROLE_REMOVAL_APPROVAL_REQUIRED:NotificationEventType.ROLE_ASSIGNMENT_APPROVAL_REQUIRED;
        emit(org,id,type,"Role approval required","A role request requires your review",recipients,removal,"stage:"+required.stream().map(Enum::name).sorted().toList());
    }
    private void emit(long org,long id,NotificationEventType type,String heading,String context,Set<Long> recipients,boolean removal){emit(org,id,type,heading,context,recipients,removal,"");}
    private void emit(long org,long id,NotificationEventType type,String heading,String context,Set<Long> recipients,boolean removal,String suffix){String source=source(type,id)+suffix;notifications.ingest("identity-service",new IngestNotificationRequest(org,source,type,NotificationCategory.APPROVALS,NotificationImportance.IMPORTANT,heading,context,new NotificationAction(NotificationActionType.ROLE_REQUEST,id,null,null),group(removal,id),OffsetDateTime.now(),recipients));}
    private String group(boolean removal,long id){return (removal?"role-removal:":"role-assignment:")+id;}
    private String source(NotificationEventType type,long id){return type.name().toLowerCase()+":"+id;}
}
