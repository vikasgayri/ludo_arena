package com.ludoarena.service;

import com.ludoarena.config.AppProperties;
import com.ludoarena.dto.Dtos.SettingsDto;
import com.ludoarena.dto.Requests.UpdateSettings;
import com.ludoarena.game.model.GameRules;
import com.ludoarena.model.PlatformSettings;
import com.ludoarena.repository.PlatformSettingsRepository;
import java.time.Instant;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class PlatformSettingsService {
    private final PlatformSettingsRepository repo;
    private final AppProperties props;

    public PlatformSettingsService(PlatformSettingsRepository repo, AppProperties props) {
        this.repo = repo;
        this.props = props;
    }

    public PlatformSettings get() {
        return repo.findById(PlatformSettings.ID).orElseGet(() -> {
            try {
                return repo.save(new PlatformSettings());
            } catch (DuplicateKeyException e) {
                return repo.findById(PlatformSettings.ID).orElseGet(PlatformSettings::new);
            }
        });
    }

    /** Snapshot of the configurable rules used for a new match. */
    public GameRules gameRules() {
        PlatformSettings s = get();
        GameRules r = new GameRules();
        r.extraTurnOnSix = s.extraTurnOnSix;
        r.extraTurnOnCapture = s.extraTurnOnCapture;
        r.extraTurnOnHome = s.extraTurnOnHome;
        r.maxConsecutiveSixes = s.maxConsecutiveSixes;
        r.turnTimeoutSeconds = s.turnTimeoutSeconds;
        return r;
    }

    public PlatformSettings update(UpdateSettings u, String adminId) {
        PlatformSettings s = get();
        if (u.registrationOpen() != null) s.registrationOpen = u.registrationOpen();
        if (u.maintenanceMode() != null) s.maintenanceMode = u.maintenanceMode();
        if (u.signupBonusCoins() != null) s.signupBonusCoins = u.signupBonusCoins();
        if (u.dailyBonusCoins() != null) s.dailyBonusCoins = u.dailyBonusCoins();
        if (u.matchWinRewardCoins() != null) s.matchWinRewardCoins = u.matchWinRewardCoins();
        if (u.matchParticipationRewardCoins() != null) s.matchParticipationRewardCoins = u.matchParticipationRewardCoins();
        if (u.extraTurnOnSix() != null) s.extraTurnOnSix = u.extraTurnOnSix();
        if (u.extraTurnOnCapture() != null) s.extraTurnOnCapture = u.extraTurnOnCapture();
        if (u.extraTurnOnHome() != null) s.extraTurnOnHome = u.extraTurnOnHome();
        if (u.maxConsecutiveSixes() != null) s.maxConsecutiveSixes = u.maxConsecutiveSixes();
        if (u.turnTimeoutSeconds() != null) s.turnTimeoutSeconds = u.turnTimeoutSeconds();
        s.updatedAt = Instant.now();
        s.updatedBy = adminId;
        return repo.save(s);
    }

    public SettingsDto toDto(PlatformSettings s) {
        return new SettingsDto(s.registrationOpen, s.maintenanceMode, s.signupBonusCoins, s.dailyBonusCoins,
                s.matchWinRewardCoins, s.matchParticipationRewardCoins, s.extraTurnOnSix, s.extraTurnOnCapture,
                s.extraTurnOnHome, s.maxConsecutiveSixes, s.turnTimeoutSeconds, props.payments().enabled());
    }
}
