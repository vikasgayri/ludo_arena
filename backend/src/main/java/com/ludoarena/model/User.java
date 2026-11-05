package com.ludoarena.model;

import com.ludoarena.model.Enums.AccountStatus;
import com.ludoarena.model.Enums.Role;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("users")
@CompoundIndex(name = "status_createdAt", def = "{'status': 1, 'createdAt': -1}")
public class User {
    @Id
    public String id;
    public String name;
    /** always stored lower-case */
    @Indexed(unique = true)
    public String email;
    public String passwordHash;
    public Role role = Role.USER;
    public AccountStatus status = AccountStatus.ACTIVE;
    public String suspensionReason;
    public String avatar = "avatar-01";
    public Stats stats = new Stats();
    public Cosmetics cosmetics = new Cosmetics();
    public Preferences preferences = new Preferences();
    /** Atomic "one active room per user" lock. Set when joining/creating a room, cleared on leave/finish. */
    public String activeRoomId;
    public Instant createdAt;
    public Instant updatedAt;
    public Instant lastLoginAt;

    public static class Stats {
        public int totalMatches;
        public int wins;
        public int losses;
        public long xp;
        public int captures;
        /** practice matches against the computer: counted separately, never on the leaderboard */
        public int botGames;
        public int botWins;
    }

    /** Equipped cosmetic item SKUs (empty = default). */
    public static class Cosmetics {
        public String boardSkin;
        public String diceSkin;
        public String profileTheme;
    }

    public static class Preferences {
        public boolean sound = true;
        public boolean animations = true;
    }
}
