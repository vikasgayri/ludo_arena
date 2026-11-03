package com.ludoarena.dto;

import com.ludoarena.model.Enums.AccountStatus;
import com.ludoarena.model.Enums.ItemCategory;
import com.ludoarena.model.Enums.ReportStatus;
import com.ludoarena.model.Enums.ReportType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** All request bodies with Bean Validation constraints. */
public final class Requests {
    private Requests() {
    }

    public static final String PASSWORD_RULE = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String PASSWORD_MSG = "Password must be 8-72 characters and contain a letter and a digit";

    public record Register(
            @NotBlank @Size(min = 2, max = 30) String name,
            @NotBlank @Email @Size(max = 120) String email,
            @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MSG) String password) {
    }

    public record Login(@NotBlank @Email String email, @NotBlank @Size(max = 72) String password) {
    }

    public record Refresh(@NotBlank String refreshToken) {
    }

    public record Logout(String refreshToken) {
    }

    public record ForgotPassword(@NotBlank @Email String email) {
    }

    public record ResetPassword(
            @NotBlank String token,
            @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MSG) String newPassword) {
    }

    public record ChangePassword(
            @NotBlank String currentPassword,
            @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MSG) String newPassword) {
    }

    public record UpdateProfile(
            @Size(min = 2, max = 30) String name,
            @Pattern(regexp = "^[a-z0-9-]{3,30}$", message = "Invalid avatar") String avatar,
            Boolean sound,
            Boolean animations) {
    }

    public record CreateRoom(
            @NotBlank @Size(min = 3, max = 40) String name,
            @NotNull @Min(2) @Max(4) Integer maxPlayers,
            boolean isPrivate) {
    }

    public record PurchaseRequest(@NotBlank String itemId) {
    }

    public record EquipRequest(@NotBlank String itemId) {
    }

    public record UnequipRequest(@NotNull ItemCategory category) {
    }

    public record CreateReport(
            @NotNull ReportType type,
            String targetUserId,
            String matchId,
            @NotBlank @Size(min = 10, max = 1000) String description) {
    }

    public record UpdateUserStatus(@NotNull AccountStatus status, @Size(max = 200) String reason) {
    }

    public record UpdateUserRole(@NotNull com.ludoarena.model.Enums.Role role) {
    }

    public record AdminCredit(@NotNull @Min(-100000) @Max(100000) Long amount, @NotBlank @Size(max = 200) String reason) {
    }

    public record UpdateReport(@NotNull ReportStatus status, @Size(max = 500) String adminNote) {
    }

    public record StoreItemRequest(
            @NotBlank @Pattern(regexp = "^[a-z0-9_-]{3,40}$", message = "sku must be lowercase letters, digits, - or _") String sku,
            @NotBlank @Size(max = 60) String name,
            @Size(max = 300) String description,
            @NotNull ItemCategory category,
            @NotNull @Min(0) @Max(1000000) Long priceCoins,
            @Size(max = 20) String rarity,
            @NotBlank @Size(max = 40) String assetKey,
            Boolean active) {
    }

    public record UpdateSettings(
            Boolean registrationOpen,
            Boolean maintenanceMode,
            @Min(0) @Max(100000) Long signupBonusCoins,
            @Min(0) @Max(100000) Long dailyBonusCoins,
            @Min(0) @Max(100000) Long matchWinRewardCoins,
            @Min(0) @Max(100000) Long matchParticipationRewardCoins,
            Boolean extraTurnOnSix,
            Boolean extraTurnOnCapture,
            Boolean extraTurnOnHome,
            @Min(0) @Max(10) Integer maxConsecutiveSixes,
            @Min(10) @Max(300) Integer turnTimeoutSeconds) {
    }

    public record CreatePaymentOrder(@NotBlank String packageId) {
    }

    public record VerifyPayment(@NotBlank String orderId, @NotBlank String paymentId, @NotBlank String signature) {
    }

    /** WebSocket payloads */
    public record RoomRef(@NotBlank String roomId) {
    }

    public record RoomReady(@NotBlank String roomId, boolean ready) {
    }

    public record RollDice(@NotBlank String matchId, @NotBlank @Size(max = 64) String actionId) {
    }

    public record MoveToken(@NotBlank String matchId, @Min(0) @Max(3) int tokenIndex, @NotBlank @Size(max = 64) String actionId) {
    }

    public record RequestState(@NotBlank String matchId, String reconnectToken) {
    }
}
