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
        List<Achievement> userAchievements = achievementRepository.findByProfileId(profileId);

        // Exclui as próprias meta-conquistas da contagem para evitar auto-referência
        List<Achievement> coreAchievements = userAchievements.stream()
                .filter(a -> !a.getAchievementKey().startsWith("meta_"))
                .toList();

        // =========================================================================
        // Planilha linha 20: Trilha de Conquistas de Ouro
        // BUG CORRIGIDO: as keys antigas (meta_first_gold, meta_first_platinum) foram
        // renomeadas para meta_gold_count_X no AchievementService para refletir a
        // planilha com precisão. O evaluator agora usa as novas keys.
        //
        // meta_gold_count_1 (Bronze) → 1 conquista de ouro
        // meta_gold_count_5 (Prata) → 5 conquistas de ouro
        // meta_gold_count_10 (Ouro) → 10 conquistas de ouro
        // meta_all_gold (Platina) → todas as conquistas de ouro do sistema
        // =========================================================================
        if (achievementKey.startsWith("meta_gold_count_")) {
            long currentGoldCount = coreAchievements.stream()
                    .filter(a -> "GOLD".equalsIgnoreCase(a.getTier().name()))
                    .count();
            return currentGoldCount >= conditionValue;
        }

        if ("meta_all_gold".equals(achievementKey)) {
            long currentGoldCount = coreAchievements.stream()
                    .filter(a -> "GOLD".equalsIgnoreCase(a.getTier().name()))
                    .count();
            // Total de Ouros no sistema (contados no AchievementService):
            // Focus: daily(1) + cycles(1) + total_days(1) + accumulated(1) = 4
            // Streak: current(1) + count_x3(1) + count_x5(1) = 3
            // Challenge: constancy_days(2 trilhas × 1 ouro) + perfect(1) + count_constancy
            // ×3 + intensity ×4 + count_intensity(1) = muitos
            // O total exato deve ser mantido em sincronia com getDefinitionsFromPlanilha().
            // Para não hardcodar, calculamos dinamicamente via repositório:
            long totalGoldInSystem = coreAchievements.stream()
                    .filter(a -> "GOLD".equalsIgnoreCase(a.getTier().name()))
                    .count();
            // Verifica se TODAS as definições de Ouro do sistema foram desbloqueadas.
            // Como não temos acesso direto às definições aqui, usamos o conditionValue=0
            // como sinal de "verificar via repositório separado" — o ideal futuro é
            // injetar o AchievementService aqui. Por ora, delegamos ao repositório:
            int totalGoldDefined = achievementRepository.countTotalDefinedByTier("GOLD");
            return currentGoldCount >= totalGoldDefined;
        }

        // =========================================================================
        // Planilha linha 21: Total geral de conquistas
        // meta_total_5 (Bronze) → 5 conquistas quaisquer
        // meta_total_15 (Prata) → 15 conquistas quaisquer
        // meta_total_30 (Ouro) → 30 conquistas quaisquer
        // =========================================================================
        if (achievementKey.startsWith("meta_total_") && !achievementKey.startsWith("meta_total_platinum_")) {
            return coreAchievements.size() >= conditionValue;
        }

        // =========================================================================
        // Planilha linha 22: Trilha de Platinas obtidas
        // meta_total_platinum_1 (Bronze) → 1 platina
        // meta_total_platinum_3 (Prata) → 3 platinas
        // meta_total_platinum_7 (Ouro) → 7 platinas
        // meta_total_platinum_12 (Platina) → 12 platinas
        // =========================================================================
        if (achievementKey.startsWith("meta_total_platinum_")) {
            long platinumCount = coreAchievements.stream()
                    .filter(a -> "PLATINUM".equalsIgnoreCase(a.getTier().name()))
                    .count();
            return platinumCount >= conditionValue;
        }

        return false;
    }

    @Override
    public AchievementCategory getCategory() {
        return AchievementCategory.ACHIEVEMENTS;
    }
}