package com.dawnrise.academic;

import com.dawnrise.academic.academicyear.repository.AcademicYearRepository;
import com.dawnrise.academic.gradelevel.repository.GradeLevelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

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
	}

}
