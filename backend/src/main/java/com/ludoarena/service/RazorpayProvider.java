package com.ludoarena.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ludoarena.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Razorpay-compatible implementation (orders API + HMAC-SHA256 signatures).
 * NOT exercised against the live gateway in this repository - see TODO.md. Only used when payments are enabled.
 */
@Component
public class RazorpayProvider implements PaymentProvider {
    private final AppProperties.Payments cfg;

    public RazorpayProvider(AppProperties props) {
        this.cfg = props.payments();
    }

    private RestClient client() {
        return RestClient.builder()
                .baseUrl(cfg.apiBaseUrl())
                .defaultHeaders(h -> h.setBasicAuth(cfg.keyId(), cfg.keySecret()))
                .build();
    }

    @Override
    public ProviderOrder createOrder(long amountMinor, String currency, String receipt) {
        JsonNode res = client().post().uri("/orders").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", amountMinor, "currency", currency, "receipt", receipt))
                .retrieve().body(JsonNode.class);
        if (res == null || !res.hasNonNull("id")) throw new IllegalStateException("Gateway returned no order id");
        return new ProviderOrder(res.get("id").asText(), res.path("amount").asLong(amountMinor), res.path("currency").asText(currency));
    }

    @Override
    public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) {
        return constantTimeEquals(hmacHex(cfg.keySecret(), orderId + "|" + paymentId), signature);
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        if (cfg.webhookSecret() == null || cfg.webhookSecret().isBlank()) return false;
        return constantTimeEquals(hmacHex(cfg.webhookSecret(), rawBody), signature);
    }

    @Override
    public List<ProviderPayment> fetchPayments(String orderId) {
        JsonNode res = client().get().uri("/orders/{id}/payments", orderId).retrieve().body(JsonNode.class);
        List<ProviderPayment> out = new ArrayList<>();
        if (res != null && res.has("items")) {
            for (JsonNode n : res.get("items")) {
                out.add(new ProviderPayment(n.path("id").asText(), n.path("status").asText(), n.path("amount").asLong()));
            }
        }
        return out;
    }

    @Override
    public String publicKeyId() {
        return cfg.keyId();
    }

    static String hmacHex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean constantTimeEquals(String a, String b) {
        return a != null && b != null && java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
