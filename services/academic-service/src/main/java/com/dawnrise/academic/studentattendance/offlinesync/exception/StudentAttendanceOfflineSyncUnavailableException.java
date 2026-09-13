package com.dawnrise.academic.studentattendance.offlinesync.exception;

public class StudentAttendanceOfflineSyncUnavailableException extends RuntimeException {

    public StudentAttendanceOfflineSyncUnavailableException(String message) {
        super(message);
    }

    public StudentAttendanceOfflineSyncUnavailableException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
