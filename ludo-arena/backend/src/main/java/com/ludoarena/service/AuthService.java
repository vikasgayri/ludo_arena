package com.ludoarena.service;

import com.ludoarena.config.AppProperties;
import com.ludoarena.dto.Dtos.AuthResponse;
import com.ludoarena.dto.Dtos.UserDto;
import com.ludoarena.dto.Requests.ChangePassword;
import com.ludoarena.dto.Requests.Login;
import com.ludoarena.dto.Requests.Register;
import com.ludoarena.dto.Requests.ResetPassword;
import com.ludoarena.exception.ApiException;
import com.ludoarena.model.Enums.AccountStatus;
import com.ludoarena.model.Enums.Role;
import com.ludoarena.model.PasswordResetToken;
import com.ludoarena.model.RefreshToken;
import com.ludoarena.model.User;
import com.ludoarena.repository.PasswordResetTokenRepository;
import com.ludoarena.repository.RefreshTokenRepository;
import com.ludoarena.repository.UserRepository;
import com.ludoarena.security.JwtService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final long RESET_TOKEN_MINUTES = 30;

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final WalletService wallets;
    private final PlatformSettingsService settings;
    private final MailService mail;
    private final NotificationService notifications;
    private final MongoTemplate mongo;
    private final AppProperties props;
    /** compared against when the email is unknown, so response time does not reveal which emails exist */
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordResetTokenRepository resetTokens,
                       PasswordEncoder encoder, JwtService jwt, WalletService wallets, PlatformSettingsService settings,
                       MailService mail, NotificationService notifications, MongoTemplate mongo, AppProperties props) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.encoder = encoder;
        this.jwt = jwt;
        this.wallets = wallets;
        this.settings = settings;
        this.mail = mail;
        this.notifications = notifications;
        this.mongo = mongo;
        this.props = props;
        this.dummyHash = encoder.encode("not-a-real-password-" + UUID.randomUUID());
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- register / login

    public AuthResponse register(Register req) {
        if (!settings.get().registrationOpen) {
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "REGISTRATION_CLOSED", "Registration is currently closed");
        }
        String email = normalizeEmail(req.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        User u = new User();
        u.name = req.name().trim();
        u.email = email;
        u.passwordHash = encoder.encode(req.password());
        u.createdAt = Instant.now();
        u.updatedAt = u.createdAt;
        u.lastLoginAt = u.createdAt;
        try {
            u = users.save(u);
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists");
        }
        wallets.ensureWallet(u.id);
        try {
            wallets.grantSignupBonus(u.id);
        } catch (RuntimeException e) {
            log.error("Signup bonus failed for {}", u.id, e);
        }
        notifications.notify(u.id, "WELCOME", "Welcome to Ludo Arena", "Your account is ready. Create a room and roll the dice!");
        return issueTokens(u, UUID.randomUUID().toString());
    }

    public AuthResponse login(Login req) {
        User u = users.findByEmail(normalizeEmail(req.email())).orElse(null);
        boolean ok = encoder.matches(req.password(), u == null ? dummyHash : u.passwordHash);
        if (u == null || !ok) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid email or password");
        }
        if (u.status == AccountStatus.SUSPENDED) {
            throw ApiException.forbidden("ACCOUNT_SUSPENDED", "Your account has been suspended"
                    + (u.suspensionReason == null || u.suspensionReason.isBlank() ? "" : ": " + u.suspensionReason));
        }
        if (settings.get().maintenanceMode && u.role == Role.USER) {
            throw new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "MAINTENANCE", "Ludo Arena is under maintenance. Please try again soon.");
        }
        u.lastLoginAt = Instant.now();
        mongo.updateFirst(Query.query(Criteria.where("id").is(u.id)), new Update().set("lastLoginAt", u.lastLoginAt), User.class);
        return issueTokens(u, UUID.randomUUID().toString());
    }

    // -------------------------------------------------------------------- tokens

    private AuthResponse issueTokens(User u, String familyId) {
        String raw = Hashing.randomToken(32);
        RefreshToken rt = new RefreshToken();
        rt.userId = u.id;
        rt.tokenHash = Hashing.sha256(raw);
        rt.familyId = familyId;
        rt.createdAt = Instant.now();
        rt.expiresAt = rt.createdAt.plus(props.jwt().refreshTokenDays(), ChronoUnit.DAYS);
        refreshTokens.save(rt);
        return new AuthResponse(jwt.createAccessToken(u), raw, jwt.accessTtlSeconds(), "Bearer", UserDto.from(u));
    }

    /** Rotating refresh: each token works once; presenting a used token revokes the whole login family. */
    public AuthResponse refresh(String rawToken) {
        RefreshToken rt = refreshTokens.findByTokenHash(Hashing.sha256(rawToken))
                .orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Please sign in again"));
        if (rt.revoked) {
            revokeFamily(rt.familyId);
            throw ApiException.unauthorized("REFRESH_TOKEN_REUSED", "Session expired. Please sign in again");
        }
        if (rt.expiresAt.isBefore(Instant.now())) {
            throw ApiException.unauthorized("REFRESH_TOKEN_EXPIRED", "Session expired. Please sign in again");
        }
        // atomic claim: only one concurrent request can rotate this token
        var claimed = mongo.updateFirst(Query.query(Criteria.where("id").is(rt.id).and("revoked").is(false)),
                new Update().set("revoked", true), RefreshToken.class);
        if (claimed.getModifiedCount() == 0) {
            revokeFamily(rt.familyId);
            throw ApiException.unauthorized("REFRESH_TOKEN_REUSED", "Session expired. Please sign in again");
        }
        User u = users.findById(rt.userId).orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Please sign in again"));
        if (u.status == AccountStatus.SUSPENDED) {
            throw ApiException.forbidden("ACCOUNT_SUSPENDED", "Your account has been suspended");
        }
        return issueTokens(u, rt.familyId);
    }

    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        refreshTokens.findByTokenHash(Hashing.sha256(rawToken)).ifPresent(rt -> revokeFamily(rt.familyId));
    }

    public void revokeAllForUser(String userId) {
        mongo.updateMulti(Query.query(Criteria.where("userId").is(userId)), new Update().set("revoked", true), RefreshToken.class);
    }

    private void revokeFamily(String familyId) {
        mongo.updateMulti(Query.query(Criteria.where("familyId").is(familyId)), new Update().set("revoked", true), RefreshToken.class);
    }

    // ------------------------------------------------------------- password flows

    /** Always succeeds from the caller's point of view so the endpoint cannot be used to enumerate emails. */
    public void forgotPassword(String emailRaw) {
        User u = users.findByEmail(normalizeEmail(emailRaw)).orElse(null);
        if (u == null || u.status == AccountStatus.SUSPENDED) return;
        mongo.updateMulti(Query.query(Criteria.where("userId").is(u.id).and("used").is(false)),
                new Update().set("used", true), PasswordResetToken.class);
        String raw = Hashing.randomToken(32);
        PasswordResetToken t = new PasswordResetToken();
        t.userId = u.id;
        t.tokenHash = Hashing.sha256(raw);
        t.createdAt = Instant.now();
        t.expiresAt = t.createdAt.plus(RESET_TOKEN_MINUTES, ChronoUnit.MINUTES);
        resetTokens.save(t);
        String base = props.allowedOrigins().isEmpty() ? "" : props.allowedOrigins().get(0);
        try {
            mail.sendPasswordReset(u.email, u.name, base + "/reset-password?token=" + raw);
        } catch (RuntimeException e) {
            log.error("Could not send password reset mail to {}", u.email, e);
        }
    }

    public void resetPassword(ResetPassword req) {
        PasswordResetToken t = resetTokens.findByTokenHash(Hashing.sha256(req.token()))
                .orElseThrow(() -> ApiException.badRequest("INVALID_RESET_TOKEN", "This reset link is invalid or has expired"));
        if (t.used || t.expiresAt.isBefore(Instant.now())) {
            throw ApiException.badRequest("INVALID_RESET_TOKEN", "This reset link is invalid or has expired");
        }
        var claimed = mongo.updateFirst(Query.query(Criteria.where("id").is(t.id).and("used").is(false)),
                new Update().set("used", true), PasswordResetToken.class);
        if (claimed.getModifiedCount() == 0) {
            throw ApiException.badRequest("INVALID_RESET_TOKEN", "This reset link is invalid or has expired");
        }
        User u = users.findById(t.userId).orElseThrow(() -> ApiException.badRequest("INVALID_RESET_TOKEN", "This reset link is invalid or has expired"));
        u.passwordHash = encoder.encode(req.newPassword());
        u.updatedAt = Instant.now();
        users.save(u);
        revokeAllForUser(u.id);
        notifications.notify(u.id, "SECURITY", "Password changed", "Your password was reset. If this wasn't you, contact support.");
    }

    public void changePassword(String userId, ChangePassword req) {
        User u = users.findById(userId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        if (!encoder.matches(req.currentPassword(), u.passwordHash)) {
            throw ApiException.badRequest("WRONG_PASSWORD", "Current password is incorrect");
        }
        u.passwordHash = encoder.encode(req.newPassword());
        u.updatedAt = Instant.now();
        users.save(u);
        revokeAllForUser(userId);
        notifications.notify(userId, "SECURITY", "Password changed", "Your password was changed. You were signed out on other devices.");
    }
}
