package com.dawnrise.academic.studentattendance.policy.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class StudentAttendancePolicyRepositoryTest {

    @Test
    void incrementVersionIfCurrentIsAtomicAndClearsPersistenceContext()
            throws Exception {
        Method method = StudentAttendancePolicyRepository.class
                .getMethod(
                        "incrementVersionIfCurrent",
                        Long.class,
                        Long.class
                );

        Modifying modifying = method.getAnnotation(Modifying.class);
        Query query = method.getAnnotation(Query.class);

        assertThat(method.getReturnType()).isEqualTo(int.class);
        assertThat(modifying.flushAutomatically()).isTrue();
        assertThat(modifying.clearAutomatically()).isTrue();
        assertThat(query.value())
                .contains("policy.version = policy.version + 1")
                .contains("policy.updatedAt = CURRENT_TIMESTAMP")
                .contains("policy.organizationId = :organizationId")
                .contains("policy.version = :expectedVersion");
    }
}
