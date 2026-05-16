package com.thomazcollet.service.achievement.impl;

import com.thomazcollet.domain.model.AchievementCategory;
import com.thomazcollet.domain.repository.FocusSessionRepository;
import com.thomazcollet.service.achievement.AchievementEvaluator;

public class DailyFocusEvaluator implements AchievementEvaluator {

    private final FocusSessionRepository focusSessionRepository;

    public DailyFocusEvaluator(FocusSessionRepository focusSessionRepository) {
        this.focusSessionRepository = focusSessionRepository;
    }

    @Override
    public boolean evaluate(Long profileId, String achievementKey, int conditionValue) {
        // Linha Verde 1: Tempo acumulado em um único dia (ex: focus_daily_1h_hours)
        if (achievementKey.startsWith("focus_daily_") && achievementKey.endsWith("_hours")) {
            int maxMinutesInSingleDay = focusSessionRepository.findMaxFocusMinutesInAGivenDay(profileId);
            return maxMinutesInSingleDay >= conditionValue;
        }

        // Linha Verde 2: Ciclos totais de Pomodoro completos (ex:
        // focus_cycles_1_bronze)
        if (achievementKey.startsWith("focus_cycles_")) {
            int totalCycles = focusSessionRepository.countCompletedSessionsByProfileId(profileId);
            return totalCycles >= conditionValue;
        }

        // Linha Verde 3: Dias totais com foco realizado (ex:
        // focus_total_days_15_bronze)
        if (achievementKey.startsWith("focus_total_days_")) {
            int totalDays = focusSessionRepository.countDistinctDaysWithCompletedFocus(profileId);
            return totalDays >= conditionValue;
        }

        // Linha Verde 4: Tempo total histórico acumulado (ex:
        // focus_accumulated_12h_hours)
        if (achievementKey.startsWith("focus_accumulated_") && achievementKey.endsWith("_hours")) {
            int totalMinutesAccumulated = focusSessionRepository.sumTotalFocusMinutesByProfileId(profileId);
            return totalMinutesAccumulated >= conditionValue;
        }

        // Retorno defensivo (Fail-Fast) caso a chave não case com nenhuma regra
        // conhecida
        return false;
    }

    @Override
    public AchievementCategory getCategory() {
        return AchievementCategory.DAILY_FOCUS;
    }
}