package com.ludoarena.model;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("matchEvents")
@CompoundIndex(name = "match_seq", def = "{'matchId': 1, 'seq': 1}", unique = true)
public class MatchEvent {
    @Id
    public String id;
    public String matchId;
    public long seq;
    @Indexed(unique = true)
    public String eventId;
    public String type;
    public String userId;
    public Map<String, Object> data = new LinkedHashMap<>();
    public long at;
}
