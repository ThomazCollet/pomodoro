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
import java.util.stream.Collectors;

public class AchievementService {

        private static final Logger logger = LoggerFactory.getLogger(AchievementService.class);

        private final AchievementRepository achievementRepository;
        private final ProfileRepository profileRepository;
        private final Map<AchievementCategory, AchievementEvaluator> evaluators = new EnumMap<>(
                        AchievementCategory.class);

        // Cache estático e imutável das definições da planilha para evitar alocações
        // redundantes de memória
        private static final List<AchievementDefinition> PLANILHA_DEFINITIONS = createPlanilhaDefinitions();

        public record AchievementDefinition(String key, AchievementCategory category, AchievementTier tier,
                        int conditionValue) {
        }

        public AchievementService(AchievementRepository achievementRepository,
                        ProfileRepository profileRepository,
                        List<AchievementEvaluator> evaluatorList) {
                // Fix Fail-Fast com mensagens esperadas pelas asserções dos testes unitários
                this.achievementRepository = Objects.requireNonNull(achievementRepository,
                                "AchievementRepository não pode ser nulo");
                this.profileRepository = Objects.requireNonNull(profileRepository,
                                "ProfileRepository não pode ser nulo");

                if (evaluatorList == null || evaluatorList.isEmpty()) {
                        logger.warn("AchievementService inicializado sem nenhum AchievementEvaluator mapeado!");
                        return;
                }

                evaluatorList.stream()
                                .filter(Objects::nonNull)
                                .forEach(evaluator -> this.evaluators.put(evaluator.getCategory(), evaluator));
        }

        public List<AchievementDefinition> getDefinitions() {
                return PLANILHA_DEFINITIONS;
        }

        public void checkAndUnlockNewAchievements(Long profileId) {
                logger.info("Iniciando verificação de conquistas para o perfil: {}", profileId);

                // Separa as conquistas normais das meta-conquistas usando particionamento por
                // predicado
                Map<Boolean, List<AchievementDefinition>> partitioned = PLANILHA_DEFINITIONS.stream()
                                .collect(Collectors.partitioningBy(
                                                def -> def.category() == AchievementCategory.ACHIEVEMENTS));

                List<AchievementDefinition> normalAchievements = partitioned.get(false);
                List<AchievementDefinition> metaAchievements = partitioned.get(true);

                // Executa a avaliação garantindo que as Meta-Conquistas rodem por último
                // (Permite efeito Cascata)
                normalAchievements.forEach(def -> processEvaluation(profileId, def));
                metaAchievements.forEach(def -> processEvaluation(profileId, def));
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

                        concedeXpReward(profileId, def.tier());
                }
        }

        private void concedeXpReward(Long profileId, AchievementTier tier) {
                int xpReward = switch (tier) {
                        case BRONZE -> 100;
                        case SILVER -> 300;
                        case GOLD -> 800;
                        case PLATINUM -> 2500;
                };

                profileRepository.findById(profileId).ifPresent(profile -> {
                        profile.addXp(xpReward);
                        profileRepository.updateXp(profile.getId(), profile.getXp());
                        logger.info("✨ {} XP concedido ao perfil ID {} por desbloquear a conquista!", xpReward,
                                        profileId);
                });
        }

        private static List<AchievementDefinition> createPlanilhaDefinitions() {
                return List.of(
                                // =========================================================================
                                // CATEGORIA: DAILY_FOCUS
                                // =========================================================================
                                new AchievementDefinition("focus_daily_2h_hours", AchievementCategory.DAILY_FOCUS,
                                                AchievementTier.BRONZE, 120),
                                new AchievementDefinition("focus_daily_3h_hours", AchievementCategory.DAILY_FOCUS,
                                                AchievementTier.SILVER, 180),
                                new AchievementDefinition("focus_daily_4h_hours", AchievementCategory.DAILY_FOCUS,
                                                AchievementTier.GOLD, 240),
                                new AchievementDefinition("focus_daily_6h_hours", AchievementCategory.DAILY_FOCUS,
                                                AchievementTier.PLATINUM, 360),

                                new AchievementDefinition("focus_cycles_1_bronze", AchievementCategory.DAILY_FOCUS,
                                                AchievementTier.BRONZE, 1),
                                new AchievementDefinition("focus_cycles_10_silver", AchievementCategory.DAILY_FOCUS,
                                                AchievementTier.SILVER, 10),
                                new AchievementDefinition("focus_cycles_25_gold", AchievementCategory.DAILY_FOCUS,
                                                AchievementTier.GOLD, 25),
                                new AchievementDefinition("focus_cycles_100_platinum", AchievementCategory.DAILY_FOCUS,
                                                AchievementTier.PLATINUM, 100),

                                new AchievementDefinition("focus_total_days_15_bronze", AchievementCategory.DAILY_FOCUS,
                                                AchievementTier.BRONZE, 15),
                                new AchievementDefinition("focus_total_days_30_silver", AchievementCategory.DAILY_FOCUS,
                                                AchievementTier.SILVER, 30),
                                new AchievementDefinition("focus_total_days_90_gold", AchievementCategory.DAILY_FOCUS,
                                                AchievementTier.GOLD, 90),
                                new AchievementDefinition("focus_total_days_365_platinum",
                                                AchievementCategory.DAILY_FOCUS, AchievementTier.PLATINUM, 365),

                                new AchievementDefinition("focus_accumulated_12h_hours",
                                                AchievementCategory.DAILY_FOCUS, AchievementTier.BRONZE, 720),
                                new AchievementDefinition("focus_accumulated_24h_hours",
                                                AchievementCategory.DAILY_FOCUS, AchievementTier.SILVER, 1440),
                                new AchievementDefinition("focus_accumulated_300h_hours",
                                                AchievementCategory.DAILY_FOCUS, AchievementTier.GOLD, 18000),
                                new AchievementDefinition("focus_accumulated_1000h_hours",
                                                AchievementCategory.DAILY_FOCUS, AchievementTier.PLATINUM, 60000),

                                // =========================================================================
                                // CATEGORIA: STREAK
                                // =========================================================================
                                new AchievementDefinition("streak_current_5", AchievementCategory.STREAK,
                                                AchievementTier.BRONZE, 5),
                                new AchievementDefinition("streak_current_12", AchievementCategory.STREAK,
                                                AchievementTier.SILVER, 12),
                                new AchievementDefinition("streak_current_15", AchievementCategory.STREAK,
                                                AchievementTier.GOLD, 15),
                                new AchievementDefinition("streak_current_30", AchievementCategory.STREAK,
                                                AchievementTier.PLATINUM, 30),

                                new AchievementDefinition("streak_count_5_x3", AchievementCategory.STREAK,
                                                AchievementTier.BRONZE, 5),
                                new AchievementDefinition("streak_count_12_x3", AchievementCategory.STREAK,
                                                AchievementTier.SILVER, 12),
                                new AchievementDefinition("streak_count_15_x3", AchievementCategory.STREAK,
                                                AchievementTier.GOLD, 15),
                                new AchievementDefinition("streak_count_30_x3", AchievementCategory.STREAK,
                                                AchievementTier.PLATINUM, 30),

                                new AchievementDefinition("streak_count_5_x5", AchievementCategory.STREAK,
                                                AchievementTier.BRONZE, 5),
                                new AchievementDefinition("streak_count_12_x5", AchievementCategory.STREAK,
                                                AchievementTier.SILVER, 12),
                                new AchievementDefinition("streak_count_15_x5", AchievementCategory.STREAK,
                                                AchievementTier.GOLD, 15),
                                new AchievementDefinition("streak_count_30_x5", AchievementCategory.STREAK,
                                                AchievementTier.PLATINUM, 30),

                                // =========================================================================
                                // CATEGORIA: CHALLENGE
                                // =========================================================================
                                new AchievementDefinition("challenge_constancy_days_7", AchievementCategory.CHALLENGE,
                                                AchievementTier.BRONZE, 7),
                                new AchievementDefinition("challenge_constancy_days_15", AchievementCategory.CHALLENGE,
                                                AchievementTier.SILVER, 15),
                                new AchievementDefinition("challenge_constancy_days_30", AchievementCategory.CHALLENGE,
                                                AchievementTier.GOLD, 30),
                                new AchievementDefinition("challenge_constancy_days_90", AchievementCategory.CHALLENGE,
                                                AchievementTier.BRONZE, 90),
                                new AchievementDefinition("challenge_constancy_days_180", AchievementCategory.CHALLENGE,
                                                AchievementTier.SILVER, 180),
                                new AchievementDefinition("challenge_constancy_days_365", AchievementCategory.CHALLENGE,
                                                AchievementTier.GOLD, 365),

                                new AchievementDefinition("challenge_perfect_days_5", AchievementCategory.CHALLENGE,
                                                AchievementTier.BRONZE, 5),
                                new AchievementDefinition("challenge_perfect_days_7", AchievementCategory.CHALLENGE,
                                                AchievementTier.SILVER, 7),
                                new AchievementDefinition("challenge_perfect_days_15", AchievementCategory.CHALLENGE,
                                                AchievementTier.GOLD, 15),
                                new AchievementDefinition("challenge_perfect_days_30", AchievementCategory.CHALLENGE,
                                                AchievementTier.PLATINUM, 30),

                                new AchievementDefinition("challenge_count_constancy_min_7_3",
                                                AchievementCategory.CHALLENGE, AchievementTier.BRONZE, 3),
                                new AchievementDefinition("challenge_count_constancy_min_7_6",
                                                AchievementCategory.CHALLENGE, AchievementTier.SILVER, 6),
                                new AchievementDefinition("challenge_count_constancy_min_7_10",
                                                AchievementCategory.CHALLENGE, AchievementTier.GOLD, 10),
                                new AchievementDefinition("challenge_count_constancy_min_7_24",
                                                AchievementCategory.CHALLENGE, AchievementTier.PLATINUM, 24),

                                new AchievementDefinition("challenge_count_constancy_min_15_3",
                                                AchievementCategory.CHALLENGE, AchievementTier.BRONZE, 3),
                                new AchievementDefinition("challenge_count_constancy_min_15_6",
                                                AchievementCategory.CHALLENGE, AchievementTier.SILVER, 6),
                                new AchievementDefinition("challenge_count_constancy_min_15_10",
                                                AchievementCategory.CHALLENGE, AchievementTier.GOLD, 10),
                                new AchievementDefinition("challenge_count_constancy_min_15_24",
                                                AchievementCategory.CHALLENGE, AchievementTier.PLATINUM, 24),

                                new AchievementDefinition("challenge_count_constancy_min_30_3",
                                                AchievementCategory.CHALLENGE, AchievementTier.BRONZE, 3),
                                new AchievementDefinition("challenge_count_constancy_min_30_6",
                                                AchievementCategory.CHALLENGE, AchievementTier.SILVER, 6),
                                new AchievementDefinition("challenge_count_constancy_min_30_10",
                                                AchievementCategory.CHALLENGE, AchievementTier.GOLD, 10),
                                new AchievementDefinition("challenge_count_constancy_min_30_18",
                                                AchievementCategory.CHALLENGE, AchievementTier.PLATINUM, 18),

                                new AchievementDefinition("challenge_intensity_hours_10", AchievementCategory.CHALLENGE,
                                                AchievementTier.BRONZE, 15),
                                new AchievementDefinition("challenge_intensity_hours_20", AchievementCategory.CHALLENGE,
                                                AchievementTier.SILVER, 15),
                                new AchievementDefinition("challenge_intensity_hours_40", AchievementCategory.CHALLENGE,
                                                AchievementTier.GOLD, 15),
                                new AchievementDefinition("challenge_intensity_hours_60", AchievementCategory.CHALLENGE,
                                                AchievementTier.PLATINUM, 15),

                                new AchievementDefinition("challenge_intensity_hours_22", AchievementCategory.CHALLENGE,
                                                AchievementTier.BRONZE, 30),
                                new AchievementDefinition("challenge_intensity_hours_44", AchievementCategory.CHALLENGE,
                                                AchievementTier.SILVER, 30),
                                new AchievementDefinition("challenge_intensity_hours_90", AchievementCategory.CHALLENGE,
                                                AchievementTier.GOLD, 30),
                                new AchievementDefinition("challenge_intensity_hours_120",
                                                AchievementCategory.CHALLENGE, AchievementTier.PLATINUM, 30),

                                new AchievementDefinition("challenge_intensity_hours_66", AchievementCategory.CHALLENGE,
                                                AchievementTier.BRONZE, 90),
                                new AchievementDefinition("challenge_intensity_hours_132",
                                                AchievementCategory.CHALLENGE, AchievementTier.SILVER, 90),
                                new AchievementDefinition("challenge_intensity_hours_200",
                                                AchievementCategory.CHALLENGE, AchievementTier.GOLD, 90),
                                new AchievementDefinition("challenge_intensity_hours_270",
                                                AchievementCategory.CHALLENGE, AchievementTier.PLATINUM, 90),

                                new AchievementDefinition("challenge_intensity_hours_365_triple",
                                                AchievementCategory.CHALLENGE, AchievementTier.PLATINUM, 365),

                                new AchievementDefinition("challenge_count_intensity_min_15_3",
                                                AchievementCategory.CHALLENGE, AchievementTier.BRONZE, 3),
                                new AchievementDefinition("challenge_count_intensity_min_15_6",
                                                AchievementCategory.CHALLENGE, AchievementTier.SILVER, 6),
                                new AchievementDefinition("challenge_count_intensity_min_15_10",
                                                AchievementCategory.CHALLENGE, AchievementTier.GOLD, 10),
                                new AchievementDefinition("challenge_count_intensity_min_15_30",
                                                AchievementCategory.CHALLENGE, AchievementTier.PLATINUM, 30),

                                // =========================================================================
                                // CATEGORIA: ACHIEVEMENTS (Meta-Conquistas)
                                // =========================================================================
                                new AchievementDefinition("meta_gold_count_1", AchievementCategory.ACHIEVEMENTS,
                                                AchievementTier.BRONZE, 1),
                                new AchievementDefinition("meta_gold_count_5", AchievementCategory.ACHIEVEMENTS,
                                                AchievementTier.SILVER, 5),
                                new AchievementDefinition("meta_gold_count_10", AchievementCategory.ACHIEVEMENTS,
                                                AchievementTier.GOLD, 10),
                                new AchievementDefinition("meta_all_gold", AchievementCategory.ACHIEVEMENTS,
                                                AchievementTier.PLATINUM, 0),

                                new AchievementDefinition("meta_total_5", AchievementCategory.ACHIEVEMENTS,
                                                AchievementTier.BRONZE, 5),
                                new AchievementDefinition("meta_total_15", AchievementCategory.ACHIEVEMENTS,
                                                AchievementTier.SILVER, 15),
                                new AchievementDefinition("meta_total_30", AchievementCategory.ACHIEVEMENTS,
                                                AchievementTier.GOLD, 30),

                                new AchievementDefinition("meta_total_platinum_1", AchievementCategory.ACHIEVEMENTS,
                                                AchievementTier.BRONZE, 1),
                                new AchievementDefinition("meta_total_platinum_3", AchievementCategory.ACHIEVEMENTS,
                                                AchievementTier.SILVER, 3),
                                new AchievementDefinition("meta_total_platinum_7", AchievementCategory.ACHIEVEMENTS,
                                                AchievementTier.GOLD, 7),
                                new AchievementDefinition("meta_total_platinum_12", AchievementCategory.ACHIEVEMENTS,
                                                AchievementTier.PLATINUM, 12),

                                // =========================================================================
                                // CATEGORIA: RANKING
                                // =========================================================================
                                new AchievementDefinition("ranking_tier_c", AchievementCategory.RANKING,
                                                AchievementTier.BRONZE, 1),
                                new AchievementDefinition("ranking_tier_a", AchievementCategory.RANKING,
                                                AchievementTier.SILVER, 2),
                                new AchievementDefinition("ranking_tier_s", AchievementCategory.RANKING,
                                                AchievementTier.GOLD, 3),
                                new AchievementDefinition("ranking_tier_ss", AchievementCategory.RANKING,
                                                AchievementTier.PLATINUM, 4));
        }
}