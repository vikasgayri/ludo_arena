package com.ludoarena.service;

import com.ludoarena.dto.Dtos.PageResponse;
import com.ludoarena.dto.Dtos.PurchaseDto;
import com.ludoarena.dto.Dtos.TransactionDto;
import com.ludoarena.dto.Dtos.WalletDto;
import com.ludoarena.exception.ApiException;
import com.ludoarena.model.Enums.TxStatus;
import com.ludoarena.model.Enums.TxType;
import com.ludoarena.model.InventoryItem;
import com.ludoarena.model.Wallet;
import com.ludoarena.model.WalletTransaction;
import com.ludoarena.repository.InventoryRepository;
import com.ludoarena.repository.WalletRepository;
import com.ludoarena.repository.WalletTransactionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Ledger-based virtual coin wallet.
 *
 * Guarantees:
 *  - every balance change has exactly one ledger entry with a unique referenceId (idempotency key);
 *  - the balance is changed with ONE atomic conditional update ($inc guarded by "not applied yet" and by
 *    "enough coins" for debits), so concurrent requests can never overdraw and a replay never double-applies;
 *  - ledger entries are never deleted; they only move PENDING -> COMPLETED / FAILED once;
 *  - there is no API that sets a balance directly.
 * Works on standalone MongoDB and Atlas (no multi-document transactions required).
 */
@Service
public class WalletService {
    private static final Logger log = LoggerFactory.getLogger(WalletService.class);
    private static final int APPLIED_REFS_WINDOW = 50;

    private final MongoTemplate mongo;
    private final WalletRepository wallets;
    private final WalletTransactionRepository txs;
    private final InventoryRepository inventory;
    private final PlatformSettingsService settings;

    public WalletService(MongoTemplate mongo, WalletRepository wallets, WalletTransactionRepository txs,
                         InventoryRepository inventory, PlatformSettingsService settings) {
        this.mongo = mongo;
        this.wallets = wallets;
        this.txs = txs;
        this.inventory = inventory;
        this.settings = settings;
    }

    // ------------------------------------------------------------------ reads

    public Wallet ensureWallet(String userId) {
        Query q = Query.query(Criteria.where("userId").is(userId));
        Update u = new Update()
                .setOnInsert("coinBalance", 0L)
                .setOnInsert("currencyType", "NON_REDEEMABLE")
                .setOnInsert("appliedRefs", new ArrayList<String>())
                .setOnInsert("createdAt", Instant.now())
                .setOnInsert("updatedAt", Instant.now());
        try {
            return mongo.findAndModify(q, u, FindAndModifyOptions.options().upsert(true).returnNew(true), Wallet.class);
        } catch (DuplicateKeyException e) {
            return wallets.findByUserId(userId).orElseThrow(() -> ApiException.notFound("WALLET_NOT_FOUND", "Wallet not found"));
        }
    }

    public WalletDto getWalletDto(String userId) {
        Wallet w = ensureWallet(userId);
        return WalletDto.from(w, dailyBonusAvailable(w), settings.get().dailyBonusCoins);
    }

    public long balance(String userId) {
        return ensureWallet(userId).coinBalance;
    }

    private boolean dailyBonusAvailable(Wallet w) {
        if (settings.get().dailyBonusCoins <= 0) return false;
        return w.lastDailyBonusAt == null
                || !LocalDate.ofInstant(w.lastDailyBonusAt, ZoneOffset.UTC).equals(LocalDate.now(ZoneOffset.UTC));
    }

    public PageResponse<TransactionDto> transactions(String userId, TxType type, int page, int size) {
        Criteria c = Criteria.where("userId").is(userId);
        if (type != null) c = c.and("type").is(type);
        Page<WalletTransaction> p = MongoPaging.page(mongo, c, MongoPaging.newestFirst(page, size), WalletTransaction.class);
        return PageResponse.of(p, TransactionDto::from);
    }

    public PageResponse<PurchaseDto> purchases(String userId, int page, int size) {
        Page<InventoryItem> p = inventory.findByUserId(userId,
                MongoPaging.pageable(page, size, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "purchasedAt")));
        return PageResponse.of(p, PurchaseDto::from);
    }

    // ----------------------------------------------------------------- writes

    public WalletTransaction credit(String userId, TxType type, long amount, String referenceId, String description) {
        if (amount <= 0) throw ApiException.badRequest("INVALID_AMOUNT", "Credit amount must be positive");
        return apply(userId, type, amount, referenceId, description, null);
    }

    public WalletTransaction debit(String userId, TxType type, long amount, String referenceId, String description,
                                   Map<String, Object> metadata) {
        if (amount <= 0) throw ApiException.badRequest("INVALID_AMOUNT", "Debit amount must be positive");
        return apply(userId, type, -amount, referenceId, description, metadata);
    }

    /**
     * Applies a signed amount exactly once for the given reference id. Replaying the same reference returns the
     * existing ledger entry (COMPLETED or FAILED) without touching the balance again.
     */
    public WalletTransaction apply(String userId, TxType type, long signedAmount, String referenceId, String description,
                                   Map<String, Object> metadata) {
        if (signedAmount == 0) throw ApiException.badRequest("INVALID_AMOUNT", "Amount must not be zero");
        if (referenceId == null || referenceId.isBlank()) throw ApiException.badRequest("INVALID_REFERENCE", "Reference id required");
        ensureWallet(userId);

        WalletTransaction tx = txs.findByReferenceId(referenceId).orElse(null);
        if (tx == null) {
            tx = new WalletTransaction();
            tx.userId = userId;
            tx.type = type;
            tx.amount = signedAmount;
            tx.referenceId = referenceId;
            tx.description = description;
            tx.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
            tx.status = TxStatus.PENDING;
            tx.createdAt = Instant.now();
            try {
                tx = mongo.insert(tx);
            } catch (DuplicateKeyException e) {
                tx = txs.findByReferenceId(referenceId)
                        .orElseThrow(() -> ApiException.conflict("DUPLICATE_TRANSACTION", "Duplicate transaction"));
            }
        }
        if (!tx.userId.equals(userId) || tx.amount != signedAmount) {
            throw ApiException.conflict("REFERENCE_REUSED", "Reference id already used by a different transaction");
        }
        if (tx.status != TxStatus.PENDING) return tx; // idempotent replay
        return settle(tx);
    }

    private WalletTransaction settle(WalletTransaction tx) {
        Query q = new Query(Criteria.where("userId").is(tx.userId).and("appliedRefs").ne(tx.referenceId));
        if (tx.amount < 0) q.addCriteria(Criteria.where("coinBalance").gte(-tx.amount));
        Update u = new Update().inc("coinBalance", tx.amount).set("updatedAt", Instant.now());
        u.push("appliedRefs").slice(-APPLIED_REFS_WINDOW).each(tx.referenceId);

        Wallet after = mongo.findAndModify(q, u, FindAndModifyOptions.options().returnNew(true), Wallet.class);
        if (after != null) {
            return complete(tx, after.coinBalance, false);
        }
        Wallet current = wallets.findByUserId(tx.userId).orElse(null);
        if (current != null && current.appliedRefs.contains(tx.referenceId)) {
            // the balance was already changed by an earlier attempt that crashed before finishing the ledger entry
            log.warn("Recovering ledger entry {} (balance already applied)", tx.referenceId);
            return complete(tx, current.coinBalance, true);
        }
        failPending(tx);
        throw ApiException.badRequest("INSUFFICIENT_COINS", "Not enough coins");
    }

    private WalletTransaction complete(WalletTransaction tx, long newBalance, boolean recovered) {
        Query q = Query.query(Criteria.where("id").is(tx.id).and("status").is(TxStatus.PENDING));
        Update u = new Update()
                .set("status", TxStatus.COMPLETED)
                .set("newBalance", newBalance)
                .set("previousBalance", newBalance - tx.amount)
                .set("completedAt", Instant.now());
        if (recovered) u.set("metadata.recovered", true);
        mongo.updateFirst(q, u, WalletTransaction.class);
        return txs.findById(tx.id).orElseThrow(() -> ApiException.notFound("TX_NOT_FOUND", "Transaction not found"));
    }

    private void failPending(WalletTransaction tx) {
        Query q = Query.query(Criteria.where("id").is(tx.id).and("status").is(TxStatus.PENDING));
        mongo.updateFirst(q, new Update().set("status", TxStatus.FAILED).set("completedAt", Instant.now()), WalletTransaction.class);
    }

    // ------------------------------------------------------------ promotions

    public void grantSignupBonus(String userId) {
        long bonus = settings.get().signupBonusCoins;
        if (bonus > 0) {
            credit(userId, TxType.SIGNUP_BONUS, bonus, "SIGNUP:" + userId, "Welcome bonus");
        }
    }

    public WalletTransaction claimDailyBonus(String userId) {
        long bonus = settings.get().dailyBonusCoins;
        if (bonus <= 0) throw ApiException.badRequest("DAILY_BONUS_DISABLED", "Daily bonus is currently disabled");
        String ref = "DAILY:" + userId + ":" + LocalDate.now(ZoneOffset.UTC);
        if (txs.findByReferenceId(ref).filter(t -> t.status == TxStatus.COMPLETED).isPresent()) {
            throw ApiException.conflict("ALREADY_CLAIMED", "You already claimed today's bonus");
        }
        WalletTransaction tx = credit(userId, TxType.DAILY_BONUS, bonus, ref, "Daily login bonus");
        mongo.updateFirst(Query.query(Criteria.where("userId").is(userId)), new Update().set("lastDailyBonusAt", Instant.now()), Wallet.class);
        return tx;
    }
}
