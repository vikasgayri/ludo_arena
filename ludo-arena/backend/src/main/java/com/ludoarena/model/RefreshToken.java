package com.ludoarena.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("refreshTokens")
public class RefreshToken {
    @Id
    public String id;
    @Indexed
    public String userId;
    /** SHA-256 hex of the opaque token - the token itself is never stored */
    @Indexed(unique = true)
    public String tokenHash;
    /** all tokens descending from one login share a family; reuse of a revoked token revokes the family */
    @Indexed
    public String familyId;
    public boolean revoked;
    /** TTL index: Mongo deletes the document after this instant */
    @Indexed(expireAfter = "0s")
    public Instant expiresAt;
    public Instant createdAt;
}
