package com.ludoarena.model;

import com.ludoarena.model.Enums.ItemCategory;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("storeItems")
public class StoreItem {
    @Id
    public String id;
    @Indexed(unique = true)
    public String sku;
    public String name;
    public String description;
    @Indexed
    public ItemCategory category;
    public long priceCoins;
    public String rarity = "COMMON";
    /** key the frontend maps to a visual (palette, dice style, avatar glyph, theme) */
    public String assetKey;
    public boolean active = true;
    public Instant createdAt;
    public Instant updatedAt;
}
