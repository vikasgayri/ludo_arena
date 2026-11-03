package com.ludoarena.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ludoarena.exception.ApiException;
import com.ludoarena.model.Enums.TxStatus;
import com.ludoarena.model.Enums.TxType;
import com.ludoarena.model.PlatformSettings;
import com.ludoarena.model.Wallet;
import com.ludoarena.model.WalletTransaction;
import com.ludoarena.repository.InventoryRepository;
import com.ludoarena.repository.WalletRepository;
import com.ludoarena.repository.WalletTransactionRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.UpdateDefinition;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WalletServiceTest {
    @Mock MongoTemplate mongo;
    @Mock WalletRepository wallets;
    @Mock WalletTransactionRepository txs;
    @Mock InventoryRepository inventory;
    @Mock PlatformSettingsService settings;

    private WalletService service;

    @BeforeEach
    void setUp() {
        service = new WalletService(mongo, wallets, txs, inventory, settings);
        when(settings.get()).thenReturn(new PlatformSettings());
        when(mongo.insert(any(WalletTransaction.class))).thenAnswer(i -> {
            WalletTransaction t = i.getArgument(0);
            t.id = "tx-1";
            return t;
        });
    }

    private static WalletTransaction completed(String user, long amount, String ref) {
        WalletTransaction t = new WalletTransaction();
        t.id = "tx-old";
        t.userId = user;
        t.amount = amount;
        t.referenceId = ref;
        t.status = TxStatus.COMPLETED;
        t.newBalance = 100L;
        t.previousBalance = 100L - amount;
        return t;
    }

    private void walletUpdateReturns(Wallet w) {
        when(mongo.findAndModify(any(Query.class), any(UpdateDefinition.class), any(FindAndModifyOptions.class), eq(Wallet.class)))
                .thenReturn(w);
    }

    @Test
    void creditAppliesOnceAndRecordsPreviousAndNewBalance() {
        Wallet after = new Wallet();
        after.userId = "u1";
        after.coinBalance = 150;
        walletUpdateReturns(after);
        WalletTransaction done = completed("u1", 50, "REF-1");
        done.previousBalance = 100L;
        done.newBalance = 150L;
        when(txs.findById("tx-1")).thenReturn(Optional.of(done));

        WalletTransaction result = service.credit("u1", TxType.PROMO_CREDIT, 50, "REF-1", "promo");

        assertEquals(TxStatus.COMPLETED, result.status);
        assertEquals(150L, result.newBalance.longValue());
        assertEquals(100L, result.previousBalance.longValue());
        verify(mongo).insert(any(WalletTransaction.class));
    }

    @Test
    void replayingAReferenceReturnsTheExistingEntryWithoutChangingTheBalance() {
        when(txs.findByReferenceId("REF-1")).thenReturn(Optional.of(completed("u1", 50, "REF-1")));

        WalletTransaction result = service.credit("u1", TxType.PROMO_CREDIT, 50, "REF-1", "promo");

        assertEquals("tx-old", result.id);
        verify(mongo, never()).insert(any(WalletTransaction.class));
        // only the wallet-existence upsert may touch the wallet; the balance $inc must not run again
        verify(mongo, org.mockito.Mockito.times(1)).findAndModify(any(Query.class), any(UpdateDefinition.class),
                any(FindAndModifyOptions.class), eq(Wallet.class));
    }

    @Test
    void reusingAReferenceForADifferentAmountIsRejected() {
        when(txs.findByReferenceId("REF-1")).thenReturn(Optional.of(completed("u1", 50, "REF-1")));
        ApiException e = assertThrows(ApiException.class, () -> service.credit("u1", TxType.PROMO_CREDIT, 999, "REF-1", "x"));
        assertEquals("REFERENCE_REUSED", e.code());
    }

    @Test
    void referenceCannotBeReusedByAnotherUser() {
        when(txs.findByReferenceId("REF-1")).thenReturn(Optional.of(completed("u1", 50, "REF-1")));
        ApiException e = assertThrows(ApiException.class, () -> service.credit("u2", TxType.PROMO_CREDIT, 50, "REF-1", "x"));
        assertEquals("REFERENCE_REUSED", e.code());
    }

    @Test
    void debitWithInsufficientCoinsFailsTheEntryAndThrows() {
        walletUpdateReturns(null); // the guarded $inc matched nothing
        Wallet current = new Wallet();
        current.userId = "u1";
        current.coinBalance = 10;
        when(wallets.findByUserId("u1")).thenReturn(Optional.of(current));

        ApiException e = assertThrows(ApiException.class,
                () -> service.debit("u1", TxType.STORE_PURCHASE, 500, "PURCHASE:u1:item", "buy", null));

        assertEquals("INSUFFICIENT_COINS", e.code());
        verify(mongo).updateFirst(any(Query.class), any(UpdateDefinition.class), eq(WalletTransaction.class));
    }

    @Test
    void recoversWhenTheBalanceWasAlreadyAppliedByACrashedAttempt() {
        walletUpdateReturns(null);
        Wallet current = new Wallet();
        current.userId = "u1";
        current.coinBalance = 70;
        current.appliedRefs.add("PURCHASE:u1:item");
        when(wallets.findByUserId("u1")).thenReturn(Optional.of(current));
        WalletTransaction pending = new WalletTransaction();
        pending.id = "tx-pending";
        pending.userId = "u1";
        pending.amount = -30;
        pending.referenceId = "PURCHASE:u1:item";
        pending.status = TxStatus.PENDING;
        when(txs.findByReferenceId("PURCHASE:u1:item")).thenReturn(Optional.of(pending));
        when(txs.findById("tx-pending")).thenReturn(Optional.of(completed("u1", -30, "PURCHASE:u1:item")));

        WalletTransaction r = service.debit("u1", TxType.STORE_PURCHASE, 30, "PURCHASE:u1:item", "buy", null);

        assertEquals(TxStatus.COMPLETED, r.status);
        verify(mongo, never()).insert(any(WalletTransaction.class));
    }

    @Test
    void zeroAndNegativeAmountsAreRejected() {
        assertThrows(ApiException.class, () -> service.credit("u1", TxType.PROMO_CREDIT, 0, "R", "x"));
        assertThrows(ApiException.class, () -> service.credit("u1", TxType.PROMO_CREDIT, -5, "R", "x"));
        assertThrows(ApiException.class, () -> service.debit("u1", TxType.STORE_PURCHASE, 0, "R", "x", null));
        assertThrows(ApiException.class, () -> service.apply("u1", TxType.ADMIN_ADJUSTMENT, 5, " ", "x", null));
    }
}
