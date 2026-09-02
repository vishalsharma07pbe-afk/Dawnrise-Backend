package com.dawnrise.academic.academicyear.entity;

import com.dawnrise.academic.academicyear.enums.AcademicYearStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.dawnrise.academic.academicyear.exception.AcademicYearConflictException;
import com.dawnrise.academic.academicyear.exception.InvalidAcademicYearException;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "academic_years")
public class AcademicYear {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AcademicYearStatus status = AcademicYearStatus.PLANNED;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public AcademicYear() {}

    public AcademicYear(
            Long organizationId,
            String name,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (organizationId == null || organizationId <= 0) {
            throw new InvalidAcademicYearException(
                    "Organization ID must be greater than zero"
            );
        }

        this.organizationId = organizationId;
        applyDetails(name, startDate, endDate);
    }

    public void updateDetails(
            String name,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (status != AcademicYearStatus.PLANNED) {
            throw new AcademicYearConflictException(
                    "Only a planned academic year can be updated"
            );
        }

        applyDetails(name, startDate, endDate);
    }

    public void activate() {
        if (status != AcademicYearStatus.PLANNED) {
            throw new AcademicYearConflictException(
                    "Only a planned academic year can be activated"
            );
        }

        status = AcademicYearStatus.ACTIVE;
    }

    public void close() {
        if (status != AcademicYearStatus.ACTIVE) {
            throw new AcademicYearConflictException(
                    "Only an active academic year can be closed"
            );
        }

        status = AcademicYearStatus.CLOSED;
    }

    private void applyDetails(
            String name,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (name == null || name.isBlank()) {
            throw new InvalidAcademicYearException(
                    "Academic year name is required"
            );
        }

        String normalizedName = name.trim();

        if (normalizedName.length() > 50) {
            throw new InvalidAcademicYearException(
                    "Academic year name cannot exceed 50 characters"
            );
        }

        if (startDate == null || endDate == null) {
            throw new InvalidAcademicYearException(
                    "Start date and end date are required"
            );
        }

        if (!startDate.isBefore(endDate)) {
            throw new InvalidAcademicYearException(
                    "Start date must be before end date"
            );
        }

        this.name = normalizedName;
        this.startDate = startDate;
        this.endDate = endDate;
    }


    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public AcademicYearStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}