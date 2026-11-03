package com.ludoarena.model;

import com.ludoarena.model.Enums.ItemCategory;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("userInventory")
@CompoundIndex(name = "user_item", def = "{'userId': 1, 'itemId': 1}", unique = true)
@CompoundIndex(name = "user_purchasedAt", def = "{'userId': 1, 'purchasedAt': -1}")
public class InventoryItem {
    @Id
    public String id;
    public String userId;
    public String itemId;
    public String sku;
    public String name;
    public ItemCategory category;
    public long pricePaid;
    public String transactionRef;
    public Instant purchasedAt;
}
