package com.ludoarena.controller;

import com.ludoarena.dto.Dtos.ApiResponse;
import com.ludoarena.dto.Dtos.PaymentOrderDto;
import com.ludoarena.dto.Dtos.PaymentStatusDto;
import com.ludoarena.dto.Dtos.WalletDto;
import com.ludoarena.dto.Requests.CreatePaymentOrder;
import com.ludoarena.dto.Requests.VerifyPayment;
import com.ludoarena.security.AuthUser;
import com.ludoarena.service.PaymentService;
import com.ludoarena.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Disabled by default: every mutating endpoint answers 403 PAYMENTS_DISABLED until app.payments.enabled=true. */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService payments;
    private final WalletService wallets;

    public PaymentController(PaymentService payments, WalletService wallets) {
        this.payments = payments;
        this.wallets = wallets;
    }

    @GetMapping("/status")
    public ApiResponse<PaymentStatusDto> status() {
        return ApiResponse.ok(payments.status());
    }

    @PostMapping("/orders")
    public ApiResponse<PaymentOrderDto> createOrder(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreatePaymentOrder req) {
        return ApiResponse.ok(payments.createOrder(me.id(), req.packageId()));
    }

    @PostMapping("/verify")
    public ApiResponse<WalletDto> verify(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody VerifyPayment req) {
        payments.verify(me.id(), req.orderId(), req.paymentId(), req.signature());
        return ApiResponse.ok(wallets.getWalletDto(me.id()), "Payment verified");
    }

    /** Public URL but authenticated by HMAC signature of the raw body. */
    @PostMapping("/webhook")
    public ApiResponse<Void> webhook(@RequestBody String rawBody, @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        payments.handleWebhook(rawBody, signature);
        return ApiResponse.ok(null);
    }
}
