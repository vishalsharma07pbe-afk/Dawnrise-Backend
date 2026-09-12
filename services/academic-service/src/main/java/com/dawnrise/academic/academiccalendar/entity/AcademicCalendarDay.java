package com.dawnrise.academic.academiccalendar.entity;

import com.dawnrise.academic.academiccalendar.enums.AttendanceRequirement;
import com.dawnrise.academic.academiccalendar.enums.CalendarDayType;
import com.dawnrise.academic.academiccalendar.exception.InvalidAcademicCalendarException;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "academic_calendar_days")
public class AcademicCalendarDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;
    @Column(name = "academic_year_id", nullable = false)
    private Long academicYearId;
    @Column(name = "calendar_date", nullable = false)
    private LocalDate calendarDate;
    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false, length = 40)
    private CalendarDayType dayType;
    @Column(name = "name", length = 150)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_requirement", nullable = false, length = 30)
    private AttendanceRequirement attendanceRequirement;
    @Column(name = "counts_toward_percentage", nullable = false)
    private boolean countsTowardPercentage;
    @Column(name = "day_weight", nullable = false, precision = 4, scale = 2)
    private BigDecimal dayWeight;
    @Column(name = "note", length = 500)
    private String note;
    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;
    @Column(name = "updated_by_user_id", nullable = false)
    private Long updatedByUserId;
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AcademicCalendarDay() {
    }

    public AcademicCalendarDay(Long organizationId, Long academicYearId,
            LocalDate calendarDate, CalendarDayType dayType, String name,
            AttendanceRequirement attendanceRequirement,
            boolean countsTowardPercentage, BigDecimal dayWeight, String note,
            Long actorUserId) {
        requirePositive(organizationId, "Organization ID must be positive");
        requirePositive(academicYearId, "Academic year ID must be positive");
        requirePositive(actorUserId, "Actor user ID must be positive");
        if (calendarDate == null) {
            throw new InvalidAcademicCalendarException("Calendar date is required");
        }
        this.organizationId = organizationId;
        this.academicYearId = academicYearId;
        this.calendarDate = calendarDate;
        this.createdByUserId = actorUserId;
        update(dayType, name, attendanceRequirement, countsTowardPercentage,
                dayWeight, note, actorUserId);
    }

    public void update(CalendarDayType dayType, String name,
            AttendanceRequirement attendanceRequirement,
            boolean countsTowardPercentage, BigDecimal dayWeight, String note,
            Long actorUserId) {
        requirePositive(actorUserId, "Actor user ID must be positive");
        validate(dayType, attendanceRequirement, countsTowardPercentage, dayWeight);
        this.dayType = dayType;
        this.name = normalize(name, 150, "Name");
        this.attendanceRequirement = attendanceRequirement;
        this.countsTowardPercentage = countsTowardPercentage;
        this.dayWeight = dayWeight;
        this.note = normalize(note, 500, "Note");
        this.updatedByUserId = actorUserId;
    }

    private void validate(CalendarDayType dayType,
            AttendanceRequirement requirement, boolean counts, BigDecimal weight) {
        if (dayType == null || requirement == null || weight == null) {
            throw new InvalidAcademicCalendarException("Calendar day classification is required");
        }
        if (weight.compareTo(BigDecimal.ZERO) < 0 || weight.compareTo(BigDecimal.ONE) > 0) {
            throw new InvalidAcademicCalendarException("Day weight must be between 0.00 and 1.00");
        }
        if (!counts && weight.compareTo(BigDecimal.ZERO) != 0) {
            throw new InvalidAcademicCalendarException("Non-counting days must have zero weight");
        }
        if (counts && (requirement != AttendanceRequirement.REQUIRED
                || weight.compareTo(BigDecimal.ZERO) <= 0)) {
            throw new InvalidAcademicCalendarException("Counting days must require attendance and have positive weight");
        }
        if ((requirement == AttendanceRequirement.NOT_APPLICABLE
                || requirement == AttendanceRequirement.OPTIONAL)
                && (counts || weight.compareTo(BigDecimal.ZERO) != 0)) {
            throw new InvalidAcademicCalendarException("Optional and not-applicable days must not count toward percentage");
        }
        if ((dayType == CalendarDayType.WEEKLY_OFF
                || dayType == CalendarDayType.HOLIDAY
                || dayType == CalendarDayType.VACATION)
                && requirement != AttendanceRequirement.NOT_APPLICABLE) {
            throw new InvalidAcademicCalendarException("Weekly off, holiday, and vacation days must be not applicable");
        }
        if ((dayType == CalendarDayType.WORKING_DAY
                || dayType == CalendarDayType.SPECIAL_WORKING_DAY)
                && requirement != AttendanceRequirement.REQUIRED) {
            throw new InvalidAcademicCalendarException("Working days must require attendance");
        }
    }

    private String normalize(String value, int maxLength, String label) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidAcademicCalendarException(label + " cannot be blank");
        }
        if (trimmed.length() > maxLength) {
            throw new InvalidAcademicCalendarException(label + " is too long");
        }
        return trimmed;
    }

    private void requirePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new InvalidAcademicCalendarException(message);
        }
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getAcademicYearId() { return academicYearId; }
    public LocalDate getCalendarDate() { return calendarDate; }
    public CalendarDayType getDayType() { return dayType; }
    public String getName() { return name; }
    public AttendanceRequirement getAttendanceRequirement() { return attendanceRequirement; }
    public boolean isCountsTowardPercentage() { return countsTowardPercentage; }
    public BigDecimal getDayWeight() { return dayWeight; }
    public String getNote() { return note; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public Long getUpdatedByUserId() { return updatedByUserId; }
    public Long getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
