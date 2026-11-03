package com.ludoarena.model;

import com.ludoarena.model.Enums.PaymentStatus;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("paymentOrders")
public class PaymentOrder {
    @Id
    public String id;
    @Indexed
    public String userId;
    @Indexed(unique = true)
    public String providerOrderId;
    @Indexed(unique = true, sparse = true)
    public String providerPaymentId;
    public String packageId;
    public long coins;
    public long amountMinor;
    public String currency;
    public PaymentStatus status = PaymentStatus.CREATED;
    public String failureReason;
    public Instant createdAt;
    public Instant updatedAt;
}
