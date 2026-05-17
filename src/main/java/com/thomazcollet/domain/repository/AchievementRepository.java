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
     * identificadora.
     * Útil para o Fail-Fast antes de rodar queries de validação pesadas.
     */
    boolean isUnlocked(Long profileId, String achievementKey);

    /**
     * Conta o total de conquistas de uma categoria e nível específico obtidas por
     * um usuário.
     * Útil para validar a categoria "Conquistas" (Ex: obter 5 conquistas ouro).
     */
    int countByProfileAndTier(Long profileId, String tier);

    Set<String> findUnlockedKeysByProfileId(Long profileId);
}