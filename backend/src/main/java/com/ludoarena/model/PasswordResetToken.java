package com.ludoarena.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("passwordResetTokens")
public class PasswordResetToken {
    @Id
    public String id;
    @Indexed
    public String userId;
    @Indexed(unique = true)
    public String tokenHash;
    public boolean used;
    @Indexed(expireAfter = "0s")
    public Instant expiresAt;
    public Instant createdAt;
}
