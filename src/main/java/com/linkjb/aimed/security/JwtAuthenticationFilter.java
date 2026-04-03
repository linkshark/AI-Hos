package com.linkjb.aimed.security;

import com.linkjb.aimed.service.RedisAuthStateService;
import com.linkjb.aimed.service.AppUserService;
import com.linkjb.aimed.entity.AppUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenService jwtTokenService;
    private final RedisAuthStateService redisAuthStateService;
    private final AppUserService appUserService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService,
                                   RedisAuthStateService redisAuthStateService,
                                   AppUserService appUserService) {
        this.jwtTokenService = jwtTokenService;
        this.redisAuthStateService = redisAuthStateService;
        this.appUserService = appUserService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveAccessToken(request);
        if (StringUtils.hasText(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                JwtTokenService.ParsedAccessToken parsed = jwtTokenService.parseAccessToken(token);
                if (!redisAuthStateService.isAccessTokenBlacklisted(parsed.jti())) {
                    AppUser currentUser = appUserService.findById(parsed.userId());
                    if (currentUser == null || !AppUserService.STATUS_ACTIVE.equalsIgnoreCase(currentUser.getStatus())) {
                        filterChain.doFilter(request, response);
                        return;
                    }
                    // 访问请求只信任 access token；refresh token 永远不进入 SecurityContext。
                    AuthenticatedUser principal = new AuthenticatedUser(
                            currentUser.getId(),
                            currentUser.getEmail(),
                            AppUserService.normalizeRole(currentUser.getRole())
                    );
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            principal,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + AppUserService.normalizeRole(currentUser.getRole())))
                    );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception exception) {
                log.warn("auth.jwt.invalid path={} message={}", request.getRequestURI(), exception.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveAccessToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring(7);
    }
}
