package com.linkjb.aimed.service;

import com.linkjb.aimed.bean.AuthUserResponse;
import com.linkjb.aimed.bean.LoginRequest;
import com.linkjb.aimed.bean.MessageResponse;
import com.linkjb.aimed.bean.PasswordResetRequest;
import com.linkjb.aimed.bean.PasswordResetSendCodeRequest;
import com.linkjb.aimed.bean.RefreshTokenRequest;
import com.linkjb.aimed.bean.RegisterRequest;
import com.linkjb.aimed.bean.RegisterSendCodeRequest;
import com.linkjb.aimed.bean.TokenPairResponse;
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

    public AuthService(AppUserService appUserService,
                       PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService,
                       RedisAuthStateService redisAuthStateService,
                       MailSenderService mailSenderService) {
        this.appUserService = appUserService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.redisAuthStateService = redisAuthStateService;
        this.mailSenderService = mailSenderService;
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
        if (!redisAuthStateService.consumeRegisterCode(email, request.code())) {
            throw new IllegalArgumentException("验证码错误或已过期");
        }
        AppUser user = appUserService.createUser(email, passwordEncoder.encode(request.password()), request.nickname(), AppUserService.ROLE_USER);
        log.info("auth.register.success userId={} email={}", user.getId(), user.getEmail());
        return issueTokenPair(user);
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
        return new MessageResponse("密码已重置，请使用新密码登录");
    }

    public MessageResponse logout(String accessToken, String refreshToken) {
        if (StringUtils.hasText(accessToken)) {
            try {
                JwtTokenService.ParsedAccessToken parsed = jwtTokenService.parseAccessToken(accessToken);
                redisAuthStateService.blacklistAccessToken(parsed.jti(), parsed.expiresAt());
            } catch (Exception ignored) {
                // Logout should remain idempotent even if the access token is already expired.
            }
        }
        redisAuthStateService.revokeRefreshToken(refreshToken);
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
        return new AuthUserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getNickname(), user.getRole(), user.getStatus());
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
