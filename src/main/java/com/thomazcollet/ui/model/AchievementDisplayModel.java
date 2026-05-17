package com.thomazcollet.ui.model;

import com.thomazcollet.domain.model.AchievementTier;

public class AchievementDisplayModel {
    private final String key;
    private final String description;
    private final AchievementTier tier;
    private final double progress; // Valor de 0.0 a 1.0 para a ProgressBar
    private final String progressText; // Texto ex: "2/5" ou "120/240"
    private final CardState state; // LOCKED, ACTIVE, COMPLETED

    public enum CardState {
        LOCKED, ACTIVE, COMPLETED
    }

    public AchievementDisplayModel(String key, String description, AchievementTier tier,
            double progress, String progressText, CardState state) {
        this.key = key;
        this.description = description;
        this.tier = tier;
        this.progress = progress;
        this.progressText = progressText;
        this.state = state;
    }

    // Getters simples...
    public String getKey() {
        return key;
    }

    public String getDescription() {
        return description;
    }

    public AchievementTier getTier() {
        return tier;
    }

    public double getProgress() {
        return progress;
    }

    public String getProgressText() {
        return progressText;
    }

    public CardState getState() {
        return state;
    }
}