package com.dawnrise.school.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import com.dawnrise.school.school.config.SchoolProperties;
import com.dawnrise.school.security.InternalServiceAuthenticationFilter;
import com.dawnrise.school.security.InternalServiceErrorResponseWriter;
import com.dawnrise.school.security.InternalServiceSecurityProperties;
import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.LinkedHashSet;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties({
        SchoolProperties.class,
        InternalServiceSecurityProperties.class
})
public class SecurityConfig {

    @Bean
    public InternalServiceErrorResponseWriter
    internalServiceErrorResponseWriter(
            ObjectMapper objectMapper
    ) {
        return new InternalServiceErrorResponseWriter(objectMapper);
    }

    @Bean
    public InternalServiceAuthenticationFilter
    internalServiceAuthenticationFilter(
            InternalServiceSecurityProperties properties,
            InternalServiceErrorResponseWriter errorResponseWriter
    ) {
        return new InternalServiceAuthenticationFilter(
                properties,
                errorResponseWriter
        );
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
                                "/actuator/health",
                                "/actuator/info"
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
