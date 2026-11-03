package com.ludoarena.controller;

import com.ludoarena.dto.Dtos.ApiResponse;
import com.ludoarena.dto.Dtos.PageResponse;
import com.ludoarena.dto.Dtos.PurchaseDto;
import com.ludoarena.dto.Dtos.TransactionDto;
import com.ludoarena.dto.Dtos.WalletDto;
import com.ludoarena.model.Enums.TxType;
import com.ludoarena.security.AuthUser;
import com.ludoarena.service.WalletService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet")
public class WalletController {
    private final WalletService wallets;

    public WalletController(WalletService wallets) {
        this.wallets = wallets;
    }

    @GetMapping
    public ApiResponse<WalletDto> wallet(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(wallets.getWalletDto(me.id()));
    }

    @GetMapping("/transactions")
    public ApiResponse<PageResponse<TransactionDto>> transactions(@AuthenticationPrincipal AuthUser me,
                                                                  @RequestParam(required = false) TxType type,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(wallets.transactions(me.id(), type, page, size));
    }

    @GetMapping("/purchases")
    public ApiResponse<PageResponse<PurchaseDto>> purchases(@AuthenticationPrincipal AuthUser me,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(wallets.purchases(me.id(), page, size));
    }

    @PostMapping("/daily-bonus")
    public ApiResponse<WalletDto> dailyBonus(@AuthenticationPrincipal AuthUser me) {
        wallets.claimDailyBonus(me.id());
        return ApiResponse.ok(wallets.getWalletDto(me.id()), "Daily bonus claimed");
    }
}
