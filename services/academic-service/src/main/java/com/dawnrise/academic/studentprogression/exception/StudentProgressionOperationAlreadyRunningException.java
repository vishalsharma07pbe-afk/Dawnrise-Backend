package com.dawnrise.academic.studentprogression.exception;

import com.dawnrise.academic.studentprogression.enums.StudentProgressionOperationStatus;

public class StudentProgressionOperationAlreadyRunningException
        extends StudentProgressionConflictException {

    public StudentProgressionOperationAlreadyRunningException(
            String message
    ) {
        super(
                message,
                StudentProgressionOperationStatus.CONFLICTED,
                "OPERATION_ALREADY_RUNNING"
        );
    }
}