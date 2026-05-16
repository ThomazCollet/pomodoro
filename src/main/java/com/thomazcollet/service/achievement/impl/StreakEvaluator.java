package com.thomazcollet.service.achievement.impl;

import com.thomazcollet.domain.model.AchievementCategory;
import com.thomazcollet.domain.repository.FocusSessionRepository;
import com.thomazcollet.service.achievement.AchievementEvaluator;

public class StreakEvaluator implements AchievementEvaluator {

    private final FocusSessionRepository focusSessionRepository;

    public StreakEvaluator(FocusSessionRepository focusSessionRepository) {
        this.focusSessionRepository = focusSessionRepository;
    }

    @Override
    public boolean evaluate(Long profileId, String achievementKey, int conditionValue) {

        // Linha 4: Atingir um total de X streaks (ex: streak_current_5)
        if (achievementKey.startsWith("streak_current_")) {
            int currentStreak = focusSessionRepository.findCurrentStreakDaysByProfileId(profileId);
            return currentStreak >= conditionValue;
        }

        // Linha 5: Atinja um total de X streaks 3 vezes (ex: streak_count_5_x3)
        if (achievementKey.startsWith("streak_count_") && achievementKey.endsWith("_x3")) {
            int timesAchieved = focusSessionRepository.countTimesStreakTargetWasReached(profileId, conditionValue);
            return timesAchieved >= 3;
        }

        // Linha 6: Atinja um total de X streaks 5 vezes (ex: streak_count_5_x5)
        if (achievementKey.startsWith("streak_count_") && achievementKey.endsWith("_x5")) {
            int timesAchieved = focusSessionRepository.countTimesStreakTargetWasReached(profileId, conditionValue);
            return timesAchieved >= 5;
        }

        // Retorno defensivo (Fail-Fast)
        return false;
    }

    @Override
    public AchievementCategory getCategory() {
        return AchievementCategory.STREAK;
    }
}