package com.railyatra.auth;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final UserRepository users;
    private final RefreshSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final int refreshDays;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, RefreshSessionRepository sessions, PasswordEncoder passwordEncoder,
                       JwtService jwtService, @Value("${railyatra.auth.refresh-days}") int refreshDays) {
        this.users = users;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshDays = refreshDays;
    }

    @Transactional
    public AuthResult register(AuthController.RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        String phone = request.phone().trim();
        if (!request.password().equals(request.confirmPassword())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passwords do not match");
        if (users.existsByEmailIgnoreCase(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        if (users.existsByPhone(phone)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Phone is already registered");
        User user = users.save(new User(request.fullName().trim(), email, phone, passwordEncoder.encode(request.password())));
        return issueAuthentication(user);
    }

    @Transactional
    public AuthResult login(AuthController.LoginRequest request) {
        String identifier = request.identifier().trim();
        User user = users.findByEmailIgnoreCase(identifier).or(() -> users.findByPhone(identifier)).orElseThrow(this::invalidCredentials);
        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) throw invalidCredentials();
        return issueAuthentication(user);
    }

    @Transactional
    public AuthResult refresh(String refreshToken) {
        RefreshTokenParts parts = parseRefreshToken(refreshToken);
        RefreshSession session = sessions.findById(parts.sessionId()).orElseThrow(this::invalidRefresh);
        if (session.isRevoked() || session.getExpiresAt().isBefore(Instant.now()) || !session.getUser().isActive()
            || !passwordEncoder.matches(parts.secret(), session.getTokenHash())) throw invalidRefresh();
        session.revoke();
        return issueAuthentication(session.getUser());
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return;
        try {
            RefreshTokenParts parts = parseRefreshToken(refreshToken);
            sessions.findById(parts.sessionId()).ifPresent(session -> {
                if (passwordEncoder.matches(parts.secret(), session.getTokenHash())) session.revoke();
            });
        } catch (ResponseStatusException ignored) { }
    }

    @Transactional(readOnly = true)
    public AuthController.PublicUser currentUser(AuthPrincipal principal) {
        User user = users.findById(principal.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required"));
        return publicUser(user);
    }

    private AuthResult issueAuthentication(User user) {
        Instant expiry = Instant.now().plusSeconds(refreshDays * 24L * 60L * 60L);
        RefreshSession session = sessions.save(new RefreshSession(user, "pending", expiry));
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setTokenHash(passwordEncoder.encode(secret));
        return new AuthResult(new AuthController.AuthResponse(jwtService.createAccessToken(user), jwtService.getAccessSeconds(), publicUser(user)), session.getId() + "." + secret);
    }

    private AuthController.PublicUser publicUser(User user) { return new AuthController.PublicUser(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(), user.getRole()); }
    private RefreshTokenParts parseRefreshToken(String token) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.", 2);
            if (parts.length != 2 || parts[1].isBlank()) throw new IllegalArgumentException();
            return new RefreshTokenParts(Long.parseLong(parts[0]), parts[1]);
        } catch (IllegalArgumentException ex) { throw invalidRefresh(); }
    }
    private ResponseStatusException invalidCredentials() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"); }
    private ResponseStatusException invalidRefresh() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired. Please sign in again."); }
    private record RefreshTokenParts(Long sessionId, String secret) { }
    public record AuthResult(AuthController.AuthResponse response, String refreshToken) { }
}
