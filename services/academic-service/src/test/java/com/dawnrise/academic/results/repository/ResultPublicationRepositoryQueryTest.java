package com.dawnrise.academic.results.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultPublicationRepositoryQueryTest {

    @Test
    void studentPublicationQueryIsDistinctTenantScopedAndPublishedOnly() throws Exception {
        Method method = ResultPublicationRepository.class.getMethod(
                "findAllPublishedForStudent",
                Long.class,
                Long.class
        );
        String query = method.getAnnotation(Query.class).value()
                .replaceAll("\\s+", " ")
                .toLowerCase();

        assertTrue(query.contains("select distinct publication"));
        assertTrue(query.contains("sheet.organizationid = publication.organizationid"));
        assertTrue(query.contains("sheet.academicyearid = publication.academicyearid"));
        assertTrue(query.contains("sheet.examinationid = publication.examinationid"));
        assertTrue(query.contains("sheet.gradelevelid = publication.gradelevelid"));
        assertTrue(query.contains("sheet.sectionid = publication.sectionid"));
        assertTrue(query.contains("mark.subjectsheetid = sheet.id"));
        assertTrue(query.contains("mark.organizationid = publication.organizationid"));
        assertTrue(query.contains("mark.academicyearid = publication.academicyearid"));
        assertTrue(query.contains("mark.gradelevelid = publication.gradelevelid"));
        assertTrue(query.contains("mark.sectionid = publication.sectionid"));
        assertTrue(query.contains("publication.organizationid = :organizationid"));
        assertTrue(query.contains(
                "publication.status = com.dawnrise.academic.results.enums.resultpublicationstatus.published"
        ));
        assertTrue(query.contains("mark.studentuserid = :studentuserid"));
        assertTrue(query.contains("order by publication.publishedat desc, publication.id desc"));
    }
}
