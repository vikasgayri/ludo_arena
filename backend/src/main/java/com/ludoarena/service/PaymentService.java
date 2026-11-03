package com.ludoarena.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ludoarena.config.AppProperties;
import com.ludoarena.dto.Dtos.CoinPackage;
import com.ludoarena.dto.Dtos.PaymentOrderDto;
import com.ludoarena.dto.Dtos.PaymentStatusDto;
import com.ludoarena.exception.ApiException;
import com.ludoarena.model.Enums.PaymentStatus;
import com.ludoarena.model.Enums.TxType;
import com.ludoarena.model.PaymentOrder;
import com.ludoarena.repository.PaymentOrderRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Coin-pack purchase flow. DISABLED BY DEFAULT (app.payments.enabled=false): every method except status() refuses
 * to run until the operator has explicitly enabled it after provider approval and legal review.
 *
 * Coins are credited ONLY from server-verified facts: a verified checkout signature or a verified webhook. The browser's
 * redirect/"success" callback alone never credits anything. Crediting is idempotent (ledger reference PAY:{paymentId}).
 */
@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    static final List<CoinPackage> PACKAGES = List.of(
            new CoinPackage("coins_500", "Starter Pack", 500, 4900, "INR"),
            new CoinPackage("coins_1200", "Player Pack", 1200, 9900, "INR"),
            new CoinPackage("coins_3000", "Champion Pack", 3000, 19900, "INR"));

    private final AppProperties props;
    private final PaymentProvider provider;
    private final PaymentOrderRepository orders;
    private final MongoTemplate mongo;
    private final WalletService wallets;
    private final NotificationService notifications;
    private final ObjectMapper mapper;

    public PaymentService(AppProperties props, PaymentProvider provider, PaymentOrderRepository orders, MongoTemplate mongo,
                          WalletService wallets, NotificationService notifications, ObjectMapper mapper) {
        this.props = props;
        this.provider = provider;
        this.orders = orders;
        this.mongo = mongo;
        this.wallets = wallets;
        this.notifications = notifications;
        this.mapper = mapper;
    }

    public boolean enabled() {
        return props.payments().enabled();
    }

    private void requireEnabled() {
        if (!enabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PAYMENTS_DISABLED",
                    "Coin purchases are not available. Coins are earned through play and promotions.");
        }
    }

    public PaymentStatusDto status() {
        return enabled()
                ? new PaymentStatusDto(true, "Coin packs are available. Coins are non-redeemable virtual currency.", PACKAGES)
                : new PaymentStatusDto(false, "Purchases are disabled. Coins are earned through play and promotions.", List.of());
    }

    public PaymentOrderDto createOrder(String userId, String packageId) {
        requireEnabled();
        CoinPackage pack = PACKAGES.stream().filter(p -> p.id().equals(packageId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("PACKAGE_NOT_FOUND", "Unknown coin package"));
        PaymentProvider.ProviderOrder po = provider.createOrder(pack.priceMinor(), pack.currency(), "ludo_" + userId + "_" + System.currentTimeMillis());
        PaymentOrder o = new PaymentOrder();
        o.userId = userId;
        o.providerOrderId = po.id();
        o.packageId = pack.id();
        o.coins = pack.coins();
        o.amountMinor = pack.priceMinor();
        o.currency = pack.currency();
        o.createdAt = Instant.now();
        o.updatedAt = o.createdAt;
        o = orders.save(o);
        return new PaymentOrderDto(o.id, o.providerOrderId, o.amountMinor, o.currency, provider.publicKeyId(), o.coins);
    }

    /** Checkout callback: the signature is verified server-side before anything is credited. */
    public void verify(String userId, String orderId, String paymentId, String signature) {
        requireEnabled();
        PaymentOrder o = orders.findByProviderOrderId(orderId).orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "Order not found"));
        if (!o.userId.equals(userId)) throw ApiException.forbidden("NOT_YOUR_ORDER", "Order belongs to another account");
        if (!provider.verifyPaymentSignature(orderId, paymentId, signature)) {
            markFailed(o, "Invalid signature");
            throw ApiException.badRequest("INVALID_SIGNATURE", "Payment signature could not be verified");
        }
        settle(o, paymentId);
    }

    /** Gateway -> server webhook. The raw body is authenticated with the webhook secret; nothing else is trusted. */
    public void handleWebhook(String rawBody, String signature) {
        requireEnabled();
        if (!provider.verifyWebhookSignature(rawBody, signature)) {
            throw ApiException.unauthorized("INVALID_SIGNATURE", "Webhook signature invalid");
        }
        try {
            JsonNode root = mapper.readTree(rawBody);
            String event = root.path("event").asText();
            JsonNode pay = root.path("payload").path("payment").path("entity");
            String orderId = pay.path("order_id").asText(null);
            String paymentId = pay.path("id").asText(null);
            if (orderId == null || paymentId == null) return;
            PaymentOrder o = orders.findByProviderOrderId(orderId).orElse(null);
            if (o == null) return;
            if ("payment.captured".equals(event) || "order.paid".equals(event)) {
                if (pay.path("amount").asLong(-1) != o.amountMinor) {
                    markFailed(o, "Amount mismatch");
                    return;
                }
                settle(o, paymentId);
            } else if ("payment.failed".equals(event)) {
                markFailed(o, pay.path("error_description").asText("Payment failed"));
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw ApiException.badRequest("BAD_WEBHOOK", "Malformed webhook body");
        }
    }

    /** Exactly-once: order CREATED -> PAID is claimed atomically, and the ledger reference is unique per payment. */
    private void settle(PaymentOrder o, String paymentId) {
        var claim = mongo.updateFirst(Query.query(Criteria.where("id").is(o.id).and("status").is(PaymentStatus.CREATED)),
                new Update().set("status", PaymentStatus.PAID).set("providerPaymentId", paymentId).set("updatedAt", Instant.now()),
                PaymentOrder.class);
        // even if another request already claimed it, the idempotent credit below is a safe no-op replay
        wallets.credit(o.userId, TxType.PAYMENT_CREDIT, o.coins, "PAY:" + paymentId, "Coin pack " + o.packageId);
        if (claim.getModifiedCount() > 0) {
            notifications.notify(o.userId, "WALLET", "Coins added", o.coins + " coins were added to your wallet.");
        }
    }

    private void markFailed(PaymentOrder o, String reason) {
        mongo.updateFirst(Query.query(Criteria.where("id").is(o.id).and("status").is(PaymentStatus.CREATED)),
                new Update().set("status", PaymentStatus.FAILED).set("failureReason", reason).set("updatedAt", Instant.now()),
                PaymentOrder.class);
    }

    /** Hourly: asks the gateway about orders that were never confirmed (lost webhook / closed tab). */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 300_000)
    public void reconcile() {
        if (!enabled()) return;
        Instant cutoff = Instant.now().minus(15, ChronoUnit.MINUTES);
        List<PaymentOrder> open = mongo.find(Query.query(Criteria.where("status").is(PaymentStatus.CREATED).and("createdAt").lt(cutoff)), PaymentOrder.class);
        for (PaymentOrder o : open) {
            try {
                for (PaymentProvider.ProviderPayment p : provider.fetchPayments(o.providerOrderId)) {
                    if ("captured".equals(p.status()) && p.amountMinor() == o.amountMinor) {
                        settle(o, p.id());
                        break;
                    }
                }
                if (o.createdAt.isBefore(Instant.now().minus(24, ChronoUnit.HOURS))) markFailed(o, "Expired without payment");
            } catch (RuntimeException e) {
                log.warn("Reconciliation failed for order {}: {}", o.providerOrderId, e.getMessage());
            }
        }
    }
}
