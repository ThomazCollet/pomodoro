package com.thomazcollet.service;

import com.thomazcollet.domain.model.Achievement;
import com.thomazcollet.domain.model.AchievementCategory;
import com.thomazcollet.domain.model.AchievementTier;
import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.model.RankingType;
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
        private final NotificationService notificationService; // nullable — retrocompatível
        private final Map<AchievementCategory, AchievementEvaluator> evaluators = new EnumMap<>(
                        AchievementCategory.class);

        private static final List<AchievementDefinition> PLANILHA_DEFINITIONS = createPlanilhaDefinitions();

        public record AchievementDefinition(String key, AchievementCategory category, AchievementTier tier,
                        int conditionValue) {
        }

        // -----------------------------------------------------------------------
        // CONSTRUTORES — o sem NotificationService mantém compatibilidade total
        // com os testes existentes.
        // -----------------------------------------------------------------------

        public AchievementService(AchievementRepository achievementRepository,
                        ProfileRepository profileRepository,
                        List<AchievementEvaluator> evaluatorList) {
                this(achievementRepository, profileRepository, evaluatorList, null);
        }

        public AchievementService(AchievementRepository achievementRepository,
                        ProfileRepository profileRepository,
                        List<AchievementEvaluator> evaluatorList,
                        NotificationService notificationService) {

                this.achievementRepository = Objects.requireNonNull(achievementRepository,
                                "AchievementRepository não pode ser nulo");
                this.profileRepository = Objects.requireNonNull(profileRepository,
                                "ProfileRepository não pode ser nulo");
                this.notificationService = notificationService; // nullable

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

                Map<Boolean, List<AchievementDefinition>> partitioned = PLANILHA_DEFINITIONS.stream()
                                .collect(Collectors.partitioningBy(
                                                def -> def.category() == AchievementCategory.ACHIEVEMENTS));

                List<AchievementDefinition> normalAchievements = partitioned.get(false);
                List<AchievementDefinition> metaAchievements = partitioned.get(true);

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

                        // Notificação de conquista desbloqueada
                        notifyAchievementUnlocked(profileId, def);
                }
        }

        /**
         * Concede XP ao perfil e verifica se houve subida de rank,
         * disparando notificação específica caso positivo.
         */
        private void concedeXpReward(Long profileId, AchievementTier tier) {
                int xpReward = switch (tier) {
                        case BRONZE -> 100;
                        case SILVER -> 300;
                        case GOLD -> 800;
                        case PLATINUM -> 2500;
                };

                profileRepository.findById(profileId).ifPresent(profile -> {
                        RankingType rankBefore = profile.getRanking();

                        profile.addXp(xpReward);
                        profileRepository.updateXp(profile.getId(), profile.getXp());

                        RankingType rankAfter = profile.getRanking();

                        logger.info("✨ {} XP concedido ao perfil ID {} por desbloquear conquista!", xpReward,
                                        profileId);

                        // Notificação de rank-up (apenas se efetivamente subiu)
                        if (rankAfter.ordinal() > rankBefore.ordinal()) {
                                notifyRankUp(profileId.intValue(), rankBefore, rankAfter);
                        }
                });
        }

        // -----------------------------------------------------------------------
        // NOTIFICAÇÕES
        // -----------------------------------------------------------------------

        private void notifyAchievementUnlocked(Long profileId, AchievementDefinition def) {
                if (notificationService == null)
                        return;

                String tierEmoji = switch (def.tier()) {
                        case BRONZE -> "🥉";
                        case SILVER -> "🥈";
                        case GOLD -> "🥇";
                        case PLATINUM -> "💎";
                };

                String title = tierEmoji + " Conquista Desbloqueada!";
                String friendlyName = buildFriendlyAchievementName(def.key());
                String message = "Parabéns! Você desbloqueou: \"" + friendlyName + "\". Continue assim! 🎉";

                notificationService.send(profileId.intValue(), title, message);
        }

        private void notifyRankUp(int profileId, RankingType rankBefore, RankingType rankAfter) {
                if (notificationService == null)
                        return;

                String rankEmoji = switch (rankAfter) {
                        case D -> "🔵";
                        case C -> "🟢";
                        case B -> "🟡";
                        case A -> "🟠";
                        case S -> "🔴";
                        case SS -> "🌟";
                        default -> "⭐";
                };

                String title = rankEmoji + " Subiu de Rank!";
                String message = "Incrível! Você subiu do Rank " + rankBefore.name()
                                + " para o Rank " + rankAfter.name()
                                + "! Sua dedicação está valendo muito. Continue focado! 🚀";

                notificationService.send(profileId, title, message);
                logger.info("Notificação de rank-up enviada: {} → {} para o perfil {}.", rankBefore, rankAfter,
                                profileId);
        }

        /**
         * Transforma a chave técnica em um nome amigável para a notificação.
         * Reutiliza a lógica já existente no AchievementViewController — aqui
         * fazemos uma versão simplificada focada nas conquistas mais comuns.
         * Para chaves sem mapeamento explícito, aplica uma formatação automática.
         */
        private String buildFriendlyAchievementName(String key) {
                if (key == null)
                        return "conquista especial";
                return switch (key) {
                        case "focus_daily_2h_hours" -> "Bloco de Foco — 2h em um único dia";
                        case "focus_daily_3h_hours" -> "Turno Produtivo — 3h em um único dia";
                        case "focus_daily_4h_hours" -> "Modo Ultra Foco — 4h em um único dia";
                        case "focus_daily_6h_hours" -> "Dia de Elite — 6h de foco absoluto";
                        case "focus_cycles_1_bronze" -> "Primeiro Ciclo Pomodoro";
                        case "focus_cycles_10_silver" -> "Ritmo Estabelecido — 10 ciclos";
                        case "focus_cycles_25_gold" -> "Veterano dos Ciclos — 25 ciclos";
                        case "focus_cycles_100_platinum" -> "Centenário do Foco — 100 ciclos";
                        case "focus_total_days_15_bronze" -> "Primeiros Passos — 15 dias ativos";
                        case "focus_total_days_30_silver" -> "Um Mês Ativo — 30 dias com foco";
                        case "focus_total_days_90_gold" -> "Trimestre de Ouro — 90 dias";
                        case "focus_total_days_365_platinum" -> "Um Ano de Dedicação — 365 dias";
                        case "focus_accumulated_12h_hours" -> "Primeiras 12 Horas Acumuladas";
                        case "focus_accumulated_24h_hours" -> "Um Dia Inteiro de Foco Acumulado";
                        case "focus_accumulated_300h_hours" -> "Mestre do Tempo — 300h acumuladas";
                        case "focus_accumulated_1000h_hours" -> "As Mil Horas — marco lendário";
                        case "streak_current_5" -> "5 Dias de Ofensiva Seguidos";
                        case "streak_current_12" -> "Chama Crescente — 12 dias seguidos";
                        case "streak_current_15" -> "Máquina de Hábitos — 15 dias";
                        case "streak_current_30" -> "Lenda da Constância — 30 dias";
                        case "challenge_constancy_days_7" -> "Semana de Desafio Concluída";
                        case "challenge_constancy_days_15" -> "Quinzena Cumprida";
                        case "challenge_constancy_days_30" -> "Mês Completo de Desafio";
                        case "challenge_perfect_days_5" -> "Perfeccionista — 5 dias perfeitos";
                        case "challenge_perfect_days_7" -> "Semana Imaculada — 7 perfeitos";
                        case "meta_total_5" -> "Colecionador Iniciante — 5 conquistas";
                        case "meta_total_15" -> "Caçador de Medalhas — 15 conquistas";
                        case "meta_total_30" -> "Grande Colecionador — 30 conquistas";
                        case "meta_gold_count_1" -> "Primeiro Ouro Conquistado";
                        case "ranking_tier_c" -> "Primeiro Posto — Rank C";
                        case "ranking_tier_a" -> "Escalada de Elite — Rank A";
                        case "ranking_tier_s" -> "Alto Desempenho — Rank S";
                        case "ranking_tier_ss" -> "Ápice Absoluto — Rank SS";
                        default -> key.replace("_", " "); // fallback automático legível
                };
        }

        // -----------------------------------------------------------------------
        // DEFINIÇÕES DA PLANILHA (inalteradas)
        // -----------------------------------------------------------------------

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