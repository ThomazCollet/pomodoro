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

        // Aqui mapeamos as regras da sua planilha (Pode ser carregado de um arquivo,
        // ENUM ou constantes)
        // Como exemplo didático, vamos usar uma lista estática de mapeamento das chaves
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

            // Executa a validação em isolamento
            if (evaluator.evaluate(profileId, def.conditionValue())) {
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
                // Categoria: Foco Diário (Exemplos vindos da imagem)
                new AchievementDefinition("focus_daily_1h_bronze", AchievementCategory.DAILY_FOCUS,
                        AchievementTier.BRONZE, 60),
                new AchievementDefinition("focus_daily_2h_silver", AchievementCategory.DAILY_FOCUS,
                        AchievementTier.SILVER, 120),
                new AchievementDefinition("focus_daily_4h_gold", AchievementCategory.DAILY_FOCUS, AchievementTier.GOLD,
                        240),
                new AchievementDefinition("focus_daily_6h_platinum", AchievementCategory.DAILY_FOCUS,
                        AchievementTier.PLATINUM, 360));
    }
}