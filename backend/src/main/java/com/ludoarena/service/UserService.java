package com.ludoarena.service;

import com.ludoarena.dto.Dtos.AchievementDto;
import com.ludoarena.dto.Dtos.CosmeticsDto;
import com.ludoarena.dto.Dtos.PublicProfileDto;
import com.ludoarena.dto.Dtos.StatsDto;
import com.ludoarena.dto.Dtos.UserDto;
import com.ludoarena.dto.Requests.UpdateProfile;
import com.ludoarena.exception.ApiException;
import com.ludoarena.model.InventoryItem;
import com.ludoarena.model.StoreItem;
import com.ludoarena.model.User;
import com.ludoarena.repository.InventoryRepository;
import com.ludoarena.repository.StoreItemRepository;
import com.ludoarena.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private static final Pattern FREE_AVATAR = Pattern.compile("^avatar-(0[1-9]|1[0-2])$");

    private final UserRepository users;
    private final InventoryRepository inventory;
    private final StoreItemRepository storeItems;
    private final LeaderboardService leaderboard;
    private final MongoTemplate mongo;

    public UserService(UserRepository users, InventoryRepository inventory, StoreItemRepository storeItems,
                       LeaderboardService leaderboard, MongoTemplate mongo) {
        this.mongo = mongo;
        this.users = users;
        this.inventory = inventory;
        this.storeItems = storeItems;
        this.leaderboard = leaderboard;
    }

    public User require(String id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
    }

    public UserDto me(String id) {
        return UserDto.from(require(id));
    }

    /** Targeted $set update: never rewrites stats / activeRoomId, which other threads change concurrently. */
    public UserDto update(String id, UpdateProfile req) {
        require(id);
        Update upd = new Update().set("updatedAt", Instant.now());
        if (req.name() != null && !req.name().isBlank()) upd.set("name", req.name().trim());
        if (req.avatar() != null) {
            if (!FREE_AVATAR.matcher(req.avatar()).matches() && !ownsAvatar(id, req.avatar())) {
                throw ApiException.forbidden("AVATAR_LOCKED", "You do not own that avatar");
            }
            upd.set("avatar", req.avatar());
        }
        if (req.sound() != null) upd.set("preferences.sound", req.sound());
        if (req.animations() != null) upd.set("preferences.animations", req.animations());
        mongo.updateFirst(Query.query(Criteria.where("id").is(id)), upd, User.class);
        User u = require(id);
        leaderboard.syncProfile(u.id, u.name, u.avatar);
        return UserDto.from(u);
    }

    private boolean ownsAvatar(String userId, String assetKey) {
        for (InventoryItem inv : inventory.findByUserId(userId)) {
            StoreItem item = storeItems.findById(inv.itemId).orElse(null);
            if (item != null && assetKey.equals(item.assetKey)) return true;
        }
        return false;
    }

    public PublicProfileDto profile(String id) {
        User u = require(id);
        long unlocked = achievements(u).stream().filter(AchievementDto::unlocked).count();
        return new PublicProfileDto(u.id, u.name, u.avatar, StatsDto.from(u.stats), CosmeticsDto.from(u.cosmetics),
                u.createdAt, (int) unlocked);
    }

    public List<AchievementDto> achievements(String userId) {
        return achievements(require(userId));
    }

    List<AchievementDto> achievements(User u) {
        long owned = inventory.countByUserId(u.id);
        List<AchievementDto> list = new ArrayList<>();
        list.add(a("FIRST_MATCH", "Rookie", "Finish your first match", u.stats.totalMatches, 1));
        list.add(a("FIRST_WIN", "First Victory", "Win a match", u.stats.wins, 1));
        list.add(a("WINS_10", "Rising Star", "Win 10 matches", u.stats.wins, 10));
        list.add(a("WINS_50", "Arena Champion", "Win 50 matches", u.stats.wins, 50));
        list.add(a("MATCHES_25", "Regular", "Play 25 matches", u.stats.totalMatches, 25));
        list.add(a("CAPTURES_25", "Token Hunter", "Capture 25 opponent tokens", u.stats.captures, 25));
        list.add(a("XP_1000", "Veteran", "Earn 1,000 XP", u.stats.xp, 1000));
        list.add(a("COLLECTOR", "Collector", "Own 3 cosmetic items", owned, 3));
        return list;
    }

    private static AchievementDto a(String key, String title, String desc, long progress, long target) {
        return new AchievementDto(key, title, desc, progress >= target, Math.min(progress, target), target);
    }
}
