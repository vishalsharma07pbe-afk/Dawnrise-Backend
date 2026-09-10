package com.dawnrise.academic.studentprogression.exception;

import com.dawnrise.academic.studentprogression.enums.StudentProgressionOperationStatus;

public class StudentProgressionConflictException
        extends RuntimeException {

    private final StudentProgressionOperationStatus operationStatus;
    private final String failureCode;

    public StudentProgressionConflictException(String message) {
        this(
                message,
                StudentProgressionOperationStatus.CONFLICTED,
                "STUDENT_PROGRESSION_CONFLICT"
        );
    }

    public StudentProgressionConflictException(
            String message,
            StudentProgressionOperationStatus operationStatus,
            String failureCode
    ) {
        super(message);
        this.operationStatus = operationStatus;
        this.failureCode = failureCode;
    }

    public StudentProgressionOperationStatus getOperationStatus() {
        return operationStatus;
    }

    public String getFailureCode() {
        return failureCode;
    }
}