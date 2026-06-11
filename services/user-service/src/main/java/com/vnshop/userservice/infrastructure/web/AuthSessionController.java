package com.vnshop.userservice.infrastructure.web;

import com.vnshop.userservice.application.AuthSessionUseCase;
import com.vnshop.userservice.application.RefreshTokenRejectedException;
import com.vnshop.userservice.infrastructure.keycloak.KeycloakTokenClient.TokenSet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * httpOnly-cookie auth surface. Replaces the previous flow where the FE
 * called Keycloak's token endpoint directly and stored both tokens in
 * localStorage. The new contract:
 *
 * <ul>
 *   <li>{@code POST /auth/login} — body {@code {username, password}}.
 *       Sets {@code vnshop_rt} httpOnly cookie carrying the refresh token.
 *       Returns the access token + expiry in the response body so the FE
 *       can keep it in memory.</li>
 *   <li>{@code POST /auth/refresh} — reads the cookie, calls Keycloak's
 *       refresh-token grant, rotates the cookie + returns a fresh access
 *       token. No body required.</li>
 *   <li>{@code POST /auth/logout} — best-effort revoke at Keycloak,
 *       always clears the cookie.</li>
 * </ul>
 *
 * <p>The refresh cookie is {@code HttpOnly} (JS can't read it),
 * {@code SameSite=Strict} (blocks cross-site requests entirely), scoped to
 * {@code /auth}, and {@code Secure} when
 * {@code vnshop.auth.cookie-secure=true} (production). A companion
 * non-httpOnly {@code vnshop_csrf} cookie is issued alongside it so the SPA
 * can implement the double-submit CSRF pattern — see
 * {@link CsrfProtectionFilter}.
 *
 * <p>The access token never sits in {@code localStorage} — XSS can't steal
 * it on a refresh because there's no persistent copy.
 */
@RestController
@RequestMapping("/auth")
public class AuthSessionController {
    public static final String REFRESH_COOKIE_NAME = "vnshop_rt";
    private static final String COOKIE_PATH = "/auth";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthSessionUseCase useCase;
    private final boolean cookieSecure;
    private final String cookieSameSite;

    public AuthSessionController(
            AuthSessionUseCase useCase,
            @Value("${vnshop.auth.cookie-secure:false}") boolean cookieSecure,
            @Value("${vnshop.auth.cookie-same-site:Strict}") String cookieSameSite) {
        this.useCase = useCase;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        TokenSet tokens = useCase.login(request.username(), request.password());
        String csrfToken = generateCsrfToken();
        writeRefreshCookie(response, tokens.refreshToken(), tokens.refreshExpiresIn());
        writeCsrfCookie(response, csrfToken, tokens.refreshExpiresIn());
        return ApiResponse.ok(new LoginResponse(tokens.accessToken(), tokens.accessExpiresIn()));
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = readRefreshCookie(request);
        TokenSet tokens;
        try {
            tokens = useCase.refresh(refreshToken);
        } catch (RefreshTokenRejectedException e) {
            // Keycloak rejected the refresh token (expired, revoked, etc.) —
            // clear both cookies so the FE knows to bounce to /login on the
            // next 401 instead of looping on /auth/refresh.
            clearRefreshCookie(response);
            clearCsrfCookie(response);
            throw e;
        }
        String csrfToken = generateCsrfToken();
        writeRefreshCookie(response, tokens.refreshToken(), tokens.refreshExpiresIn());
        writeCsrfCookie(response, csrfToken, tokens.refreshExpiresIn());
        return ApiResponse.ok(new LoginResponse(tokens.accessToken(), tokens.accessExpiresIn()));
    }

    @PostMapping("/logout")
    public ApiResponse<LogoutResponse> logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = readRefreshCookie(request);
        useCase.logout(refreshToken);
        clearRefreshCookie(response);
        clearCsrfCookie(response);
        return ApiResponse.ok(new LogoutResponse(true));
    }

    private void writeRefreshCookie(HttpServletResponse response, String value, int maxAgeSeconds) {
        // Servlet API's Cookie class doesn't expose SameSite, so we compose
        // the Set-Cookie header manually for the SameSite + correct Path
        // semantics. Spring's ResponseCookie would also work but adding
        // org.springframework.http imports here is overkill for one header.
        StringBuilder sb = new StringBuilder();
        sb.append(REFRESH_COOKIE_NAME).append('=').append(value);
        sb.append("; Path=").append(COOKIE_PATH);
        sb.append("; HttpOnly");
        sb.append("; SameSite=").append(cookieSameSite);
        if (cookieSecure) sb.append("; Secure");
        if (maxAgeSeconds > 0) sb.append("; Max-Age=").append(maxAgeSeconds);
        response.addHeader("Set-Cookie", sb.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append(REFRESH_COOKIE_NAME).append('=');
        sb.append("; Path=").append(COOKIE_PATH);
        sb.append("; HttpOnly");
        sb.append("; SameSite=").append(cookieSameSite);
        if (cookieSecure) sb.append("; Secure");
        sb.append("; Max-Age=0");
        response.addHeader("Set-Cookie", sb.toString());
    }

    /**
     * Writes a non-httpOnly CSRF cookie that the SPA can read via
     * {@code document.cookie} and echo back in the {@code X-CSRF-Token} header.
     * Scoped to {@code /auth} (same as the refresh cookie) so it is only sent
     * on auth requests, not on every API call.
     */
    private void writeCsrfCookie(HttpServletResponse response, String token, int maxAgeSeconds) {
        StringBuilder sb = new StringBuilder();
        sb.append(CsrfProtectionFilter.CSRF_COOKIE_NAME).append('=').append(token);
        sb.append("; Path=").append(COOKIE_PATH);
        // Intentionally NOT HttpOnly — the SPA must be able to read this value.
        sb.append("; SameSite=").append(cookieSameSite);
        if (cookieSecure) sb.append("; Secure");
        if (maxAgeSeconds > 0) sb.append("; Max-Age=").append(maxAgeSeconds);
        response.addHeader("Set-Cookie", sb.toString());
    }

    private void clearCsrfCookie(HttpServletResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append(CsrfProtectionFilter.CSRF_COOKIE_NAME).append('=');
        sb.append("; Path=").append(COOKIE_PATH);
        sb.append("; SameSite=").append(cookieSameSite);
        if (cookieSecure) sb.append("; Secure");
        sb.append("; Max-Age=0");
        response.addHeader("Set-Cookie", sb.toString());
    }

    private static String generateCsrfToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                String value = cookie.getValue();
                return (value == null || value.isBlank()) ? null : value;
            }
        }
        return null;
    }
}
