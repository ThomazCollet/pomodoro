package com.thomazcollet.domain.repository;

import com.thomazcollet.domain.model.Challenge;
import java.util.List;
import java.util.Optional;

public interface ChallengeRepository {
    void save(Challenge challenge);
    Optional<Challenge> findById(Long id);
    List<Challenge> findActiveByProfile(Long profileId);
    List<Challenge> findAllByProfile(Long profileId);
    void updateProgress(Integer challengeId, int progressDays, int livesRemaining, String status);
    void delete(Integer id);
}