package com.dawnrise.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import com.dawnrise.identity.platform.auth.config.PlatformJwtProperties;
import com.dawnrise.identity.platform.auth.refreshtoken.config.PlatformRefreshTokenProperties;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import com.dawnrise.identity.organization.provisioning.security.InternalServiceAuthenticationFilter;
import com.dawnrise.identity.organization.provisioning.security.InternalServiceSecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

import java.util.Collection;
import java.util.LinkedHashSet;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({
        InternalServiceSecurityProperties.class,
        PlatformJwtProperties.class,
        PlatformRefreshTokenProperties.class
})
public class SecurityConfig {

    @Bean
    public InternalServiceAuthenticationFilter
    internalServiceAuthenticationFilter(
            InternalServiceSecurityProperties properties
    ) {
        return new InternalServiceAuthenticationFilter(properties);
    }

    @Bean
    public FilterRegistrationBean<InternalServiceAuthenticationFilter>
    disableInternalServiceFilterRegistration(
            InternalServiceAuthenticationFilter filter
    ) {
        FilterRegistrationBean<InternalServiceAuthenticationFilter>
                registration =
                new FilterRegistrationBean<>(filter);

        registration.setEnabled(false);

        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            InternalServiceAuthenticationFilter internalServiceFilter
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
                                HttpMethod.POST,
                                "/api/v1/platform/auth/login"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/platform/auth/refresh"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/platform/auth/logout"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/login"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/auth/activation/validate"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/activation/complete"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/activation/resend"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/platform/auth/activation/validate"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/platform/auth/activation/complete"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/platform/auth/activation/resend"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/password-reset/request"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/auth/password-reset/validate"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/password-reset/complete"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/refresh"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/logout"
                        ).permitAll()

                        .requestMatchers("/internal/**")
                        .hasRole("INTERNAL_SERVICE")
                        .anyRequest().authenticated()
                )

                .addFilterBefore(
                        internalServiceFilter,
                        BearerTokenAuthenticationFilter.class
                )

                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(
                                        jwtAuthenticationConverter
                                )
                        )
                );

        return http.build();
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

            authorities.addAll(rolesReader.convert(jwt));
            authorities.addAll(permissionsReader.convert(jwt));

            return authorities;
        };

        JwtAuthenticationConverter jwtAuthenticationConverter =
                new JwtAuthenticationConverter();

        jwtAuthenticationConverter
                .setJwtGrantedAuthoritiesConverter(
                        combinedAuthoritiesConverter
                );

        return jwtAuthenticationConverter;
    }
}
