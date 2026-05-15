package com.thomazcollet.domain.repository;

import com.thomazcollet.domain.model.Challenge;
import java.util.List;
import java.util.Optional;

public interface ChallengeRepository {

    void save(Challenge challenge);

    Optional<Challenge> findById(Long id);

    List<Challenge> findActiveByProfile(Long profileId);

    List<Challenge> findAllByProfile(Long profileId);

    /**
     * Atualiza o estado para desafios de CONSTÂNCIA (Streak).
     * Focado em dias concluídos e gerenciamento de vidas.
     */
    void updateProgress(Long challengeId, int progressDays, int livesRemaining, String status);

    /**
     * Atualiza o estado para desafios de INTENSIDADE (Milestone).
     * Focado no acúmulo total de minutos até o objetivo.
     */
    void updateMilestoneProgress(Long challengeId, int accumulatedMinutes, String status);

    /**
     * Atualiza especificamente os minutos de foco acumulados no dia atual.
     * Útil para o feedback visual do card (hoje fiz X/Y min).
     */
    void updateDailyFocus(Long challengeId, int minutes);

    void delete(Long id);
}