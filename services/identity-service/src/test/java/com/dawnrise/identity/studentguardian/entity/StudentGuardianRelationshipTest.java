package com.dawnrise.identity.studentguardian.entity;

import com.dawnrise.identity.studentguardian.enums.StudentGuardianRelationshipStatus;
import com.dawnrise.identity.studentguardian.enums.StudentGuardianRelationshipType;
import com.dawnrise.identity.studentguardian.exception.StudentGuardianRelationshipConflictException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class StudentGuardianRelationshipTest {

    @Test
    void constructor_whenSameUser_throwsInvalidArgument() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StudentGuardianRelationship(
                        1L,
                        10L,
                        10L,
                        StudentGuardianRelationshipType.FATHER,
                        true,
                        99L,
                        OffsetDateTime.now()
                )
        );
    }

    @Test
    void end_whenActive_marksEndedAndPreservesPrimaryFlag() {
        StudentGuardianRelationship relationship = relationship(true);

        relationship.end(99L, "Transferred", OffsetDateTime.now());

        assertEquals(
                StudentGuardianRelationshipStatus.ENDED,
                relationship.getStatus()
        );
        assertTrue(relationship.isPrimaryGuardian());
        assertEquals(99L, relationship.getEndedByUserId());
        assertEquals("Transferred", relationship.getEndReason());
        assertNotNull(relationship.getEndedAt());
    }

    @Test
    void end_whenAlreadyEnded_throwsConflict() {
        StudentGuardianRelationship relationship = relationship(false);
        relationship.end(99L, null, OffsetDateTime.now());

        assertThrows(
                StudentGuardianRelationshipConflictException.class,
                () -> relationship.end(99L, null, OffsetDateTime.now())
        );
    }

    @Test
    void end_whenBlankReason_throwsInvalidArgument() {
        StudentGuardianRelationship relationship = relationship(false);

        assertThrows(
                IllegalArgumentException.class,
                () -> relationship.end(99L, "  ", OffsetDateTime.now())
        );
    }

    @Test
    void demotePrimary_whenActive_clearsPrimaryFlag() {
        StudentGuardianRelationship relationship = relationship(true);

        relationship.demotePrimary();

        assertFalse(relationship.isPrimaryGuardian());
    }

    private StudentGuardianRelationship relationship(boolean primary) {
        return new StudentGuardianRelationship(
                1L,
                10L,
                20L,
                StudentGuardianRelationshipType.MOTHER,
                primary,
                99L,
                OffsetDateTime.now()
        );
    }
}
