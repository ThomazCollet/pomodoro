package com.thomazcollet.domain.repository;

import com.thomazcollet.domain.model.Achievement;
import java.util.List;
import java.util.Set;

public interface AchievementRepository {

    /**
     * Salva o registro de uma nova conquista desbloqueada pelo usuário.
     */
    void save(Achievement achievement);

    /**
     * Busca todas as conquistas já desbloqueadas por um perfil específico.
     */
    List<Achievement> findByProfileId(Long profileId);

    /**
     * Verifica se o usuário já possui uma conquista específica com base na chave
     * identificadora. Útil para o Fail-Fast antes de rodar queries de validação
     * pesadas.
     */
    boolean isUnlocked(Long profileId, String achievementKey);

    /**
     * Conta o total de conquistas desbloqueadas de um determinado tier por um
     * perfil.
     * Útil para validar trilhas como "obtenha 5 conquistas ouro".
     */
    int countByProfileAndTier(Long profileId, String tier);

    /**
     * Retorna todas as chaves de conquistas já desbloqueadas por um perfil.
     * Usado pela UI para determinar o estado visual de cada card
     * (COMPLETED/ACTIVE/LOCKED).
     */
    Set<String> findUnlockedKeysByProfileId(Long profileId);

    /**
     * Conta o total de conquistas definidas no sistema para um determinado tier,
     * ignorando meta-conquistas (prefixo "meta_"). Necessário para validar
     * "meta_all_gold" sem hardcodar o número de Ouros no evaluator.
     *
     * Implementação sugerida: contar linhas na tabela achievement_definitions
     * onde tier = ? e key NOT LIKE 'meta_%'. Se não houver tabela de definições,
     * pode ser implementado retornando um valor fixo atualizado manualmente,
     * ou recebendo a contagem via injeção no evaluator (ver
     * AchievementCountEvaluator).
     */
    int countTotalDefinedByTier(String tier);
}