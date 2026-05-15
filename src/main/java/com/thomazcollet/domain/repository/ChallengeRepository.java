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
     * Atualiza o estado geral do desafio (dias concluídos e vidas).
     */
    void updateProgress(Long challengeId, int progressDays, int livesRemaining, String status);

    /**
     * Atualiza especificamente os minutos de foco acumulados no dia atual.
     */
    void updateDailyFocus(Long challengeId, int minutes);

    void delete(Long id);
}