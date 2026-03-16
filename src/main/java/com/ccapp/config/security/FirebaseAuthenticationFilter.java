package com.ccapp.config.security;

import com.ccapp.common.security.AuthenticatedPrincipal;
import com.ccapp.identity.app_user.domain.entity.AppUser;
import com.ccapp.identity.app_user.repository.AppUserRepository;
import com.ccapp.identity.repository.UserRepository;
import com.ccapp.identity.service.FirebaseTokenVerifier;
import com.ccapp.identity.service.FirebaseUserClaims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private final FirebaseTokenVerifier tokenVerifier;
    private final AppUserRepository appUserRepository;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getServletPath();
        if (path.startsWith("/api/admin/") || path.startsWith("/admin/")) {
            filterChain.doFilter(request, response);
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        FirebaseUserClaims claims;
        try {
            claims = tokenVerifier.verify(token);
        } catch (RuntimeException ex) {
            log.warn(
                    "Firebase auth failed path={} method={} reason={}",
                    path,
                    request.getMethod(),
                    ex.getMessage()
            );
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"success\":false,\"message\":\"Invalid or expired token\",\"data\":null}");
            return;
        }

        AppUser appUser = appUserRepository.findByFirebaseUid(claims.uid()).orElse(null);
        UUID legacyUserId = userRepository.findByFirebaseUid(claims.uid()).map(user -> user.getId()).orElse(null);
        if (appUser == null && legacyUserId == null) {
            log.warn("Firebase auth accepted but no local user mapping uid={}", claims.uid());
        }

        AuthenticatedPrincipal principal = AuthenticatedPrincipal.builder()
                .firebaseUid(claims.uid())
                .email(claims.email())
                .userId(appUser != null ? appUser.getId() : legacyUserId)
                .accountHolderId(appUser != null ? appUser.getAccountHolderId() : legacyUserId)
                .internal(false)
                .build();

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
