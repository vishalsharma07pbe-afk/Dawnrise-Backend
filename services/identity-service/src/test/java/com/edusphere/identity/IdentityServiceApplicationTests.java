package com.edusphere.identity;

import com.edusphere.identity.auth.activation.repository.UserActivationTokenRepository;
import com.edusphere.identity.auth.passwordreset.repository.UserPasswordResetTokenRepository;
import com.edusphere.identity.auth.refreshtoken.repository.RefreshTokenRepository;
import com.edusphere.identity.organization.provisioning.repository.OrganizationProvisioningRequestRepository;
import com.edusphere.identity.organization.repository.OrganizationRepository;
import com.edusphere.identity.permission.repository.RolePermissionRepository;
import com.edusphere.identity.platform.audit.repository.PlatformSecurityAuditEventRepository;
import com.edusphere.identity.platform.auth.activation.repository.PlatformUserActivationTokenRepository;
import com.edusphere.identity.platform.auth.refreshtoken.repository.PlatformRefreshTokenRepository;
import com.edusphere.identity.platform.permission.repository.PlatformRolePermissionRepository;
import com.edusphere.identity.platform.user.repository.PlatformUserRepository;
import com.edusphere.identity.roleapproval.repository.RoleAssignmentApprovalRepository;
import com.edusphere.identity.roleapproval.repository.RoleAssignmentRequestRepository;
import com.edusphere.identity.roleremoval.repository.RoleRemovalApprovalRepository;
import com.edusphere.identity.roleremoval.repository.RoleRemovalRequestRepository;
import com.edusphere.identity.securityaudit.repository.SecurityAuditEventRepository;
import com.edusphere.identity.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "spring.autoconfigure.exclude="
        + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
        + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration")
class IdentityServiceApplicationTests {

    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private RoleAssignmentRequestRepository requestRepository;
    @MockitoBean
    private RoleAssignmentApprovalRepository approvalRepository;
    @MockitoBean
    private RoleRemovalRequestRepository removalRequestRepository;
    @MockitoBean
    private RoleRemovalApprovalRepository removalApprovalRepository;
    @MockitoBean
    private SecurityAuditEventRepository securityAuditEventRepository;
    @MockitoBean
    private UserActivationTokenRepository activationTokenRepository;
    @MockitoBean
    private UserPasswordResetTokenRepository passwordResetTokenRepository;
    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;
    @MockitoBean
    private RolePermissionRepository rolePermissionRepository;
    @MockitoBean
    private OrganizationRepository organizationRepository;
    @MockitoBean
    private OrganizationProvisioningRequestRepository
            organizationProvisioningRequestRepository;
    @MockitoBean
    private PlatformUserRepository platformUserRepository;
    @MockitoBean
    private PlatformUserActivationTokenRepository
            platformUserActivationTokenRepository;
    @MockitoBean
    private PlatformRolePermissionRepository
            platformRolePermissionRepository;
    @MockitoBean
    private PlatformRefreshTokenRepository platformRefreshTokenRepository;
    @MockitoBean
    private PlatformSecurityAuditEventRepository
            platformSecurityAuditEventRepository;
    @MockitoBean
    private JavaMailSender mailSender;

    @Test
    void contextLoads() {
    }

}
