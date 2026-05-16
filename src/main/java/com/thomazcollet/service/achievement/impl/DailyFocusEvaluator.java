package com.thomazcollet.service.achievement.impl;

import com.thomazcollet.domain.model.AchievementCategory;
import com.thomazcollet.domain.repository.FocusSessionRepository; // 
import com.thomazcollet.service.achievement.AchievementEvaluator;

public class DailyFocusEvaluator implements AchievementEvaluator {

    private final FocusSessionRepository focusSessionRepository;

    public DailyFocusEvaluator(FocusSessionRepository focusSessionRepository) {
        this.focusSessionRepository = focusSessionRepository;
    }

    @Override
    public boolean evaluate(Long profileId, int requiredMinutes) {
        // Exemplo: Consulta o banco para saber o recorde de minutos focados em um único dia
        int maxMinutesInSingleDay = focusSessionRepository.findMaxFocusMinutesInAGivenDay(profileId);
        
        return maxMinutesInSingleDay >= requiredMinutes;
    }

    @Override
    public AchievementCategory getCategory() {
        return AchievementCategory.DAILY_FOCUS;
    }
}