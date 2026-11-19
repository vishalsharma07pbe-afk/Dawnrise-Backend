package com.dawnrise.academic.learning;

import com.dawnrise.academic.learning.LearningDtos.*;
import com.dawnrise.academic.results.integration.identity.IdentityGuardianAccessClient;
import com.dawnrise.academic.studentenrollment.integration.identity.IdentityStudentEligibilityClient;
import com.dawnrise.academic.studentenrollment.integration.identity.IdentityStudentEligibilityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

@Service
public class LearningService {
    private static final Logger log=LoggerFactory.getLogger(LearningService.class);
    private final JdbcClient db;
    private final IdentityGuardianAccessClient guardians;
    private final IdentityStudentEligibilityClient identityStudents;

    public LearningService(JdbcClient db, IdentityGuardianAccessClient guardians, IdentityStudentEligibilityClient identityStudents) {
        this.db = db;
        this.guardians = guardians;
        this.identityStudents = identityStudents;
    }

    public List<Scope> scopes(long organizationId, long actorId) {
        return db.sql("""
                SELECT ta.academic_year_id,ta.grade_level_id,ta.section_id,
                       ta.grade_level_subject_id,s.name,sub.name
                  FROM teacher_assignments ta
                  JOIN sections s ON s.id=ta.section_id
                  JOIN grade_level_subjects gls ON gls.id=ta.grade_level_subject_id
                  JOIN subjects sub ON sub.id=gls.subject_id
                 WHERE ta.organization_id=:org AND ta.teacher_user_id=:actor
                   AND ta.assignment_type='SUBJECT_TEACHER'
                 ORDER BY s.name,sub.name
                """).param("org", organizationId).param("actor", actorId)
                .query((rs, n) -> new Scope(rs.getLong(1), rs.getLong(2), rs.getLong(3),
                        rs.getLong(4), rs.getString(5), rs.getString(6))).list();
    }

    public List<TeacherHomeworkSummary> teacherHomework(long organizationId,long actorId) {
        List<HomeworkHead> posts=db.sql("""
                SELECT p.id,p.title,p.status,p.due_at,p.section_id,s.name,sub.name
                  FROM learning_posts p
                  JOIN sections s ON s.organization_id=p.organization_id AND s.id=p.section_id
                  JOIN grade_level_subjects gls ON gls.organization_id=p.organization_id
                       AND gls.id=p.grade_level_subject_id
                  JOIN subjects sub ON sub.organization_id=p.organization_id AND sub.id=gls.subject_id
                 WHERE p.organization_id=:org AND p.teacher_user_id=:actor
                   AND p.post_type='HOMEWORK' AND p.status='PUBLISHED'
                 ORDER BY p.due_at DESC,p.id DESC LIMIT 100
                """).param("org",organizationId).param("actor",actorId)
                .query((rs,n)->new HomeworkHead(rs.getLong(1),rs.getString(2),rs.getString(3),
                        rs.getObject(4,OffsetDateTime.class),rs.getLong(5),rs.getString(6),rs.getString(7))).list();
        Map<Long,List<ProgressRow>> rowsByPost=new LinkedHashMap<>();
        LinkedHashSet<Long> studentIds=new LinkedHashSet<>();
        for(HomeworkHead post:posts){
            List<ProgressRow> rows=db.sql("""
                    SELECT e.student_user_id,COALESCE(r.status,'IN_PROGRESS'),COUNT(t.id),
                           COUNT(sp.task_id) FILTER(WHERE sp.completed AND sp.task_revision=t.content_revision)
                      FROM student_enrollments e
                      LEFT JOIN learning_tasks t ON t.organization_id=e.organization_id AND t.post_id=:post
                      LEFT JOIN learning_student_progress sp ON sp.organization_id=e.organization_id
                           AND sp.post_id=:post AND sp.student_user_id=e.student_user_id AND sp.task_id=t.id
                      LEFT JOIN learning_reviews r ON r.organization_id=e.organization_id
                           AND r.post_id=:post AND r.student_user_id=e.student_user_id
                     WHERE e.organization_id=:org AND e.academic_year_id=(SELECT academic_year_id FROM learning_posts WHERE id=:post AND organization_id=:org)
                       AND e.section_id=:section AND e.status='ENROLLED'
                     GROUP BY e.student_user_id,r.status ORDER BY e.student_user_id
                    """).param("post",post.id()).param("org",organizationId).param("section",post.sectionId())
                    .query((rs,n)->new ProgressRow(rs.getLong(1),rs.getString(2),rs.getInt(4),rs.getInt(3))).list();
            rowsByPost.put(post.id(),rows);rows.forEach(row->studentIds.add(row.studentId()));
        }
        Map<Long,String> names=studentNames(organizationId,studentIds);
        return posts.stream().map(post->{
            List<StudentHomeworkProgress> students=rowsByPost.getOrDefault(post.id(),List.of()).stream().map(row->{
                boolean completed=row.totalTasks()>0&&row.completedTasks()>=row.totalTasks();
                String status=Set.of("READY_FOR_REVIEW","VERIFIED","CHANGES_REQUESTED").contains(row.reviewStatus())
                        ?row.reviewStatus():completed?"COMPLETED":"PENDING";
                return new StudentHomeworkProgress(row.studentId(),names.getOrDefault(row.studentId(),"Student #"+row.studentId()),status,row.completedTasks(),row.totalTasks());
            }).toList();
            int completed=(int)students.stream().filter(item->item.totalTasks()>0&&item.completedTasks()>=item.totalTasks()).count();
            int ready=(int)students.stream().filter(item->"READY_FOR_REVIEW".equals(item.status())).count();
            int verified=(int)students.stream().filter(item->"VERIFIED".equals(item.status())).count();
            int changes=(int)students.stream().filter(item->"CHANGES_REQUESTED".equals(item.status())).count();
            return new TeacherHomeworkSummary(post.id(),post.title(),post.status(),post.dueAt(),post.sectionId(),post.sectionName(),post.subjectName(),students.size(),completed,students.size()-completed,ready,verified,changes,students);
        }).toList();
    }

    @Transactional
    public ReminderResult remind(long organizationId,long actorId,long postId,ReminderRequest input) {
        requireOwner(organizationId,actorId,postId);
        Post post=get(organizationId,postId,null);
        if(!"HOMEWORK".equals(post.type())||!"PUBLISHED".equals(post.status()))throw bad("Reminders are available only for published homework");
        LinkedHashSet<Long> requested=new LinkedHashSet<>(input.studentUserIds());
        List<Long> eligible=db.sql("""
                SELECT e.student_user_id FROM student_enrollments e
                 WHERE e.organization_id=:org AND e.academic_year_id=:year AND e.section_id=:section
                   AND e.status='ENROLLED' AND e.student_user_id IN (:students)
                """).param("org",organizationId).param("year",post.academicYearId()).param("section",post.sectionId())
                .param("students",requested).query(Long.class).list();
        if(!Set.copyOf(eligible).equals(Set.copyOf(requested)))throw bad("Every reminder recipient must be enrolled in this homework class");
        LinkedHashSet<Long> recipients=new LinkedHashSet<>(requested);
        requested.forEach(student->recipients.addAll(guardians.activeGuardianUserIds(organizationId,student)));
        UUID event=UUID.randomUUID();
        String literal=recipients.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(",","{","}"));
        db.sql("""
                INSERT INTO learning_notification_outbox(event_id,organization_id,post_id,event_type,recipient_user_ids,idempotency_key)
                VALUES(:event,:org,:post,'LEARNING_REMINDER',CAST(:recipients AS BIGINT[]),:key)
                """).param("event",event).param("org",organizationId).param("post",postId)
                .param("recipients",literal).param("key","learning:"+postId+":reminder:"+event).update();
        return new ReminderResult(requested.size(),recipients.size());
    }

    @Transactional
    public Post create(long organizationId, long actorId, Save input) {
        requireAssignment(organizationId, actorId, input);
        validate(organizationId, input);
        int points = rewardPoints(organizationId, input.academicYearId());
        long id = db.sql("""
                INSERT INTO learning_posts
                  (organization_id,academic_year_id,grade_level_id,section_id,
                   grade_level_subject_id,teacher_user_id,post_type,title,instructions,
                   lesson_date,topics,pages_exercises,recap,due_at,related_classwork_id,points)
                VALUES(:org,:year,:grade,:section,:subject,:actor,:type,:title,:instructions,
                       :lesson,:topics,:pages,:recap,:due,:related,:points)
                RETURNING id
                """).param("org", organizationId).param("year", input.academicYearId())
                .param("grade", input.gradeLevelId()).param("section", input.sectionId())
                .param("subject", input.gradeLevelSubjectId()).param("actor", actorId)
                .param("type", input.type()).param("title", input.title().trim())
                .param("instructions", input.instructions()).param("lesson", input.lessonDate())
                .param("topics", input.topics()).param("pages", input.pagesExercises())
                .param("recap", input.recap()).param("due", input.dueAt())
                .param("related", input.relatedClassworkId()).param("points", points)
                .query(Long.class).single();
        replaceChildren(organizationId, id, input);
        return get(organizationId, id, null);
    }

    @Transactional
    public Post update(long organizationId, long actorId, long id, long version, Save input) {
        requireOwner(organizationId, actorId, id);
        requireAssignment(organizationId, actorId, input);
        validate(organizationId, input);
        Object[] previous = db.sql("SELECT status,due_at FROM learning_posts WHERE organization_id=:org AND id=:id")
                .param("org", organizationId).param("id", id)
                .query((rs,n)->new Object[]{rs.getString(1),rs.getObject(2,OffsetDateTime.class)}).single();
        String previousStatus=(String)previous[0];
        OffsetDateTime previousDue=(OffsetDateTime)previous[1];
        String previousTasks=taskFingerprint(organizationId,id);
        int changed = db.sql("""
                UPDATE learning_posts SET academic_year_id=:year,grade_level_id=:grade,
                  section_id=:section,grade_level_subject_id=:subject,post_type=:type,
                  title=:title,instructions=:instructions,lesson_date=:lesson,topics=:topics,
                  pages_exercises=:pages,recap=:recap,due_at=:due,related_classwork_id=:related,
                  points=:points,version=version+1,updated_at=CURRENT_TIMESTAMP
                WHERE organization_id=:org AND id=:id AND teacher_user_id=:actor
                  AND version=:version AND status<>'WITHDRAWN'
                """).param("year", input.academicYearId()).param("grade", input.gradeLevelId())
                .param("section", input.sectionId()).param("subject", input.gradeLevelSubjectId())
                .param("type", input.type()).param("title", input.title().trim())
                .param("instructions", input.instructions()).param("lesson", input.lessonDate())
                .param("topics", input.topics()).param("pages", input.pagesExercises())
                .param("recap", input.recap()).param("due", input.dueAt())
                .param("related", input.relatedClassworkId())
                .param("points", rewardPoints(organizationId, input.academicYearId()))
                .param("org", organizationId).param("id", id).param("actor", actorId)
                .param("version", version).update();
        if (changed != 1) throw conflict();
        replaceChildren(organizationId, id, input);
        if(!previousTasks.equals(taskFingerprint(organizationId,id))) resetReviewsForChangedTasks(organizationId, id);
        if ("PUBLISHED".equals(previousStatus)) emit(organizationId, id,
                !Objects.equals(previousDue,input.dueAt()) ? "LEARNING_DUE_DATE_CHANGED" : "LEARNING_POST_CHANGED", version + 1);
        return get(organizationId, id, null);
    }

    @Transactional
    public Post publish(long organizationId, long actorId, long id, long version) {
        requireOwner(organizationId, actorId, id);
        Post post = get(organizationId, id, null);
        if ("HOMEWORK".equals(post.type()) && !post.dueAt().isAfter(OffsetDateTime.now())) {
            throw bad("Homework due date must be in the future");
        }
        changeStatus(organizationId, actorId, id, version, "PUBLISHED");
        emit(organizationId, id, "LEARNING_POST_PUBLISHED", version + 1);
        return get(organizationId, id, null);
    }

    @Transactional
    public Post withdraw(long organizationId, long actorId, long id, long version) {
        requireOwner(organizationId, actorId, id);
        changeStatus(organizationId, actorId, id, version, "WITHDRAWN");
        emit(organizationId, id, "LEARNING_POST_WITHDRAWN", version + 1);
        return get(organizationId, id, null);
    }

    public List<Post> list(long organizationId, long actorId, String roles, Long studentId) {
        String filter;
        if (roles.contains("ROLE_TEACHER")) {
            filter = "p.teacher_user_id=:actor";
        } else if (roles.contains("ROLE_STUDENT")) {
            studentId = actorId;
            filter = enrolledFilter();
        } else if (roles.contains("ROLE_PARENT") || roles.contains("ROLE_GUARDIAN")) {
            requireGuardian(organizationId, actorId, studentId);
            filter = enrolledFilter();
        } else if (isLeadership(roles)) {
            filter = "p.status<>'DRAFT'";
        } else {
            throw missing();
        }
        var query = db.sql("SELECT p.id FROM learning_posts p WHERE p.organization_id=:org AND " + filter
                        + " ORDER BY COALESCE(p.due_at,p.lesson_date::timestamptz) DESC,p.id DESC LIMIT 200")
                .param("org", organizationId).param("actor", actorId);
        if (studentId != null) query = query.param("student", studentId);
        final Long selectedStudent = studentId;
        return query.query(Long.class).list().stream()
                .map(id -> get(organizationId, id, selectedStudent)).toList();
    }

    public Post get(long organizationId, long id, Long studentId) {
        Object[] row = db.sql("SELECT * FROM learning_posts WHERE organization_id=:org AND id=:id")
                .param("org", organizationId).param("id", id)
                .query((rs, n) -> new Object[]{rs.getLong("id"),rs.getString("post_type"),
                        rs.getString("status"),rs.getLong("academic_year_id"),rs.getLong("grade_level_id"),
                        rs.getLong("section_id"),rs.getLong("grade_level_subject_id"),rs.getLong("teacher_user_id"),
                        rs.getString("title"),rs.getString("instructions"),rs.getObject("lesson_date",LocalDate.class),
                        rs.getString("topics"),rs.getString("pages_exercises"),rs.getString("recap"),
                        rs.getObject("due_at",OffsetDateTime.class),rs.getObject("related_classwork_id",Long.class),
                        rs.getInt("points"),rs.getLong("version"),rs.getObject("published_at",OffsetDateTime.class),
                        rs.getObject("updated_at",OffsetDateTime.class)}).optional().orElseThrow(this::missing);
        List<Task> tasks = db.sql("""
                SELECT t.id,t.description,t.reference_type,t.reference_value,
                  CASE WHEN CAST(:student AS BIGINT) IS NULL THEN FALSE ELSE EXISTS(
                    SELECT 1 FROM learning_student_progress p WHERE p.organization_id=t.organization_id
                      AND p.post_id=t.post_id AND p.task_id=t.id AND p.student_user_id=CAST(:student AS BIGINT)
                      AND p.completed AND p.task_revision=t.content_revision) END completed
                FROM learning_tasks t WHERE t.organization_id=:org AND t.post_id=:id ORDER BY t.display_order
                """).param("student", studentId).param("org", organizationId).param("id", id)
                .query((rs,n)->new Task(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getString(4),rs.getBoolean(5))).list();
        List<Link> links = db.sql("SELECT id,task_id,title,url,link_kind,video_start_seconds FROM learning_links WHERE organization_id=:org AND post_id=:id ORDER BY display_order")
                .param("org",organizationId).param("id",id)
                .query((rs,n)->new Link(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4),rs.getString(5),(Integer)rs.getObject(6))).list();
        return new Post((long)row[0],(String)row[1],(String)row[2],(long)row[3],(long)row[4],
                (long)row[5],(long)row[6],(long)row[7],(String)row[8],(String)row[9],
                (LocalDate)row[10],(String)row[11],(String)row[12],(String)row[13],
                (OffsetDateTime)row[14],(Long)row[15],(int)row[16],(long)row[17],
                (OffsetDateTime)row[18],(OffsetDateTime)row[19],tasks,links,attachments(organizationId,id),
                studentId == null ? null : review(organizationId,id,studentId));
    }

    @Transactional
    public Attachment upload(long organizationId,long actorId,long postId,UUID taskId,MultipartFile file) {
        requireOwner(organizationId,actorId,postId);
        byte[] content;
        try { content=file.getBytes(); } catch(IOException exception) { throw bad("Photo could not be read"); }
        if(content.length<1||content.length>10*1024*1024) throw bad("Photo must be between 1 byte and 10 MB");
        String contentType=Optional.ofNullable(file.getContentType()).orElse("");
        if(!Set.of("image/jpeg","image/png","image/webp").contains(contentType)) throw bad("Only JPEG, PNG and WebP photos are supported");
        if(taskId!=null && db.sql("SELECT count(*) FROM learning_tasks WHERE organization_id=:org AND post_id=:post AND id=:task")
                .param("org",organizationId).param("post",postId).param("task",taskId).query(Long.class).single()!=1) throw missing();
        UUID id=UUID.randomUUID();
        String name=Optional.ofNullable(file.getOriginalFilename()).orElse("photo").replaceAll("[\\r\\n\\\\/]","_");
        String sha;
        try { sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch(Exception exception) { throw new IllegalStateException(exception); }
        db.sql("INSERT INTO learning_attachments(id,organization_id,post_id,task_id,original_name,content_type,byte_size,sha256,content,uploaded_by_user_id) VALUES(:id,:org,:post,:task,:name,:type,:size,:sha,:content,:actor)")
                .param("id",id).param("org",organizationId).param("post",postId).param("task",taskId)
                .param("name",name).param("type",contentType).param("size",content.length).param("sha",sha)
                .param("content",content).param("actor",actorId).update();
        return attachments(organizationId,postId).stream().filter(value->value.id().equals(id)).findFirst().orElseThrow();
    }

    public AttachmentDownload download(long organizationId,long postId,UUID attachmentId) {
        return db.sql("SELECT original_name,content_type,content FROM learning_attachments WHERE organization_id=:org AND post_id=:post AND id=:id AND removed_at IS NULL")
                .param("org",organizationId).param("post",postId).param("id",attachmentId)
                .query((rs,n)->new AttachmentDownload(rs.getString(1),rs.getString(2),rs.getBytes(3)))
                .optional().orElseThrow(this::missing);
    }

    @Transactional
    public void removeAttachment(long organizationId,long actorId,long postId,UUID attachmentId){
        requireOwner(organizationId,actorId,postId);
        int changed=db.sql("UPDATE learning_attachments SET removed_at=CURRENT_TIMESTAMP WHERE organization_id=:org AND post_id=:post AND id=:id AND removed_at IS NULL")
                .param("org",organizationId).param("post",postId).param("id",attachmentId).update();
        if(changed!=1) throw missing();
    }

    public void requirePostAccess(long organizationId,long actorId,long postId,String roles,Long studentId) {
        if(roles.contains("ROLE_TEACHER")){ requireOwner(organizationId,actorId,postId); return; }
        if(roles.contains("ROLE_STUDENT")){ requireStudent(organizationId,actorId,postId); return; }
        if(roles.contains("ROLE_PARENT")||roles.contains("ROLE_GUARDIAN")){requireGuardian(organizationId,actorId,studentId);requireStudent(organizationId,studentId,postId);return;}
        if(isLeadership(roles)&&postExists(organizationId,postId)) return;
        throw missing();
    }

    @Transactional
    public Review toggle(long organizationId,long studentId,long postId,UUID taskId,boolean complete) {
        requireStudent(organizationId,studentId,postId);
        int revision=db.sql("SELECT content_revision FROM learning_tasks WHERE organization_id=:org AND post_id=:post AND id=:task")
                .param("org",organizationId).param("post",postId).param("task",taskId)
                .query(Integer.class).optional().orElseThrow(this::missing);
        db.sql("""
                INSERT INTO learning_student_progress
                  (organization_id,post_id,task_id,student_user_id,completed,task_revision,completed_at)
                VALUES(:org,:post,:task,:student,:complete,:revision,CASE WHEN :complete THEN CURRENT_TIMESTAMP ELSE NULL END)
                ON CONFLICT(organization_id,post_id,student_user_id,task_id) DO UPDATE SET
                  completed=:complete,task_revision=:revision,
                  completed_at=CASE WHEN :complete THEN CURRENT_TIMESTAMP ELSE NULL END
                """).param("org",organizationId).param("post",postId).param("task",taskId)
                .param("student",studentId).param("complete",complete).param("revision",revision).update();
        db.sql("INSERT INTO learning_reviews(organization_id,post_id,student_user_id,status) VALUES(:org,:post,:student,'IN_PROGRESS') ON CONFLICT DO NOTHING")
                .param("org",organizationId).param("post",postId).param("student",studentId).update();
        return review(organizationId,postId,studentId);
    }

    @Transactional
    public Review ready(long organizationId,long studentId,long postId) {
        requireStudent(organizationId,studentId,postId);
        long remaining=db.sql("""
                SELECT count(*) FROM learning_tasks t WHERE t.organization_id=:org AND t.post_id=:post
                AND NOT EXISTS(SELECT 1 FROM learning_student_progress p
                  WHERE p.organization_id=:org AND p.post_id=:post AND p.task_id=t.id
                    AND p.student_user_id=:student AND p.completed AND p.task_revision=t.content_revision)
                """).param("org",organizationId).param("post",postId).param("student",studentId).query(Long.class).single();
        if(remaining>0) throw bad("Complete every task before marking ready for review");
        db.sql("""
                INSERT INTO learning_reviews(organization_id,post_id,student_user_id,status,submitted_at)
                VALUES(:org,:post,:student,'READY_FOR_REVIEW',CURRENT_TIMESTAMP)
                ON CONFLICT(organization_id,post_id,student_user_id) DO UPDATE SET
                  status='READY_FOR_REVIEW',submitted_at=CURRENT_TIMESTAMP,reviewed_by_user_id=NULL,
                  reviewed_at=NULL,feedback=NULL,version=learning_reviews.version+1
                """).param("org",organizationId).param("post",postId).param("student",studentId).update();
        return review(organizationId,postId,studentId);
    }

    public List<ReviewQueueItem> reviewQueue(long organizationId,long teacherId) {
        return db.sql("""
                SELECT p.id,p.title,p.due_at,r.student_user_id,r.submitted_at,r.version
                FROM learning_reviews r JOIN learning_posts p ON p.id=r.post_id AND p.organization_id=r.organization_id
                WHERE r.organization_id=:org AND p.teacher_user_id=:teacher AND r.status='READY_FOR_REVIEW'
                ORDER BY r.submitted_at,p.id,r.student_user_id
                """).param("org",organizationId).param("teacher",teacherId)
                .query((rs,n)->new ReviewQueueItem(rs.getLong(1),rs.getString(2),rs.getObject(3,OffsetDateTime.class),
                        rs.getLong(4),rs.getObject(5,OffsetDateTime.class),rs.getLong(6))).list();
    }

    @Transactional
    public Review decide(long organizationId,long teacherId,long postId,long studentId,ReviewDecision decision) {
        requireOwner(organizationId,teacherId,postId);
        int changed=db.sql("""
                UPDATE learning_reviews SET status=:status,feedback=:feedback,reviewed_by_user_id=:teacher,
                  reviewed_at=CURRENT_TIMESTAMP,version=version+1
                WHERE organization_id=:org AND post_id=:post AND student_user_id=:student
                  AND version=:version AND status='READY_FOR_REVIEW'
                """).param("status",decision.decision()).param("feedback",decision.feedback())
                .param("teacher",teacherId).param("org",organizationId).param("post",postId)
                .param("student",studentId).param("version",decision.version()).update();
        if(changed!=1) throw conflict();
        if("VERIFIED".equals(decision.decision())) award(organizationId,teacherId,postId,studentId);
        return review(organizationId,postId,studentId);
    }

    public PointsSummary points(long organizationId,long studentId) {
        List<PointEntry> entries=db.sql("SELECT id,academic_year_id,recipient_user_id,source_type,source_id,amount,issuer_user_id,reason,adjustment_of_id,created_at FROM student_points_ledger WHERE organization_id=:org AND recipient_user_id=:student ORDER BY created_at DESC,id DESC")
                .param("org",organizationId).param("student",studentId)
                .query((rs,n)->new PointEntry(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getString(4),
                        rs.getString(5),rs.getInt(6),rs.getLong(7),rs.getString(8),(Long)rs.getObject(9),
                        rs.getObject(10,OffsetDateTime.class))).list();
        return new PointsSummary(entries.stream().mapToInt(PointEntry::amount).sum(),entries);
    }

    public void requirePointsAccess(long organizationId,long actorId,long studentId,String roles) {
        if(actorId==studentId&&roles.contains("ROLE_STUDENT")) return;
        if((roles.contains("ROLE_PARENT")||roles.contains("ROLE_GUARDIAN"))&&guardians.hasActiveRelationship(organizationId,actorId,studentId)) return;
        if(isLeadership(roles)) return;
        throw missing();
    }

    public RewardSettings settings(long organizationId,long academicYearId) {
        return db.sql("SELECT academic_year_id,verified_homework_points,version,updated_by_user_id,updated_at FROM learning_reward_settings WHERE organization_id=:org AND academic_year_id=:year")
                .param("org",organizationId).param("year",academicYearId)
                .query((rs,n)->new RewardSettings(rs.getLong(1),rs.getInt(2),rs.getLong(3),rs.getLong(4),rs.getObject(5,OffsetDateTime.class)))
                .optional().orElse(new RewardSettings(academicYearId,0,0,null,null));
    }

    @Transactional
    public RewardSettings saveSettings(long organizationId,long actorId,SaveRewardSettings input) {
        int changed=db.sql("""
                INSERT INTO learning_reward_settings(organization_id,academic_year_id,verified_homework_points,updated_by_user_id)
                VALUES(:org,:year,:points,:actor)
                ON CONFLICT(organization_id,academic_year_id) DO UPDATE SET
                  verified_homework_points=:points,updated_by_user_id=:actor,updated_at=CURRENT_TIMESTAMP,
                  version=learning_reward_settings.version+1
                WHERE learning_reward_settings.version=:version
                """).param("org",organizationId).param("year",input.academicYearId())
                .param("points",input.verifiedHomeworkPoints()).param("actor",actorId).param("version",input.version()).update();
        if(changed!=1) throw conflict();
        return settings(organizationId,input.academicYearId());
    }

    @Transactional
    public PointEntry correctPoints(long organizationId,long actorId,PointCorrection correction) {
        PointEntry original=db.sql("SELECT id,academic_year_id,recipient_user_id,source_type,source_id,amount,issuer_user_id,reason,adjustment_of_id,created_at FROM student_points_ledger WHERE organization_id=:org AND id=:id AND adjustment_of_id IS NULL")
                .param("org",organizationId).param("id",correction.awardId())
                .query((rs,n)->new PointEntry(rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getString(4),rs.getString(5),
                        rs.getInt(6),rs.getLong(7),rs.getString(8),(Long)rs.getObject(9),rs.getObject(10,OffsetDateTime.class)))
                .optional().orElseThrow(this::missing);
        if(correction.amount()==0) throw bad("A points correction cannot be zero");
        long id=db.sql("INSERT INTO student_points_ledger(organization_id,academic_year_id,recipient_user_id,source_type,source_id,amount,issuer_user_id,reason,adjustment_of_id) VALUES(:org,:year,:student,'HOMEWORK_CORRECTION',:source,:amount,:actor,:reason,:original) RETURNING id")
                .param("org",organizationId).param("year",original.academicYearId()).param("student",original.recipientUserId())
                .param("source",original.sourceId()+":"+UUID.randomUUID()).param("amount",correction.amount())
                .param("actor",actorId).param("reason",correction.reason()).param("original",original.id()).query(Long.class).single();
        return points(organizationId,original.recipientUserId()).entries().stream().filter(x->x.id()==id).findFirst().orElseThrow();
    }

    public List<LeadershipProgress> leadershipProgress(long organizationId) {
        return db.sql("""
                SELECT p.id,p.title,p.section_id,
                  (SELECT count(*) FROM student_enrollments e WHERE e.organization_id=p.organization_id
                    AND e.academic_year_id=p.academic_year_id AND e.section_id=p.section_id AND e.status='ENROLLED'),
                  count(*) FILTER(WHERE r.status='IN_PROGRESS'),count(*) FILTER(WHERE r.status='READY_FOR_REVIEW'),
                  count(*) FILTER(WHERE r.status='VERIFIED'),count(*) FILTER(WHERE r.status='CHANGES_REQUESTED')
                FROM learning_posts p LEFT JOIN learning_reviews r ON r.organization_id=p.organization_id AND r.post_id=p.id
                WHERE p.organization_id=:org AND p.status='PUBLISHED'
                GROUP BY p.id,p.title,p.section_id ORDER BY p.updated_at DESC
                """).param("org",organizationId)
                .query((rs,n)->new LeadershipProgress(rs.getLong(1),rs.getString(2),rs.getLong(3),rs.getLong(4),
                        rs.getLong(5),rs.getLong(6),rs.getLong(7),rs.getLong(8))).list();
    }

    private void replaceChildren(long organizationId,long postId,Save input) {
        db.sql("UPDATE learning_tasks SET display_order=display_order+10000 WHERE organization_id=:org AND post_id=:post")
                .param("org",organizationId).param("post",postId).update();
        Set<UUID> retained=new HashSet<>();int order=0;
        for(Task task:input.tasks()) {
            UUID taskId=task.id()==null?UUID.randomUUID():task.id();retained.add(taskId);
            db.sql("""
                    INSERT INTO learning_tasks(id,organization_id,post_id,display_order,description,reference_type,reference_value,content_revision)
                    VALUES(:id,:org,:post,:order,:description,:referenceType,:referenceValue,1)
                    ON CONFLICT(post_id,id) DO UPDATE SET display_order=:order,description=:description,
                      reference_type=:referenceType,reference_value=:referenceValue,
                      content_revision=CASE WHEN learning_tasks.description IS DISTINCT FROM :description
                        OR learning_tasks.reference_type IS DISTINCT FROM :referenceType
                        OR learning_tasks.reference_value IS DISTINCT FROM :referenceValue
                        THEN learning_tasks.content_revision+1 ELSE learning_tasks.content_revision END
                    """).param("id",taskId).param("org",organizationId).param("post",postId).param("order",++order)
                    .param("description",task.description().trim()).param("referenceType",task.referenceType())
                    .param("referenceValue",task.referenceValue()).update();
        }
        for(UUID old:db.sql("SELECT id FROM learning_tasks WHERE organization_id=:org AND post_id=:post")
                .param("org",organizationId).param("post",postId).query(UUID.class).list()) {
            if(!retained.contains(old)) db.sql("DELETE FROM learning_tasks WHERE organization_id=:org AND post_id=:post AND id=:id")
                    .param("org",organizationId).param("post",postId).param("id",old).update();
        }
        db.sql("DELETE FROM learning_links WHERE organization_id=:org AND post_id=:post")
                .param("org",organizationId).param("post",postId).update();order=0;
        for(Link link:input.links()) {
            if(link.taskId()!=null&&!retained.contains(link.taskId())) throw bad("A resource link must reference a task in this post");
            db.sql("INSERT INTO learning_links(id,organization_id,post_id,task_id,title,url,link_kind,video_start_seconds,display_order) VALUES(:id,:org,:post,:task,:title,:url,:kind,:seconds,:order)")
                .param("id",link.id()==null?UUID.randomUUID():link.id()).param("org",organizationId).param("post",postId)
                .param("task",link.taskId())
                .param("title",link.title()).param("url",link.url()).param("kind",link.kind())
                .param("seconds",link.videoStartSeconds()).param("order",++order).update();
        }
    }

    private void resetReviewsForChangedTasks(long organizationId,long postId) {
        db.sql("""
                UPDATE learning_reviews r SET status='IN_PROGRESS',submitted_at=NULL,reviewed_by_user_id=NULL,
                  reviewed_at=NULL,feedback=NULL,version=version+1
                WHERE r.organization_id=:org AND r.post_id=:post AND r.status<>'IN_PROGRESS'
                """).param("org",organizationId).param("post",postId).update();
    }

    private void award(long organizationId,long teacherId,long postId,long studentId) {
        Post post=get(organizationId,postId,studentId);
        if(post.points()<=0)return;
        db.sql("INSERT INTO student_points_ledger(organization_id,academic_year_id,recipient_user_id,source_type,source_id,amount,issuer_user_id,reason) VALUES(:org,:year,:student,'HOMEWORK',:source,:amount,:teacher,'Homework verified') ON CONFLICT DO NOTHING")
                .param("org",organizationId).param("year",post.academicYearId()).param("student",studentId)
                .param("source",String.valueOf(postId)).param("amount",post.points()).param("teacher",teacherId).update();
    }

    private int rewardPoints(long organizationId,long academicYearId){return settings(organizationId,academicYearId).verifiedHomeworkPoints();}
    private void changeStatus(long org,long actor,long id,long version,String status){int n=db.sql("UPDATE learning_posts SET status=:status,version=version+1,updated_at=CURRENT_TIMESTAMP,published_at=CASE WHEN :status='PUBLISHED' THEN COALESCE(published_at,CURRENT_TIMESTAMP) ELSE published_at END,withdrawn_at=CASE WHEN :status='WITHDRAWN' THEN CURRENT_TIMESTAMP ELSE NULL END WHERE organization_id=:org AND id=:id AND teacher_user_id=:actor AND version=:version").param("status",status).param("org",org).param("id",id).param("actor",actor).param("version",version).update();if(n!=1)throw conflict();}
    private void emit(long org,long post,String type,long revision){db.sql("INSERT INTO learning_notification_outbox(event_id,organization_id,post_id,event_type,idempotency_key) VALUES(:event,:org,:post,:type,:key) ON CONFLICT(organization_id,idempotency_key) DO NOTHING").param("event",UUID.randomUUID()).param("org",org).param("post",post).param("type",type).param("key","learning:"+post+":"+revision+":"+type).update();}
    private String enrolledFilter(){return "EXISTS(SELECT 1 FROM student_enrollments e WHERE e.organization_id=p.organization_id AND e.academic_year_id=p.academic_year_id AND e.section_id=p.section_id AND e.student_user_id=:student AND e.status='ENROLLED') AND p.status<>'DRAFT'";}
    private void requireAssignment(long org,long actor,Save input){long n=db.sql("SELECT count(*) FROM teacher_assignments WHERE organization_id=:org AND academic_year_id=:year AND grade_level_id=:grade AND section_id=:section AND grade_level_subject_id=:subject AND teacher_user_id=:actor AND assignment_type='SUBJECT_TEACHER'").param("org",org).param("year",input.academicYearId()).param("grade",input.gradeLevelId()).param("section",input.sectionId()).param("subject",input.gradeLevelSubjectId()).param("actor",actor).query(Long.class).single();if(n!=1)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This section and subject are not assigned to you");}
    private void requireOwner(long org,long actor,long post){if(db.sql("SELECT count(*) FROM learning_posts WHERE organization_id=:org AND id=:post AND teacher_user_id=:actor").param("org",org).param("post",post).param("actor",actor).query(Long.class).single()!=1)throw missing();}
    private void requireStudent(long org,long student,long post){if(db.sql("SELECT count(*) FROM learning_posts p JOIN student_enrollments e ON e.organization_id=p.organization_id AND e.academic_year_id=p.academic_year_id AND e.section_id=p.section_id WHERE p.organization_id=:org AND p.id=:post AND p.status='PUBLISHED' AND e.student_user_id=:student AND e.status='ENROLLED'").param("org",org).param("post",post).param("student",student).query(Long.class).single()!=1)throw missing();}
    private void requireGuardian(long org,long actor,Long student){if(student==null||!guardians.hasActiveRelationship(org,actor,student))throw missing();}
    private boolean postExists(long org,long post){return db.sql("SELECT count(*) FROM learning_posts WHERE organization_id=:org AND id=:post").param("org",org).param("post",post).query(Long.class).single()==1;}
    private boolean isLeadership(String roles){return roles.contains("ROLE_ADMIN")||roles.contains("ROLE_PRINCIPAL")||roles.contains("ROLE_VICE_PRINCIPAL")||roles.contains("ROLE_HEAD_TEACHER");}
    private void validate(long org,Save input){if("CLASSWORK".equals(input.type())&&(input.lessonDate()==null||input.dueAt()!=null))throw bad("Classwork requires a lesson date and has no due date");if("HOMEWORK".equals(input.type())&&input.dueAt()==null)throw bad("Homework requires a due date");if(input.relatedClassworkId()!=null&&db.sql("SELECT count(*) FROM learning_posts WHERE organization_id=:org AND id=:related AND post_type='CLASSWORK' AND section_id=:section AND grade_level_subject_id=:subject").param("org",org).param("related",input.relatedClassworkId()).param("section",input.sectionId()).param("subject",input.gradeLevelSubjectId()).query(Long.class).single()!=1)throw bad("Related classwork must use the same audience");}
    private String taskFingerprint(long org,long post){return db.sql("SELECT COALESCE(string_agg(id::text||':'||content_revision::text,',' ORDER BY display_order),'') FROM learning_tasks WHERE organization_id=:org AND post_id=:post").param("org",org).param("post",post).query(String.class).single();}
    private List<Attachment> attachments(long org,long post){return db.sql("SELECT id,task_id,original_name,content_type,byte_size,sha256,created_at FROM learning_attachments WHERE organization_id=:org AND post_id=:post AND removed_at IS NULL ORDER BY created_at").param("org",org).param("post",post).query((rs,n)->new Attachment(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4),rs.getLong(5),rs.getString(6),rs.getObject(7,OffsetDateTime.class))).list();}
    private Review review(long org,long post,long student){return db.sql("SELECT student_user_id,status,submitted_at,reviewed_by_user_id,reviewed_at,feedback,version FROM learning_reviews WHERE organization_id=:org AND post_id=:post AND student_user_id=:student").param("org",org).param("post",post).param("student",student).query((rs,n)->new Review(rs.getLong(1),rs.getString(2),rs.getObject(3,OffsetDateTime.class),(Long)rs.getObject(4),rs.getObject(5,OffsetDateTime.class),rs.getString(6),rs.getLong(7))).optional().orElse(new Review(student,"IN_PROGRESS",null,null,null,null,0));}
    private Map<Long,String> studentNames(long org,Set<Long> ids){
        if(ids.isEmpty())return Map.of();
        try{
            var response=identityStudents.checkBatch(org,ids.stream().sorted().toList());
            Map<Long,String> names=new HashMap<>();
            response.results().stream().filter(Objects::nonNull).filter(item->Objects.equals(item.organizationId(),org))
                    .filter(item->ids.contains(item.userId())&&item.displayName()!=null&&!item.displayName().isBlank())
                    .forEach(item->names.putIfAbsent(item.userId(),item.displayName().trim()));
            return Map.copyOf(names);
        }catch(IdentityStudentEligibilityException|IllegalStateException exception){
            log.warn("Homework student names could not be resolved for organization {}",org,exception);
            return Map.of();
        }
    }
    private ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Learning record was not found");}
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private ResponseStatusException conflict(){return new ResponseStatusException(HttpStatus.CONFLICT,"The learning record changed. Reload and try again");}
    private record HomeworkHead(long id,String title,String status,OffsetDateTime dueAt,long sectionId,String sectionName,String subjectName) {}
    private record ProgressRow(long studentId,String reviewStatus,int completedTasks,int totalTasks) {}
    public record AttachmentDownload(String name,String contentType,byte[] content) {}
}
