package com.dawnrise.academic.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.web.client.RestClient;
import com.dawnrise.academic.common.integration.identity.IdentityServiceProperties;
import com.dawnrise.academic.security.parentsession.ParentSessionEnforcementFilter;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import java.util.Collection;
import java.util.LinkedHashSet;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            ParentSessionEnforcementFilter parentSessionFilter
    ) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()

                        .anyRequest().authenticated()
                )

                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(
                                        jwtAuthenticationConverter
                                )
                        )
                )
                .addFilterAfter(parentSessionFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    ParentSessionEnforcementFilter parentSessionEnforcementFilter(ApplicationContext context) {
        RestClient restClient = context.containsBean("identityServiceRestClient")
                ? context.getBean("identityServiceRestClient", RestClient.class)
                : null;
        IdentityServiceProperties properties = context
                .getBeanProvider(IdentityServiceProperties.class)
                .getIfAvailable();
        return new ParentSessionEnforcementFilter(restClient, properties);
    }

    @Bean
    FilterRegistrationBean<ParentSessionEnforcementFilter>
    disableParentSessionFilterRegistration(ParentSessionEnforcementFilter filter) {
        FilterRegistrationBean<ParentSessionEnforcementFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {

        JwtGrantedAuthoritiesConverter rolesReader =
                new JwtGrantedAuthoritiesConverter();

        rolesReader.setAuthoritiesClaimName("roles");
        rolesReader.setAuthorityPrefix("ROLE_");

        JwtGrantedAuthoritiesConverter permissionsReader =
                new JwtGrantedAuthoritiesConverter();

        permissionsReader.setAuthoritiesClaimName("permissions");
        permissionsReader.setAuthorityPrefix("");

        Converter<Jwt, Collection<GrantedAuthority>>
                combinedAuthoritiesConverter = jwt -> {

            Collection<GrantedAuthority> authorities =
                    new LinkedHashSet<>();

            Collection<GrantedAuthority> roles =
                    rolesReader.convert(jwt);

            Collection<GrantedAuthority> permissions =
                    permissionsReader.convert(jwt);

            if (roles != null) {
                authorities.addAll(roles);
            }

            if (permissions != null) {
                authorities.addAll(permissions);
            }

            return authorities;
        };

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(
                combinedAuthoritiesConverter
        );

        return converter;
    }
}
