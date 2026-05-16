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
        return List.of(
                // Linha 1: Tempo focado em um único dia (Valores convertidos em minutos)
                new AchievementDefinition("focus_daily_1h_hours", AchievementCategory.DAILY_FOCUS,
                        AchievementTier.BRONZE, 60),
                new AchievementDefinition("focus_daily_2h_hours", AchievementCategory.DAILY_FOCUS,
                        AchievementTier.SILVER, 120),
                new AchievementDefinition("focus_daily_4h_hours", AchievementCategory.DAILY_FOCUS,
                        AchievementTier.GOLD, 240),
                new AchievementDefinition("focus_daily_6h_hours", AchievementCategory.DAILY_FOCUS,
                        AchievementTier.PLATINUM, 360),

                // Linha 2: Quantidade de ciclos pomodoro completos acumulados
                new AchievementDefinition("focus_cycles_1_bronze", AchievementCategory.DAILY_FOCUS,
                        AchievementTier.BRONZE, 1),
                new AchievementDefinition("focus_cycles_10_silver", AchievementCategory.DAILY_FOCUS,
                        AchievementTier.SILVER, 10),
                new AchievementDefinition("focus_cycles_25_gold", AchievementCategory.DAILY_FOCUS,
                        AchievementTier.GOLD, 25),
                new AchievementDefinition("focus_cycles_100_platinum", AchievementCategory.DAILY_FOCUS,
                        AchievementTier.PLATINUM, 100),

                // Linha 3: Total de dias distintos com foco realizado
                new AchievementDefinition("focus_total_days_15_bronze", AchievementCategory.DAILY_FOCUS,
                        AchievementTier.BRONZE, 15),
                new AchievementDefinition("focus_total_days_30_silver", AchievementCategory.DAILY_FOCUS,
                        AchievementTier.SILVER, 30),
                new AchievementDefinition("focus_total_days_90_gold", AchievementCategory.DAILY_FOCUS,
                        AchievementTier.GOLD, 90),
                new AchievementDefinition("focus_total_days_365_platinum", AchievementCategory.DAILY_FOCUS,
                        AchievementTier.PLATINUM, 365),

                // Linha Nova (Foco Acumulado Histórico): Horas totais acumuladas convertidas
                // para minutos
                new AchievementDefinition("focus_accumulated_12h_hours", AchievementCategory.DAILY_FOCUS,
                        AchievementTier.BRONZE, 720), // 12h * 60min
                new AchievementDefinition("focus_accumulated_24h_hours", AchievementCategory.DAILY_FOCUS,
                        AchievementTier.SILVER, 1440), // 24h * 60min
                new AchievementDefinition("focus_accumulated_300h_hours", AchievementCategory.DAILY_FOCUS,
                        AchievementTier.GOLD, 18000), // 300h * 60min
                new AchievementDefinition("focus_accumulated_1000h_hours", AchievementCategory.DAILY_FOCUS,
                        AchievementTier.PLATINUM, 60000) // 1000h * 60min
        );
    }
}