package com.linkjb.aimed.service;

import com.linkjb.aimed.bean.RegisterRequest;
import com.linkjb.aimed.config.AuthProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthServiceTest {

    @Test
    void shouldRegisterPatientByDefault() {
        AuthService service = new AuthService(null, null, null, null, null, null, new AuthProperties());

        String role = service.resolveRegisterRole(request(false, null));

        assertEquals(AppUserService.ROLE_PATIENT, role);
    }

    @Test
    void shouldRejectAdminRegisterWhenFeatureDisabled() {
        AuthProperties properties = new AuthProperties();
        properties.setAdminRegisterEnabled(false);
        properties.setAdminRegisterInviteToken("secret-token");
        AuthService service = new AuthService(null, null, null, null, null, null, properties);

        assertThrows(IllegalStateException.class, () -> service.resolveRegisterRole(request(true, "secret-token")));
    }

    @Test
    void shouldRejectAdminRegisterWithWrongInviteToken() {
        AuthProperties properties = new AuthProperties();
        properties.setAdminRegisterEnabled(true);
        properties.setAdminRegisterInviteToken("secret-token");
        AuthService service = new AuthService(null, null, null, null, null, null, properties);

        assertThrows(IllegalArgumentException.class, () -> service.resolveRegisterRole(request(true, "wrong-token")));
    }

    @Test
    void shouldAllowAdminRegisterWithInviteToken() {
        AuthProperties properties = new AuthProperties();
        properties.setAdminRegisterEnabled(true);
        properties.setAdminRegisterInviteToken("secret-token");
        AuthService service = new AuthService(null, null, null, null, null, null, properties);

        String role = service.resolveRegisterRole(request(true, " secret-token "));

        assertEquals(AppUserService.ROLE_ADMIN, role);
    }

    private static RegisterRequest request(Boolean adminRequested, String adminInviteToken) {
        return new RegisterRequest(
                "user@example.com",
                "123456",
                "password123",
                "password123",
                "用户",
                adminRequested,
                adminInviteToken
        );
    }
}
