package com.thomazcollet.service.achievement.impl;

import com.thomazcollet.domain.model.Achievement;
import com.thomazcollet.domain.model.AchievementCategory;
import com.thomazcollet.domain.repository.AchievementRepository;
import com.thomazcollet.service.achievement.AchievementEvaluator;

import java.util.List;

public class AchievementCountEvaluator implements AchievementEvaluator {

    private final AchievementRepository achievementRepository;

    public AchievementCountEvaluator(AchievementRepository achievementRepository) {
        this.achievementRepository = achievementRepository;
    }

    @Override
    public boolean evaluate(Long profileId, String achievementKey, int conditionValue) {
        // Busca todas as conquistas atuais do usuário para avaliar no formato in-memory
        List<Achievement> userAchievements = achievementRepository.findByProfileId(profileId);

        // Filtra para ignorar as próprias meta-conquistas da contagem, focando apenas
        // em conquistas de atividade real
        List<Achievement> coreAchievements = userAchievements.stream()
                .filter(a -> !a.getAchievementKey().startsWith("meta_"))
                .toList();

        // Linha 21: Obter a primeira conquista Ouro ou Platina (Ex: meta_first_gold,
        // meta_first_platinum)
        if (achievementKey.startsWith("meta_first_")) {
            String targetTier = achievementKey.contains("gold") ? "GOLD" : "PLATINUM";

            long countTier = coreAchievements.stream()
                    .filter(a -> targetTier.equalsIgnoreCase(a.getTier().name()))
                    .count();

            return countTier >= 1;
        }

        // Linha 22: Obter um total de X conquistas (Ex: meta_total_5, meta_total_15,
        // meta_total_30)
        if (achievementKey.startsWith("meta_total_")) {
            return coreAchievements.size() >= conditionValue;
        }

        // Linha 23: Obter todas as conquistas de Ouro (Ex: meta_all_gold)
        if ("meta_all_gold".equals(achievementKey)) {
            long currentGoldCount = coreAchievements.stream()
                    .filter(a -> "GOLD".equalsIgnoreCase(a.getTier().name()))
                    .count();

            // Total de conquistas de Ouro planejadas na planilha até o momento:
            // 1 de Daily Focus (4h), 1 de Ciclos (25), 1 de Dias Totais (90), 1 de Foco
            // Histórico (300h),
            // 1 de Streak Corrente (15 dias), 1 de Streak x3 (15), 1 de Streak x5 (15).
            // Total = 7.
            int totalGoldPlannedInSystem = 7;

            return currentGoldCount >= totalGoldPlannedInSystem;
        }

        // Fail-Fast defensivo
        return false;
    }

    @Override
    public AchievementCategory getCategory() {
        return AchievementCategory.ACHIEVEMENTS;
    }
}