package com.dawnrise.academic.studentattendance.recording.entity;

import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import com.dawnrise.academic.studentattendance.recording.exception.InvalidStudentAttendanceRecordingException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentAttendanceRecordTest {

    @Test
    void storesRecordedAndEffectiveStatusWithCreditSnapshot() {
        StudentAttendanceSession session = session();

        StudentAttendanceRecord record = new StudentAttendanceRecord(
                session,
                31L,
                41L,
                AttendanceStatus.LATE,
                AttendanceStatus.HALF_DAY,
                new BigDecimal("0.50"),
                new BigDecimal("1.00"),
                true,
                "Late arrival",
                51L
        );

        assertThat(record.getRecordedStatus()).isEqualTo(AttendanceStatus.LATE);
        assertThat(record.getEffectiveStatus()).isEqualTo(AttendanceStatus.HALF_DAY);
        assertThat(record.getEarnedCredit()).isEqualByComparingTo("0.50");
        assertThat(record.isLatePenaltyApplied()).isTrue();
        assertThat(record.getRemarks()).isEqualTo("Late arrival");
    }

    @Test
    void rejectsEffectiveStatusMismatchWithoutLatePenalty() {
        StudentAttendanceSession session = session();

        assertThatThrownBy(() -> new StudentAttendanceRecord(
                session,
                31L,
                41L,
                AttendanceStatus.PRESENT,
                AttendanceStatus.ABSENT,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                false,
                null,
                51L
        )).isInstanceOf(InvalidStudentAttendanceRecordingException.class);
    }

    @Test
    void rejectsBlankRemarks() {
        StudentAttendanceSession session = session();

        assertThatThrownBy(() -> new StudentAttendanceRecord(
                session,
                31L,
                41L,
                AttendanceStatus.PRESENT,
                AttendanceStatus.PRESENT,
                BigDecimal.ONE,
                BigDecimal.ONE,
                false,
                " ",
                51L
        )).isInstanceOf(InvalidStudentAttendanceRecordingException.class);
    }

    private static StudentAttendanceSession session() {
        return new StudentAttendanceSession(
                11L,
                21L,
                22L,
                23L,
                24L,
                LocalDate.of(2026, 4, 10),
                51L
        );
    }
}
