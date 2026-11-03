package com.ludoarena.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ludoarena.TestProps;
import com.ludoarena.dto.Dtos.AuthResponse;
import com.ludoarena.dto.Requests.Login;
import com.ludoarena.dto.Requests.Register;
import com.ludoarena.dto.Requests.ResetPassword;
import com.ludoarena.exception.ApiException;
import com.ludoarena.model.Enums.AccountStatus;
import com.ludoarena.model.PasswordResetToken;
import com.ludoarena.model.PlatformSettings;
import com.ludoarena.model.RefreshToken;
import com.ludoarena.model.User;
import com.ludoarena.repository.PasswordResetTokenRepository;
import com.ludoarena.repository.RefreshTokenRepository;
import com.ludoarena.repository.UserRepository;
import com.ludoarena.security.JwtService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.mongodb.client.result.UpdateResult;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {
    @Mock UserRepository users;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock PasswordResetTokenRepository resetTokens;
    @Mock WalletService wallets;
    @Mock PlatformSettingsService settings;
    @Mock MailService mail;
    @Mock NotificationService notifications;
    @Mock MongoTemplate mongo;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, refreshTokens, resetTokens, encoder, new JwtService(TestProps.props()),
                wallets, settings, mail, notifications, mongo, TestProps.props());
        when(settings.get()).thenReturn(new PlatformSettings());
        when(users.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            if (u.id == null) u.id = "u-1";
            return u;
        });
        when(refreshTokens.save(any(RefreshToken.class))).thenAnswer(i -> i.getArgument(0));
    }

    private User existing(String password) {
        User u = new User();
        u.id = "u-1";
        u.name = "Alice";
        u.email = "alice@example.com";
        u.passwordHash = encoder.encode(password);
        return u;
    }

    @Test
    void registerHashesThePasswordGrantsBonusAndReturnsTokens() {
        when(users.existsByEmail("new@example.com")).thenReturn(false);
        AuthResponse r = service.register(new Register("New Player", "  New@Example.com ", "Passw0rdOK"));

        assertNotNull(r.accessToken());
        assertNotNull(r.refreshToken());
        assertEquals("new@example.com", r.user().email());
        verify(wallets).ensureWallet("u-1");
        verify(wallets).grantSignupBonus("u-1");
        org.mockito.ArgumentCaptor<User> saved = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertNotNull(saved.getValue().passwordHash);
        assertEquals(false, saved.getValue().passwordHash.contains("Passw0rdOK"));
        assertEquals(true, encoder.matches("Passw0rdOK", saved.getValue().passwordHash));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(users.existsByEmail("alice@example.com")).thenReturn(true);
        ApiException e = assertThrows(ApiException.class,
                () -> service.register(new Register("Alice", "alice@example.com", "Passw0rdOK")));
        assertEquals("EMAIL_TAKEN", e.code());
        verify(users, never()).save(any(User.class));
    }

    @Test
    void registerIsBlockedWhenClosed() {
        PlatformSettings closed = new PlatformSettings();
        closed.registrationOpen = false;
        when(settings.get()).thenReturn(closed);
        ApiException e = assertThrows(ApiException.class,
                () -> service.register(new Register("Alice", "alice@example.com", "Passw0rdOK")));
        assertEquals("REGISTRATION_CLOSED", e.code());
    }

    @Test
    void loginGivesTheSameErrorForUnknownEmailAndWrongPassword() {
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(existing("Passw0rdOK")));

        ApiException unknown = assertThrows(ApiException.class, () -> service.login(new Login("ghost@example.com", "whatever1")));
        ApiException wrong = assertThrows(ApiException.class, () -> service.login(new Login("alice@example.com", "WrongPass1")));

        assertEquals("INVALID_CREDENTIALS", unknown.code());
        assertEquals("INVALID_CREDENTIALS", wrong.code());
        assertEquals(unknown.getMessage(), wrong.getMessage());
    }

    @Test
    void loginWorksWithCorrectPasswordAndNeverReturnsTheHash() {
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(existing("Passw0rdOK")));
        AuthResponse r = service.login(new Login("Alice@Example.com", "Passw0rdOK"));
        assertNotNull(r.accessToken());
        assertEquals("Bearer", r.tokenType());
        assertEquals(false, r.user().toString().contains("$2"));
    }

    @Test
    void suspendedAccountsCannotLogIn() {
        User u = existing("Passw0rdOK");
        u.status = AccountStatus.SUSPENDED;
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(u));
        ApiException e = assertThrows(ApiException.class, () -> service.login(new Login("alice@example.com", "Passw0rdOK")));
        assertEquals("ACCOUNT_SUSPENDED", e.code());
        verify(refreshTokens, never()).save(any(RefreshToken.class));
    }

    @Test
    void unknownRefreshTokenIsRejected() {
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.empty());
        ApiException e = assertThrows(ApiException.class, () -> service.refresh("nope"));
        assertEquals("INVALID_REFRESH_TOKEN", e.code());
    }

    @Test
    void reusingARotatedRefreshTokenRevokesTheWholeFamily() {
        RefreshToken used = new RefreshToken();
        used.id = "rt-1";
        used.userId = "u-1";
        used.familyId = "fam-1";
        used.revoked = true;
        used.expiresAt = Instant.now().plusSeconds(3600);
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.of(used));

        ApiException e = assertThrows(ApiException.class, () -> service.refresh("stolen"));

        assertEquals("REFRESH_TOKEN_REUSED", e.code());
        verify(mongo).updateMulti(any(Query.class), any(UpdateDefinition.class), eq(RefreshToken.class));
    }

    @Test
    void validRefreshRotatesTheToken() {
        RefreshToken rt = new RefreshToken();
        rt.id = "rt-1";
        rt.userId = "u-1";
        rt.familyId = "fam-1";
        rt.expiresAt = Instant.now().plusSeconds(3600);
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.of(rt));
        when(mongo.updateFirst(any(Query.class), any(UpdateDefinition.class), eq(RefreshToken.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));
        when(users.findById("u-1")).thenReturn(Optional.of(existing("Passw0rdOK")));

        AuthResponse r = service.refresh("good");

        assertNotNull(r.refreshToken());
        verify(refreshTokens).save(any(RefreshToken.class)); // the replacement token
    }

    @Test
    void forgotPasswordForUnknownEmailSendsNothingAndDoesNotFail() {
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        service.forgotPassword("ghost@example.com");
        verify(mail, never()).sendPasswordReset(anyString(), anyString(), anyString());
    }

    @Test
    void forgotPasswordMailsALinkAndStoresOnlyTheHash() {
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(existing("Passw0rdOK")));
        service.forgotPassword("alice@example.com");
        org.mockito.ArgumentCaptor<String> link = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mail).sendPasswordReset(eq("alice@example.com"), eq("Alice"), link.capture());
        org.mockito.ArgumentCaptor<PasswordResetToken> stored = org.mockito.ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetTokens).save(stored.capture());
        String raw = link.getValue().substring(link.getValue().indexOf("token=") + 6);
        assertEquals(false, stored.getValue().tokenHash.equals(raw));
        assertEquals(Hashing.sha256(raw), stored.getValue().tokenHash);
    }

    @Test
    void usedOrExpiredResetTokensAreRejected() {
        PasswordResetToken used = new PasswordResetToken();
        used.id = "p1";
        used.userId = "u-1";
        used.used = true;
        used.expiresAt = Instant.now().plusSeconds(600);
        when(resetTokens.findByTokenHash(anyString())).thenReturn(Optional.of(used));
        ApiException e = assertThrows(ApiException.class, () -> service.resetPassword(new ResetPassword("tok", "NewPassw0rd")));
        assertEquals("INVALID_RESET_TOKEN", e.code());

        PasswordResetToken expired = new PasswordResetToken();
        expired.id = "p2";
        expired.userId = "u-1";
        expired.expiresAt = Instant.now().minusSeconds(5);
        when(resetTokens.findByTokenHash(anyString())).thenReturn(Optional.of(expired));
        assertThrows(ApiException.class, () -> service.resetPassword(new ResetPassword("tok", "NewPassw0rd")));
    }
}
