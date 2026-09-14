package com.dawnrise.academic.studentattendance.notificationoutbox.service;

import com.dawnrise.academic.studentattendance.notificationoutbox.enums.StudentAttendanceNotificationEventType;
import com.dawnrise.academic.studentattendance.policy.enums.AttendanceStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudentAttendanceNotificationClassifierTest {

    private final StudentAttendanceNotificationClassifier classifier =
            new StudentAttendanceNotificationClassifier();

    @Test
    void classifiesAlertableSubmissionSnapshots() {
        assertThat(classifier.classify(
                AttendanceStatus.ABSENT,
                AttendanceStatus.ABSENT,
                false
        )).contains(StudentAttendanceNotificationEventType.ABSENCE_RECORDED);
        assertThat(classifier.classify(
                AttendanceStatus.LATE,
                AttendanceStatus.LATE,
                false
        )).contains(StudentAttendanceNotificationEventType.LATE_RECORDED);
        assertThat(classifier.classify(
                AttendanceStatus.LATE,
                AttendanceStatus.HALF_DAY,
                true
        )).contains(StudentAttendanceNotificationEventType.LATE_PENALTY_APPLIED);
        assertThat(classifier.classify(
                AttendanceStatus.LATE,
                AttendanceStatus.ABSENT,
                true
        )).contains(StudentAttendanceNotificationEventType.LATE_PENALTY_APPLIED);
    }

    @Test
    void ignoresNonAlertableSubmissionSnapshots() {
        assertThat(classifier.classify(
                AttendanceStatus.PRESENT,
                AttendanceStatus.PRESENT,
                false
        )).isEmpty();
        assertThat(classifier.classify(
                AttendanceStatus.EXCUSED,
                AttendanceStatus.EXCUSED,
                false
        )).isEmpty();
        assertThat(classifier.classify(
                AttendanceStatus.HALF_DAY,
                AttendanceStatus.HALF_DAY,
                false
        )).isEmpty();
    }

    @Test
    void penalizedLateDoesNotEmitDuplicateLateOrAbsenceEvents() {
        assertThat(classifier.classify(
                AttendanceStatus.LATE,
                AttendanceStatus.ABSENT,
                true
        )).contains(StudentAttendanceNotificationEventType.LATE_PENALTY_APPLIED);
    }

    @Test
    void rejectsInvalidSnapshots() {
        assertThatThrownBy(() -> classifier.classify(null, AttendanceStatus.ABSENT, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> classifier.classify(
                AttendanceStatus.PRESENT,
                AttendanceStatus.HALF_DAY,
                true
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detectsNotificationRelevantSnapshotChangesOnly() {
        assertThat(classifier.notificationSnapshotChanged(
                AttendanceStatus.ABSENT,
                AttendanceStatus.ABSENT,
                false,
                AttendanceStatus.ABSENT,
                AttendanceStatus.ABSENT,
                false
        )).isFalse();
        assertThat(classifier.notificationSnapshotChanged(
                AttendanceStatus.ABSENT,
                AttendanceStatus.ABSENT,
                false,
                AttendanceStatus.PRESENT,
                AttendanceStatus.PRESENT,
                false
        )).isTrue();
    }
}
