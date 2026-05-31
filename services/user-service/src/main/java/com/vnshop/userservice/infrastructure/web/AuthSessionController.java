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
 * <p>The cookie is {@code HttpOnly} (JS can't read it), {@code SameSite=Lax}
 * (sufficient for first-party auth; protects against CSRF on top-level GETs),
 * scoped to {@code /auth} (the only path that needs to read it), and
 * {@code Secure} when {@code vnshop.auth.cookie-secure=true} (production).
 *
 * <p>The access token never sits in {@code localStorage} again — XSS can't
 * steal it on a refresh because there's no persistent copy.
 */
@RestController
@RequestMapping("/auth")
public class AuthSessionController {
    public static final String REFRESH_COOKIE_NAME = "vnshop_rt";
    private static final String COOKIE_PATH = "/auth";

    private final AuthSessionUseCase useCase;
    private final boolean cookieSecure;
    private final String cookieSameSite;

    public AuthSessionController(
            AuthSessionUseCase useCase,
            @Value("${vnshop.auth.cookie-secure:false}") boolean cookieSecure,
            @Value("${vnshop.auth.cookie-same-site:Lax}") String cookieSameSite) {
        this.useCase = useCase;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        TokenSet tokens = useCase.login(request.username(), request.password());
        writeRefreshCookie(response, tokens.refreshToken(), tokens.refreshExpiresIn());
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
            // clear the cookie so the FE knows to bounce to /login on the
            // next 401 instead of looping on /auth/refresh.
            clearRefreshCookie(response);
            throw e;
        }
        writeRefreshCookie(response, tokens.refreshToken(), tokens.refreshExpiresIn());
        return ApiResponse.ok(new LoginResponse(tokens.accessToken(), tokens.accessExpiresIn()));
    }

    @PostMapping("/logout")
    public ApiResponse<LogoutResponse> logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = readRefreshCookie(request);
        useCase.logout(refreshToken);
        clearRefreshCookie(response);
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
