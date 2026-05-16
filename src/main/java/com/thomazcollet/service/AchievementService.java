package com.thomazcollet.service;

import com.thomazcollet.domain.model.Achievement;
import com.thomazcollet.domain.model.AchievementCategory;
import com.thomazcollet.domain.model.AchievementTier;
import com.thomazcollet.domain.repository.AchievementRepository;
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
    private final Map<AchievementCategory, AchievementEvaluator> evaluators = new EnumMap<>(AchievementCategory.class);

    public AchievementService(AchievementRepository achievementRepository, List<AchievementEvaluator> evaluatorList) {
        // Garante que o serviço não seja instanciado sem um repositório válido
        this.achievementRepository = Objects.requireNonNull(achievementRepository,
                "AchievementRepository não pode ser nulo");

        // Alerta se o sistema subir sem nenhum validador configurado
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
     * Varre as regras de negócio para verificar se novas conquistas foram
     * desbloqueadas.
     * Pode ser chamado ao finalizar um Pomodoro ou ao concluir um Desafio.
     */
    public void checkAndUnlockNewAchievements(Long profileId) {
        logger.info("Iniciando verificação de conquistas para o perfil: {}", profileId);

        // Mapeamento estruturado vindo do planejamento de gamificação
        List<AchievementDefinition> definitions = getDefinitionsFromPlanilha();

        for (AchievementDefinition def : definitions) {

            // Fail-Fast: Se o usuário já tem essa conquista desbloqueada, pula para a
            // próxima
            if (achievementRepository.isUnlocked(profileId, def.key())) {
                continue;
            }

            // Busca o validador polimórfico correto para a categoria
            AchievementEvaluator evaluator = evaluators.get(def.category());
            if (evaluator == null) {
                continue;
            }

            // Executa a validação passando a chave contextual da conquista para suportar
            // sub-regras granulares
            if (evaluator.evaluate(profileId, def.key(), def.conditionValue())) {
                Achievement newAchievement = new Achievement();
                newAchievement.setProfileId(profileId);
                newAchievement.setAchievementKey(def.key());
                newAchievement.setCategory(def.category());
                newAchievement.setTier(def.tier());

                achievementRepository.save(newAchievement);
                logger.info("🏆 CONQUISTA DESBLOQUEADA: {} [{}]", def.key(), def.tier());

                // TODO: Notificar a UI (JavaFX) para disparar o Toast animado na tela
            }
        }
    }

    // Record utilitário auxiliar para representar as linhas planejadas na planilha
    private record AchievementDefinition(String key, AchievementCategory category, AchievementTier tier,
            int conditionValue) {
    }

    private List<AchievementDefinition> getDefinitionsFromPlanilha() {
        List<AchievementDefinition> list = new ArrayList<>();

        // =========================================================================
        // CATEGORIA: DAILY_FOCUS (Foco Acumulado)
        // =========================================================================

        // Linha 1: Tempo focado em um único dia (Valores convertidos em minutos)
        list.add(new AchievementDefinition("focus_daily_1h_hours", AchievementCategory.DAILY_FOCUS,
                AchievementTier.BRONZE, 60));
        list.add(new AchievementDefinition("focus_daily_2h_hours", AchievementCategory.DAILY_FOCUS,
                AchievementTier.SILVER, 120));
        list.add(new AchievementDefinition("focus_daily_4h_hours", AchievementCategory.DAILY_FOCUS,
                AchievementTier.GOLD, 240));
        list.add(new AchievementDefinition("focus_daily_6h_hours", AchievementCategory.DAILY_FOCUS,
                AchievementTier.PLATINUM, 360));

        // Linha 2: Quantidade de ciclos pomodoro completos acumulados
        list.add(new AchievementDefinition("focus_cycles_1_bronze", AchievementCategory.DAILY_FOCUS,
                AchievementTier.BRONZE, 1));
        list.add(new AchievementDefinition("focus_cycles_10_silver", AchievementCategory.DAILY_FOCUS,
                AchievementTier.SILVER, 10));
        list.add(new AchievementDefinition("focus_cycles_25_gold", AchievementCategory.DAILY_FOCUS,
                AchievementTier.GOLD, 25));
        list.add(new AchievementDefinition("focus_cycles_100_platinum", AchievementCategory.DAILY_FOCUS,
                AchievementTier.PLATINUM, 100));

        // Linha 3: Total de dias distintos com foco realizado
        list.add(new AchievementDefinition("focus_total_days_15_bronze", AchievementCategory.DAILY_FOCUS,
                AchievementTier.BRONZE, 15));
        list.add(new AchievementDefinition("focus_total_days_30_silver", AchievementCategory.DAILY_FOCUS,
                AchievementTier.SILVER, 30));
        list.add(new AchievementDefinition("focus_total_days_90_gold", AchievementCategory.DAILY_FOCUS,
                AchievementTier.GOLD, 90));
        list.add(new AchievementDefinition("focus_total_days_365_platinum", AchievementCategory.DAILY_FOCUS,
                AchievementTier.PLATINUM, 365));

        // Linha Nova (Foco Acumulado Histórico): Horas totais acumuladas convertidas
        // para minutos
        list.add(new AchievementDefinition("focus_accumulated_12h_hours", AchievementCategory.DAILY_FOCUS,
                AchievementTier.BRONZE, 720)); // 12h * 60min
        list.add(new AchievementDefinition("focus_accumulated_24h_hours", AchievementCategory.DAILY_FOCUS,
                AchievementTier.SILVER, 1440)); // 24h * 60min
        list.add(new AchievementDefinition("focus_accumulated_300h_hours", AchievementCategory.DAILY_FOCUS,
                AchievementTier.GOLD, 18000)); // 300h * 60min
        list.add(new AchievementDefinition("focus_accumulated_1000h_hours", AchievementCategory.DAILY_FOCUS,
                AchievementTier.PLATINUM, 60000)); // 1000h * 60min

        // =========================================================================
        // CATEGORIA: STREAK (Ofensivas)
        // =========================================================================

        // Linha 4: Atingir um total de X streaks (Dias consecutivos de ofensiva)
        list.add(new AchievementDefinition("streak_current_5", AchievementCategory.STREAK, AchievementTier.BRONZE, 5));
        list.add(
                new AchievementDefinition("streak_current_12", AchievementCategory.STREAK, AchievementTier.SILVER, 12));
        list.add(new AchievementDefinition("streak_current_15", AchievementCategory.STREAK, AchievementTier.GOLD, 15));
        list.add(new AchievementDefinition("streak_current_30", AchievementCategory.STREAK, AchievementTier.PLATINUM,
                30));

        // Linha 5: Atinja um total de X streaks 3 vezes (conditionValue guarda o
        // tamanho do streak alvo)
        list.add(new AchievementDefinition("streak_count_5_x3", AchievementCategory.STREAK, AchievementTier.BRONZE, 5));
        list.add(new AchievementDefinition("streak_count_12_x3", AchievementCategory.STREAK, AchievementTier.SILVER,
                12));
        list.add(new AchievementDefinition("streak_count_15_x3", AchievementCategory.STREAK, AchievementTier.GOLD, 15));
        list.add(new AchievementDefinition("streak_count_30_x3", AchievementCategory.STREAK, AchievementTier.PLATINUM,
                30));

        // Linha 6: Atinja um total de X streaks 5 vezes (conditionValue guarda o
        // tamanho do streak alvo)
        list.add(new AchievementDefinition("streak_count_5_x5", AchievementCategory.STREAK, AchievementTier.BRONZE, 5));
        list.add(new AchievementDefinition("streak_count_12_x5", AchievementCategory.STREAK, AchievementTier.SILVER,
                12));
        list.add(new AchievementDefinition("streak_count_15_x5", AchievementCategory.STREAK, AchievementTier.GOLD, 15));
        list.add(new AchievementDefinition("streak_count_30_x5", AchievementCategory.STREAK, AchievementTier.PLATINUM,
                30));

        // =========================================================================
        // CATEGORIA: ACHIEVEMENTS (Meta-Conquistas)
        // =========================================================================

        // Linha 21: Obtenha sua primeira conquista ouro / platina
        list.add(new AchievementDefinition("meta_first_gold", AchievementCategory.ACHIEVEMENTS, AchievementTier.BRONZE,
                1));
        list.add(new AchievementDefinition("meta_first_platinum", AchievementCategory.ACHIEVEMENTS,
                AchievementTier.SILVER, 1));

        // Linha 22: Obtenha um total de X conquistas
        list.add(
                new AchievementDefinition("meta_total_5", AchievementCategory.ACHIEVEMENTS, AchievementTier.BRONZE, 5));
        list.add(new AchievementDefinition("meta_total_15", AchievementCategory.ACHIEVEMENTS, AchievementTier.SILVER,
                15));
        list.add(
                new AchievementDefinition("meta_total_30", AchievementCategory.ACHIEVEMENTS, AchievementTier.GOLD, 30));

        // Linha 23: Obtenha todas as conquistas ouro
        list.add(new AchievementDefinition("meta_all_gold", AchievementCategory.ACHIEVEMENTS, AchievementTier.PLATINUM,
                7));

        return list;
    }
}