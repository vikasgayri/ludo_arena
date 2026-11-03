package com.ludoarena.service;

import java.util.List;

/** Gateway abstraction so the payment flow is provider-neutral. */
public interface PaymentProvider {
    record ProviderOrder(String id, long amountMinor, String currency) {
    }

    record ProviderPayment(String id, String status, long amountMinor) {
    }

    ProviderOrder createOrder(long amountMinor, String currency, String receipt);

    /** Server-side check of the signature the gateway returns to the browser after checkout. */
    boolean verifyPaymentSignature(String orderId, String paymentId, String signature);

    /** Verifies the HMAC of a raw webhook body. */
    boolean verifyWebhookSignature(String rawBody, String signature);

    /** Asks the gateway which payments exist for an order (reconciliation). */
    List<ProviderPayment> fetchPayments(String orderId);

    String publicKeyId();
}
