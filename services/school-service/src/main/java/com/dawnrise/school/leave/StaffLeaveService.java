package com.dawnrise.school.leave;

import com.dawnrise.school.leave.LeaveDtos.*;
import com.dawnrise.school.school.exception.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Array;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StaffLeaveService {
    private final NamedParameterJdbcTemplate jdbc; private final IdentityStaffClient identities;
    private final AcademicWorkingDayClient workingDays; private final ObjectMapper json; private final LeaveUnitCalculator calculator;
    public StaffLeaveService(NamedParameterJdbcTemplate jdbc,IdentityStaffClient identities,
                             AcademicWorkingDayClient workingDays,ObjectMapper json,LeaveUnitCalculator calculator){
        this.jdbc=jdbc;this.identities=identities;this.workingDays=workingDays;this.json=json;this.calculator=calculator;
    }

    @Transactional(readOnly=true) public List<PolicyView> policies(long org){
        return jdbc.query("SELECT * FROM staff_leave_types WHERE organization_id=:org ORDER BY name,id",p("org",org),this::policy);
    }
    @Transactional public PolicyView createPolicy(long org,long actor,PolicyInput input){
        validatePolicy(input); long id=jdbc.queryForObject("""
                INSERT INTO staff_leave_types(organization_id,code,name,active_from,active_to,yearly_allowance,
                carry_forward_enabled,carry_forward_limit,maximum_consecutive_days,supporting_document_required,
                paid,partial_time_enabled,negative_balance_allowed,applicable_roles,created_by_user_id)
                VALUES(:org,:code,:name,:from,:to,:allowance,:carry,:carryLimit,:maximum,:document,:paid,:partial,
                :negative,:roles,:actor) RETURNING id
                """,policyParams(org,actor,input),Long.class);auditPolicy(org,id,actor,"CREATED");return policy(org,id);
    }
    @Transactional public PolicyView updatePolicy(long org,long actor,long id,PolicyInput input){
        validatePolicy(input);int changed=jdbc.update("""
                UPDATE staff_leave_types SET code=:code,name=:name,active_from=:from,active_to=:to,
                yearly_allowance=:allowance,carry_forward_enabled=:carry,carry_forward_limit=:carryLimit,
                maximum_consecutive_days=:maximum,supporting_document_required=:document,paid=:paid,
                partial_time_enabled=:partial,negative_balance_allowed=:negative,applicable_roles=:roles,
                version=version+1,updated_at=CURRENT_TIMESTAMP
                WHERE organization_id=:org AND id=:id AND version=:version
                """,policyParams(org,actor,input).addValue("id",id).addValue("version",input.expectedVersion()));
        if(changed!=1)throw new OptimisticLockingFailureException("Leave policy changed");auditPolicy(org,id,actor,"UPDATED");return policy(org,id);
    }
    @Transactional(readOnly=true) public List<ApprovalRuleView> approvalRules(long org){
        return jdbc.query("SELECT * FROM staff_leave_approval_rules WHERE organization_id=:org ORDER BY requester_role,step_number,id",p("org",org),
                (rs,n)->new ApprovalRuleView(rs.getLong("id"),rs.getString("requester_role"),rs.getInt("step_number"),rs.getString("approver_role"),rs.getBoolean("active"),rs.getLong("version")));
    }
    @Transactional public ApprovalRuleView createApprovalRule(long org,ApprovalRule input){
        long id=jdbc.queryForObject("INSERT INTO staff_leave_approval_rules(organization_id,requester_role,step_number,approver_role,active) VALUES(:org,:requester,:step,:approver,:active) RETURNING id",
                p("org",org).addValue("requester",input.requesterRole()).addValue("step",input.stepNumber()).addValue("approver",input.approverRole()).addValue("active",input.active()),Long.class);
        return approvalRule(org,id);
    }
    @Transactional public ApprovalRuleView updateApprovalRule(long org,long id,ApprovalRule input){
        int changed=jdbc.update("UPDATE staff_leave_approval_rules SET requester_role=:requester,step_number=:step,approver_role=:approver,active=:active,version=version+1 WHERE organization_id=:org AND id=:id AND version=:version",
                p("org",org).addValue("id",id).addValue("requester",input.requesterRole()).addValue("step",input.stepNumber()).addValue("approver",input.approverRole()).addValue("active",input.active()).addValue("version",input.expectedVersion()));
        if(changed!=1)throw new OptimisticLockingFailureException("Leave approval rule changed");return approvalRule(org,id);
    }

    @Transactional public List<BalanceView> balances(long org,long staffUserId){
        var staff=identities.staff(org,staffUserId);int year=LocalDate.now().getYear();
        for(PolicyView policy:policies(org))if(applies(policy,staff.roles())) ensureBalance(org,staffUserId,policy,year);
        return jdbc.query("SELECT b.*,t.negative_balance_allowed FROM staff_leave_balances b JOIN staff_leave_types t ON t.id=b.leave_type_id AND t.organization_id=b.organization_id WHERE b.organization_id=:org AND b.staff_user_id=:staff ORDER BY t.name",p("org",org).addValue("staff",staffUserId),this::balance);
    }
    @Transactional public BalanceView adjust(long org,long actor,long balanceId,BalanceAdjustment request){
        var rows=jdbc.query("SELECT b.*,t.negative_balance_allowed FROM staff_leave_balances b JOIN staff_leave_types t ON t.id=b.leave_type_id AND t.organization_id=b.organization_id WHERE b.organization_id=:org AND b.id=:id FOR UPDATE OF b",p("org",org).addValue("id",balanceId),this::balance);
        if(rows.isEmpty())throw new ResourceNotFoundException("Leave balance not found");BalanceView before=rows.getFirst();if(before.version()!=request.expectedVersion())throw new OptimisticLockingFailureException("Leave balance changed");
        Boolean negativeAllowed=jdbc.queryForObject("SELECT t.negative_balance_allowed FROM staff_leave_balances b JOIN staff_leave_types t ON t.id=b.leave_type_id AND t.organization_id=b.organization_id WHERE b.organization_id=:org AND b.id=:id",p("org",org).addValue("id",balanceId),Boolean.class);
        if(!Boolean.TRUE.equals(negativeAllowed)&&before.available().add(request.amount()).signum()<0)throw new IllegalStateException("Balance adjustment would make available leave negative");
        jdbc.update("UPDATE staff_leave_balances SET adjusted=adjusted+:amount,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE organization_id=:org AND id=:id",p("org",org).addValue("id",balanceId).addValue("amount",request.amount()));
        jdbc.update("INSERT INTO staff_leave_balance_adjustments(organization_id,balance_id,amount,reason,actor_user_id) VALUES(:org,:id,:amount,:reason,:actor)",p("org",org).addValue("id",balanceId).addValue("amount",request.amount()).addValue("reason",request.reason().trim()).addValue("actor",actor));
        return jdbc.query("SELECT b.*,t.negative_balance_allowed FROM staff_leave_balances b JOIN staff_leave_types t ON t.id=b.leave_type_id AND t.organization_id=b.organization_id WHERE b.organization_id=:org AND b.id=:id",p("org",org).addValue("id",balanceId),this::balance).getFirst();
    }

    @Transactional(readOnly=true) public Preview preview(long org,long actor,RequestInput input){
        var staff=identities.staff(org,actor);PolicyView policy=policy(org,input.leaveTypeId());validateRequest(org,policy,staff.roles(),input);
        BigDecimal units=units(org,input);BalanceView balance=balanceFor(org,actor,policy,input.startDate().getYear());
        var days=workingDays.days(org,input.startDate(),input.endDate());
        return new Preview(units,balance.available().subtract(units),policy.negativeBalanceAllowed()||balance.available().compareTo(units)>=0,days.excludedDates());
    }
    @Transactional public RequestView createDraft(long org,long actor,RequestInput input){
        var staff=identities.staff(org,actor);PolicyView policy=policy(org,input.leaveTypeId());validateRequest(org,policy,staff.roles(),input);
        BigDecimal units=units(org,input);long id=jdbc.queryForObject("""
                INSERT INTO staff_leave_requests(organization_id,requester_user_id,leave_type_id,request_kind,
                start_date,end_date,start_time,end_time,half_day_period,units,reason,document_name,
                document_content_type,document_reference) VALUES(:org,:actor,:type,:kind,:start,:end,:startTime,
                :endTime,:half,:units,:reason,:document,:contentType,:reference) RETURNING id
                """,requestParams(org,actor,input,units),Long.class);audit(org,id,actor,"DRAFT_CREATED",null);return request(org,id,true);
    }
    @Transactional public RequestView updateDraft(long org,long actor,long id,long expectedVersion,RequestInput input){
        RequestHead head=head(org,id,true);requireOwner(head,actor);requireStatus(head,"DRAFT");requireVersion(head,expectedVersion);
        var staff=identities.staff(org,actor);PolicyView policy=policy(org,input.leaveTypeId());validateRequest(org,policy,staff.roles(),input);BigDecimal units=units(org,input);
        int changed=jdbc.update("""
                UPDATE staff_leave_requests SET leave_type_id=:type,request_kind=:kind,start_date=:start,end_date=:end,
                start_time=:startTime,end_time=:endTime,half_day_period=:half,units=:units,reason=:reason,
                document_name=:document,document_content_type=:contentType,document_reference=:reference,
                version=version+1,updated_at=CURRENT_TIMESTAMP WHERE organization_id=:org AND id=:id AND version=:version
                """,requestParams(org,actor,input,units).addValue("id",id).addValue("version",expectedVersion));
        if(changed!=1)throw new OptimisticLockingFailureException("Leave request changed");audit(org,id,actor,"DRAFT_UPDATED",null);return request(org,id,true);
    }
    @Transactional public RequestView submit(long org,long actor,long id,long expectedVersion){
        RequestHead head=head(org,id,true);requireOwner(head,actor);requireStatus(head,"DRAFT");requireVersion(head,expectedVersion);
        RequestView value=request(org,id,true);var staff=identities.staff(org,actor);PolicyView policy=policy(org,value.leaveTypeId());
        rejectOverlap(org,actor,value.startDate(),value.endDate(),id);BalanceView balance=balanceFor(org,actor,policy,value.startDate().getYear());
        if(!policy.negativeBalanceAllowed()&&balance.available().compareTo(value.units())<0)throw new IllegalStateException("Insufficient leave balance");
        List<ApprovalTarget> chain=approvalChain(org,actor,staff.roles());if(chain.isEmpty())throw new IllegalStateException("No eligible leave approver is configured");
        int step=1;for(var target:chain)jdbc.update("INSERT INTO staff_leave_approvals(organization_id,leave_request_id,step_number,approver_role,approver_user_id,status) VALUES(:org,:id,:step,:role,:user,:status)",p("org",org).addValue("id",id).addValue("step",step).addValue("role",target.role()).addValue("user",target.userId()).addValue("status",step==1?"PENDING":"WAITING"));
        jdbc.update("UPDATE staff_leave_requests SET status='PENDING',current_approval_step=1,submitted_at=CURRENT_TIMESTAMP,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE organization_id=:org AND id=:id",p("org",org).addValue("id",id));
        jdbc.update("UPDATE staff_leave_balances SET pending=pending+:units,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE organization_id=:org AND id=:balance",p("org",org).addValue("balance",balance.id()).addValue("units",value.units()));
        audit(org,id,actor,"SUBMITTED",null);notify(org,id,"LEAVE_REQUESTED","Leave request submitted","Your leave request was submitted",Set.of(actor),"leave:"+id);
        notify(org,id,"LEAVE_APPROVAL_REQUIRED","Leave approval required","A staff leave request requires your review",Set.of(chain.getFirst().userId()),"leave-approval:"+id);
        return request(org,id,true);
    }
    @Transactional public RequestView approve(long org,long actor,long id,DecisionRequest decision){return decide(org,actor,id,decision,true);}
    @Transactional public RequestView reject(long org,long actor,long id,DecisionRequest decision){return decide(org,actor,id,decision,false);}
    private RequestView decide(long org,long actor,long id,DecisionRequest decision,boolean approved){
        RequestHead head=head(org,id,true);requireStatus(head,"PENDING");requireVersion(head,decision.expectedVersion());if(head.requester()==actor)throw new IllegalStateException("Self-approval is not allowed");
        var rows=jdbc.query("SELECT id,step_number,approver_user_id FROM staff_leave_approvals WHERE organization_id=:org AND leave_request_id=:id AND status='PENDING' FOR UPDATE",p("org",org).addValue("id",id),(rs,n)->new long[]{rs.getLong(1),rs.getLong(2),rs.getLong(3)});
        if(rows.isEmpty()||rows.getFirst()[2]!=actor)throw new ResourceNotFoundException("Pending leave approval not found");
        long[] current=rows.getFirst();jdbc.update("UPDATE staff_leave_approvals SET status=:status,reason=:reason,decided_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=:approval",p("status",approved?"APPROVED":"REJECTED").addValue("reason",decision.reason().trim()).addValue("approval",current[0]));
        RequestView value=request(org,id,true);if(!approved){finishRejected(org,actor,value,decision.reason());return request(org,id,true);}
        var next=jdbc.query("SELECT id,step_number,approver_user_id FROM staff_leave_approvals WHERE organization_id=:org AND leave_request_id=:id AND status='WAITING' ORDER BY step_number LIMIT 1",p("org",org).addValue("id",id),(rs,n)->new long[]{rs.getLong(1),rs.getLong(2),rs.getLong(3)});
        if(!next.isEmpty()){long[] row=next.getFirst();jdbc.update("UPDATE staff_leave_approvals SET status='PENDING' WHERE id=:id",p("id",row[0]));jdbc.update("UPDATE staff_leave_requests SET current_approval_step=:step,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE organization_id=:org AND id=:id",p("org",org).addValue("id",id).addValue("step",row[1]));notify(org,id,"LEAVE_APPROVAL_REQUIRED","Leave approval required","A staff leave request requires your review",Set.of(row[2]),"leave-approval:"+id);audit(org,id,actor,"STEP_APPROVED",decision.reason());}
        else finishApproved(org,actor,value,decision.reason());return request(org,id,true);
    }
    @Transactional public RequestView withdraw(long org,long actor,long id,long expectedVersion){
        RequestHead head=head(org,id,true);requireOwner(head,actor);requireStatus(head,"PENDING");requireVersion(head,expectedVersion);RequestView value=request(org,id,true);
        releasePending(org,value);jdbc.update("UPDATE staff_leave_requests SET status='WITHDRAWN',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE organization_id=:org AND id=:id",p("org",org).addValue("id",id));cancelApprovals(org,id);audit(org,id,actor,"WITHDRAWN",null);notify(org,id,"LEAVE_WITHDRAWN","Leave request withdrawn","A leave request was withdrawn",approverRecipients(value,actor),"leave:"+id);return request(org,id,true);
    }
    @Transactional public RequestView cancel(long org,long actor,long id,DecisionRequest decision){
        RequestHead head=head(org,id,true);if(head.requester()!=actor)throw new ResourceNotFoundException("Leave request not found");requireStatus(head,"APPROVED");requireVersion(head,decision.expectedVersion());RequestView value=request(org,id,true);
        jdbc.update("UPDATE staff_leave_requests SET status='CANCELLED',cancelled_at=CURRENT_TIMESTAMP,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE organization_id=:org AND id=:id",p("org",org).addValue("id",id));
        jdbc.update("UPDATE staff_leave_balances SET used=GREATEST(0,used-:units),version=version+1,updated_at=CURRENT_TIMESTAMP WHERE organization_id=:org AND staff_user_id=:staff AND leave_type_id=:type AND leave_year=:year",p("org",org).addValue("staff",actor).addValue("type",value.leaveTypeId()).addValue("year",value.startDate().getYear()).addValue("units",value.units()));
        coverage(org,value,"CANCEL",actor);audit(org,id,actor,"CANCELLED",decision.reason());notify(org,id,"LEAVE_CANCELLED","Approved leave cancelled","An approved leave request was cancelled",approverRecipients(value,actor),"leave:"+id);return request(org,id,true);
    }
    @Transactional public RequestView partialCancel(long org,long actor,long id,PartialCancelRequest decision){
        RequestHead head=head(org,id,true);requireOwner(head,actor);if(!List.of("APPROVED","PARTIALLY_CANCELLED").contains(head.status()))throw new IllegalStateException("Only approved leave can be partially cancelled");requireVersion(head,decision.expectedVersion());RequestView old=request(org,id,true);
        if(!decision.cancelFrom().isAfter(old.startDate())||decision.cancelFrom().isAfter(old.endDate()))throw new IllegalArgumentException("Partial cancellation must leave at least one approved date");
        if(decision.cancelFrom().isBefore(LocalDate.now()))throw new IllegalArgumentException("Elapsed leave dates cannot be cancelled");
        RequestInput remaining=new RequestInput(old.leaveTypeId(),old.kind(),old.startDate(),decision.cancelFrom().minusDays(1),old.startTime(),old.endTime(),old.halfDayPeriod(),old.reason(),old.documentName(),null,old.documentName()==null?null:"retained");
        BigDecimal newUnits=units(org,remaining),released=old.units().subtract(newUnits);
        coverage(org,old,"CANCEL",actor);
        jdbc.update("UPDATE staff_leave_requests SET end_date=:end,units=:units,status='PARTIALLY_CANCELLED',cancelled_at=CURRENT_TIMESTAMP,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE organization_id=:org AND id=:id",p("org",org).addValue("id",id).addValue("end",remaining.endDate()).addValue("units",newUnits));
        jdbc.update("UPDATE staff_leave_balances SET used=GREATEST(0,used-:released),version=version+1,updated_at=CURRENT_TIMESTAMP WHERE organization_id=:org AND staff_user_id=:staff AND leave_type_id=:type AND leave_year=:year",p("org",org).addValue("staff",actor).addValue("type",old.leaveTypeId()).addValue("year",old.startDate().getYear()).addValue("released",released));
        RequestView updated=request(org,id,true);coverage(org,updated,"UPSERT",actor);audit(org,id,actor,"PARTIALLY_CANCELLED",decision.reason());notify(org,id,"LEAVE_CHANGED","Approved leave changed","Future approved leave dates were changed",approverRecipients(updated,actor),"leave:"+id);return updated;
    }

    @Transactional(readOnly=true) public List<RequestView> mine(long org,long actor){return ids("SELECT id FROM staff_leave_requests WHERE organization_id=:org AND requester_user_id=:actor ORDER BY created_at DESC",p("org",org).addValue("actor",actor)).stream().map(id->request(org,id,true)).toList();}
    @Transactional(readOnly=true) public List<RequestView> queue(long org,long actor){return ids("SELECT r.id FROM staff_leave_requests r JOIN staff_leave_approvals a ON a.leave_request_id=r.id AND a.organization_id=r.organization_id WHERE r.organization_id=:org AND a.approver_user_id=:actor AND a.status='PENDING' ORDER BY r.submitted_at,r.id",p("org",org).addValue("actor",actor)).stream().map(id->request(org,id,true)).toList();}
    @Transactional(readOnly=true) public List<AuditView> audit(long org,long actor,long id,boolean reviewer){RequestHead head=head(org,id,false);if(head.requester()!=actor&&!reviewer)throw new ResourceNotFoundException("Leave request not found");return jdbc.query("SELECT action,actor_user_id,reason,occurred_at FROM staff_leave_audit WHERE organization_id=:org AND leave_request_id=:id ORDER BY occurred_at,id",p("org",org).addValue("id",id),(rs,n)->new AuditView(rs.getString(1),rs.getLong(2),rs.getString(3),rs.getObject(4,OffsetDateTime.class)));}
    @Transactional(readOnly=true) public List<CalendarEntry> calendar(long org,LocalDate start,LocalDate end){return jdbc.query("SELECT r.id,r.requester_user_id,r.start_date,r.end_date,r.request_kind,r.status FROM staff_leave_requests r WHERE r.organization_id=:org AND r.status IN ('APPROVED','PARTIALLY_CANCELLED') AND r.start_date<=:end AND r.end_date>=:start ORDER BY r.start_date,r.id",p("org",org).addValue("start",start).addValue("end",end),(rs,n)->{long user=rs.getLong(2);String name=identities.staff(org,user).displayName();return new CalendarEntry(rs.getLong(1),user,name,rs.getObject(3,LocalDate.class),rs.getObject(4,LocalDate.class),RequestKind.valueOf(rs.getString(5)),rs.getString(6));});}

    private void finishApproved(long org,long actor,RequestView value,String reason){releasePending(org,value);jdbc.update("UPDATE staff_leave_balances SET used=used+:units,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE organization_id=:org AND staff_user_id=:staff AND leave_type_id=:type AND leave_year=:year",p("org",org).addValue("staff",value.requesterUserId()).addValue("type",value.leaveTypeId()).addValue("year",value.startDate().getYear()).addValue("units",value.units()));jdbc.update("UPDATE staff_leave_requests SET status='APPROVED',decided_at=CURRENT_TIMESTAMP,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE organization_id=:org AND id=:id",p("org",org).addValue("id",value.id()));coverage(org,value,"UPSERT",actor);audit(org,value.id(),actor,"APPROVED",reason);notify(org,value.id(),"LEAVE_APPROVED","Leave request approved","Your leave request was approved",Set.of(value.requesterUserId()),"leave:"+value.id());}
    private void finishRejected(long org,long actor,RequestView value,String reason){releasePending(org,value);cancelApprovals(org,value.id());jdbc.update("UPDATE staff_leave_requests SET status='REJECTED',decided_at=CURRENT_TIMESTAMP,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE organization_id=:org AND id=:id",p("org",org).addValue("id",value.id()));audit(org,value.id(),actor,"REJECTED",reason);notify(org,value.id(),"LEAVE_REJECTED","Leave request rejected","Your leave request was not approved",Set.of(value.requesterUserId()),"leave:"+value.id());}
    private void releasePending(long org,RequestView value){jdbc.update("UPDATE staff_leave_balances SET pending=GREATEST(0,pending-:units),version=version+1,updated_at=CURRENT_TIMESTAMP WHERE organization_id=:org AND staff_user_id=:staff AND leave_type_id=:type AND leave_year=:year",p("org",org).addValue("staff",value.requesterUserId()).addValue("type",value.leaveTypeId()).addValue("year",value.startDate().getYear()).addValue("units",value.units()));}
    private void cancelApprovals(long org,long id){jdbc.update("UPDATE staff_leave_approvals SET status='CANCELLED' WHERE organization_id=:org AND leave_request_id=:id AND status IN ('PENDING','WAITING')",p("org",org).addValue("id",id));}

    private void coverage(long org,RequestView value,String operation,long actor){try{String event="leave:"+value.id()+":"+operation+":"+value.version();var dates=workingDays.days(org,value.startDate(),value.endDate()).workingDates();String payload=json.writeValueAsString(Map.of("leaveRequestId",value.id(),"teacherUserId",value.requesterUserId(),"workingDates",dates,"startTime",value.startTime()==null?"":value.startTime().toString(),"endTime",value.endTime()==null?"":value.endTime().toString(),"halfDayPeriod",value.halfDayPeriod()==null?"":value.halfDayPeriod().name(),"kind",value.kind().name(),"sourceEventId",event));jdbc.update("INSERT INTO staff_leave_coverage_outbox(organization_id,leave_request_id,operation,source_event_id,actor_user_id,payload) VALUES(:org,:id,:operation,:event,:actor,CAST(:payload AS JSONB)) ON CONFLICT(organization_id,source_event_id) DO NOTHING",p("org",org).addValue("id",value.id()).addValue("operation",operation).addValue("event",event).addValue("actor",actor).addValue("payload",payload));}catch(Exception exception){throw new IllegalStateException("Leave coverage could not be queued",exception);}}
    private void notify(long org,long id,String type,String heading,String context,Set<Long> recipients,String group){try{String event=type.toLowerCase()+":"+id+":"+currentVersion(org,id);String users=json.writeValueAsString(recipients);jdbc.update("INSERT INTO school_notification_outbox(source_type,source_id,organization_id,source_event_id,event_type,importance,heading,context,audience_spec,recipient_user_ids,available_at) VALUES('LEAVE',:id,:org,:event,:type,'NORMAL',:heading,:context,'[]'::JSONB,CAST(:users AS JSONB),CURRENT_TIMESTAMP) ON CONFLICT(organization_id,source_event_id) DO NOTHING",p("id",id).addValue("org",org).addValue("event",event).addValue("type",type).addValue("heading",heading).addValue("context",context).addValue("users",users));}catch(Exception exception){throw new IllegalStateException("Leave notification could not be queued",exception);}}
    private void audit(long org,long id,long actor,String action,String reason){jdbc.update("INSERT INTO staff_leave_audit(organization_id,leave_request_id,actor_user_id,action,reason) VALUES(:org,:id,:actor,:action,:reason)",p("org",org).addValue("id",id).addValue("actor",actor).addValue("action",action).addValue("reason",reason));}
    private void auditPolicy(long org,long id,long actor,String action){try{String snapshot=json.writeValueAsString(policy(org,id));jdbc.update("INSERT INTO staff_leave_policy_audit(organization_id,leave_type_id,actor_user_id,action,snapshot) VALUES(:org,:id,:actor,:action,CAST(:snapshot AS JSONB))",p("org",org).addValue("id",id).addValue("actor",actor).addValue("action",action).addValue("snapshot",snapshot));}catch(Exception exception){throw new IllegalStateException("Leave policy audit could not be recorded",exception);}}

    private RequestView request(long org,long id,boolean privateView){var rows=jdbc.query("SELECT r.*,t.name leave_type_name FROM staff_leave_requests r JOIN staff_leave_types t ON t.id=r.leave_type_id AND t.organization_id=r.organization_id WHERE r.organization_id=:org AND r.id=:id",p("org",org).addValue("id",id),(rs,n)->new RequestView(rs.getLong("id"),rs.getLong("requester_user_id"),rs.getLong("leave_type_id"),rs.getString("leave_type_name"),RequestKind.valueOf(rs.getString("request_kind")),rs.getObject("start_date",LocalDate.class),rs.getObject("end_date",LocalDate.class),rs.getObject("start_time",java.time.LocalTime.class),rs.getObject("end_time",java.time.LocalTime.class),rs.getString("half_day_period")==null?null:HalfDayPeriod.valueOf(rs.getString("half_day_period")),rs.getBigDecimal("units"),privateView?rs.getString("reason"):null,privateView?rs.getString("document_name"):null,rs.getString("status"),rs.getInt("current_approval_step"),rs.getLong("version"),rs.getObject("created_at",OffsetDateTime.class),approvals(org,id)));if(rows.isEmpty())throw new ResourceNotFoundException("Leave request not found");return rows.getFirst();}
    private List<ApprovalView> approvals(long org,long id){return jdbc.query("SELECT * FROM staff_leave_approvals WHERE organization_id=:org AND leave_request_id=:id ORDER BY step_number",p("org",org).addValue("id",id),(rs,n)->new ApprovalView(rs.getInt("step_number"),rs.getString("approver_role"),(Long)rs.getObject("approver_user_id"),rs.getString("status"),rs.getString("reason"),rs.getObject("decided_at",OffsetDateTime.class),rs.getLong("version")));}
    private RequestHead head(long org,long id,boolean lock){var rows=jdbc.query("SELECT requester_user_id,status,version FROM staff_leave_requests WHERE organization_id=:org AND id=:id"+(lock?" FOR UPDATE":""),p("org",org).addValue("id",id),(rs,n)->new RequestHead(rs.getLong(1),rs.getString(2),rs.getLong(3)));if(rows.isEmpty())throw new ResourceNotFoundException("Leave request not found");return rows.getFirst();}
    private PolicyView policy(long org,long id){var rows=jdbc.query("SELECT * FROM staff_leave_types WHERE organization_id=:org AND id=:id",p("org",org).addValue("id",id),this::policy);if(rows.isEmpty())throw new ResourceNotFoundException("Leave policy not found");return rows.getFirst();}
    private ApprovalRuleView approvalRule(long org,long id){var rows=jdbc.query("SELECT * FROM staff_leave_approval_rules WHERE organization_id=:org AND id=:id",p("org",org).addValue("id",id),(rs,n)->new ApprovalRuleView(rs.getLong("id"),rs.getString("requester_role"),rs.getInt("step_number"),rs.getString("approver_role"),rs.getBoolean("active"),rs.getLong("version")));if(rows.isEmpty())throw new ResourceNotFoundException("Leave approval rule not found");return rows.getFirst();}
    private long currentVersion(long org,long id){Long value=jdbc.queryForObject("SELECT version FROM staff_leave_requests WHERE organization_id=:org AND id=:id",p("org",org).addValue("id",id),Long.class);return value==null?0:value;}
    private PolicyView policy(java.sql.ResultSet rs,int n)throws java.sql.SQLException{Array roles=rs.getArray("applicable_roles");return new PolicyView(rs.getLong("id"),rs.getString("code"),rs.getString("name"),rs.getObject("active_from",LocalDate.class),rs.getObject("active_to",LocalDate.class),rs.getBigDecimal("yearly_allowance"),rs.getBoolean("carry_forward_enabled"),rs.getBigDecimal("carry_forward_limit"),rs.getBigDecimal("maximum_consecutive_days"),rs.getBoolean("supporting_document_required"),rs.getBoolean("paid"),rs.getBoolean("partial_time_enabled"),rs.getBoolean("negative_balance_allowed"),Set.of((String[])roles.getArray()),rs.getLong("version"));}
    private BalanceView balance(java.sql.ResultSet rs,int n)throws java.sql.SQLException{BigDecimal available=rs.getBigDecimal("opening").add(rs.getBigDecimal("accrued")).add(rs.getBigDecimal("carried")).add(rs.getBigDecimal("adjusted")).subtract(rs.getBigDecimal("used")).subtract(rs.getBigDecimal("pending"));return new BalanceView(rs.getLong("id"),rs.getLong("staff_user_id"),rs.getLong("leave_type_id"),rs.getInt("leave_year"),rs.getBigDecimal("opening"),rs.getBigDecimal("accrued"),rs.getBigDecimal("carried"),rs.getBigDecimal("used"),rs.getBigDecimal("pending"),rs.getBigDecimal("adjusted"),available,rs.getLong("version"));}
    private BalanceView balanceFor(long org,long staff,PolicyView policy,int year){ensureBalance(org,staff,policy,year);return jdbc.query("SELECT b.*,t.negative_balance_allowed FROM staff_leave_balances b JOIN staff_leave_types t ON t.id=b.leave_type_id AND t.organization_id=b.organization_id WHERE b.organization_id=:org AND b.staff_user_id=:staff AND b.leave_type_id=:type AND b.leave_year=:year",p("org",org).addValue("staff",staff).addValue("type",policy.id()).addValue("year",year),this::balance).getFirst();}
    private void ensureBalance(long org,long staff,PolicyView policy,int year){jdbc.update("INSERT INTO staff_leave_balances(organization_id,staff_user_id,leave_type_id,leave_year,accrued) VALUES(:org,:staff,:type,:year,:allowance) ON CONFLICT DO NOTHING",p("org",org).addValue("staff",staff).addValue("type",policy.id()).addValue("year",year).addValue("allowance",policy.yearlyAllowance()));}
    private List<ApprovalTarget> approvalChain(long org,long requester,Set<String> requesterRoles){List<String> roles=jdbc.queryForList("SELECT approver_role FROM staff_leave_approval_rules WHERE organization_id=:org AND requester_role IN (:roles) AND active ORDER BY step_number",p("org",org).addValue("roles",requesterRoles),String.class);if(roles.isEmpty())roles=List.of("HR","PRINCIPAL");List<ApprovalTarget> result=new ArrayList<>();for(String role:new LinkedHashSet<>(roles)){var available=identities.approvers(org,List.of(role)).stream().filter(v->v.userId()!=requester).findFirst();available.ifPresent(staff->result.add(new ApprovalTarget(role,staff.userId())));}return result;}
    private Set<Long> approverRecipients(RequestView value,long requester){Set<Long> users=new LinkedHashSet<>();users.add(requester);value.approvals().stream().map(ApprovalView::approverUserId).filter(java.util.Objects::nonNull).forEach(users::add);return users;}
    private BigDecimal units(long org,RequestInput input){var days=workingDays.days(org,input.startDate(),input.endDate());return calculator.calculate(input.kind(),days.workingDates().size(),input.startTime(),input.endTime());}
    private void validateRequest(long org,PolicyView policy,Set<String> roles,RequestInput input){if(input.endDate().isBefore(input.startDate()))throw new IllegalArgumentException("End date cannot precede start date");if(input.startDate().isBefore(policy.activeFrom())||(policy.activeTo()!=null&&input.endDate().isAfter(policy.activeTo())))throw new IllegalArgumentException("Leave policy is not active for these dates");if(!applies(policy,roles))throw new IllegalArgumentException("Leave policy does not apply to this staff member");if((input.kind()==RequestKind.PARTIAL_TIME||input.kind()==RequestKind.LATE_ARRIVAL||input.kind()==RequestKind.EARLY_DEPARTURE)&&!policy.partialTimeEnabled())throw new IllegalArgumentException("Partial-time leave is not enabled");if(input.kind()==RequestKind.HALF_DAY&&input.halfDayPeriod()==null)throw new IllegalArgumentException("Half-day period is required");if(policy.supportingDocumentRequired()&&(input.documentReference()==null||input.documentReference().isBlank()))throw new IllegalArgumentException("Supporting document metadata is required");BigDecimal count=units(org,input);if(policy.maximumConsecutiveDays()!=null&&count.compareTo(policy.maximumConsecutiveDays())>0)throw new IllegalArgumentException("Maximum consecutive leave days exceeded");}
    private void rejectOverlap(long org,long staff,LocalDate start,LocalDate end,long id){Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM staff_leave_requests WHERE organization_id=:org AND requester_user_id=:staff AND id<>:id AND status IN ('PENDING','APPROVED','PARTIALLY_CANCELLED') AND start_date<=:end AND end_date>=:start",p("org",org).addValue("staff",staff).addValue("id",id).addValue("start",start).addValue("end",end),Integer.class);if(count!=null&&count>0)throw new DataIntegrityViolationException("Leave request overlaps an existing request");}
    private boolean applies(PolicyView policy,Set<String> roles){return roles.stream().anyMatch(policy.applicableRoles()::contains);}
    private void validatePolicy(PolicyInput input){if(input.activeTo()!=null&&input.activeTo().isBefore(input.activeFrom()))throw new IllegalArgumentException("Policy end date cannot precede start date");if(!input.carryForwardEnabled()&&input.carryForwardLimit()!=null)throw new IllegalArgumentException("Carry-forward limit requires carry-forward to be enabled");}
    private MapSqlParameterSource policyParams(long org,long actor,PolicyInput i){return p("org",org).addValue("actor",actor).addValue("code",i.code()).addValue("name",i.name().trim()).addValue("from",i.activeFrom()).addValue("to",i.activeTo()).addValue("allowance",i.yearlyAllowance()).addValue("carry",i.carryForwardEnabled()).addValue("carryLimit",i.carryForwardLimit()).addValue("maximum",i.maximumConsecutiveDays()).addValue("document",i.supportingDocumentRequired()).addValue("paid",i.paid()).addValue("partial",i.partialTimeEnabled()).addValue("negative",i.negativeBalanceAllowed()).addValue("roles",i.applicableRoles().toArray(String[]::new));}
    private MapSqlParameterSource requestParams(long org,long actor,RequestInput i,BigDecimal units){return p("org",org).addValue("actor",actor).addValue("type",i.leaveTypeId()).addValue("kind",i.kind().name()).addValue("start",i.startDate()).addValue("end",i.endDate()).addValue("startTime",i.startTime()).addValue("endTime",i.endTime()).addValue("half",i.halfDayPeriod()==null?null:i.halfDayPeriod().name()).addValue("units",units).addValue("reason",i.reason().trim()).addValue("document",i.documentName()).addValue("contentType",i.documentContentType()).addValue("reference",i.documentReference());}
    private List<Long> ids(String sql,MapSqlParameterSource parameters){return jdbc.queryForList(sql,parameters,Long.class);}
    private void requireOwner(RequestHead head,long actor){if(head.requester()!=actor)throw new ResourceNotFoundException("Leave request not found");}
    private void requireStatus(RequestHead head,String status){if(!status.equals(head.status()))throw new IllegalStateException("Leave request is not "+status.toLowerCase());}
    private void requireVersion(RequestHead head,long expected){if(head.version()!=expected)throw new OptimisticLockingFailureException("Leave request changed");}
    private MapSqlParameterSource p(String key,Object value){return new MapSqlParameterSource(key,value);}
    private record RequestHead(long requester,String status,long version){}
    private record ApprovalTarget(String role,long userId){}
}
