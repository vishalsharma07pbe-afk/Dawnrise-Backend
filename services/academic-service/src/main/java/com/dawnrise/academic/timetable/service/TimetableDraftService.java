package com.dawnrise.academic.timetable.service;

import com.dawnrise.academic.timetable.dto.TimetableConfigurationDtos.BellDayResponse;
import com.dawnrise.academic.timetable.dto.TimetableConfigurationDtos.BellIntervalResponse;
import com.dawnrise.academic.timetable.dto.TimetableConfigurationDtos.IntervalType;
import com.dawnrise.academic.timetable.dto.TimetableConfigurationDtos.ResolvedSectionSchedule;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.ActivityType;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.BulkLessonRequest;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.Conflict;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.ConflictPreview;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.ConflictSeverity;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.CreateDraftRequest;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.DraftResponse;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.DraftPage;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.DraftSummary;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.DraftSource;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.DraftHistory;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.EditAuditEvent;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.EffectiveDateIntentRequest;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.LessonRequest;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.MoveLessonRequest;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.SectionDraftResponse;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.SlotResponse;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.Suggestion;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.SwapLessonsRequest;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.ValidationReport;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.VersionRequest;
import com.dawnrise.academic.timetable.dto.TimetableDraftDtos.WorkflowStatus;
import com.dawnrise.academic.timetable.dto.TimetableConfigurationDtos.CoverageMode;
import com.dawnrise.academic.timetable.dto.TimetableConfigurationDtos.AssigneeRequirement;
import com.dawnrise.academic.timetable.exception.InvalidTimetableConfigurationException;
import com.dawnrise.academic.timetable.exception.TimetableConfigurationConflictException;
import com.dawnrise.academic.timetable.exception.TimetableConfigurationNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class TimetableDraftService {
    private final JdbcClient jdbc;
    private final TimetableConfigurationService configurationService;
    private final PlatformTransactionManager transactionManager;

    public TimetableDraftService(JdbcClient jdbc, TimetableConfigurationService configurationService,
                                 PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.configurationService = configurationService;
        this.transactionManager = transactionManager;
    }

    @Transactional(readOnly = true)
    public DraftPage list(long org, long year, WorkflowStatus status, int page, int size) {
        requireYear(org, year);
        long total = jdbc.sql("""
                SELECT COUNT(*) FROM timetable_drafts
                WHERE organization_id=:org AND academic_year_id=:year
                  AND (CAST(:status AS VARCHAR) IS NULL OR workflow_status=:status)
                """).param("org", org).param("year", year)
                .param("status", status == null ? null : status.name()).query(Long.class).single();
        List<DraftSummary> content = jdbc.sql("""
                SELECT d.id,d.academic_year_id,d.name,d.workflow_status,d.version,d.updated_at,
                       d.effective_from_intent,d.effective_to_exclusive_intent,
                       gp.job_id,gp.proposal_index
                FROM timetable_drafts d
                LEFT JOIN LATERAL (
                    SELECT job_id,proposal_index FROM timetable_generation_proposals candidate
                    WHERE candidate.organization_id=d.organization_id
                      AND candidate.academic_year_id=d.academic_year_id AND candidate.draft_id=d.id
                    ORDER BY candidate.created_at,candidate.id LIMIT 1
                ) gp ON TRUE
                WHERE d.organization_id=:org AND d.academic_year_id=:year
                  AND (CAST(:status AS VARCHAR) IS NULL OR d.workflow_status=:status)
                ORDER BY d.updated_at DESC,d.id DESC LIMIT :size OFFSET :offset
                """).param("org", org).param("year", year)
                .param("status", status == null ? null : status.name()).param("size", size)
                .param("offset", page * size).query((rs,n) -> new DraftSummary(
                        rs.getLong("id"), rs.getLong("academic_year_id"), rs.getString("name"),
                        WorkflowStatus.valueOf(rs.getString("workflow_status")),
                        rs.getObject("job_id") == null ? DraftSource.MANUAL : DraftSource.GENERATED,
                        (Long) rs.getObject("job_id"), (Integer) rs.getObject("proposal_index"),
                        rs.getLong("version"), rs.getObject("updated_at", OffsetDateTime.class),
                        rs.getObject("effective_from_intent", LocalDate.class),
                        rs.getObject("effective_to_exclusive_intent", LocalDate.class))).list();
        return new DraftPage(content, page, size, total, (int) ((total + size - 1) / size));
    }

    @Transactional
    public DraftResponse updateIntent(long org, long actor, long draftId, EffectiveDateIntentRequest request) {
        DraftHead draft = head(org, draftId, true);
        requireValidityMutable(draft);
        requireDraftVersion(draft, request.expectedVersion());
        if (request.effectiveToExclusive() != null && (request.effectiveFrom() == null
                || !request.effectiveToExclusive().isAfter(request.effectiveFrom()))) {
            throw new InvalidTimetableConfigurationException("Effective end must be after effective start");
        }
        int changed = jdbc.sql("""
                UPDATE timetable_drafts SET effective_from_intent=:from,effective_to_exclusive_intent=:to,
                    version=version+1,updated_by_user_id=:actor,updated_at=CURRENT_TIMESTAMP
                WHERE id=:id AND organization_id=:org AND version=:version
                """).param("from", request.effectiveFrom()).param("to", request.effectiveToExclusive())
                .param("actor", actor).param("id", draftId).param("org", org)
                .param("version", request.expectedVersion()).update();
        if (changed != 1) throw new TimetableConfigurationConflictException("Draft was modified; reload and retry");
        audit(org, draft.year(), draftId, actor, "UPDATE_INTENT", null);
        return get(org, draftId);
    }

    @Transactional(readOnly = true)
    public DraftHistory history(long org, long draftId) {
        DraftHead draft = head(org, draftId, false);
        List<EditAuditEvent> edits = jdbc.sql("""
                SELECT id,action,slot_id,actor_user_id,created_at FROM timetable_draft_audit
                WHERE organization_id=:org AND academic_year_id=:year AND draft_id=:draft
                ORDER BY created_at,id
                """).param("org", org).param("year", draft.year()).param("draft", draftId)
                .query((rs,n) -> new EditAuditEvent(rs.getLong("id"), rs.getString("action"),
                        (Long) rs.getObject("slot_id"), rs.getLong("actor_user_id"),
                        rs.getObject("created_at", OffsetDateTime.class))).list();
        var workflow = jdbc.sql("""
                SELECT id,from_status,to_status,reason,actor_user_id,occurred_at
                FROM timetable_workflow_events WHERE organization_id=:org AND academic_year_id=:year
                  AND draft_id=:draft ORDER BY occurred_at,id
                """).param("org", org).param("year", draft.year()).param("draft", draftId)
                .query((rs,n) -> new com.dawnrise.academic.timetable.dto.TimetableWorkflowDtos.WorkflowEvent(
                        rs.getLong("id"), WorkflowStatus.valueOf(rs.getString("from_status")),
                        WorkflowStatus.valueOf(rs.getString("to_status")), rs.getString("reason"),
                        rs.getLong("actor_user_id"), rs.getObject("occurred_at", OffsetDateTime.class))).list();
        return new DraftHistory(edits, workflow);
    }

    @Transactional
    public DraftResponse create(long org, long actor, CreateDraftRequest request) {
        requireYear(org, request.academicYearId());
        long id;
        try {
            id = jdbc.sql("""
                    INSERT INTO timetable_drafts
                    (organization_id,academic_year_id,name,created_by_user_id,updated_by_user_id)
                    VALUES (:org,:year,:name,:actor,:actor) RETURNING id
                    """).param("org", org).param("year", request.academicYearId())
                    .param("name", request.name().trim()).param("actor", actor).query(Long.class).single();
        } catch (DataIntegrityViolationException ex) {
            throw new TimetableConfigurationConflictException("A draft with this name already exists for the academic year");
        }
        audit(org, request.academicYearId(), id, actor, "CREATE_DRAFT", null);
        return get(org, id);
    }

    @Transactional(readOnly = true)
    public DraftResponse get(long org, long draftId) {
        DraftHead head = head(org, draftId, false);
        return new DraftResponse(head.id(), head.year(), head.name(), head.status(), head.version(), head.createdBy(), head.updatedBy(),
                head.createdAt(), head.updatedAt(), slots(org, head.year(), draftId, null));
    }

    @Transactional(readOnly = true)
    public SectionDraftResponse section(long org, long draftId, long sectionId) {
        DraftHead head = head(org, draftId, false);
        ResolvedSectionSchedule resolved = configurationService.resolvedSchedule(org, head.year(), sectionId);
        return new SectionDraftResponse(draftId, head.year(), sectionId, head.version(),
                resolved.bellSchedule().days(), slots(org, head.year(), draftId, sectionId));
    }

    @Transactional(readOnly = true)
    public ConflictPreview preview(long org, long draftId, Long excludedSlotId, LessonRequest proposed) {
        DraftHead head = head(org, draftId, false);
        requireDraftVersion(head, proposed.expectedDraftVersion());
        if (head.year() != proposedYear(org, proposed.sectionId()))
            throw new InvalidTimetableConfigurationException("Section does not belong to the draft academic year");
        Candidate candidate = candidate(org, head.year(), proposed);
        List<Conflict> conflicts = validateCandidate(org, draftId, excludedSlotId, candidate);
        return new ConflictPreview(conflicts.stream().noneMatch(c -> c.severity() == ConflictSeverity.ERROR),
                List.copyOf(conflicts), suggestions(org, draftId, excludedSlotId, candidate, conflicts));
    }

    @Transactional
    public SlotResponse add(long org, long actor, long draftId, LessonRequest request) {
        DraftHead draft = head(org, draftId, true);
        requireMutable(draft);
        requireDraftVersion(draft, request.expectedDraftVersion());
        Candidate candidate = candidate(org, draft.year(), request);
        rejectErrors(validateCandidate(org, draftId, null, candidate));
        long id;
        try {
            id = insertSlot(org, draftId, draft.year(), candidate);
            bumpDraft(org, actor, draftId, request.expectedDraftVersion());
        } catch (DataIntegrityViolationException ex) {
            throw new TimetableConfigurationConflictException("The lesson conflicts with a concurrent section, teacher, room, or bell-time edit");
        }
        audit(org, draft.year(), draftId, actor, "ADD", id);
        return slot(org, draft.year(), draftId, id);
    }

    @Transactional
    public DraftResponse addMany(long org,long actor,long draftId,BulkLessonRequest request) {
        DraftHead draft=head(org,draftId,true);requireMutable(draft);requireDraftVersion(draft,request.expectedDraftVersion());
        long version=request.expectedDraftVersion();
        for(LessonRequest lesson:request.lessons()){
            add(org,actor,draftId,new LessonRequest(lesson.sectionId(),lesson.bellScheduleIntervalId(),lesson.activityType(),
                    lesson.activityName(),lesson.gradeLevelSubjectId(),lesson.teacherUserId(),lesson.roomId(),version++));
        }
        return get(org,draftId);
    }

    @Transactional
    public SlotResponse move(long org, long actor, long draftId, long slotId, MoveLessonRequest request) {
        DraftHead draft = head(org, draftId, true);
        requireMutable(draft);
        requireDraftVersion(draft, request.expectedDraftVersion());
        SlotResponse current = lockedSlot(org, draft.year(), draftId, slotId);
        requireSlotVersion(current, request.expectedSlotVersion());
        ensureNotPinned(org, draft.year(), draftId, slotId);
        LessonRequest proposed = new LessonRequest(request.sectionId(), request.bellScheduleIntervalId(),
                current.activityType(), current.activityName(), current.gradeLevelSubjectId(),
                request.teacherUserId(), request.roomId(), request.expectedDraftVersion());
        Candidate candidate = candidate(org, draft.year(), proposed);
        rejectErrors(validateCandidate(org, draftId, slotId, candidate));
        try {
            updateSlot(org, draft.year(), draftId, slotId, candidate, request.expectedSlotVersion());
            bumpDraft(org, actor, draftId, request.expectedDraftVersion());
        } catch (DataIntegrityViolationException ex) {
            throw new TimetableConfigurationConflictException("The move conflicts with a concurrent section, teacher, room, or bell-time edit");
        }
        audit(org, draft.year(), draftId, actor, "MOVE", slotId);
        return slot(org, draft.year(), draftId, slotId);
    }

    @Transactional
    public DraftResponse swap(long org, long actor, long draftId, long firstId, SwapLessonsRequest request) {
        DraftHead draft = head(org, draftId, true);
        requireMutable(draft);
        requireDraftVersion(draft, request.expectedDraftVersion());
        List<Long> ids = new ArrayList<>(List.of(firstId, request.otherSlotId())); ids.sort(Long::compare);
        SlotResponse firstLocked = lockedSlot(org, draft.year(), draftId, ids.get(0));
        SlotResponse secondLocked = lockedSlot(org, draft.year(), draftId, ids.get(1));
        SlotResponse first = firstLocked.id() == firstId ? firstLocked : secondLocked;
        SlotResponse second = firstLocked.id() == firstId ? secondLocked : firstLocked;
        requireSlotVersion(first, request.expectedSlotVersion());
        requireSlotVersion(second, request.expectedOtherSlotVersion());
        ensureNotPinned(org, draft.year(), draftId, first.id()); ensureNotPinned(org, draft.year(), draftId, second.id());
        LessonRequest firstTarget = new LessonRequest(second.sectionId(), second.bellScheduleIntervalId(), first.activityType(),
                first.activityName(), first.gradeLevelSubjectId(), first.teacherUserId(), first.roomId(), request.expectedDraftVersion());
        LessonRequest secondTarget = new LessonRequest(first.sectionId(), first.bellScheduleIntervalId(), second.activityType(),
                second.activityName(), second.gradeLevelSubjectId(), second.teacherUserId(), second.roomId(), request.expectedDraftVersion());
        Candidate c1 = candidate(org, draft.year(), firstTarget); Candidate c2 = candidate(org, draft.year(), secondTarget);
        try {
            jdbc.sql("""
                    SET CONSTRAINTS uk_timetable_draft_section_interval,
                        ex_timetable_draft_section_overlap, ex_timetable_draft_teacher_overlap,
                        ex_timetable_draft_exclusive_room_overlap DEFERRED
                    """).update();
            updateSlot(org,draft.year(),draftId,first.id(),c1,request.expectedSlotVersion());
            updateSlot(org,draft.year(),draftId,second.id(),c2,request.expectedOtherSlotVersion());
            List<Conflict> conflicts = new ArrayList<>(validateCandidate(org, draftId, first.id(), c1));
            conflicts.addAll(validateCandidate(org, draftId, second.id(), c2));
            rejectErrors(conflicts);
        } catch (DataIntegrityViolationException ex) {
            throw new TimetableConfigurationConflictException("The swap conflicts with a concurrent section, teacher, room, or bell-time edit");
        }
        bumpDraft(org, actor, draftId, request.expectedDraftVersion());
        audit(org, draft.year(), draftId, actor, "SWAP", first.id()); audit(org, draft.year(), draftId, actor, "SWAP", second.id());
        return get(org, draftId);
    }

    public ConflictPreview previewSwap(long org, long draftId, long firstId, SwapLessonsRequest request) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> {
        DraftHead draft = head(org, draftId, true);
        requireMutable(draft);
        requireDraftVersion(draft, request.expectedDraftVersion());
        List<Long> ids = new ArrayList<>(List.of(firstId, request.otherSlotId()));
        ids.sort(Long::compare);
        SlotResponse firstLocked = lockedSlot(org, draft.year(), draftId, ids.get(0));
        SlotResponse secondLocked = lockedSlot(org, draft.year(), draftId, ids.get(1));
        SlotResponse first = firstLocked.id() == firstId ? firstLocked : secondLocked;
        SlotResponse second = firstLocked.id() == firstId ? secondLocked : firstLocked;
        requireSlotVersion(first, request.expectedSlotVersion());
        requireSlotVersion(second, request.expectedOtherSlotVersion());
        ensureNotPinned(org, draft.year(), draftId, first.id());
        ensureNotPinned(org, draft.year(), draftId, second.id());
        Candidate firstCandidate = candidate(org, draft.year(), new LessonRequest(second.sectionId(),
                second.bellScheduleIntervalId(), first.activityType(), first.activityName(),
                first.gradeLevelSubjectId(), first.teacherUserId(), first.roomId(), request.expectedDraftVersion()));
        Candidate secondCandidate = candidate(org, draft.year(), new LessonRequest(first.sectionId(),
                first.bellScheduleIntervalId(), second.activityType(), second.activityName(),
                second.gradeLevelSubjectId(), second.teacherUserId(), second.roomId(), request.expectedDraftVersion()));
        jdbc.sql("""
                SET CONSTRAINTS uk_timetable_draft_section_interval,
                    ex_timetable_draft_section_overlap, ex_timetable_draft_teacher_overlap,
                    ex_timetable_draft_exclusive_room_overlap DEFERRED
                """).update();
        updateSlot(org, draft.year(), draftId, first.id(), firstCandidate, request.expectedSlotVersion());
        updateSlot(org, draft.year(), draftId, second.id(), secondCandidate, request.expectedOtherSlotVersion());
        List<Conflict> conflicts = new ArrayList<>(validateCandidate(org, draftId, first.id(), firstCandidate));
        conflicts.addAll(validateCandidate(org, draftId, second.id(), secondCandidate));
        List<Suggestion> suggestions = new ArrayList<>(suggestions(org, draftId, first.id(), firstCandidate, conflicts));
        suggestions.addAll(suggestions(org, draftId, second.id(), secondCandidate, conflicts));
        status.setRollbackOnly();
        return new ConflictPreview(conflicts.stream().noneMatch(c -> c.severity() == ConflictSeverity.ERROR),
                List.copyOf(conflicts), List.copyOf(suggestions));
        });
    }

    @Transactional
    public void remove(long org, long actor, long draftId, long slotId, VersionRequest request) {
        DraftHead draft = head(org, draftId, true); requireMutable(draft); requireDraftVersion(draft, request.expectedDraftVersion());
        SlotResponse current = lockedSlot(org, draft.year(), draftId, slotId);
        requireSlotVersion(current, requiredSlotVersion(request)); ensureNotPinned(org, draft.year(), draftId, slotId);
        jdbc.sql("DELETE FROM timetable_draft_section_slots WHERE id=:id AND organization_id=:org AND academic_year_id=:year AND draft_id=:draft")
                .param("id", slotId).param("org", org).param("year", draft.year()).param("draft", draftId).update();
        bumpDraft(org, actor, draftId, request.expectedDraftVersion()); audit(org, draft.year(), draftId, actor, "REMOVE", slotId);
    }

    @Transactional
    public SlotResponse pin(long org, long actor, long draftId, long slotId, VersionRequest request, boolean pin) {
        DraftHead draft = head(org, draftId, true); requireMutable(draft); requireDraftVersion(draft, request.expectedDraftVersion());
        SlotResponse current = lockedSlot(org, draft.year(), draftId, slotId);
        requireSlotVersion(current, requiredSlotVersion(request));
        if (pin) jdbc.sql("""
                INSERT INTO timetable_draft_slot_pins (organization_id,academic_year_id,draft_id,slot_id,pinned_by_user_id)
                VALUES (:org,:year,:draft,:slot,:actor) ON CONFLICT DO NOTHING
                """).param("org",org).param("year",draft.year()).param("draft",draftId).param("slot",slotId).param("actor",actor).update();
        else jdbc.sql("DELETE FROM timetable_draft_slot_pins WHERE organization_id=:org AND academic_year_id=:year AND draft_id=:draft AND slot_id=:slot")
                .param("org",org).param("year",draft.year()).param("draft",draftId).param("slot",slotId).update();
        bumpDraft(org, actor, draftId, request.expectedDraftVersion()); audit(org,draft.year(),draftId,actor,pin?"PIN":"UNPIN",slotId);
        return slot(org,draft.year(),draftId,slotId);
    }

    @Transactional(readOnly = true)
    public ValidationReport validate(long org, long draftId) {
        DraftHead draft = head(org, draftId, false); List<Conflict> issues = new ArrayList<>();
        for (SlotResponse slot : slots(org, draft.year(), draftId, null)) {
            LessonRequest proposed = new LessonRequest(slot.sectionId(),slot.bellScheduleIntervalId(),slot.activityType(),
                    slot.activityName(),slot.gradeLevelSubjectId(),slot.teacherUserId(),slot.roomId(),draft.version());
            issues.addAll(validateCandidate(org,draftId,slot.id(),candidate(org,draft.year(),proposed)));
            if (slot.teacherUserId()==null)
                issues.add(conflict("MISSING_COVERAGE",ConflictSeverity.INCOMPLETE,
                        slot.activityType()==ActivityType.TEACHING
                                ? "Teaching slot requires an assigned teacher"
                                : "Supervised activity requires an assigned teacher or approved supervisor",
                        slot.id(),null,null,slot.sectionId(),slot.gradeLevelSubjectId()));
        }
        for (long sectionId : sectionIds(org,draft.year())) {
            ResolvedSectionSchedule schedule = configurationService.resolvedSchedule(org,draft.year(),sectionId);
            for (BellDayResponse day : schedule.bellSchedule().days()) for (BellIntervalResponse interval : day.intervals())
                if (interval.intervalType()!=IntervalType.TEACHING && requiresSectionEntry(interval)
                        && !slotExists(org,draft.year(),draftId,sectionId,day.dayOfWeek(),interval.id()))
                    issues.add(conflict("MISSING_ACTIVITY",ConflictSeverity.INCOMPLETE,
                            "Section-supervised period has no named activity or approved coverage",
                            null,null,null,sectionId,null));
        }
        addFrequencyCompleteness(org,draft,issues);
        boolean valid=issues.stream().noneMatch(i->i.severity()==ConflictSeverity.ERROR);
        boolean complete=valid&&issues.stream().noneMatch(i->i.severity()==ConflictSeverity.INCOMPLETE);
        return new ValidationReport(valid,complete,List.copyOf(issues));
    }

    private List<Conflict> validateCandidate(long org,long draftId,Long excluded,Candidate c) {
        List<Conflict> out=new ArrayList<>();
        clash(org,draftId,excluded,"section_id",c.section(),c,"SECTION_OVERLAP",out);
        if(c.teacher()!=null) clash(org,draftId,excluded,"teacher_user_id",c.teacher(),c,"TEACHER_OVERLAP",out);
        if(c.room()!=null&&c.roomExclusive()) clash(org,draftId,excluded,"room_id",c.room(),c,"ROOM_OVERLAP",out);
        if(c.type()==ActivityType.TEACHING&&c.teacher()!=null&&!teacherEligible(org,c,c.teacher()))
            out.add(conflict("TEACHER_NOT_ELIGIBLE",ConflictSeverity.ERROR,"Teacher is not committed or approved for this subject and section",null,c.teacher(),null,c.section(),c.subject()));
        if(c.type()==ActivityType.SUPERVISED_ACTIVITY&&c.teacher()!=null&&!supervisorEligible(org,c,c.teacher()))
            out.add(conflict("SUPERVISOR_NOT_ELIGIBLE",ConflictSeverity.ERROR,"Supervisor must be an assigned or approved teacher for this academic year",null,c.teacher(),null,c.section(),null));
        if(c.teacher()!=null) { validateAvailability(org,c,out); validateWorkload(org,draftId,excluded,c,out); }
        if(c.room()!=null) validateRoom(org,c,out);
        validateFrequency(org,draftId,excluded,c,out);
        return out;
    }

    private void clash(long org,long draftId,Long excluded,String column,long value,Candidate c,String code,List<Conflict> out) {
        List<ClashRow> clashes=jdbc.sql("""
                SELECT slot.id,slot.section_id,grade.name,section.name,slot.activity_name,
                       slot.teacher_user_id,slot.start_time,slot.end_time
                FROM timetable_draft_section_slots slot
                JOIN sections section ON section.id=slot.section_id AND section.organization_id=slot.organization_id
                JOIN grade_levels grade ON grade.id=slot.grade_level_id AND grade.organization_id=slot.organization_id
                WHERE slot.organization_id=:org AND slot.academic_year_id=:year AND slot.draft_id=:draft AND slot.%s=:value
                  AND slot.day_of_week=:day AND slot.start_time<:end AND slot.end_time>:start
                  AND (CAST(:excluded AS BIGINT) IS NULL OR slot.id<>:excluded)
                """.formatted(column))
                .param("org",org).param("year",c.year()).param("draft",draftId).param("value",value).param("day",c.day())
                .param("start",c.start()).param("end",c.end()).param("excluded",excluded)
                .query((rs,n)->new ClashRow(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getString(5),
                        (Long)rs.getObject(6),rs.getObject(7,LocalTime.class),rs.getObject(8,LocalTime.class))).list();
        for(ClashRow clash:clashes){
            String when=dayName(c.day())+" "+clash.start()+"–"+clash.end();
            String className=clash.gradeName()+" · "+clash.sectionName();
            String detail=switch(code){
                case "TEACHER_OVERLAP" -> when+": the selected teacher is already teaching "+className+" — "+clash.activity()+".";
                case "SECTION_OVERLAP" -> when+": "+className+" already has "+clash.activity()+(clash.teacher()==null?".":" with teacher #"+clash.teacher()+".");
                case "ROOM_OVERLAP" -> when+": this classroom is already being used by "+className+" — "+clash.activity()+".";
                default -> when+": the selected lesson conflicts with an existing lesson for "+className+".";
            };
            out.add(conflict(code,ConflictSeverity.ERROR,detail,clash.id(),code.startsWith("TEACHER")?c.teacher():null,
                    code.startsWith("ROOM")?c.room():null,c.section(),c.subject()));
        }
    }

    private String dayName(int day){return switch(day){case 1->"Monday";case 2->"Tuesday";case 3->"Wednesday";case 4->"Thursday";case 5->"Friday";case 6->"Saturday";case 7->"Sunday";default->"Day "+day;};}

    private void validateAvailability(long org,Candidate c,List<Conflict> out) {
        long unavailable=count("SELECT COUNT(*) FROM timetable_teacher_availability WHERE organization_id=:org AND academic_year_id=:year AND teacher_user_id=:teacher AND day_of_week=:day AND availability_type='UNAVAILABLE' AND start_time<:end AND end_time>:start",org,c);
        long availableRules=jdbc.sql("SELECT COUNT(*) FROM timetable_teacher_availability WHERE organization_id=:org AND academic_year_id=:year AND teacher_user_id=:teacher AND day_of_week=:day AND availability_type='AVAILABLE'")
                .param("org",org).param("year",c.year()).param("teacher",c.teacher()).param("day",c.day()).query(Long.class).single();
        long containing=count("SELECT COUNT(*) FROM timetable_teacher_availability WHERE organization_id=:org AND academic_year_id=:year AND teacher_user_id=:teacher AND day_of_week=:day AND availability_type='AVAILABLE' AND start_time<=:start AND end_time>=:end",org,c);
        if(unavailable>0||availableRules>0&&containing==0) out.add(conflict("TEACHER_UNAVAILABLE",ConflictSeverity.ERROR,"Teacher is unavailable for the actual interval",null,c.teacher(),null,c.section(),c.subject()));
        long discouraged=count("SELECT COUNT(*) FROM timetable_teacher_availability WHERE organization_id=:org AND academic_year_id=:year AND teacher_user_id=:teacher AND day_of_week=:day AND availability_type='DISCOURAGED' AND start_time<:end AND end_time>:start",org,c);
        if(discouraged>0) out.add(conflict("TEACHER_TIME_DISCOURAGED",ConflictSeverity.WARNING,"Teacher has marked this time as discouraged",null,c.teacher(),null,c.section(),c.subject()));
    }

    private void validateWorkload(long org,long draftId,Long excluded,Candidate c,List<Conflict> out) {
        Policy p=jdbc.sql("SELECT maximum_periods_per_day,maximum_periods_per_week,maximum_consecutive_periods,minimum_rest_minutes FROM timetable_teacher_policies WHERE organization_id=:org AND academic_year_id=:year AND teacher_user_id=:teacher")
                .param("org",org).param("year",c.year()).param("teacher",c.teacher()).query((rs,n)->new Policy(rs.getInt(1),rs.getInt(2),rs.getInt(3),rs.getInt(4))).optional().orElse(null);
        if(p==null)return;
        long weekly=jdbc.sql("SELECT COUNT(*) FROM timetable_draft_section_slots WHERE organization_id=:org AND academic_year_id=:year AND draft_id=:draft AND teacher_user_id=:teacher AND (CAST(:excluded AS BIGINT) IS NULL OR id<>:excluded)")
                .param("org",org).param("year",c.year()).param("draft",draftId).param("teacher",c.teacher()).param("excluded",excluded).query(Long.class).single()+1;
        long daily=jdbc.sql("SELECT COUNT(*) FROM timetable_draft_section_slots WHERE organization_id=:org AND academic_year_id=:year AND draft_id=:draft AND teacher_user_id=:teacher AND day_of_week=:day AND (CAST(:excluded AS BIGINT) IS NULL OR id<>:excluded)")
                .param("org",org).param("year",c.year()).param("draft",draftId).param("teacher",c.teacher()).param("day",c.day()).param("excluded",excluded).query(Long.class).single()+1;
        if(weekly>p.weekly()||daily>p.daily()) out.add(conflict("TEACHER_LOAD_EXCEEDED",ConflictSeverity.ERROR,"Teacher maximum daily or weekly load would be exceeded",null,c.teacher(),null,c.section(),c.subject()));
        List<TimeSpan> spans=jdbc.sql("SELECT start_time,end_time FROM timetable_draft_section_slots WHERE organization_id=:org AND academic_year_id=:year AND draft_id=:draft AND teacher_user_id=:teacher AND day_of_week=:day AND (CAST(:excluded AS BIGINT) IS NULL OR id<>:excluded) ORDER BY start_time")
                .param("org",org).param("year",c.year()).param("draft",draftId).param("teacher",c.teacher()).param("day",c.day()).param("excluded",excluded)
                .query((rs,n)->new TimeSpan(rs.getObject(1,LocalTime.class),rs.getObject(2,LocalTime.class))).list();
        spans=new ArrayList<>(spans);spans.add(new TimeSpan(c.start(),c.end()));spans.sort(Comparator.comparing(TimeSpan::start));
        int consecutive=1;
        for(int i=1;i<spans.size();i++){ long gap=Duration.between(spans.get(i-1).end(),spans.get(i).start()).toMinutes();
            boolean meaningfulBreak=p.rest()<=0?gap>10:gap>=p.rest();
            if(meaningfulBreak)consecutive=1;else consecutive++;
            if(consecutive>p.consecutive())out.add(conflict("TEACHER_CONSECUTIVE_LIMIT",ConflictSeverity.ERROR,"Teacher consecutive-period limit would be exceeded",null,c.teacher(),null,c.section(),c.subject())); }
    }

    private void validateRoom(long org,Candidate c,List<Conflict> out) {
        long active=jdbc.sql("SELECT COUNT(*) FROM timetable_rooms WHERE id=:room AND organization_id=:org AND active")
                .param("room",c.room()).param("org",org).query(Long.class).single();
        if(active==0){out.add(conflict("ROOM_UNAVAILABLE",ConflictSeverity.ERROR,"Room is inactive or unavailable",null,null,c.room(),c.section(),c.subject()));return;}
        if(c.subject()!=null){List<String> missing=jdbc.sql("SELECT capability_code FROM timetable_subject_room_requirements rr WHERE rr.organization_id=:org AND rr.academic_year_id=:year AND rr.grade_level_subject_id=:subject AND rr.required AND NOT EXISTS(SELECT 1 FROM timetable_room_capabilities rc WHERE rc.organization_id=rr.organization_id AND rc.room_id=:room AND rc.capability_code=rr.capability_code)")
                .param("org",org).param("year",c.year()).param("subject",c.subject()).param("room",c.room()).query(String.class).list();
            if(!missing.isEmpty())out.add(conflict("ROOM_CAPABILITY_MISSING",ConflictSeverity.ERROR,"Room lacks required capabilities: "+String.join(", ",missing),null,null,c.room(),c.section(),c.subject()));}
    }

    private void validateFrequency(long org,long draftId,Long excluded,Candidate c,List<Conflict> out){if(c.subject()==null)return;
        Requirement r=jdbc.sql("SELECT periods_per_week,maximum_periods_per_day,double_period_blocks_per_week,required_each_working_day FROM timetable_section_subject_requirements WHERE organization_id=:org AND academic_year_id=:year AND section_id=:section AND grade_level_subject_id=:subject")
                .param("org",org).param("year",c.year()).param("section",c.section()).param("subject",c.subject()).query((rs,n)->new Requirement(rs.getInt(1),rs.getInt(2),rs.getInt(3),rs.getBoolean(4))).optional().orElse(null);
        if(r==null){out.add(conflict("SUBJECT_REQUIREMENT_MISSING",ConflictSeverity.ERROR,"No subject period requirement is configured",null,c.teacher(),c.room(),c.section(),c.subject()));return;}
        long weekly=subjectCount(org,draftId,excluded,c,false)+1,daily=subjectCount(org,draftId,excluded,c,true)+1;
        if(weekly>r.weekly()||daily>r.daily())out.add(conflict("SUBJECT_FREQUENCY_EXCEEDED",ConflictSeverity.ERROR,"Subject weekly or daily frequency would be exceeded",null,c.teacher(),c.room(),c.section(),c.subject()));
        List<SubjectSpan> spans=subjectSpans(org,draftId,excluded,c);
        spans.add(new SubjectSpan(c.day(),c.start(),c.end()));
        if(doubleBlocks(spans)>r.doubles())out.add(conflict("DOUBLE_PERIOD_COUNT_EXCEEDED",ConflictSeverity.ERROR,
                "The configured number of explicit double-period blocks would be exceeded",null,c.teacher(),c.room(),c.section(),c.subject()));
    }

    private List<Suggestion> suggestions(long org,long draftId,Long excluded,Candidate c,List<Conflict> conflicts){List<Suggestion>s=new ArrayList<>();
        if(conflicts.stream().anyMatch(x->x.code().startsWith("TEACHER")||x.code().startsWith("SUPERVISOR"))){
            Set<Long> teachers=c.subject()!=null
                    ?new LinkedHashSet<>(jdbc.sql("SELECT teacher_user_id FROM teacher_assignments WHERE organization_id=:org AND academic_year_id=:year AND section_id=:section AND grade_level_subject_id=:subject AND assignment_type='SUBJECT_TEACHER' UNION SELECT teacher_user_id FROM timetable_alternate_teacher_eligibilities WHERE organization_id=:org AND academic_year_id=:year AND grade_level_subject_id=:subject AND active AND (section_id IS NULL OR section_id=:section) AND (wing_id IS NULL OR wing_id=:wing)")
                    .param("org",org).param("year",c.year()).param("section",c.section()).param("subject",c.subject()).param("wing",c.wing()).query(Long.class).list())
                    :new LinkedHashSet<>(jdbc.sql("SELECT DISTINCT teacher_user_id FROM teacher_assignments WHERE organization_id=:org AND academic_year_id=:year ORDER BY teacher_user_id LIMIT 30").param("org",org).param("year",c.year()).query(Long.class).list());
            for(Long id:teachers){Candidate alternative=new Candidate(c.year(),c.section(),c.grade(),c.wing(),c.interval(),c.day(),c.start(),c.end(),c.type(),c.name(),c.subject(),id,c.room(),c.roomExclusive());
                if(validateCandidate(org,draftId,excluded,alternative).stream().noneMatch(x->x.severity()==ConflictSeverity.ERROR))s.add(new Suggestion("TEACHER",id,c.subject()==null?"Assigned teacher eligible to supervise":"Eligible available teacher",null,null));
                if(s.stream().filter(x->x.type().equals("TEACHER")).count()>=10)break;}
        }
        if(conflicts.stream().anyMatch(x->x.code().startsWith("ROOM"))){for(Room room:jdbc.sql("SELECT id,exclusive_use FROM timetable_rooms WHERE organization_id=:org AND active ORDER BY code LIMIT 30").param("org",org).query((rs,n)->new Room(rs.getLong(1),rs.getBoolean(2))).list()){
            Candidate roomCandidate=new Candidate(c.year(),c.section(),c.grade(),c.wing(),c.interval(),c.day(),c.start(),c.end(),c.type(),c.name(),c.subject(),c.teacher(),room.id(),room.exclusive());
            List<Conflict> roomIssues=new ArrayList<>();validateRoom(org,roomCandidate,roomIssues);
            if(room.exclusive())clash(org,draftId,excluded,"room_id",room.id(),roomCandidate,"ROOM_OVERLAP",roomIssues);
            if(roomIssues.stream().noneMatch(x->x.severity()==ConflictSeverity.ERROR))s.add(new Suggestion("ROOM",room.id(),"Compatible available room",null,null));
            if(s.size()>=10)break;
        }}
        if(conflicts.stream().anyMatch(x->Set.of("SECTION_OVERLAP","TEACHER_OVERLAP","ROOM_OVERLAP","TEACHER_UNAVAILABLE","TEACHER_REST_VIOLATION").contains(x.code()))){
            ResolvedSectionSchedule schedule=configurationService.resolvedSchedule(org,c.year(),c.section());
            for(BellDayResponse day:schedule.bellSchedule().days())for(BellIntervalResponse interval:day.intervals()){
                if(interval.intervalType()!=IntervalType.valueOf(c.type().name())||interval.id()==c.interval())continue;
                Candidate alternative=new Candidate(c.year(),c.section(),c.grade(),c.wing(),interval.id(),day.dayOfWeek(),interval.startTime(),interval.endTime(),c.type(),c.name(),c.subject(),c.teacher(),c.room(),c.roomExclusive());
                if(validateCandidate(org,draftId,excluded,alternative).stream().noneMatch(x->x.severity()==ConflictSeverity.ERROR))
                    s.add(new Suggestion("SLOT",interval.id(),"Valid alternative time",day.dayOfWeek(),interval.id()));
                if(s.stream().filter(x->x.type().equals("SLOT")).count()>=10)return List.copyOf(s);
            }
        }
        return List.copyOf(s);}

    private Candidate candidate(long org,long year,LessonRequest r){ResolvedSectionSchedule resolved=configurationService.resolvedSchedule(org,year,r.sectionId());
        BellDayResponse foundDay=null;BellIntervalResponse found=null;for(BellDayResponse d:resolved.bellSchedule().days())for(BellIntervalResponse i:d.intervals())if(i.id()==r.bellScheduleIntervalId()){foundDay=d;found=i;}
        if(found==null)throw new InvalidTimetableConfigurationException("Bell interval is not part of the section's resolved schedule");
        if(found.intervalType()==IntervalType.RECESS||found.intervalType()==IntervalType.TRANSITION||found.intervalType()==IntervalType.ASSEMBLY||found.intervalType()==IntervalType.FIXED_NON_STUDENT)
            throw new InvalidTimetableConfigurationException("Lessons cannot be placed in assembly, recess, transition, or fixed intervals");
        if(r.activityType()==ActivityType.TEACHING&&found.intervalType()!=IntervalType.TEACHING||r.activityType()==ActivityType.SUPERVISED_ACTIVITY&&found.intervalType()!=IntervalType.SUPERVISED_ACTIVITY)
            throw new InvalidTimetableConfigurationException("Activity type does not match the resolved bell interval");
        if(r.activityType()==ActivityType.TEACHING&&r.gradeLevelSubjectId()==null)throw new InvalidTimetableConfigurationException("Teaching lessons require a grade subject mapping");
        Room room=r.roomId()==null?jdbc.sql("""
                SELECT room.id,room.exclusive_use FROM timetable_section_home_rooms home
                JOIN timetable_rooms room ON room.id=home.room_id AND room.organization_id=home.organization_id
                WHERE home.organization_id=:org AND home.academic_year_id=:year AND home.section_id=:section AND room.active
                """).param("org",org).param("year",year).param("section",r.sectionId())
                .query((rs,n)->new Room(rs.getLong(1),rs.getBoolean(2))).optional().orElse(new Room(null,false))
                :jdbc.sql("SELECT id,exclusive_use FROM timetable_rooms WHERE id=:id AND organization_id=:org").param("id",r.roomId()).param("org",org).query((rs,n)->new Room(rs.getLong(1),rs.getBoolean(2))).optional().orElseThrow(()->new TimetableConfigurationNotFoundException("Room not found"));
        return new Candidate(year,r.sectionId(),resolved.gradeLevelId(),resolved.wingId(),r.bellScheduleIntervalId(),foundDay.dayOfWeek(),found.startTime(),found.endTime(),r.activityType(),r.activityName().trim(),r.gradeLevelSubjectId(),r.teacherUserId(),room.id(),room.exclusive());}

    private boolean teacherEligible(long org,Candidate c,long teacher){long count=jdbc.sql("SELECT COUNT(*) FROM teacher_assignments WHERE organization_id=:org AND academic_year_id=:year AND section_id=:section AND grade_level_subject_id=:subject AND teacher_user_id=:teacher AND assignment_type='SUBJECT_TEACHER'")
            .param("org",org).param("year",c.year()).param("section",c.section()).param("subject",c.subject()).param("teacher",teacher).query(Long.class).single();if(count>0)return true;
        return jdbc.sql("SELECT COUNT(*) FROM timetable_alternate_teacher_eligibilities WHERE organization_id=:org AND academic_year_id=:year AND grade_level_subject_id=:subject AND teacher_user_id=:teacher AND active AND (section_id IS NULL OR section_id=:section) AND (wing_id IS NULL OR wing_id=:wing) AND (valid_from IS NULL OR valid_from<=CURRENT_DATE) AND (valid_to IS NULL OR valid_to>=CURRENT_DATE)")
                .param("org",org).param("year",c.year()).param("subject",c.subject()).param("teacher",teacher).param("section",c.section()).param("wing",c.wing()).query(Long.class).single()>0;}
    private boolean supervisorEligible(long org,Candidate c,long teacher){return jdbc.sql("SELECT COUNT(*) FROM teacher_assignments WHERE organization_id=:org AND academic_year_id=:year AND teacher_user_id=:teacher")
            .param("org",org).param("year",c.year()).param("teacher",teacher).query(Long.class).single()>0||
            jdbc.sql("SELECT COUNT(*) FROM timetable_alternate_teacher_eligibilities WHERE organization_id=:org AND academic_year_id=:year AND teacher_user_id=:teacher AND active AND (valid_from IS NULL OR valid_from<=CURRENT_DATE) AND (valid_to IS NULL OR valid_to>=CURRENT_DATE)")
                    .param("org",org).param("year",c.year()).param("teacher",teacher).query(Long.class).single()>0;}

    private long insertSlot(long org,long draft,long year,Candidate c){return jdbc.sql("INSERT INTO timetable_draft_section_slots(organization_id,academic_year_id,draft_id,grade_level_id,section_id,wing_id,bell_schedule_interval_id,day_of_week,start_time,end_time,activity_type,activity_name,grade_level_subject_id,teacher_user_id,room_id,room_exclusive) VALUES(:org,:year,:draft,:grade,:section,:wing,:interval,:day,:start,:end,:type,:name,:subject,:teacher,:room,:exclusive) RETURNING id")
            .param("org",org).param("year",year).param("draft",draft).param("grade",c.grade()).param("section",c.section()).param("wing",c.wing()).param("interval",c.interval()).param("day",c.day()).param("start",c.start()).param("end",c.end()).param("type",c.type().name()).param("name",c.name()).param("subject",c.subject()).param("teacher",c.teacher()).param("room",c.room()).param("exclusive",c.roomExclusive()).query(Long.class).single();}
    private void updateSlot(long org,long year,long draft,long id,Candidate c,long version){int n=jdbc.sql("UPDATE timetable_draft_section_slots SET grade_level_id=:grade,section_id=:section,wing_id=:wing,bell_schedule_interval_id=:interval,day_of_week=:day,start_time=:start,end_time=:end,teacher_user_id=:teacher,room_id=:room,room_exclusive=:exclusive,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND organization_id=:org AND academic_year_id=:year AND draft_id=:draft AND version=:version")
            .param("grade",c.grade()).param("section",c.section()).param("wing",c.wing()).param("interval",c.interval()).param("day",c.day()).param("start",c.start()).param("end",c.end()).param("teacher",c.teacher()).param("room",c.room()).param("exclusive",c.roomExclusive()).param("id",id).param("org",org).param("year",year).param("draft",draft).param("version",version).update();if(n!=1)throw stale("Lesson");}
    private void bumpDraft(long org,long actor,long id,long version){int n=jdbc.sql("UPDATE timetable_drafts SET version=version+1,updated_by_user_id=:actor,updated_at=CURRENT_TIMESTAMP WHERE id=:id AND organization_id=:org AND version=:version").param("actor",actor).param("id",id).param("org",org).param("version",version).update();if(n!=1)throw stale("Draft");}

    private List<SlotResponse> slots(long org,long year,long draft,Long section){return jdbc.sql("SELECT s.*,EXISTS(SELECT 1 FROM timetable_draft_slot_pins p WHERE p.organization_id=s.organization_id AND p.academic_year_id=s.academic_year_id AND p.draft_id=s.draft_id AND p.slot_id=s.id) pinned FROM timetable_draft_section_slots s WHERE s.organization_id=:org AND s.academic_year_id=:year AND s.draft_id=:draft AND (CAST(:section AS BIGINT) IS NULL OR s.section_id=:section) ORDER BY s.day_of_week,s.start_time,s.section_id,s.id")
            .param("org",org).param("year",year).param("draft",draft).param("section",section).query((rs,n)->mapSlot(rs)).list();}
    private SlotResponse slot(long org,long year,long draft,long id){return slots(org,year,draft,null).stream().filter(s->s.id()==id).findFirst().orElseThrow(()->new TimetableConfigurationNotFoundException("Lesson not found"));}
    private SlotResponse lockedSlot(long org,long year,long draft,long id){jdbc.sql("SELECT id FROM timetable_draft_section_slots WHERE id=:id AND organization_id=:org AND academic_year_id=:year AND draft_id=:draft FOR UPDATE").param("id",id).param("org",org).param("year",year).param("draft",draft).query(Long.class).optional().orElseThrow(()->new TimetableConfigurationNotFoundException("Lesson not found"));return slot(org,year,draft,id);}
    private SlotResponse mapSlot(java.sql.ResultSet rs)throws java.sql.SQLException{return new SlotResponse(rs.getLong("id"),rs.getLong("section_id"),rs.getLong("grade_level_id"),rs.getLong("wing_id"),rs.getLong("bell_schedule_interval_id"),rs.getInt("day_of_week"),rs.getObject("start_time",LocalTime.class),rs.getObject("end_time",LocalTime.class),ActivityType.valueOf(rs.getString("activity_type")),rs.getString("activity_name"),(Long)rs.getObject("grade_level_subject_id"),(Long)rs.getObject("teacher_user_id"),(Long)rs.getObject("room_id"),rs.getBoolean("pinned"),rs.getLong("version"));}
    private DraftHead head(long org,long id,boolean lock){return jdbc.sql("SELECT * FROM timetable_drafts WHERE id=:id AND organization_id=:org"+(lock?" FOR UPDATE":"")).param("id",id).param("org",org).query((rs,n)->new DraftHead(rs.getLong("id"),rs.getLong("academic_year_id"),rs.getString("name"),WorkflowStatus.valueOf(rs.getString("workflow_status")),rs.getLong("version"),rs.getLong("created_by_user_id"),rs.getLong("updated_by_user_id"),rs.getObject("created_at",java.time.OffsetDateTime.class),rs.getObject("updated_at",java.time.OffsetDateTime.class))).optional().orElseThrow(()->new TimetableConfigurationNotFoundException("Timetable draft not found"));}
    private void audit(long org,long year,long draft,long actor,String action,Long slot){jdbc.sql("INSERT INTO timetable_draft_audit(organization_id,academic_year_id,draft_id,actor_user_id,action,slot_id)VALUES(:org,:year,:draft,:actor,:action,:slot)").param("org",org).param("year",year).param("draft",draft).param("actor",actor).param("action",action).param("slot",slot).update();}
    private void ensureNotPinned(long org,long year,long draft,long slot){if(jdbc.sql("SELECT COUNT(*) FROM timetable_draft_slot_pins WHERE organization_id=:org AND academic_year_id=:year AND draft_id=:draft AND slot_id=:slot").param("org",org).param("year",year).param("draft",draft).param("slot",slot).query(Long.class).single()>0)throw new TimetableConfigurationConflictException("Pinned lesson must be unpinned before editing");}
    private void requireDraftVersion(DraftHead d,long v){if(d.version()!=v)throw stale("Draft");} private void requireSlotVersion(SlotResponse s,long v){if(s.version()!=v)throw stale("Lesson");}
    private void requireMutable(DraftHead draft){if(draft.status()!=WorkflowStatus.DRAFT)throw new TimetableConfigurationConflictException("Only a DRAFT timetable can be edited");}
    private void requireValidityMutable(DraftHead draft){if(draft.status()==WorkflowStatus.PUBLISHED)throw new TimetableConfigurationConflictException("A published timetable's validity cannot be changed");}
    private boolean requiresSectionEntry(BellIntervalResponse interval){return interval.schedulable() &&
            (interval.intervalType()==IntervalType.TEACHING || interval.intervalType()==IntervalType.SUPERVISED_ACTIVITY) &&
            interval.coverageMode()==CoverageMode.SECTION && interval.assigneeRequirement()!=AssigneeRequirement.NONE;}
    private long requiredSlotVersion(VersionRequest r){if(r.expectedSlotVersion()==null)throw new InvalidTimetableConfigurationException("expectedSlotVersion is required");return r.expectedSlotVersion();}
    private TimetableConfigurationConflictException stale(String what){return new TimetableConfigurationConflictException(what+" was modified by another request; reload and retry");}
    private void rejectErrors(List<Conflict> c){if(c.stream().anyMatch(x->x.severity()==ConflictSeverity.ERROR))throw new TimetableConfigurationConflictException(c.stream().filter(x->x.severity()==ConflictSeverity.ERROR).findFirst().orElseThrow().message());}
    private long proposedYear(long org,long section){return jdbc.sql("SELECT academic_year_id FROM sections WHERE id=:id AND organization_id=:org").param("id",section).param("org",org).query(Long.class).optional().orElseThrow(()->new TimetableConfigurationNotFoundException("Section not found"));}
    private void requireYear(long org,long year){if(jdbc.sql("SELECT COUNT(*) FROM academic_years WHERE id=:year AND organization_id=:org").param("year",year).param("org",org).query(Long.class).single()==0)throw new TimetableConfigurationNotFoundException("Academic year not found");}
    private long count(String sql,long org,Candidate c){return jdbc.sql(sql).param("org",org).param("year",c.year()).param("teacher",c.teacher()).param("day",c.day()).param("start",c.start()).param("end",c.end()).query(Long.class).single();}
    private long subjectCount(long org,long draft,Long excluded,Candidate c,boolean daily){String sql="SELECT COUNT(*) FROM timetable_draft_section_slots WHERE organization_id=:org AND academic_year_id=:year AND draft_id=:draft AND section_id=:section AND grade_level_subject_id=:subject AND (CAST(:excluded AS BIGINT) IS NULL OR id<>:excluded)"+(daily?" AND day_of_week=:day":"");var statement=jdbc.sql(sql).param("org",org).param("year",c.year()).param("draft",draft).param("section",c.section()).param("subject",c.subject()).param("excluded",excluded);if(daily)statement=statement.param("day",c.day());return statement.query(Long.class).single();}
    private boolean slotExists(long org,long year,long draft,long section,int day,long interval){return jdbc.sql("SELECT COUNT(*) FROM timetable_draft_section_slots WHERE organization_id=:org AND academic_year_id=:year AND draft_id=:draft AND section_id=:section AND day_of_week=:day AND bell_schedule_interval_id=:interval").param("org",org).param("year",year).param("draft",draft).param("section",section).param("day",day).param("interval",interval).query(Long.class).single()>0;}
    private List<Long> sectionIds(long org,long year){return jdbc.sql("SELECT id FROM sections WHERE organization_id=:org AND academic_year_id=:year ORDER BY id").param("org",org).param("year",year).query(Long.class).list();}
    private void addFrequencyCompleteness(long org,DraftHead d,List<Conflict> out){
        List<RequirementRow> requirements=jdbc.sql("SELECT section_id,grade_level_subject_id,periods_per_week,required_each_working_day,double_period_blocks_per_week FROM timetable_section_subject_requirements WHERE organization_id=:org AND academic_year_id=:year")
                .param("org",org).param("year",d.year()).query((rs,n)->new RequirementRow(rs.getLong(1),rs.getLong(2),rs.getInt(3),rs.getBoolean(4),rs.getInt(5))).list();
        for(RequirementRow r:requirements){
            List<SubjectSpan> spans=jdbc.sql("SELECT day_of_week,start_time,end_time FROM timetable_draft_section_slots WHERE organization_id=:org AND academic_year_id=:year AND draft_id=:draft AND section_id=:section AND grade_level_subject_id=:subject ORDER BY day_of_week,start_time")
                    .param("org",org).param("year",d.year()).param("draft",d.id()).param("section",r.section()).param("subject",r.subject())
                    .query((rs,n)->new SubjectSpan(rs.getInt(1),rs.getObject(2,LocalTime.class),rs.getObject(3,LocalTime.class))).list();
            if(spans.size()!=r.weekly())out.add(conflict("SUBJECT_FREQUENCY_INCOMPLETE",ConflictSeverity.INCOMPLETE,"Subject requires "+r.weekly()+" periods but has "+spans.size(),null,null,null,r.section(),r.subject()));
            if(r.requiredDaily()){
                ResolvedSectionSchedule resolved=configurationService.resolvedSchedule(org,d.year(),r.section());
                for(BellDayResponse day:resolved.bellSchedule().days())if(day.workingDay()&&spans.stream().noneMatch(x->x.day()==day.dayOfWeek()))
                    out.add(conflict("SUBJECT_DAILY_REQUIREMENT_INCOMPLETE",ConflictSeverity.INCOMPLETE,"Subject is required on working day "+day.dayOfWeek(),null,null,null,r.section(),r.subject()));
            }
            int actualDoubles=doubleBlocks(spans);
            if(actualDoubles!=r.doubles())out.add(conflict("DOUBLE_PERIOD_REQUIREMENT_INCOMPLETE",ConflictSeverity.INCOMPLETE,"Subject requires "+r.doubles()+" explicit double-period blocks but has "+actualDoubles,null,null,null,r.section(),r.subject()));
        }
    }
    private List<SubjectSpan> subjectSpans(long org,long draft,Long excluded,Candidate c){return new ArrayList<>(jdbc.sql("SELECT day_of_week,start_time,end_time FROM timetable_draft_section_slots WHERE organization_id=:org AND academic_year_id=:year AND draft_id=:draft AND section_id=:section AND grade_level_subject_id=:subject AND (CAST(:excluded AS BIGINT) IS NULL OR id<>:excluded) ORDER BY day_of_week,start_time")
            .param("org",org).param("year",c.year()).param("draft",draft).param("section",c.section()).param("subject",c.subject()).param("excluded",excluded)
            .query((rs,n)->new SubjectSpan(rs.getInt(1),rs.getObject(2,LocalTime.class),rs.getObject(3,LocalTime.class))).list());}
    private int doubleBlocks(List<SubjectSpan> source){List<SubjectSpan> spans=new ArrayList<>(source);spans.sort(Comparator.comparingInt(SubjectSpan::day).thenComparing(SubjectSpan::start));int blocks=0,run=1;for(int i=1;i<spans.size();i++){SubjectSpan previous=spans.get(i-1),current=spans.get(i);long gap=Duration.between(previous.end(),current.start()).toMinutes();if(previous.day()==current.day()&&gap>=0&&gap<=10)run++;else{blocks+=run/2;run=1;}}return spans.isEmpty()?0:blocks+run/2;}
    private Conflict conflict(String code,ConflictSeverity severity,String message,Long slot,Long teacher,Long room,Long section,Long subject){return new Conflict(code,severity,message,slot,teacher,room,section,subject);}
    private record DraftHead(long id,long year,String name,WorkflowStatus status,long version,long createdBy,long updatedBy,java.time.OffsetDateTime createdAt,java.time.OffsetDateTime updatedAt){}
    private record Candidate(long year,long section,long grade,long wing,long interval,int day,LocalTime start,LocalTime end,ActivityType type,String name,Long subject,Long teacher,Long room,boolean roomExclusive){}
    private record Room(Long id,boolean exclusive){} private record Policy(int daily,int weekly,int consecutive,int rest){} private record Requirement(int weekly,int daily,int doubles,boolean requiredDaily){} private record TimeSpan(LocalTime start,LocalTime end){}
    private record ClashRow(long id,long section,String gradeName,String sectionName,String activity,Long teacher,LocalTime start,LocalTime end){}
    private record SubjectSpan(int day,LocalTime start,LocalTime end){} private record RequirementRow(long section,long subject,int weekly,boolean requiredDaily,int doubles){}
}
