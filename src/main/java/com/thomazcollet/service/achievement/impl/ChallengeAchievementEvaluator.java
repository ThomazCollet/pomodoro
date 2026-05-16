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

        // Fail-Fast estrutural
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
        // REGRA 3: Linha 9 - Caso Especial da Platina Tripla (3 desafios de 365 dias)
        // =========================================================================
        if (achievementKey.equals("challenge_intensity_hours_365_triple")) {
            int totalTriple = challengeRepository.countCompletedChallengesByTypeAndMinDuration(
                    profileId, "MILESTONE_CHALLENGE", 365);
            return totalTriple >= 3;
        }

        // =========================================================================
        // REGRA 4: Desafios de Intensidade com Metas de Horas (Dinâmico)
        // Mapeia os blocos de 15, 30 e os targets puros da Linha 9 (90, 180, 365 dias)
        // =========================================================================
        if (achievementKey.startsWith("challenge_intensity_hours_")) {

            // Tratamento específico para as durações puras da Linha 9 (Bronze, Prata, Ouro)
            if (achievementKey.equals("challenge_intensity_hours_66")) {
                // Completar desafio de intensidade de 90 dias ou mais (Sem meta de horas
                // obrigatória)
                int count = challengeRepository.countCompletedChallengesByTypeAndMinDuration(profileId,
                        "MILESTONE_CHALLENGE", 90);
                return count > 0;
            }
            if (achievementKey.equals("challenge_intensity_hours_132")) {
                // Completar desafio de intensidade de 180 dias ou mais
                int count = challengeRepository.countCompletedChallengesByTypeAndMinDuration(profileId,
                        "MILESTONE_CHALLENGE", 180);
                return count > 0;
            }
            if (achievementKey.equals("challenge_intensity_hours_200")) {
                // Completar desafio de intensidade de 365 dias ou mais
                int count = challengeRepository.countCompletedChallengesByTypeAndMinDuration(profileId,
                        "MILESTONE_CHALLENGE", 365);
                return count > 0;
            }

            // Fallback dinâmico para os blocos de 15 e 30 dias que dependem de horas
            // Captura o valor numérico do final da chave (Ex: challenge_intensity_hours_10
            // -> 10)
            String parts = achievementKey.substring("challenge_intensity_hours_".length());
            try {
                int targetHours = Integer.parseInt(parts);
                // conditionValue extraído do Service define se a régua é de 15 ou 30 dias
                return challengeRepository.hasCompletedIntensityChallenge(profileId, conditionValue, targetHours);
            } catch (NumberFormatException e) {
                return false;
            }
        }

        // =========================================================================
        // REGRA 5: Totalizadores Cumulativos (Quantidade total de desafios concluídos)
        // =========================================================================

        // Acumulador de desafios de Constância
        if (achievementKey.startsWith("challenge_count_constancy_min_7_")) {
            int totalConstancy = challengeRepository.countCompletedChallengesByTypeAndMinDuration(
                    profileId, "STREAK_CHALLENGE", 7);
            return totalConstancy >= conditionValue;
        }

        // Acumulador de desafios de Intensidade
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