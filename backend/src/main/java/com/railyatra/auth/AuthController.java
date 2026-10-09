package com.railyatra.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String REFRESH_COOKIE = "railyatra_refresh";
    private final AuthService authService;
    private final int refreshDays;
    private final boolean refreshCookieSecure;

    public AuthController(AuthService authService, @Value("${railyatra.auth.refresh-days}") int refreshDays,
                          @Value("${railyatra.auth.refresh-cookie-secure}") boolean refreshCookieSecure) {
        this.authService = authService;
        this.refreshDays = refreshDays;
        this.refreshCookieSecure = refreshCookieSecure;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) { return authenticated(HttpStatus.CREATED, authService.register(request)); }
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) { return authenticated(HttpStatus.OK, authService.login(request)); }
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String token) { return authenticated(HttpStatus.OK, authService.refresh(token)); }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String token) {
        authService.logout(token);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString()).build();
    }
    @GetMapping("/me")
    public PublicUser me(Authentication authentication) { return authService.currentUser((AuthPrincipal) authentication.getPrincipal()); }

    private ResponseEntity<AuthResponse> authenticated(HttpStatus status, AuthService.AuthResult result) {
        return ResponseEntity.status(status).header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken()).toString()).body(result.response());
    }
    private ResponseCookie refreshCookie(String token) {
        return ResponseCookie.from(REFRESH_COOKIE, token).httpOnly(true).secure(refreshCookieSecure).sameSite("Strict")
            .path("/api/auth").maxAge(Duration.ofDays(refreshDays)).build();
    }
    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(REFRESH_COOKIE, "").httpOnly(true).secure(refreshCookieSecure).sameSite("Strict")
            .path("/api/auth").maxAge(Duration.ZERO).build();
    }

    public record RegisterRequest(@NotBlank @Size(max = 140) String fullName, @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "^[0-9+ -]{7,20}$", message = "Enter a valid phone number") String phone,
        @NotBlank @Size(min = 8, max = 72) String password, @NotBlank String confirmPassword) { }
    public record LoginRequest(@NotBlank String identifier, @NotBlank String password) { }
    public record PublicUser(Long id, String fullName, String email, String phone, String role) { }
    public record AuthResponse(String accessToken, long expiresInSeconds, PublicUser user) { }
}
