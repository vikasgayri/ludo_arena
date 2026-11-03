package com.ludoarena.model;

import com.ludoarena.model.Enums.RoomStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("rooms")
@CompoundIndex(name = "status_private_createdAt", def = "{'status': 1, 'isPrivate': 1, 'createdAt': -1}")
@CompoundIndex(name = "players_userId", def = "{'players.userId': 1}")
public class Room {
    @Id
    public String id;
    /** 6 character join code */
    @Indexed(unique = true)
    public String code;
    public String name;
    public String hostId;
    /** 2 or 4 */
    public int maxPlayers;
    public RoomStatus status = RoomStatus.WAITING;
    public boolean isPrivate;
    public List<RoomPlayer> players = new ArrayList<>();
    public String matchId;
    public Instant createdAt;
    public Instant updatedAt;

    public static class RoomPlayer {
        public String userId;
        public String name;
        public String avatar;
        public boolean ready;
        public Instant joinedAt;
    }

    public boolean hasPlayer(String userId) {
        for (RoomPlayer p : players) {
            if (p.userId.equals(userId)) return true;
        }
        return false;
    }
}
