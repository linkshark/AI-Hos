package com.linkjb.aimed.controller;

import com.linkjb.aimed.entity.dto.response.AuthUserResponse;
import com.linkjb.aimed.entity.dto.request.LoginRequest;
import com.linkjb.aimed.entity.dto.request.LogoutRequest;
import com.linkjb.aimed.entity.dto.response.MessageResponse;
import com.linkjb.aimed.entity.dto.request.PasswordResetRequest;
import com.linkjb.aimed.entity.dto.request.PasswordResetSendCodeRequest;
import com.linkjb.aimed.entity.dto.request.RefreshTokenRequest;
import com.linkjb.aimed.entity.dto.request.RegisterRequest;
import com.linkjb.aimed.entity.dto.request.RegisterSendCodeRequest;
import com.linkjb.aimed.entity.dto.response.TokenPairResponse;
import com.linkjb.aimed.security.AuthenticatedUser;
import com.linkjb.aimed.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth")
@RestController
@RequestMapping("/aimed/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "发送注册邮箱验证码")
    @PostMapping("/register/send-code")
    public MessageResponse sendRegisterCode(@Valid @RequestBody RegisterSendCodeRequest request) {
        return authService.sendRegisterCode(request);
    }

    @Operation(summary = "发送密码重置邮箱验证码")
    @PostMapping("/password/send-code")
    public MessageResponse sendPasswordResetCode(@Valid @RequestBody PasswordResetSendCodeRequest request) {
        return authService.sendPasswordResetCode(request);
    }

    @Operation(summary = "注册并登录")
    @PostMapping("/register")
    public TokenPairResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @Operation(summary = "邮箱登录")
    @PostMapping("/login")
    public TokenPairResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "刷新登录态")
    @PostMapping("/refresh")
    public TokenPairResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @Operation(summary = "邮箱验证码重置密码")
    @PostMapping("/password/reset")
    public MessageResponse resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        return authService.resetPassword(request);
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public MessageResponse logout(@RequestBody(required = false) LogoutRequest request,
                                  @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String accessToken = resolveBearerToken(authorization);
        return authService.logout(accessToken, request == null ? null : request.refreshToken());
    }

    @Operation(summary = "当前登录用户")
    @GetMapping("/me")
    public AuthUserResponse me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return authService.me(currentUser);
    }

    private String resolveBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring(7);
    }
}
