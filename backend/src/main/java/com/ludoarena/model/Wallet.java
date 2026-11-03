package com.ludoarena.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("wallets")
public class Wallet {
    @Id
    public String id;
    @Indexed(unique = true)
    public String userId;
    public long coinBalance;
    /** Coins are virtual entertainment currency: never redeemable, never withdrawable. */
    public String currencyType = "NON_REDEEMABLE";
    /** Most recent ledger reference ids applied to this wallet - gives exactly-once balance updates. */
    public List<String> appliedRefs = new ArrayList<>();
    public Instant lastDailyBonusAt;
    public Instant createdAt;
    public Instant updatedAt;
}
