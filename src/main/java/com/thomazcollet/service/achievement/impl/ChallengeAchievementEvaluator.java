package com.thomazcollet.service.achievement.impl;

import com.thomazcollet.domain.model.AchievementCategory;
import com.thomazcollet.domain.repository.ChallengeRepository;
import com.thomazcollet.service.achievement.AchievementEvaluator;

public class ChallengeAchievementEvaluator implements AchievementEvaluator {

    private final ChallengeRepository challengeRepository;

    public ChallengeAchievementEvaluator(ChallengeRepository challengeRepository) {
        this.challengeRepository = challengeRepository;
    }

    @Override
    public boolean evaluate(Long profileId, String achievementKey, int conditionValue) {

        if (!achievementKey.startsWith("challenge_")) {
            return false;
        }

        // =========================================================================
        // REGRA 1: Desafios de Constância Puros (Duração em dias)
        // =========================================================================
        if (achievementKey.startsWith("challenge_constancy_days_")) {
            int completedCount = challengeRepository.countCompletedChallengesByTypeAndMinDuration(
                    profileId, "STREAK_CHALLENGE", conditionValue);
            return completedCount > 0;
        }

        // =========================================================================
        // REGRA 2: Desafios Perfeitos (Sem gastar vidas)
        // =========================================================================
        if (achievementKey.startsWith("challenge_perfect_days_")) {
            int perfectCount = challengeRepository.countPerfectCompletedChallenges(profileId, conditionValue);
            return perfectCount > 0;
        }

        // =========================================================================
        // REGRA 3: Platina Tripla (3 desafios de 365 dias)
        // =========================================================================
        if (achievementKey.equals("challenge_intensity_hours_365_triple")) {
            int totalTriple = challengeRepository.countCompletedChallengesByTypeAndMinDuration(
                    profileId, "MILESTONE_CHALLENGE", 365);
            return totalTriple >= 3;
        }

        // =========================================================================
        // REGRA 4: Desafios de Intensidade com Metas de Horas
        // =========================================================================
        if (achievementKey.startsWith("challenge_intensity_hours_")) {

            // Linha 9 da planilha: durações puras (90, 180, 365 dias) — sem meta de horas
            // Identificados pelas keys que NÃO são parseáveis como integer simples,
            // ou que possuem conditionValue >= 90 (dias de ciclo)
            if (achievementKey.equals("challenge_intensity_hours_66")) {
                return challengeRepository.countCompletedChallengesByTypeAndMinDuration(
                        profileId, "MILESTONE_CHALLENGE", 90) > 0;
            }
            if (achievementKey.equals("challenge_intensity_hours_132")) {
                return challengeRepository.countCompletedChallengesByTypeAndMinDuration(
                        profileId, "MILESTONE_CHALLENGE", 180) > 0;
            }
            if (achievementKey.equals("challenge_intensity_hours_200")) {
                return challengeRepository.countCompletedChallengesByTypeAndMinDuration(
                        profileId, "MILESTONE_CHALLENGE", 365) > 0;
            }

            // Blocos de 15, 30 e 90 dias com metas de horas — conditionValue = dias do
            // ciclo
            String suffix = achievementKey.substring("challenge_intensity_hours_".length());
            try {
                int targetHours = Integer.parseInt(suffix);
                return challengeRepository.hasCompletedIntensityChallenge(profileId, conditionValue, targetHours);
            } catch (NumberFormatException e) {
                return false;
            }
        }

        // =========================================================================
        // REGRA 5: Totalizadores Cumulativos de Constância
        // BUG CORRIGIDO: min_15 e min_30 não tinham regra e sempre retornavam false.
        // Solução: extraímos o limiar mínimo de dias diretamente da chave,
        // eliminando a necessidade de duplicar o bloco para cada variante.
        // =========================================================================
        if (achievementKey.startsWith("challenge_count_constancy_min_")) {
            // Extrai o número de dias mínimos da chave.
            // Ex: "challenge_count_constancy_min_15_6" → minDays = 15
            String afterMin = achievementKey.substring("challenge_count_constancy_min_".length());
            // afterMin = "15_6" → pega só o primeiro segmento antes do "_"
            String[] parts = afterMin.split("_");
            try {
                int minDays = Integer.parseInt(parts[0]);
                int totalConstancy = challengeRepository.countCompletedChallengesByTypeAndMinDuration(
                        profileId, "STREAK_CHALLENGE", minDays);
                return totalConstancy >= conditionValue;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        // =========================================================================
        // REGRA 6: Totalizadores Cumulativos de Intensidade
        // =========================================================================
        if (achievementKey.startsWith("challenge_count_intensity_min_15_")) {
            int totalIntensity = challengeRepository.countCompletedChallengesByTypeAndMinDuration(
                    profileId, "MILESTONE_CHALLENGE", 15);
            return totalIntensity >= conditionValue;
        }

        return false;
    }

    @Override
    public AchievementCategory getCategory() {
        return AchievementCategory.CHALLENGE;
    }
}