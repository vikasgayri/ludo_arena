package com.ludoarena.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Single document (id = "global") holding admin-editable platform configuration. */
@Document("platformSettings")
public class PlatformSettings {
    public static final String ID = "global";

    @Id
    public String id = ID;
    public boolean registrationOpen = true;
    public boolean maintenanceMode = false;
    public long signupBonusCoins = 500;
    public long dailyBonusCoins = 100;
    public long matchWinRewardCoins = 50;
    public long matchParticipationRewardCoins = 10;
    public boolean extraTurnOnSix = true;
    public boolean extraTurnOnCapture = true;
    public boolean extraTurnOnHome = true;
    public int maxConsecutiveSixes = 3;
    public int turnTimeoutSeconds = 30;
    public Instant updatedAt;
    public String updatedBy;
}
