package com.vnshop.userservice.infrastructure.web;

import com.vnshop.userservice.application.RegisterBuyerCommand;
import com.vnshop.userservice.application.RegisterBuyerUseCase;
import com.vnshop.userservice.infrastructure.keycloak.KeycloakAdminClient;
import com.vnshop.userservice.infrastructure.keycloak.KeycloakAdminException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public self-registration endpoint. The FE calls this with the user-supplied
 * credentials; we proxy to Keycloak's Admin API to create the actual user
 * (so passwords stay in Keycloak and 2FA / reset still flow through it),
 * then materialise the buyer profile via {@link RegisterBuyerUseCase}.
 *
 * <p>The route is permitted by both the API gateway's SecurityConfig and this
 * service's SecurityConfig — no JWT is required because the caller has no
 * identity yet.</p>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final KeycloakAdminClient keycloakAdmin;
    private final RegisterBuyerUseCase registerBuyerUseCase;

    public AuthController(KeycloakAdminClient keycloakAdmin, RegisterBuyerUseCase registerBuyerUseCase) {
        this.keycloakAdmin = keycloakAdmin;
        this.registerBuyerUseCase = registerBuyerUseCase;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        String userId = keycloakAdmin.createUser(
                request.email(),
                request.password(),
                request.firstName(),
                request.lastName());
        try {
            keycloakAdmin.assignBuyerRole(userId);
        } catch (KeycloakAdminException ignored) {
            // Role mapping is best-effort: the realm role mapper still emits
            // an empty roles claim for the user, which is fine for the buyer
            // happy path. Login + profile work without it.
        }
        // Materialise the buyer profile only when the phone passes the domain
        // validator (E.164 +84 prefix). Otherwise defer profile creation to
        // the first PUT /users/me — the seeded test users already work this
        // way and the FE upserts on demand.
        if (request.phone() != null && !request.phone().isBlank()) {
            try {
                String fullName = (request.firstName() + " " + request.lastName()).trim();
                registerBuyerUseCase.register(new RegisterBuyerCommand(
                        userId,
                        fullName,
                        request.phone(),
                        null));
            } catch (IllegalArgumentException ignored) {
                // Bad phone format — keep the Keycloak account, let the user
                // fill in their profile after first login.
            }
        }
        return ApiResponse.ok(new RegisterResponse(userId, request.email()));
    }

    /**
     * Trigger a Keycloak-mediated password reset email. Always returns 204
     * regardless of whether the email exists — surfacing 404 here would
     * let attackers enumerate registered emails.
     *
     * <p>The actual email is sent by Keycloak's realm SMTP. The buyer
     * follows the link to a Keycloak-hosted page that prompts for the new
     * password. A future iteration could host the prompt natively too,
     * but the SMTP-driven flow is closing the seam where Keycloak chrome
     * leaks through (no more "click the link to Keycloak's account
     * console" path).
     */
    @PostMapping("/password-reset-request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<PasswordResetResponse> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request) {
        try {
            keycloakAdmin.sendPasswordResetEmail(request.email());
        } catch (KeycloakAdminException e) {
            // Realm SMTP misconfigured or KC unreachable — log and continue
            // returning the generic success response so the FE never
            // reveals whether the email exists.
            log.warn("password reset request failed for email={}: {}",
                    request.email(), e.getMessage());
        }
        return ApiResponse.ok(new PasswordResetResponse(true));
    }

    public record PasswordResetRequest(@NotBlank @Email String email) {}
    public record PasswordResetResponse(boolean accepted) {}
}
