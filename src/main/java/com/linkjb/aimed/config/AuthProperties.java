package com.linkjb.aimed.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {
    private String jwtSecret;
    private Duration accessExpires = Duration.ofMinutes(30);
    private Duration refreshExpires = Duration.ofDays(14);
    private Duration verificationExpires = Duration.ofMinutes(10);
    private boolean mailMockEnabled = true;
    private boolean adminBootstrapEnabled = true;
    private boolean adminRegisterEnabled = false;
    private String adminRegisterInviteToken ;
    private String adminUsername = "admin";
    private String adminEmail = "admin@shulan.local";
    private String adminPassword = "change-me-admin-password";
    private String adminNickname = "树兰管理员";

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public Duration getAccessExpires() {
        return accessExpires;
    }

    public void setAccessExpires(Duration accessExpires) {
        this.accessExpires = accessExpires;
    }

    public Duration getRefreshExpires() {
        return refreshExpires;
    }

    public void setRefreshExpires(Duration refreshExpires) {
        this.refreshExpires = refreshExpires;
    }

    public Duration getVerificationExpires() {
        return verificationExpires;
    }

    public void setVerificationExpires(Duration verificationExpires) {
        this.verificationExpires = verificationExpires;
    }

    public boolean isMailMockEnabled() {
        return mailMockEnabled;
    }

    public void setMailMockEnabled(boolean mailMockEnabled) {
        this.mailMockEnabled = mailMockEnabled;
    }

    public boolean isAdminBootstrapEnabled() {
        return adminBootstrapEnabled;
    }

    public void setAdminBootstrapEnabled(boolean adminBootstrapEnabled) {
        this.adminBootstrapEnabled = adminBootstrapEnabled;
    }

    public boolean isAdminRegisterEnabled() {
        return adminRegisterEnabled;
    }

    public void setAdminRegisterEnabled(boolean adminRegisterEnabled) {
        this.adminRegisterEnabled = adminRegisterEnabled;
    }

    public String getAdminRegisterInviteToken() {
        return adminRegisterInviteToken;
    }

    public void setAdminRegisterInviteToken(String adminRegisterInviteToken) {
        this.adminRegisterInviteToken = adminRegisterInviteToken;
    }

    public String getAdminUsername() {
        return adminUsername;
    }

    public void setAdminUsername(String adminUsername) {
        this.adminUsername = adminUsername;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public String getAdminNickname() {
        return adminNickname;
    }

    public void setAdminNickname(String adminNickname) {
        this.adminNickname = adminNickname;
    }
}
