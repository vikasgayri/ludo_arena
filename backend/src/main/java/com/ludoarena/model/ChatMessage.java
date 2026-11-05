package com.ludoarena.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** In-match chat line. Kept 30 days (TTL) so admins can review reported abuse; never shown outside its match. */
@Document("matchChat")
@CompoundIndex(name = "match_at", def = "{'matchId': 1, 'at': -1}")
public class ChatMessage {
    @Id
    public String id;
    public String matchId;
    public String userId;
    public String name;
    public String color;
    public String text;
    @Indexed(expireAfter = "30d")
    public Instant at;
}
