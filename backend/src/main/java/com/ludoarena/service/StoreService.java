package com.ludoarena.service;

import com.ludoarena.dto.Dtos.PurchaseDto;
import com.ludoarena.dto.Dtos.PurchaseResult;
import com.ludoarena.dto.Dtos.StoreItemDto;
import com.ludoarena.dto.Dtos.UserDto;
import com.ludoarena.dto.Requests.StoreItemRequest;
import com.ludoarena.exception.ApiException;
import com.ludoarena.model.Enums.ItemCategory;
import com.ludoarena.model.Enums.TxStatus;
import com.ludoarena.model.Enums.TxType;
import com.ludoarena.model.InventoryItem;
import com.ludoarena.model.StoreItem;
import com.ludoarena.model.User;
import com.ludoarena.model.WalletTransaction;
import com.ludoarena.repository.InventoryRepository;
import com.ludoarena.repository.StoreItemRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/** Cosmetic store: items are bought with virtual coins through the wallet ledger. No gameplay advantage is sold. */
@Service
public class StoreService {
    private final StoreItemRepository items;
    private final InventoryRepository inventory;
    private final WalletService wallets;
    private final UserService userService;
    private final MongoTemplate mongo;
    private final NotificationService notifications;

    public StoreService(StoreItemRepository items, InventoryRepository inventory, WalletService wallets,
                        UserService userService, MongoTemplate mongo, NotificationService notifications) {
        this.items = items;
        this.inventory = inventory;
        this.wallets = wallets;
        this.userService = userService;
        this.mongo = mongo;
        this.notifications = notifications;
    }

    public List<StoreItemDto> catalog(String userId, boolean includeInactive) {
        User u = userService.require(userId);
        Set<String> owned = new HashSet<>();
        for (InventoryItem i : inventory.findByUserId(userId)) owned.add(i.itemId);
        List<StoreItemDto> out = new ArrayList<>();
        List<StoreItem> all = includeInactive ? items.findAll() : items.findByActiveTrue();
        for (StoreItem s : all) {
            out.add(StoreItemDto.from(s, owned.contains(s.id), isEquipped(u, s)));
        }
        out.sort((a, b) -> a.category() == b.category() ? Long.compare(a.priceCoins(), b.priceCoins()) : a.category().compareTo(b.category()));
        return out;
    }

    public List<StoreItemDto> inventory(String userId) {
        User u = userService.require(userId);
        List<StoreItemDto> out = new ArrayList<>();
        for (InventoryItem i : inventory.findByUserId(userId)) {
            items.findById(i.itemId).ifPresent(s -> out.add(StoreItemDto.from(s, true, isEquipped(u, s))));
        }
        return out;
    }

    private boolean isEquipped(User u, StoreItem s) {
        return switch (s.category) {
            case BOARD_SKIN -> s.assetKey.equals(u.cosmetics.boardSkin);
            case DICE_SKIN -> s.assetKey.equals(u.cosmetics.diceSkin);
            case PROFILE_THEME -> s.assetKey.equals(u.cosmetics.profileTheme);
            case AVATAR -> s.assetKey.equals(u.avatar);
        };
    }

    /**
     * Idempotent purchase. The ledger reference "PURCHASE:{user}:{item}" can only ever be applied once, so a
     * double click / retry / replay cannot charge twice and an item can never be bought twice.
     */
    public PurchaseResult purchase(String userId, String itemId) {
        StoreItem item = items.findById(itemId).orElseThrow(() -> ApiException.notFound("ITEM_NOT_FOUND", "Item not found"));
        if (!item.active) throw ApiException.badRequest("ITEM_UNAVAILABLE", "This item is not available");
        if (inventory.existsByUserIdAndItemId(userId, itemId)) {
            throw ApiException.conflict("ALREADY_OWNED", "You already own this item");
        }
        String ref = "PURCHASE:" + userId + ":" + itemId;
        WalletTransaction tx = wallets.debit(userId, TxType.STORE_PURCHASE, item.priceCoins, ref, "Purchased " + item.name,
                Map.of("itemId", item.id, "sku", item.sku));
        if (tx.status != TxStatus.COMPLETED) {
            throw ApiException.badRequest("PURCHASE_FAILED", "The purchase could not be completed");
        }
        InventoryItem inv = inventory.findByUserIdAndItemId(userId, itemId).orElse(null);
        if (inv == null) {
            inv = new InventoryItem();
            inv.userId = userId;
            inv.itemId = item.id;
            inv.sku = item.sku;
            inv.name = item.name;
            inv.category = item.category;
            inv.pricePaid = item.priceCoins;
            inv.transactionRef = ref;
            inv.purchasedAt = Instant.now();
            try {
                inv = inventory.save(inv);
            } catch (DuplicateKeyException e) {
                inv = inventory.findByUserIdAndItemId(userId, itemId).orElseThrow();
            }
            notifications.notify(userId, "PURCHASE", "Item unlocked", "You unlocked " + item.name + ".");
        }
        return new PurchaseResult(PurchaseDto.from(inv), wallets.getWalletDto(userId));
    }

    public UserDto equip(String userId, String itemId) {
        StoreItem item = items.findById(itemId).orElseThrow(() -> ApiException.notFound("ITEM_NOT_FOUND", "Item not found"));
        if (!inventory.existsByUserIdAndItemId(userId, itemId)) {
            throw ApiException.forbidden("NOT_OWNED", "Buy this item before equipping it");
        }
        userService.require(userId);
        setCosmetic(userId, item.category, item.assetKey);
        return UserDto.from(userService.require(userId));
    }

    public UserDto unequip(String userId, ItemCategory category) {
        userService.require(userId);
        setCosmetic(userId, category, category == ItemCategory.AVATAR ? "avatar-01" : null);
        return UserDto.from(userService.require(userId));
    }

    /** Targeted $set: never rewrites stats / activeRoomId, which the match engine updates concurrently. */
    private void setCosmetic(String userId, ItemCategory category, String value) {
        String field = switch (category) {
            case BOARD_SKIN -> "cosmetics.boardSkin";
            case DICE_SKIN -> "cosmetics.diceSkin";
            case PROFILE_THEME -> "cosmetics.profileTheme";
            case AVATAR -> "avatar";
        };
        mongo.updateFirst(Query.query(Criteria.where("id").is(userId)),
                new Update().set(field, value).set("updatedAt", Instant.now()), User.class);
    }

    // ------------------------------------------------------------------ admin

    public StoreItem create(StoreItemRequest r) {
        if (items.findBySku(r.sku()).isPresent()) throw ApiException.conflict("SKU_EXISTS", "SKU already exists");
        StoreItem s = new StoreItem();
        apply(s, r);
        s.createdAt = Instant.now();
        s.updatedAt = s.createdAt;
        return items.save(s);
    }

    public StoreItem update(String id, StoreItemRequest r) {
        StoreItem s = items.findById(id).orElseThrow(() -> ApiException.notFound("ITEM_NOT_FOUND", "Item not found"));
        items.findBySku(r.sku()).filter(o -> !o.id.equals(id)).ifPresent(o -> {
            throw ApiException.conflict("SKU_EXISTS", "SKU already exists");
        });
        apply(s, r);
        s.updatedAt = Instant.now();
        return items.save(s);
    }

    private void apply(StoreItem s, StoreItemRequest r) {
        s.sku = r.sku();
        s.name = r.name().trim();
        s.description = r.description();
        s.category = r.category();
        s.priceCoins = r.priceCoins();
        s.rarity = r.rarity() == null || r.rarity().isBlank() ? "COMMON" : r.rarity().toUpperCase();
        s.assetKey = r.assetKey();
        s.active = r.active() == null || r.active();
    }
}
