package com.edusphere.identity.platform.user.controller;

import com.edusphere.identity.common.dto.PageResponse;
import com.edusphere.identity.platform.auth.security.PlatformAuthorizationContext;
import com.edusphere.identity.platform.user.dto.CreatePlatformUserRequest;
import com.edusphere.identity.platform.user.dto.PlatformUserResponse;
import com.edusphere.identity.platform.user.enums.PlatformUserStatus;
import com.edusphere.identity.platform.user.service.PlatformUserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/users")
@PreAuthorize("@platformTokenSecurity.isPlatformUser(authentication)")
public class PlatformUserController {

    private final PlatformUserService platformUserService;

    public PlatformUserController(
            PlatformUserService platformUserService
    ) {
        this.platformUserService = platformUserService;
    }

    @PostMapping
    @PreAuthorize("""
        @platformTokenSecurity.isPlatformUser(authentication)
        and hasAuthority('PLATFORM_USER_CREATE')
        and hasAuthority('PLATFORM_ROLE_ASSIGN')
        """)
    public ResponseEntity<PlatformUserResponse> createPlatformUser(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreatePlatformUserRequest request
    ) {
        PlatformUserResponse response =
                platformUserService.createPlatformUser(
                        PlatformAuthorizationContext.fromJwt(jwt),
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{platformUserId}")
    @PreAuthorize("""
        @platformTokenSecurity.isPlatformUser(authentication)
        and hasAuthority('PLATFORM_USER_VIEW')
        """)
    public ResponseEntity<PlatformUserResponse> getPlatformUser(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long platformUserId
    ) {
        PlatformUserResponse response =
                platformUserService.getPlatformUserById(
                        PlatformAuthorizationContext.fromJwt(jwt),
                        platformUserId
                );

        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("""
        @platformTokenSecurity.isPlatformUser(authentication)
        and hasAuthority('PLATFORM_USER_VIEW')
        """)
    public ResponseEntity<PageResponse<PlatformUserResponse>>
    getPlatformUsers(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) PlatformUserStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(
                    page = 0,
                    size = 20,
                    sort = "firstName"
            )
            Pageable pageable
    ) {
        PageResponse<PlatformUserResponse> response =
                platformUserService.getPlatformUsers(
                        PlatformAuthorizationContext.fromJwt(jwt),
                        status,
                        search,
                        pageable
                );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{platformUserId}/activation/resend")
    @PreAuthorize("""
        @platformTokenSecurity.isPlatformUser(authentication)
        and hasAuthority('PLATFORM_USER_ACTIVATE')
        """)
    public ResponseEntity<Void> resendActivationLink(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long platformUserId
    ) {
        platformUserService.resendActivationLink(
                PlatformAuthorizationContext.fromJwt(jwt),
                platformUserId
        );

        return ResponseEntity.accepted().build();
    }
}
