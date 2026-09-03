package com.dawnrise.academic;

import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.gradelevel.repository.GradeLevelRepository;
import com.dawnrise.academic.gradelevelsubject.repository.GradeLevelSubjectRepository;
import com.dawnrise.academic.section.repository.SectionRepository;
import com.dawnrise.academic.subject.repository.SubjectRepository;
import com.dawnrise.academic.teacherassignment.integration.identity.IdentityTeacherEligibilityClient;
import com.dawnrise.academic.teacherassignment.repository.TeacherAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.lang.reflect.Proxy;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=" +
				"org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
				"org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration," +
				"org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
class AcademicServiceApplicationTests {

	@Test
	void contextLoads() {
	}

	@TestConfiguration
	static class TestConfig {

		@Bean
		AcademicYearRepository academicYearRepository() {
			return (AcademicYearRepository) Proxy.newProxyInstance(
					AcademicYearRepository.class.getClassLoader(),
					new Class<?>[]{AcademicYearRepository.class},
					(proxy, method, args) -> {
						if (method.getName().equals("hashCode")) {
							return System.identityHashCode(proxy);
						}
						if (method.getName().equals("equals")) {
							return proxy == args[0];
						}
						if (method.getName().equals("toString")) {
							return "AcademicYearRepositoryStub";
						}
						throw new UnsupportedOperationException(method.getName());
					}
			);
		}

		@Bean
		GradeLevelRepository gradeLevelRepository() {
			return (GradeLevelRepository) Proxy.newProxyInstance(
					GradeLevelRepository.class.getClassLoader(),
					new Class<?>[]{GradeLevelRepository.class},
					(proxy, method, args) -> {
						if (method.getName().equals("hashCode")) {
							return System.identityHashCode(proxy);
						}
						if (method.getName().equals("equals")) {
							return proxy == args[0];
						}
						if (method.getName().equals("toString")) {
							return "GradeLevelRepositoryStub";
						}
						throw new UnsupportedOperationException(method.getName());
					}
			);
		}

		@Bean
		SectionRepository sectionRepository() {
			return (SectionRepository) Proxy.newProxyInstance(
					SectionRepository.class.getClassLoader(),
					new Class<?>[]{SectionRepository.class},
					(proxy, method, args) -> {
						if (method.getName().equals("hashCode")) {
							return System.identityHashCode(proxy);
						}
						if (method.getName().equals("equals")) {
							return proxy == args[0];
						}
						if (method.getName().equals("toString")) {
							return "SectionRepositoryStub";
						}
						throw new UnsupportedOperationException(method.getName());
					}
			);
		}

		@Bean
		SubjectRepository subjectRepository() {
			return (SubjectRepository) Proxy.newProxyInstance(
					SubjectRepository.class.getClassLoader(),
					new Class<?>[]{SubjectRepository.class},
					(proxy, method, args) -> {
						if (method.getName().equals("hashCode")) {
							return System.identityHashCode(proxy);
						}
						if (method.getName().equals("equals")) {
							return proxy == args[0];
						}
						if (method.getName().equals("toString")) {
							return "SubjectRepositoryStub";
						}
						throw new UnsupportedOperationException(method.getName());
					}
			);
		}

		@Bean
		GradeLevelSubjectRepository gradeLevelSubjectRepository() {
			return (GradeLevelSubjectRepository) Proxy.newProxyInstance(
					GradeLevelSubjectRepository.class.getClassLoader(),
					new Class<?>[]{GradeLevelSubjectRepository.class},
					(proxy, method, args) -> {
						if (method.getName().equals("hashCode")) {
							return System.identityHashCode(proxy);
						}
						if (method.getName().equals("equals")) {
							return proxy == args[0];
						}
						if (method.getName().equals("toString")) {
							return "GradeLevelSubjectRepositoryStub";
						}
						throw new UnsupportedOperationException(method.getName());
					}
			);
		}

		@Bean
		TeacherAssignmentRepository teacherAssignmentRepository() {
			return (TeacherAssignmentRepository) Proxy.newProxyInstance(
					TeacherAssignmentRepository.class.getClassLoader(),
					new Class<?>[]{TeacherAssignmentRepository.class},
					(proxy, method, args) -> {
						if (method.getName().equals("hashCode")) {
							return System.identityHashCode(proxy);
						}
						if (method.getName().equals("equals")) {
							return proxy == args[0];
						}
						if (method.getName().equals("toString")) {
							return "TeacherAssignmentRepositoryStub";
						}
						throw new UnsupportedOperationException(method.getName());
					}
			);
		}

		@Bean
		@Primary
		IdentityTeacherEligibilityClient identityTeacherEligibilityClient() {
			return (organizationId, userId) -> {
				throw new UnsupportedOperationException(
						"IdentityTeacherEligibilityClientStub"
				);
			};
		}
	}

}
