package com.ludoarena.model;

/** Small enums shared by several documents. */
public final class Enums {
    private Enums() {
    }

    public enum Role { USER, ADMIN, SUPER_ADMIN }

    public enum AccountStatus { ACTIVE, SUSPENDED }

    public enum TxType { SIGNUP_BONUS, DAILY_BONUS, MATCH_REWARD, STORE_PURCHASE, PROMO_CREDIT, ADMIN_ADJUSTMENT, PAYMENT_CREDIT }

    public enum TxStatus { PENDING, COMPLETED, FAILED }

    public enum RoomStatus { WAITING, IN_PROGRESS, FINISHED, CLOSED }

    public enum MatchStatus { ACTIVE, FINISHED, ABANDONED }

    public enum ItemCategory { BOARD_SKIN, DICE_SKIN, AVATAR, PROFILE_THEME }

    public enum ReportType { CHEATING, ABUSE, BUG, OTHER }

    public enum ReportStatus { OPEN, IN_REVIEW, RESOLVED, DISMISSED }

    public enum PaymentStatus { CREATED, PAID, FAILED }
}
