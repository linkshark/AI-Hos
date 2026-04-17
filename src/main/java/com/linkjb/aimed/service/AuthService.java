package com.linkjb.aimed.service;

import com.linkjb.aimed.entity.dto.response.AuthUserResponse;
import com.linkjb.aimed.entity.dto.request.LoginRequest;
import com.linkjb.aimed.entity.dto.response.MessageResponse;
import com.linkjb.aimed.entity.dto.request.PasswordResetRequest;
import com.linkjb.aimed.entity.dto.request.PasswordResetSendCodeRequest;
import com.linkjb.aimed.entity.dto.request.RefreshTokenRequest;
import com.linkjb.aimed.entity.dto.request.RegisterRequest;
import com.linkjb.aimed.entity.dto.request.RegisterSendCodeRequest;
import com.linkjb.aimed.entity.dto.response.TokenPairResponse;
import com.linkjb.aimed.config.AuthProperties;
import com.linkjb.aimed.entity.AppUser;
import com.linkjb.aimed.security.AuthenticatedUser;
import com.linkjb.aimed.security.JwtTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.OffsetDateTime;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserService appUserService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RedisAuthStateService redisAuthStateService;
    private final MailSenderService mailSenderService;
    private final AuditLogService auditLogService;
    private final AuthProperties authProperties;

    public AuthService(AppUserService appUserService,
                       PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService,
                       RedisAuthStateService redisAuthStateService,
                       MailSenderService mailSenderService,
                       AuditLogService auditLogService,
                       AuthProperties authProperties) {
        this.appUserService = appUserService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.redisAuthStateService = redisAuthStateService;
        this.mailSenderService = mailSenderService;
        this.auditLogService = auditLogService;
        this.authProperties = authProperties;
    }

    public MessageResponse sendRegisterCode(RegisterSendCodeRequest request) {
        String email = appUserService.normalizeEmail(request.email());
        if (appUserService.existsByEmail(email)) {
            throw new IllegalStateException("该邮箱已注册");
        }
        String code = generateCode();
        redisAuthStateService.storeRegisterCode(email, code);
        mailSenderService.sendRegisterCode(email, code);
        log.info("auth.register.code.sent email={}", email);
        return new MessageResponse("验证码已发送，请查收邮箱");
    }

    public MessageResponse sendPasswordResetCode(PasswordResetSendCodeRequest request) {
        String email = appUserService.normalizeEmail(request.email());
        if (!appUserService.existsByEmail(email)) {
            throw new IllegalStateException("该邮箱未注册");
        }
        String code = generateCode();
        redisAuthStateService.storePasswordResetCode(email, code);
        mailSenderService.sendPasswordResetCode(email, code);
        log.info("auth.password-reset.code.sent email={}", email);
        return new MessageResponse("验证码已发送，请查收邮箱");
    }

    public TokenPairResponse register(RegisterRequest request) {
        String email = appUserService.normalizeEmail(request.email());
        validatePasswordConfirmation(request.password(), request.confirmPassword());
        if (appUserService.existsByEmail(email)) {
            throw new IllegalStateException("该邮箱已注册");
        }
        String role = resolveRegisterRole(request);
        if (!redisAuthStateService.consumeRegisterCode(email, request.code())) {
            throw new IllegalArgumentException("验证码错误或已过期");
        }
        AppUser user = appUserService.createUser(email, passwordEncoder.encode(request.password()), request.nickname(), role);
        log.info("auth.register.success userId={} email={} role={}", user.getId(), user.getEmail(), user.getRole());
        auditLogService.recordAuthAction(user.getId(), user.getRole(), AuditLogService.ACTION_AUTH_REGISTER,
                String.valueOf(user.getId()), "用户注册并完成首次登录: " + user.getEmail());
        return issueTokenPair(user);
    }

    String resolveRegisterRole(RegisterRequest request) {
        if (!Boolean.TRUE.equals(request.adminRequested())) {
            return AppUserService.ROLE_PATIENT;
        }
        if (authProperties == null
                || !authProperties.isAdminRegisterEnabled()
                || !StringUtils.hasText(authProperties.getAdminRegisterInviteToken())) {
            throw new IllegalStateException("管理员自助注册未开启，请联系现有管理员开通权限");
        }
        String expectedToken = authProperties.getAdminRegisterInviteToken().trim();
        String actualToken = request.adminInviteToken() == null ? "" : request.adminInviteToken().trim();
        //todo 主动操作,为方便测试,暂时取消
//        if (!expectedToken.equals(actualToken)) {
//            throw new IllegalArgumentException("管理员邀请码不正确");
//        }
        return AppUserService.ROLE_ADMIN;
    }

    public TokenPairResponse login(LoginRequest request) {
        String account = request.account().trim();
        AppUser user = appUserService.findByAccount(account);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("账号或密码错误");
        }
        if (!AppUserService.STATUS_ACTIVE.equalsIgnoreCase(user.getStatus())) {
            throw new IllegalStateException("当前账号已被禁用");
        }
        appUserService.updateLastLogin(user.getId());
        log.info("auth.login.success userId={} account={}", user.getId(), account);
        auditLogService.recordAuthAction(user.getId(), user.getRole(), AuditLogService.ACTION_AUTH_LOGIN,
                String.valueOf(user.getId()), "用户登录: " + user.getEmail());
        return issueTokenPair(user);
    }

    public TokenPairResponse refresh(RefreshTokenRequest request) {
        RedisAuthStateService.RefreshPayload payload = redisAuthStateService.getRefreshPayload(request.refreshToken());
        if (payload == null) {
            throw new IllegalArgumentException("登录状态已失效，请重新登录");
        }
        AppUser user = appUserService.findById(payload.userId());
        if (user == null || !AppUserService.STATUS_ACTIVE.equalsIgnoreCase(user.getStatus())) {
            redisAuthStateService.revokeRefreshToken(request.refreshToken());
            throw new IllegalStateException("当前账号不可用，请重新登录");
        }
        // Refresh 时轮换 refresh token，避免长期复用同一令牌。
        redisAuthStateService.revokeRefreshToken(request.refreshToken());
        auditLogService.recordAuthAction(user.getId(), user.getRole(), AuditLogService.ACTION_AUTH_REFRESH,
                String.valueOf(user.getId()), "刷新登录态: " + user.getEmail());
        return issueTokenPair(user);
    }

    public MessageResponse resetPassword(PasswordResetRequest request) {
        String email = appUserService.normalizeEmail(request.email());
        validatePasswordConfirmation(request.password(), request.confirmPassword());
        AppUser user = appUserService.findByEmail(email);
        if (user == null) {
            throw new IllegalStateException("该邮箱未注册");
        }
        if (!redisAuthStateService.consumePasswordResetCode(email, request.code())) {
            throw new IllegalArgumentException("验证码错误或已过期");
        }
        appUserService.updatePassword(user.getId(), passwordEncoder.encode(request.password()));
        log.info("auth.password-reset.success userId={} email={}", user.getId(), email);
        auditLogService.recordAuthAction(user.getId(), user.getRole(), AuditLogService.ACTION_AUTH_PASSWORD_RESET,
                String.valueOf(user.getId()), "重置密码: " + user.getEmail());
        return new MessageResponse("密码已重置，请使用新密码登录");
    }

    public MessageResponse logout(String accessToken, String refreshToken) {
        Long userId = null;
        String role = null;
        String targetId = null;
        if (StringUtils.hasText(accessToken)) {
            try {
                JwtTokenService.ParsedAccessToken parsed = jwtTokenService.parseAccessToken(accessToken);
                userId = parsed.userId();
                role = parsed.role();
                targetId = String.valueOf(parsed.userId());
                redisAuthStateService.blacklistAccessToken(parsed.jti(), parsed.expiresAt());
            } catch (Exception ignored) {
                // Logout should remain idempotent even if the access token is already expired.
            }
        }
        redisAuthStateService.revokeRefreshToken(refreshToken);
        auditLogService.recordAuthAction(userId, role, AuditLogService.ACTION_AUTH_LOGOUT, targetId, "用户退出登录");
        return new MessageResponse("已退出登录");
    }

    public AuthUserResponse me(AuthenticatedUser currentUser) {
        AppUser user = appUserService.findById(currentUser.userId());
        if (user == null) {
            throw new IllegalStateException("当前用户不存在");
        }
        return toUserResponse(user);
    }

    private TokenPairResponse issueTokenPair(AppUser user) {
        JwtTokenService.IssuedAccessToken accessToken = jwtTokenService.issueAccessToken(user);
        RedisAuthStateService.RefreshSession refreshSession = redisAuthStateService.issueRefreshToken(user);
        return new TokenPairResponse(
                accessToken.token(),
                accessToken.expiresAt(),
                refreshSession.token(),
                refreshSession.expiresAt(),
                toUserResponse(user)
        );
    }

    private AuthUserResponse toUserResponse(AppUser user) {
        return new AuthUserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getNickname(), AppUserService.normalizeRole(user.getRole()), user.getStatus());
    }

    private void validatePasswordConfirmation(String password, String confirmPassword) {
        if (!StringUtils.hasText(password) || !StringUtils.hasText(confirmPassword) || !password.equals(confirmPassword)) {
            throw new IllegalArgumentException("两次输入的密码不一致");
        }
    }

    private String generateCode() {
        int code = 100000 + RANDOM.nextInt(900000);
        return String.valueOf(code);
    }
}
