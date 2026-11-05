package com.ludoarena.dto;

import com.ludoarena.game.model.GameEvent;
import com.ludoarena.game.model.GameState;
import com.ludoarena.game.model.PlayerState;
import com.ludoarena.model.AdminLog;
import com.ludoarena.model.Enums.AccountStatus;
import com.ludoarena.model.Enums.ItemCategory;
import com.ludoarena.model.Enums.MatchStatus;
import com.ludoarena.model.Enums.ReportStatus;
import com.ludoarena.model.Enums.ReportType;
import com.ludoarena.model.Enums.Role;
import com.ludoarena.model.Enums.RoomStatus;
import com.ludoarena.model.Enums.TxStatus;
import com.ludoarena.model.Enums.TxType;
import com.ludoarena.model.InventoryItem;
import com.ludoarena.model.LeaderboardEntry;
import com.ludoarena.model.Match;
import com.ludoarena.model.Notification;
import com.ludoarena.model.Report;
import com.ludoarena.model.Room;
import com.ludoarena.model.StoreItem;
import com.ludoarena.model.User;
import com.ludoarena.model.Wallet;
import com.ludoarena.model.WalletTransaction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Response DTOs. Entities are never serialised directly where they hold secrets (e.g. User.passwordHash). */
public final class Dtos {
    private Dtos() {
    }

    // ------------------------------------------------------------ envelopes

    public record ErrorBody(String code, String message, Map<String, String> fieldErrors) {
    }

    public record ApiResponse<T>(boolean success, T data, String message, ErrorBody error, Instant timestamp) {
        public static <T> ApiResponse<T> ok(T data) {
            return new ApiResponse<>(true, data, null, null, Instant.now());
        }

        public static <T> ApiResponse<T> ok(T data, String message) {
            return new ApiResponse<>(true, data, message, null, Instant.now());
        }

        public static <T> ApiResponse<T> error(String code, String message, Map<String, String> fieldErrors) {
            return new ApiResponse<>(false, null, message, new ErrorBody(code, message, fieldErrors), Instant.now());
        }
    }

    public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
        public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
            return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                    page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }

        public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
            int pages = size == 0 ? 0 : (int) Math.ceil(total / (double) size);
            return new PageResponse<>(items, page, size, total, pages);
        }
    }

    // ----------------------------------------------------------------- auth/users

    public record StatsDto(int totalMatches, int wins, int losses, long xp, int captures, int level, double winRate,
                           int botGames, int botWins) {
        public static StatsDto from(User.Stats s) {
            int level = 1 + (int) Math.floor(Math.sqrt(s.xp / 50.0));
            double rate = s.totalMatches == 0 ? 0 : Math.round(1000.0 * s.wins / s.totalMatches) / 10.0;
            return new StatsDto(s.totalMatches, s.wins, s.losses, s.xp, s.captures, level, rate, s.botGames, s.botWins);
        }
    }

    public record CosmeticsDto(String boardSkin, String diceSkin, String profileTheme) {
        public static CosmeticsDto from(User.Cosmetics c) {
            return new CosmeticsDto(c.boardSkin, c.diceSkin, c.profileTheme);
        }
    }

    public record PreferencesDto(boolean sound, boolean animations) {
    }

    public record UserDto(String id, String name, String email, Role role, AccountStatus status, String avatar,
                          StatsDto stats, CosmeticsDto cosmetics, PreferencesDto preferences,
                          String activeRoomId, Instant createdAt) {
        public static UserDto from(User u) {
            return new UserDto(u.id, u.name, u.email, u.role, u.status, u.avatar, StatsDto.from(u.stats),
                    CosmeticsDto.from(u.cosmetics), new PreferencesDto(u.preferences.sound, u.preferences.animations),
                    u.activeRoomId, u.createdAt);
        }
    }

    /** What other players are allowed to see about a user (no email, no status details). */
    public record PublicProfileDto(String id, String name, String avatar, StatsDto stats, CosmeticsDto cosmetics, Instant memberSince,
                                   int achievementsUnlocked) {
    }

    public record AuthResponse(String accessToken, String refreshToken, long expiresInSeconds, String tokenType, UserDto user) {
    }

    public record AchievementDto(String key, String title, String description, boolean unlocked, long progress, long target) {
    }

    // ------------------------------------------------------------------- wallet

    public record WalletDto(long coinBalance, String currencyType, boolean redeemable, String notice,
                            Instant lastDailyBonusAt, boolean dailyBonusAvailable, long dailyBonusCoins) {
        public static WalletDto from(Wallet w, boolean dailyAvailable, long dailyCoins) {
            return new WalletDto(w.coinBalance, w.currencyType, false,
                    "Coins are virtual entertainment currency. They cannot be withdrawn, redeemed for cash or wagered.",
                    w.lastDailyBonusAt, dailyAvailable, dailyCoins);
        }
    }

    public record TransactionDto(String id, String userId, TxType type, long amount, Long previousBalance, Long newBalance,
                                 String referenceId, TxStatus status, String description, Instant createdAt) {
        public static TransactionDto from(WalletTransaction t) {
            return new TransactionDto(t.id, t.userId, t.type, t.amount, t.previousBalance, t.newBalance, t.referenceId,
                    t.status, t.description, t.createdAt);
        }
    }

    public record PurchaseDto(String id, String itemId, String sku, String name, ItemCategory category, long pricePaid,
                              String transactionRef, Instant purchasedAt) {
        public static PurchaseDto from(InventoryItem i) {
            return new PurchaseDto(i.id, i.itemId, i.sku, i.name, i.category, i.pricePaid, i.transactionRef, i.purchasedAt);
        }
    }

    // -------------------------------------------------------------------- store

    public record StoreItemDto(String id, String sku, String name, String description, ItemCategory category, long priceCoins,
                               String rarity, String assetKey, boolean active, boolean owned, boolean equipped) {
        public static StoreItemDto from(StoreItem s, boolean owned, boolean equipped) {
            return new StoreItemDto(s.id, s.sku, s.name, s.description, s.category, s.priceCoins, s.rarity, s.assetKey,
                    s.active, owned, equipped);
        }
    }

    public record PurchaseResult(PurchaseDto purchase, WalletDto wallet) {
    }

    // -------------------------------------------------------------------- rooms

    public record RoomPlayerDto(String userId, String name, String avatar, boolean ready, boolean host) {
    }

    public record RoomDto(String id, String code, String name, String hostId, int maxPlayers, RoomStatus status,
                          boolean isPrivate, List<RoomPlayerDto> players, String matchId, Instant createdAt,
                          boolean vsComputer, String botDifficulty) {
        public static RoomDto from(Room r, String viewerId) {
            boolean member = viewerId != null && r.hasPlayer(viewerId);
            List<RoomPlayerDto> players = new ArrayList<>();
            for (Room.RoomPlayer p : r.players) {
                players.add(new RoomPlayerDto(p.userId, p.name, p.avatar, p.ready, p.userId.equals(r.hostId)));
            }
            return new RoomDto(r.id, (!r.isPrivate || member) ? r.code : null, r.name, r.hostId, r.maxPlayers, r.status,
                    r.isPrivate, players, r.matchId, r.createdAt, r.vsComputer, r.botDifficulty);
        }
    }

    public record ActiveGameDto(RoomDto room, String matchId) {
    }

    public record RoomUpdate(String type, RoomDto room, String matchId) {
    }

    // ------------------------------------------------------------------ matches

    public record PlayerView(String userId, String name, String avatar, String color, int seat, int[] tokens,
                             boolean connected, boolean forfeited, int finishedTokens, int rank, String boardSkin, String diceSkin,
                             boolean bot, String botDifficulty) {
    }

    public record MatchStateDto(String matchId, String roomId, MatchStatus status, int mode, List<PlayerView> players,
                                String currentUserId, Integer diceValue, Integer lastDiceValue, String lastDiceUserId,
                                boolean awaitingMove, List<Integer> validMoves, int consecutiveSixes, String winnerId,
                                long turnDeadline, long serverTime, long seq, boolean vsComputer, String botDifficulty,
                                Long botActAt) {
        public static MatchStateDto from(Match m, Map<String, User.Cosmetics> cosmetics) {
            GameState g = m.game;
            List<PlayerView> players = new ArrayList<>();
            for (PlayerState p : g.players) {
                User.Cosmetics c = cosmetics == null ? null : cosmetics.get(p.userId);
                players.add(new PlayerView(p.userId, p.name, p.avatar, p.color.name(), p.seat, p.tokens.clone(), p.connected,
                        p.forfeited, p.finishedTokens(), p.rank, c == null ? null : c.boardSkin, c == null ? null : c.diceSkin,
                        p.bot, p.botDifficulty));
            }
            return new MatchStateDto(m.id, m.roomId, m.status, m.mode, players,
                    g.isFinished() ? null : g.current().userId, g.diceValue, g.lastDiceValue, g.lastDiceUserId,
                    g.awaitingMove, List.copyOf(g.validMoves), g.consecutiveSixes, g.winnerUserId,
                    g.turnDeadlineEpochMs, System.currentTimeMillis(), g.seq, m.vsComputer, m.botDifficulty, g.botActAtEpochMs);
        }
    }

    public record EventDto(String eventId, long seq, String type, String userId, Map<String, Object> data, long at) {
        public static EventDto from(GameEvent e) {
            return new EventDto(e.eventId, e.seq, e.type, e.userId, e.data, e.at);
        }

        public static EventDto from(com.ludoarena.model.MatchEvent e) {
            return new EventDto(e.eventId, e.seq, e.type, e.userId, e.data, e.at);
        }
    }

    /** Broadcast on /topic/match/{id} after every accepted action. */
    public record MatchUpdate(String matchId, List<EventDto> events, MatchStateDto state) {
    }

    /** Sent privately on /user/queue/state. */
    public record StateSnapshot(MatchStateDto state, String reconnectToken) {
    }

    public record MatchPlayerSummary(String userId, String name, String color, int rank, int finishedTokens) {
    }

    public record MatchSummaryDto(String id, String roomId, int mode, MatchStatus status, String winnerId,
                                  List<MatchPlayerSummary> players, Instant createdAt, Instant finishedAt, Boolean youWon,
                                  boolean vsComputer) {
        public static MatchSummaryDto from(Match m, String viewerId) {
            List<MatchPlayerSummary> ps = new ArrayList<>();
            for (PlayerState p : m.game.players) {
                ps.add(new MatchPlayerSummary(p.userId, p.name, p.color.name(), p.rank, p.finishedTokens()));
            }
            Boolean won = viewerId == null || m.winnerId == null ? null : m.winnerId.equals(viewerId);
            return new MatchSummaryDto(m.id, m.roomId, m.mode, m.status, m.winnerId, ps, m.createdAt, m.finishedAt, won, m.vsComputer);
        }
    }

    // -------------------------------------------------------------- leaderboard

    public record LeaderboardEntryDto(long rank, String userId, String name, String avatar, long xp, int level, int wins,
                                      int totalMatches, double winRate) {
        public static LeaderboardEntryDto from(LeaderboardEntry e, long rank) {
            int level = 1 + (int) Math.floor(Math.sqrt(e.xp / 50.0));
            double rate = e.totalMatches == 0 ? 0 : Math.round(1000.0 * e.wins / e.totalMatches) / 10.0;
            return new LeaderboardEntryDto(rank, e.userId, e.name, e.avatar, e.xp, level, e.wins, e.totalMatches, rate);
        }
    }

    // ------------------------------------------------------------ notifications

    public record NotificationDto(String id, String type, String title, String message, boolean read, Instant createdAt) {
        public static NotificationDto from(Notification n) {
            return new NotificationDto(n.id, n.type, n.title, n.message, n.read, n.createdAt);
        }
    }

    public record UnreadCount(long unread) {
    }

    // ------------------------------------------------------------------ reports

    public record ReportDto(String id, String reporterId, String reporterName, ReportType type, String targetUserId,
                            String matchId, String description, ReportStatus status, String adminNote, Instant createdAt,
                            Instant updatedAt) {
        public static ReportDto from(Report r, String reporterName) {
            return new ReportDto(r.id, r.reporterId, reporterName, r.type, r.targetUserId, r.matchId, r.description,
                    r.status, r.adminNote, r.createdAt, r.updatedAt);
        }
    }

    // -------------------------------------------------------------------- admin

    public record AdminUserDto(String id, String name, String email, Role role, AccountStatus status, String suspensionReason,
                               long coinBalance, StatsDto stats, Instant createdAt, Instant lastLoginAt) {
    }

    public record ActivityDto(String kind, String message, Instant at) {
    }

    public record DashboardDto(long totalUsers, long activeUsers, long suspendedUsers, long newUsers7d, long onlineUsers,
                               long totalMatches, long activeMatches, long completedMatches, long coinCirculation,
                               long storePurchases, long coinsSpentInStore, long openReports, List<ActivityDto> recentActivity) {
    }

    public record DailyCount(String date, long count) {
    }

    public record GameStatsDto(List<DailyCount> matchesPerDay, List<DailyCount> signupsPerDay, long twoPlayerMatches,
                               long fourPlayerMatches, long abandonedMatches, double averageEventsPerMatch,
                               List<LeaderboardEntryDto> topPlayers) {
    }

    public record AdminLogDto(String id, String adminId, String adminEmail, String action, String targetType, String targetId,
                              Map<String, Object> details, Instant createdAt) {
        public static AdminLogDto from(AdminLog l) {
            return new AdminLogDto(l.id, l.adminId, l.adminEmail, l.action, l.targetType, l.targetId, l.details, l.createdAt);
        }
    }

    public record SettingsDto(boolean registrationOpen, boolean maintenanceMode, long signupBonusCoins, long dailyBonusCoins,
                              long matchWinRewardCoins, long matchParticipationRewardCoins, boolean extraTurnOnSix,
                              boolean extraTurnOnCapture, boolean extraTurnOnHome, int maxConsecutiveSixes,
                              int turnTimeoutSeconds, boolean paymentsEnabled) {
    }

    // ---------------------------------------------------------------- payments

    public record CoinPackage(String id, String name, long coins, long priceMinor, String currency) {
    }

    public record PaymentOrderDto(String orderId, String providerOrderId, long amountMinor, String currency, String keyId, long coins) {
    }

    public record PaymentStatusDto(boolean enabled, String message, List<CoinPackage> packages) {
    }
}
