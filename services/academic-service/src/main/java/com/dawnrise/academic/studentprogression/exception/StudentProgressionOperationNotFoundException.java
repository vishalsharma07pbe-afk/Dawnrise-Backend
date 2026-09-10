package com.dawnrise.academic.studentprogression.exception;

public class StudentProgressionOperationNotFoundException
        extends RuntimeException {

    public StudentProgressionOperationNotFoundException(
            String message
    ) {
        super(message);
    }
}