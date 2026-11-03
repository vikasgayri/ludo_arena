package com.ludoarena.model;

import com.ludoarena.model.Enums.TxStatus;
import com.ludoarena.model.Enums.TxType;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Append-only ledger entry. Only the PENDING -> COMPLETED/FAILED transition is ever written. */
@Document("walletTransactions")
@CompoundIndex(name = "user_createdAt", def = "{'userId': 1, 'createdAt': -1}")
@CompoundIndex(name = "type_status_createdAt", def = "{'type': 1, 'status': 1, 'createdAt': -1}")
public class WalletTransaction {
    @Id
    public String id;
    public String userId;
    public TxType type;
    /** signed: positive = credit, negative = debit */
    public long amount;
    public Long previousBalance;
    public Long newBalance;
    @Indexed(unique = true)
    public String referenceId;
    public TxStatus status = TxStatus.PENDING;
    public String description;
    public Map<String, Object> metadata = new LinkedHashMap<>();
    public Instant createdAt;
    public Instant completedAt;
}
