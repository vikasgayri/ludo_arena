package com.ludoarena.service;

import com.ludoarena.dto.Dtos.ActivityDto;
import com.ludoarena.dto.Dtos.AdminLogDto;
import com.ludoarena.dto.Dtos.AdminUserDto;
import com.ludoarena.dto.Dtos.DailyCount;
import com.ludoarena.dto.Dtos.DashboardDto;
import com.ludoarena.dto.Dtos.GameStatsDto;
import com.ludoarena.dto.Dtos.PageResponse;
import com.ludoarena.dto.Dtos.StatsDto;
import com.ludoarena.dto.Dtos.TransactionDto;
import com.ludoarena.dto.Requests.AdminCredit;
import com.ludoarena.dto.Requests.UpdateUserRole;
import com.ludoarena.dto.Requests.UpdateUserStatus;
import com.ludoarena.exception.ApiException;
import com.ludoarena.model.AdminLog;
import com.ludoarena.model.Enums.AccountStatus;
import com.ludoarena.model.Enums.MatchStatus;
import com.ludoarena.model.Enums.ReportStatus;
import com.ludoarena.model.Enums.Role;
import com.ludoarena.model.Enums.TxStatus;
import com.ludoarena.model.Enums.TxType;
import com.ludoarena.model.Match;
import com.ludoarena.model.MatchEvent;
import com.ludoarena.model.User;
import com.ludoarena.model.Wallet;
import com.ludoarena.model.WalletTransaction;
import com.ludoarena.repository.AdminLogRepository;
import com.ludoarena.repository.MatchRepository;
import com.ludoarena.repository.ReportRepository;
import com.ludoarena.repository.UserRepository;
import com.ludoarena.security.AuthUser;
import com.ludoarena.websocket.SessionTracker;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/** Admin operations. Authorisation is enforced by the security filter chain (/api/v1/admin/**) AND re-checked here. */
@Service
public class AdminService {
    private final MongoTemplate mongo;
    private final UserRepository users;
    private final MatchRepository matchRepo;
    private final ReportRepository reports;
    private final AdminLogRepository logs;
    private final AdminLogService adminLog;
    private final AuthService auth;
    private final MatchService matchService;
    private final WalletService wallets;
    private final NotificationService notifications;
    private final LeaderboardService leaderboard;
    private final SessionTracker sessions;

    public AdminService(MongoTemplate mongo, UserRepository users, MatchRepository matchRepo, ReportRepository reports,
                        AdminLogRepository logs, AdminLogService adminLog, AuthService auth, MatchService matchService,
                        WalletService wallets, NotificationService notifications, LeaderboardService leaderboard,
                        SessionTracker sessions) {
        this.mongo = mongo;
        this.users = users;
        this.matchRepo = matchRepo;
        this.reports = reports;
        this.logs = logs;
        this.adminLog = adminLog;
        this.auth = auth;
        this.matchService = matchService;
        this.wallets = wallets;
        this.notifications = notifications;
        this.leaderboard = leaderboard;
        this.sessions = sessions;
    }

    private static void requireAdmin(AuthUser a) {
        if (a == null || !a.isAdmin()) throw ApiException.forbidden("FORBIDDEN", "Administrator access required");
    }

    // ------------------------------------------------------------------ dashboard

    public DashboardDto dashboard(AuthUser admin) {
        requireAdmin(admin);
        Instant now = Instant.now();
        long total = mongo.count(new Query(), User.class);
        long suspended = mongo.count(Query.query(Criteria.where("status").is(AccountStatus.SUSPENDED)), User.class);
        long new7d = mongo.count(Query.query(Criteria.where("createdAt").gte(now.minus(7, ChronoUnit.DAYS))), User.class);
        long circulation = sumField("wallets", "coinBalance", null);
        Document purchases = aggregateOne("walletTransactions",
                Criteria.where("type").is(TxType.STORE_PURCHASE.name()).and("status").is(TxStatus.COMPLETED.name()));

        List<ActivityDto> activity = new ArrayList<>();
        for (AdminLog l : mongo.find(new Query().with(MongoPaging.newestFirst(0, 8)), AdminLog.class)) {
            activity.add(new ActivityDto("ADMIN", l.adminEmail + " - " + l.action + " " + l.targetType + (l.targetId == null ? "" : " " + l.targetId), l.createdAt));
        }
        for (User u : mongo.find(new Query().with(MongoPaging.newestFirst(0, 6)), User.class)) {
            activity.add(new ActivityDto("SIGNUP", u.name + " joined", u.createdAt));
        }
        Query finished = Query.query(Criteria.where("status").is(MatchStatus.FINISHED)).with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "finishedAt")).limit(6);
        for (Match m : mongo.find(finished, Match.class)) {
            String winner = m.game.players.stream().filter(p -> p.userId.equals(m.winnerId)).map(p -> p.name).findFirst().orElse("-");
            activity.add(new ActivityDto("MATCH", m.mode + "-player match finished, winner: " + winner, m.finishedAt));
        }
        activity.removeIf(a -> a.at() == null);
        activity.sort(Comparator.comparing(ActivityDto::at).reversed());

        return new DashboardDto(total, total - suspended, suspended, new7d, sessions.onlineUsers(),
                matchRepo.count(), matchRepo.countByStatus(MatchStatus.ACTIVE), matchRepo.countByStatus(MatchStatus.FINISHED),
                circulation, num(purchases, "n"), -num(purchases, "sum"), reports.countByStatus(ReportStatus.OPEN),
                activity.size() > 12 ? activity.subList(0, 12) : activity);
    }

    private Document aggregateOne(String collection, Criteria match) {
        Aggregation agg = Aggregation.newAggregation(Aggregation.match(match),
                Aggregation.group().count().as("n").sum("amount").as("sum"));
        return mongo.aggregate(agg, collection, Document.class).getUniqueMappedResult();
    }

    private long sumField(String collection, String field, Criteria match) {
        Aggregation agg = match == null
                ? Aggregation.newAggregation(Aggregation.group().sum(field).as("sum"))
                : Aggregation.newAggregation(Aggregation.match(match), Aggregation.group().sum(field).as("sum"));
        return num(mongo.aggregate(agg, collection, Document.class).getUniqueMappedResult(), "sum");
    }

    private static long num(Document d, String key) {
        if (d == null || !(d.get(key) instanceof Number n)) return 0;
        return n.longValue();
    }

    // ----------------------------------------------------------------------- users

    public PageResponse<AdminUserDto> users(AuthUser admin, String q, AccountStatus status, Role role, int page, int size) {
        requireAdmin(admin);
        List<Criteria> and = new ArrayList<>();
        if (q != null && !q.isBlank()) {
            String rx = Pattern.quote(q.trim());
            and.add(new Criteria().orOperator(Criteria.where("name").regex(rx, "i"), Criteria.where("email").regex(rx, "i")));
        }
        if (status != null) and.add(Criteria.where("status").is(status));
        if (role != null) and.add(Criteria.where("role").is(role));
        Criteria c = and.isEmpty() ? null : new Criteria().andOperator(and.toArray(new Criteria[0]));
        Page<User> p = MongoPaging.page(mongo, c, MongoPaging.newestFirst(page, size), User.class);

        List<String> ids = p.getContent().stream().map(u -> u.id).toList();
        Map<String, Long> balances = new HashMap<>();
        for (Wallet w : mongo.find(Query.query(Criteria.where("userId").in(ids)), Wallet.class)) balances.put(w.userId, w.coinBalance);
        return PageResponse.of(p, u -> new AdminUserDto(u.id, u.name, u.email, u.role, u.status, u.suspensionReason,
                balances.getOrDefault(u.id, 0L), StatsDto.from(u.stats), u.createdAt, u.lastLoginAt));
    }

    public AdminUserDto updateStatus(AuthUser admin, String targetId, UpdateUserStatus req) {
        requireAdmin(admin);
        User t = users.findById(targetId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        if (t.id.equals(admin.id())) throw ApiException.badRequest("SELF_ACTION", "You cannot change your own status");
        if (t.role == Role.SUPER_ADMIN) throw ApiException.forbidden("PROTECTED_ACCOUNT", "Super admins cannot be suspended");
        if (t.role == Role.ADMIN && admin.role() != Role.SUPER_ADMIN) {
            throw ApiException.forbidden("INSUFFICIENT_ROLE", "Only a super admin can change another admin");
        }
        AccountStatus before = t.status;
        Update upd = new Update().set("status", req.status()).set("updatedAt", Instant.now());
        if (req.status() == AccountStatus.SUSPENDED) upd.set("suspensionReason", req.reason()); else upd.unset("suspensionReason");
        mongo.updateFirst(Query.query(Criteria.where("id").is(t.id)), upd, User.class);
        t = users.findById(targetId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        if (t.status == AccountStatus.SUSPENDED) {
            auth.revokeAllForUser(t.id);
            matchService.forfeitActiveMatchOf(t.id);
        }
        notifications.notify(t.id, "ACCOUNT", t.status == AccountStatus.SUSPENDED ? "Account suspended" : "Account restored",
                t.status == AccountStatus.SUSPENDED ? "Your account was suspended." + (req.reason() == null ? "" : " Reason: " + req.reason()) : "Your account is active again.");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", before.name());
        details.put("to", t.status.name());
        details.put("reason", req.reason());
        adminLog.log(admin, "USER_" + t.status, "user", t.id, details);
        return toAdminUser(t);
    }

    public AdminUserDto updateRole(AuthUser admin, String targetId, UpdateUserRole req) {
        if (admin.role() != Role.SUPER_ADMIN) throw ApiException.forbidden("INSUFFICIENT_ROLE", "Super admin only");
        if (req.role() == Role.SUPER_ADMIN) throw ApiException.badRequest("INVALID_ROLE", "Super admin cannot be granted through the API");
        User t = users.findById(targetId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        if (t.id.equals(admin.id()) || t.role == Role.SUPER_ADMIN) {
            throw ApiException.forbidden("PROTECTED_ACCOUNT", "This account's role cannot be changed");
        }
        Role before = t.role;
        mongo.updateFirst(Query.query(Criteria.where("id").is(t.id)),
                new Update().set("role", req.role()).set("updatedAt", Instant.now()), User.class);
        t = users.findById(targetId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        adminLog.log(admin, "USER_ROLE_CHANGED", "user", t.id, Map.of("from", before.name(), "to", t.role.name()));
        return toAdminUser(t);
    }

    /** Promotional coin credit (or correction). Goes through the same idempotent ledger as everything else. */
    public TransactionDto creditCoins(AuthUser admin, String targetId, AdminCredit req) {
        if (admin.role() != Role.SUPER_ADMIN) throw ApiException.forbidden("INSUFFICIENT_ROLE", "Super admin only");
        if (req.amount() == 0) throw ApiException.badRequest("INVALID_AMOUNT", "Amount must not be zero");
        users.findById(targetId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        String ref = "ADMIN:" + UUID.randomUUID();
        WalletTransaction tx = wallets.apply(targetId, req.amount() > 0 ? TxType.PROMO_CREDIT : TxType.ADMIN_ADJUSTMENT,
                req.amount(), ref, req.reason(), Map.of("adminId", admin.id()));
        adminLog.log(admin, "COIN_ADJUSTMENT", "user", targetId, Map.of("amount", req.amount(), "reason", req.reason(), "reference", ref));
        notifications.notify(targetId, "WALLET", req.amount() > 0 ? "Coins added" : "Coin adjustment",
                (req.amount() > 0 ? "+" : "") + req.amount() + " coins: " + req.reason());
        return TransactionDto.from(tx);
    }

    private AdminUserDto toAdminUser(User u) {
        return new AdminUserDto(u.id, u.name, u.email, u.role, u.status, u.suspensionReason, wallets.balance(u.id),
                StatsDto.from(u.stats), u.createdAt, u.lastLoginAt);
    }

    // ------------------------------------------------------------ ledger / logs / stats

    public PageResponse<TransactionDto> transactions(AuthUser admin, String userId, TxType type, TxStatus status, int page, int size) {
        requireAdmin(admin);
        List<Criteria> and = new ArrayList<>();
        if (userId != null && !userId.isBlank()) and.add(Criteria.where("userId").is(userId));
        if (type != null) and.add(Criteria.where("type").is(type));
        if (status != null) and.add(Criteria.where("status").is(status));
        Criteria c = and.isEmpty() ? null : new Criteria().andOperator(and.toArray(new Criteria[0]));
        Page<WalletTransaction> p = MongoPaging.page(mongo, c, MongoPaging.newestFirst(page, size), WalletTransaction.class);
        return PageResponse.of(p, TransactionDto::from);
    }

    public PageResponse<AdminLogDto> logs(AuthUser admin, int page, int size) {
        requireAdmin(admin);
        return PageResponse.of(logs.findAll(MongoPaging.newestFirst(page, size)), AdminLogDto::from);
    }

    public GameStatsDto stats(AuthUser admin) {
        requireAdmin(admin);
        int days = 14;
        Instant from = LocalDate.now(ZoneOffset.UTC).minusDays(days - 1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Query mq = Query.query(Criteria.where("createdAt").gte(from));
        mq.fields().include("createdAt");
        Query uq = Query.query(Criteria.where("createdAt").gte(from));
        uq.fields().include("createdAt");
        long matchCount = matchRepo.count();
        long events = mongo.count(new Query(), MatchEvent.class);
        return new GameStatsDto(
                bucket(mongo.find(mq, Match.class).stream().map(m -> m.createdAt).toList(), days),
                bucket(mongo.find(uq, User.class).stream().map(u -> u.createdAt).toList(), days),
                mongo.count(Query.query(Criteria.where("mode").is(2)), Match.class),
                mongo.count(Query.query(Criteria.where("mode").is(4)), Match.class),
                matchRepo.countByStatus(MatchStatus.ABANDONED),
                matchCount == 0 ? 0 : Math.round(10.0 * events / matchCount) / 10.0,
                leaderboard.top(0, 5).items());
    }

    private static List<DailyCount> bucket(List<Instant> times, int days) {
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (int i = days - 1; i >= 0; i--) counts.put(today.minusDays(i), 0L);
        for (Instant t : times) {
            if (t == null) continue;
            counts.computeIfPresent(LocalDate.ofInstant(t, ZoneOffset.UTC), (k, v) -> v + 1);
        }
        List<DailyCount> out = new ArrayList<>();
        counts.forEach((d, n) -> out.add(new DailyCount(d.toString(), n)));
        return out;
    }
}
