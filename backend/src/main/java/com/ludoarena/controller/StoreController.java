package com.ludoarena.controller;

import com.ludoarena.dto.Dtos.ApiResponse;
import com.ludoarena.dto.Dtos.PurchaseResult;
import com.ludoarena.dto.Dtos.StoreItemDto;
import com.ludoarena.dto.Dtos.UserDto;
import com.ludoarena.dto.Requests.EquipRequest;
import com.ludoarena.dto.Requests.PurchaseRequest;
import com.ludoarena.dto.Requests.UnequipRequest;
import com.ludoarena.security.AuthUser;
import com.ludoarena.service.StoreService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/store")
public class StoreController {
    private final StoreService store;

    public StoreController(StoreService store) {
        this.store = store;
    }

    @GetMapping("/items")
    public ApiResponse<List<StoreItemDto>> items(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(store.catalog(me.id(), false));
    }

    @PostMapping("/purchase")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PurchaseResult> purchase(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody PurchaseRequest req) {
        return ApiResponse.ok(store.purchase(me.id(), req.itemId()), "Item unlocked");
    }

    @GetMapping("/inventory")
    public ApiResponse<List<StoreItemDto>> inventory(@AuthenticationPrincipal AuthUser me) {
        return ApiResponse.ok(store.inventory(me.id()));
    }

    @PostMapping("/equip")
    public ApiResponse<UserDto> equip(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody EquipRequest req) {
        return ApiResponse.ok(store.equip(me.id(), req.itemId()), "Equipped");
    }

    @PostMapping("/unequip")
    public ApiResponse<UserDto> unequip(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UnequipRequest req) {
        return ApiResponse.ok(store.unequip(me.id(), req.category()), "Unequipped");
    }
}
