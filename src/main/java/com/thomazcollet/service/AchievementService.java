package com.thomazcollet.service;

import com.thomazcollet.domain.model.Achievement;
import com.thomazcollet.domain.model.AchievementCategory;
import com.thomazcollet.domain.model.AchievementTier;
import com.thomazcollet.domain.repository.AchievementRepository;
import com.thomazcollet.domain.repository.ProfileRepository;
import com.thomazcollet.service.achievement.AchievementEvaluator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ArrayList;

public class AchievementService {

    private static final Logger logger = LoggerFactory.getLogger(AchievementService.class);

    private final AchievementRepository achievementRepository;
    private final ProfileRepository profileRepository;
    private final Map<AchievementCategory, AchievementEvaluator> evaluators = new EnumMap<>(AchievementCategory.class);

    /**
     * Record público que serve como a definição estática de cada conquista mapeada.
     * Utilizado tanto pelo motor de regras quanto pela UI para renderizar os
     * cartões.
     */
    public record AchievementDefinition(String key, AchievementCategory category, AchievementTier tier,
            int conditionValue) {
    }

    public AchievementService(AchievementRepository achievementRepository,
            ProfileRepository profileRepository,
            List<AchievementEvaluator> evaluatorList) {
        this.achievementRepository = Objects.requireNonNull(achievementRepository,
                "AchievementRepository não pode ser nulo");
        this.profileRepository = Objects.requireNonNull(profileRepository,
                "ProfileRepository não pode ser nulo");

        if (evaluatorList == null || evaluatorList.isEmpty()) {
            logger.warn("AchievementService inicializado sem nenhum AchievementEvaluator mapeado!");
            return;
        }

        for (AchievementEvaluator evaluator : evaluatorList) {
            if (evaluator != null) {
                this.evaluators.put(evaluator.getCategory(), evaluator);
            }
        }
    }

    /**
     * Expõe a lista de todas as conquistas mapeadas na planilha para que a UI possa
     * listá-las.
     */
    public List<AchievementDefinition> getDefinitions() {
        return getDefinitionsFromPlanilha();
    }

    public void checkAndUnlockNewAchievements(Long profileId) {
        logger.info("Iniciando verificação de conquistas para o perfil: {}", profileId);

        List<AchievementDefinition> definitions = getDefinitionsFromPlanilha();

        List<AchievementDefinition> metaAchievements = new ArrayList<>();

        // Passo 1: Avalia todas as categorias base
        for (AchievementDefinition def : definitions) {
            if (def.category() == AchievementCategory.ACHIEVEMENTS) {
                metaAchievements.add(def);
                continue;
            }

            processEvaluation(profileId, def);
        }

        // Passo 2: Avalia as Meta-Conquistas por último
        for (AchievementDefinition def : metaAchievements) {
            processEvaluation(profileId, def);
        }
    }

    private void processEvaluation(Long profileId, AchievementDefinition def) {
        if (achievementRepository.isUnlocked(profileId, def.key())) {
            return;
        }

        AchievementEvaluator evaluator = evaluators.get(def.category());
        if (evaluator == null) {
            return;
        }

        if (evaluator.evaluate(profileId, def.key(), def.conditionValue())) {
            Achievement newAchievement = new Achievement();
            newAchievement.setProfileId(profileId);
            newAchievement.setAchievementKey(def.key());
            newAchievement.setCategory(def.category());
            newAchievement.setTier(def.tier());

            achievementRepository.save(newAchievement);
            logger.info("🏆 CONQUISTA DESBLOQUEADA: {} [{}]", def.key(), def.tier());

            int xpReward = switch (def.tier()) {
                case BRONZE -> 100;
                case SILVER -> 300;
                case GOLD -> 800;
                case PLATINUM -> 2500;
            };

            profileRepository.findById(profileId).ifPresent(profile -> {
                profile.addXp(xpReward);
                profileRepository.updateXp(profile.getId(), profile.getXp());
                logger.info("✨ {} XP concedido ao perfil ID {} por desbloquear a conquista!", xpReward, profileId);
            });

            // TODO: Notificar a UI (JavaFX) para disparar o Toast animado na tela
        }
    }

    private List<AchievementDefinition> getDefinitionsFromPlanilha() {
        List<AchievementDefinition> list = new ArrayList<>();

        // =========================================================================
        // CATEGORIA: DAILY_FOCUS (Foco Acumulado)
        // =========================================================================
        list.add(new AchievementDefinition("focus_daily_1h_hours", AchievementCategory.DAILY_FOCUS,
                AchievementTier.BRONZE, 60));
        list.add(new AchievementDefinition("focus_daily_2h_hours", AchievementCategory.DAILY_FOCUS,
                AchievementTier.SILVER, 120));
        list.add(new AchievementDefinition("focus_daily_4h_hours", AchievementCategory.DAILY_FOCUS,
                AchievementTier.GOLD, 240));
        list.add(new AchievementDefinition("focus_daily_6h_hours", AchievementCategory.DAILY_FOCUS,
                AchievementTier.PLATINUM, 360));

        list.add(new AchievementDefinition("focus_cycles_1_bronze", AchievementCategory.DAILY_FOCUS,
                AchievementTier.BRONZE, 1));
        list.add(new AchievementDefinition("focus_cycles_10_silver", AchievementCategory.DAILY_FOCUS,
                AchievementTier.SILVER, 10));
        list.add(new AchievementDefinition("focus_cycles_25_gold", AchievementCategory.DAILY_FOCUS,
                AchievementTier.GOLD, 25));
        list.add(new AchievementDefinition("focus_cycles_100_platinum", AchievementCategory.DAILY_FOCUS,
                AchievementTier.PLATINUM, 100));

        list.add(new AchievementDefinition("focus_total_days_15_bronze", AchievementCategory.DAILY_FOCUS,
                AchievementTier.BRONZE, 15));
        list.add(new AchievementDefinition("focus_total_days_30_silver", AchievementCategory.DAILY_FOCUS,
                AchievementTier.SILVER, 30));
        list.add(new AchievementDefinition("focus_total_days_90_gold", AchievementCategory.DAILY_FOCUS,
                AchievementTier.GOLD, 90));
        list.add(new AchievementDefinition("focus_total_days_365_platinum", AchievementCategory.DAILY_FOCUS,
                AchievementTier.PLATINUM, 365));

        list.add(new AchievementDefinition("focus_accumulated_12h_hours", AchievementCategory.DAILY_FOCUS,
                AchievementTier.BRONZE, 720));
        list.add(new AchievementDefinition("focus_accumulated_24h_hours", AchievementCategory.DAILY_FOCUS,
                AchievementTier.SILVER, 1440));
        list.add(new AchievementDefinition("focus_accumulated_300h_hours", AchievementCategory.DAILY_FOCUS,
                AchievementTier.GOLD, 18000));
        list.add(new AchievementDefinition("focus_accumulated_1000h_hours", AchievementCategory.DAILY_FOCUS,
                AchievementTier.PLATINUM, 60000));

        // =========================================================================
        // CATEGORIA: STREAK (Ofensivas)
        // =========================================================================
        list.add(new AchievementDefinition("streak_current_5", AchievementCategory.STREAK, AchievementTier.BRONZE, 5));
        list.add(
                new AchievementDefinition("streak_current_12", AchievementCategory.STREAK, AchievementTier.SILVER, 12));
        list.add(new AchievementDefinition("streak_current_15", AchievementCategory.STREAK, AchievementTier.GOLD, 15));
        list.add(new AchievementDefinition("streak_current_30", AchievementCategory.STREAK, AchievementTier.PLATINUM,
                30));

        list.add(new AchievementDefinition("streak_count_5_x3", AchievementCategory.STREAK, AchievementTier.BRONZE, 5));
        list.add(new AchievementDefinition("streak_count_12_x3", AchievementCategory.STREAK, AchievementTier.SILVER,
                12));
        list.add(new AchievementDefinition("streak_count_15_x3", AchievementCategory.STREAK, AchievementTier.GOLD, 15));
        list.add(new AchievementDefinition("streak_count_30_x3", AchievementCategory.STREAK, AchievementTier.PLATINUM,
                30));

        list.add(new AchievementDefinition("streak_count_5_x5", AchievementCategory.STREAK, AchievementTier.BRONZE, 5));
        list.add(new AchievementDefinition("streak_count_12_x5", AchievementCategory.STREAK, AchievementTier.SILVER,
                12));
        list.add(new AchievementDefinition("streak_count_15_x5", AchievementCategory.STREAK, AchievementTier.GOLD, 15));
        list.add(new AchievementDefinition("streak_count_30_x5", AchievementCategory.STREAK, AchievementTier.PLATINUM,
                30));

        // =========================================================================
        // CATEGORIA: CHALLENGE (Desafios)
        // =========================================================================
        list.add(new AchievementDefinition("challenge_constancy_days_7", AchievementCategory.CHALLENGE,
                AchievementTier.BRONZE, 7));
        list.add(new AchievementDefinition("challenge_constancy_days_15", AchievementCategory.CHALLENGE,
                AchievementTier.SILVER, 15));
        list.add(new AchievementDefinition("challenge_constancy_days_30", AchievementCategory.CHALLENGE,
                AchievementTier.GOLD, 30));
        list.add(new AchievementDefinition("challenge_constancy_days_90", AchievementCategory.CHALLENGE,
                AchievementTier.BRONZE, 90));
        list.add(new AchievementDefinition("challenge_constancy_days_180", AchievementCategory.CHALLENGE,
                AchievementTier.SILVER, 180));
        list.add(new AchievementDefinition("challenge_constancy_days_365", AchievementCategory.CHALLENGE,
                AchievementTier.GOLD, 365));

        list.add(new AchievementDefinition("challenge_perfect_days_5", AchievementCategory.CHALLENGE,
                AchievementTier.BRONZE, 5));
        list.add(new AchievementDefinition("challenge_perfect_days_7", AchievementCategory.CHALLENGE,
                AchievementTier.SILVER, 7));
        list.add(new AchievementDefinition("challenge_perfect_days_15", AchievementCategory.CHALLENGE,
                AchievementTier.GOLD, 15));
        list.add(new AchievementDefinition("challenge_perfect_days_30", AchievementCategory.CHALLENGE,
                AchievementTier.PLATINUM, 30));

        list.add(new AchievementDefinition("challenge_count_constancy_min_7_3", AchievementCategory.CHALLENGE,
                AchievementTier.BRONZE, 3));
        list.add(new AchievementDefinition("challenge_count_constancy_min_7_6", AchievementCategory.CHALLENGE,
                AchievementTier.SILVER, 6));
        list.add(new AchievementDefinition("challenge_count_constancy_min_7_10", AchievementCategory.CHALLENGE,
                AchievementTier.GOLD, 10));
        list.add(new AchievementDefinition("challenge_count_constancy_min_7_24", AchievementCategory.CHALLENGE,
                AchievementTier.PLATINUM, 24));

        list.add(new AchievementDefinition("challenge_count_constancy_min_15_3", AchievementCategory.CHALLENGE,
                AchievementTier.BRONZE, 3));
        list.add(new AchievementDefinition("challenge_count_constancy_min_15_6", AchievementCategory.CHALLENGE,
                AchievementTier.SILVER, 6));
        list.add(new AchievementDefinition("challenge_count_constancy_min_15_10", AchievementCategory.CHALLENGE,
                AchievementTier.GOLD, 10));
        list.add(new AchievementDefinition("challenge_count_constancy_min_15_24", AchievementCategory.CHALLENGE,
                AchievementTier.PLATINUM, 24));

        list.add(new AchievementDefinition("challenge_count_constancy_min_30_3", AchievementCategory.CHALLENGE,
                AchievementTier.BRONZE, 3));
        list.add(new AchievementDefinition("challenge_count_constancy_min_30_6", AchievementCategory.CHALLENGE,
                AchievementTier.SILVER, 6));
        list.add(new AchievementDefinition("challenge_count_constancy_min_30_10", AchievementCategory.CHALLENGE,
                AchievementTier.GOLD, 10));
        list.add(new AchievementDefinition("challenge_count_constancy_min_30_18", AchievementCategory.CHALLENGE,
                AchievementTier.PLATINUM, 18));

        list.add(new AchievementDefinition("challenge_intensity_hours_10", AchievementCategory.CHALLENGE,
                AchievementTier.BRONZE, 15));
        list.add(new AchievementDefinition("challenge_intensity_hours_20", AchievementCategory.CHALLENGE,
                AchievementTier.SILVER, 15));
        list.add(new AchievementDefinition("challenge_intensity_hours_40", AchievementCategory.CHALLENGE,
                AchievementTier.GOLD, 15));
        list.add(new AchievementDefinition("challenge_intensity_hours_60", AchievementCategory.CHALLENGE,
                AchievementTier.PLATINUM, 15));

        list.add(new AchievementDefinition("challenge_intensity_hours_22", AchievementCategory.CHALLENGE,
                AchievementTier.BRONZE, 30));
        list.add(new AchievementDefinition("challenge_intensity_hours_44", AchievementCategory.CHALLENGE,
                AchievementTier.SILVER, 30));
        list.add(new AchievementDefinition("challenge_intensity_hours_90", AchievementCategory.CHALLENGE,
                AchievementTier.GOLD, 30));
        list.add(new AchievementDefinition("challenge_intensity_hours_120", AchievementCategory.CHALLENGE,
                AchievementTier.PLATINUM, 30));

        list.add(new AchievementDefinition("challenge_intensity_hours_66", AchievementCategory.CHALLENGE,
                AchievementTier.BRONZE, 90));
        list.add(new AchievementDefinition("challenge_intensity_hours_132", AchievementCategory.CHALLENGE,
                AchievementTier.SILVER, 90));
        list.add(new AchievementDefinition("challenge_intensity_hours_200", AchievementCategory.CHALLENGE,
                AchievementTier.GOLD, 90));
        list.add(new AchievementDefinition("challenge_intensity_hours_270", AchievementCategory.CHALLENGE,
                AchievementTier.PLATINUM, 90));

        list.add(new AchievementDefinition("challenge_intensity_hours_365_triple", AchievementCategory.CHALLENGE,
                AchievementTier.PLATINUM, 365));

        list.add(new AchievementDefinition("challenge_count_intensity_min_15_3", AchievementCategory.CHALLENGE,
                AchievementTier.BRONZE, 3));
        list.add(new AchievementDefinition("challenge_count_intensity_min_15_6", AchievementCategory.CHALLENGE,
                AchievementTier.SILVER, 6));
        list.add(new AchievementDefinition("challenge_count_intensity_min_15_10", AchievementCategory.CHALLENGE,
                AchievementTier.GOLD, 10));
        list.add(new AchievementDefinition("challenge_count_intensity_min_15_30", AchievementCategory.CHALLENGE,
                AchievementTier.PLATINUM, 30));

        // =========================================================================
        // CATEGORIA: ACHIEVEMENTS (Meta-Conquistas)
        // =========================================================================
        list.add(new AchievementDefinition("meta_first_gold", AchievementCategory.ACHIEVEMENTS, AchievementTier.BRONZE,
                1));
        list.add(new AchievementDefinition("meta_first_platinum", AchievementCategory.ACHIEVEMENTS,
                AchievementTier.SILVER, 1));
        list.add(
                new AchievementDefinition("meta_total_5", AchievementCategory.ACHIEVEMENTS, AchievementTier.BRONZE, 5));
        list.add(new AchievementDefinition("meta_total_15", AchievementCategory.ACHIEVEMENTS, AchievementTier.SILVER,
                15));
        list.add(
                new AchievementDefinition("meta_total_30", AchievementCategory.ACHIEVEMENTS, AchievementTier.GOLD, 30));
        list.add(new AchievementDefinition("meta_all_gold", AchievementCategory.ACHIEVEMENTS, AchievementTier.PLATINUM,
                7));

        list.add(new AchievementDefinition("meta_total_platinum_1", AchievementCategory.ACHIEVEMENTS,
                AchievementTier.BRONZE, 1));
        list.add(new AchievementDefinition("meta_total_platinum_3", AchievementCategory.ACHIEVEMENTS,
                AchievementTier.SILVER, 3));
        list.add(new AchievementDefinition("meta_total_platinum_7", AchievementCategory.ACHIEVEMENTS,
                AchievementTier.GOLD, 7));
        list.add(new AchievementDefinition("meta_total_platinum_12", AchievementCategory.ACHIEVEMENTS,
                AchievementTier.PLATINUM, 12));

        // =========================================================================
        // CATEGORIA: RANKING (Sistema de Elos)
        // =========================================================================
        list.add(new AchievementDefinition("ranking_tier_c", AchievementCategory.RANKING, AchievementTier.BRONZE, 1));
        list.add(new AchievementDefinition("ranking_tier_a", AchievementCategory.RANKING, AchievementTier.SILVER, 2));
        list.add(new AchievementDefinition("ranking_tier_s", AchievementCategory.RANKING, AchievementTier.GOLD, 3));
        list.add(
                new AchievementDefinition("ranking_tier_ss", AchievementCategory.RANKING, AchievementTier.PLATINUM, 4));

        return list;
    }
}