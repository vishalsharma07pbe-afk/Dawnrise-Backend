package com.dawnrise.identity.studentguardian.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class StudentGuardianRelationshipRepositoryTest {

    @Test
    void viewQueries_joinStudentAndGuardianInRepositoryLayer()
            throws Exception {
        assertQueryJoinsUsers("findActiveViewsByStudent");
        assertQueryJoinsUsers("findHistoryViewsByStudent");
        assertQueryJoinsUsers("findActiveViewsByGuardian");
    }

    private void assertQueryJoinsUsers(String methodName)
            throws Exception {
        Method method = findMethod(methodName);
        Query query = method.getAnnotation(Query.class);

        assertThat(query).isNotNull();
        assertThat(query.value())
                .contains("StudentGuardianRelationshipView")
                .contains("join User student")
                .contains("join User guardian")
                .contains("student.organizationId = relationship.organizationId")
                .contains("guardian.organizationId = relationship.organizationId");
    }

    private Method findMethod(String name) {
        for (Method method :
                StudentGuardianRelationshipRepository.class
                        .getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }

        throw new AssertionError("Repository method not found: " + name);
    }
}
