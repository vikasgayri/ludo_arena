package com.ludoarena.config;

import com.ludoarena.model.Enums.ItemCategory;
import com.ludoarena.model.Enums.Role;
import com.ludoarena.model.StoreItem;
import com.ludoarena.model.User;
import com.ludoarena.repository.StoreItemRepository;
import com.ludoarena.repository.UserRepository;
import com.ludoarena.service.PlatformSettingsService;
import com.ludoarena.service.WalletService;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Startup bootstrap: default platform settings, the cosmetic catalogue (only when the collection is empty) and the
 * optional first SUPER_ADMIN taken from environment variables. No credentials are hard-coded.
 */
@Component
public class DataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final PlatformSettingsService settings;
    private final StoreItemRepository storeItems;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final WalletService wallets;
    private final AppProperties props;

    public DataSeeder(PlatformSettingsService settings, StoreItemRepository storeItems, UserRepository users,
                      PasswordEncoder encoder, WalletService wallets, AppProperties props) {
        this.settings = settings;
        this.storeItems = storeItems;
        this.users = users;
        this.encoder = encoder;
        this.wallets = wallets;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        settings.get();
        seedStore();
        bootstrapAdmin();
    }

    private void seedStore() {
        if (storeItems.count() > 0) return;
        add("board-neon-night", "Neon Night Board", "Deep navy board with neon glow lanes.", ItemCategory.BOARD_SKIN, 300, "RARE", "board-neon");
        add("board-royal-gold", "Royal Gold Board", "Regal cream and gold finish.", ItemCategory.BOARD_SKIN, 500, "EPIC", "board-royal");
        add("board-forest", "Forest Board", "Calm green wood grain.", ItemCategory.BOARD_SKIN, 350, "COMMON", "board-forest");
        add("board-ocean-glass", "Ocean Glass Board", "Cool translucent blues.", ItemCategory.BOARD_SKIN, 400, "RARE", "board-ocean");
        add("dice-obsidian", "Obsidian Dice", "Black dice with silver pips.", ItemCategory.DICE_SKIN, 150, "COMMON", "dice-obsidian");
        add("dice-gold-pips", "Gold Pip Dice", "Ivory dice with gold pips.", ItemCategory.DICE_SKIN, 250, "RARE", "dice-gold");
        add("dice-neon", "Neon Dice", "Glowing violet dice.", ItemCategory.DICE_SKIN, 200, "RARE", "dice-neon");
        add("avatar-crown", "Crown Avatar", "A golden crown badge.", ItemCategory.AVATAR, 400, "EPIC", "avatar-crown");
        add("avatar-dragon", "Dragon Avatar", "A fierce dragon emblem.", ItemCategory.AVATAR, 500, "EPIC", "avatar-dragon");
        add("avatar-rocket", "Rocket Avatar", "Blast off.", ItemCategory.AVATAR, 300, "RARE", "avatar-rocket");
        add("theme-aurora", "Aurora Profile Theme", "Shimmering green-violet profile banner.", ItemCategory.PROFILE_THEME, 350, "RARE", "theme-aurora");
        add("theme-sunset", "Sunset Profile Theme", "Warm orange-pink profile banner.", ItemCategory.PROFILE_THEME, 350, "RARE", "theme-sunset");
        log.info("Seeded cosmetic store catalogue");
    }

    private void add(String sku, String name, String desc, ItemCategory cat, long price, String rarity, String assetKey) {
        StoreItem s = new StoreItem();
        s.sku = sku;
        s.name = name;
        s.description = desc;
        s.category = cat;
        s.priceCoins = price;
        s.rarity = rarity;
        s.assetKey = assetKey;
        s.createdAt = Instant.now();
        s.updatedAt = s.createdAt;
        storeItems.save(s);
    }

    private void bootstrapAdmin() {
        String email = props.bootstrapAdmin().email();
        String password = props.bootstrapAdmin().password();
        if (email == null || email.isBlank() || password == null || password.isBlank()) return;
        if (users.existsByRole(Role.SUPER_ADMIN)) return;
        if (password.length() < 10) {
            log.error("ADMIN_BOOTSTRAP_PASSWORD must be at least 10 characters; super admin was NOT created");
            return;
        }
        User u = new User();
        u.name = "Super Admin";
        u.email = email.trim().toLowerCase();
        u.passwordHash = encoder.encode(password);
        u.role = Role.SUPER_ADMIN;
        u.createdAt = Instant.now();
        u.updatedAt = u.createdAt;
        u = users.save(u);
        wallets.ensureWallet(u.id);
        log.info("Bootstrapped SUPER_ADMIN account {}", u.email);
    }
}
