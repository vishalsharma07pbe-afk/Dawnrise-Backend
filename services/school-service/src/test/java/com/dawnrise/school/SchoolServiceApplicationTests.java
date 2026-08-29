package com.dawnrise.school;

import com.dawnrise.school.school.provisioning.IdentityProvisioningClient;
import com.dawnrise.school.school.repository.SchoolProvisioningRepository;
import com.dawnrise.school.school.repository.schoolRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
				+ "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
class SchoolServiceApplicationTests {

	@MockitoBean
	private schoolRepository schoolRepository;

	@MockitoBean
	private SchoolProvisioningRepository provisioningRepository;

	@MockitoBean
	private IdentityProvisioningClient identityProvisioningClient;

	@MockitoBean
	private TransactionTemplate transactionTemplate;

	@Test
	void contextLoads() {
	}

}
