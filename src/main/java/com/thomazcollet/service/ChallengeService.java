package com.thomazcollet.service;

import com.thomazcollet.domain.exception.ChallengeNotFoundException;
import com.thomazcollet.domain.model.Challenge;
import com.thomazcollet.domain.model.ChallengeStatus;
import com.thomazcollet.domain.model.ChallengeType;
import com.thomazcollet.domain.repository.ChallengeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class ChallengeService {

    private static final Logger logger = LoggerFactory.getLogger(ChallengeService.class);
    private final ChallengeRepository repository;

    public ChallengeService(ChallengeRepository repository) {
        this.repository = repository;
    }

    public void createChallenge(Challenge challenge) {
        logger.info("Criando novo desafio de {}: {}", challenge.getType(), challenge.getTitle());

        validateChallengeData(challenge);

        challenge.setStatus(ChallengeStatus.ACTIVE);
        challenge.setLivesRemaining(challenge.getLivesTotal());
        challenge.setProgressDays(0);
        challenge.setAccumulatedMinutes(0);
        challenge.setTodayFocusMinutes(0);

        repository.save(challenge);
    }

    /**
     * Atualiza o foco diário em tempo real (ex: ao terminar um Pomodoro).
     */
    public void addFocusMinutes(Long challengeId, int minutes) {
        Challenge challenge = repository.findById(challengeId)
                .orElseThrow(() -> new ChallengeNotFoundException("Desafio não encontrado. ID: " + challengeId));

        if (challenge.getStatus() != ChallengeStatus.ACTIVE)
            return;

        // 1. Atualiza o foco do dia (UI)
        int newDailyFocus = challenge.getTodayFocusMinutes() + minutes;
        repository.updateDailyFocus(challengeId, newDailyFocus);

        // 2. Se for Milestone, atualiza o acumulado total
        if (challenge.getType() == ChallengeType.MILESTONE_CHALLENGE) {
            int newTotal = challenge.getAccumulatedMinutes() + minutes;
            ChallengeStatus newStatus = (newTotal >= challenge.getTargetTotalMinutes())
                    ? ChallengeStatus.COMPLETED
                    : ChallengeStatus.ACTIVE;

            repository.updateMilestoneProgress(challengeId, newTotal, newStatus.name());

            if (newStatus == ChallengeStatus.COMPLETED) {
                logger.info("MILSTONE CONCLUÍDO: {}", challenge.getTitle());
            }
        }
    }

    /**
     * Processamento de fim de dia (chamado geralmente à meia-noite ou ao iniciar o
     * app no dia seguinte).
     */
    public void processDailyProgress(Long profileId, int focusMinutesToday) {
        List<Challenge> activeChallenges = repository.findActiveByProfile(profileId);

        for (Challenge challenge : activeChallenges) {
            try {
                if (challenge.getType() == ChallengeType.STREAK_CHALLENGE) {
                    processStreakLogic(challenge, focusMinutesToday);
                }
                // Limpa o foco diário para o novo dia
                repository.updateDailyFocus(challenge.getId(), 0);
            } catch (Exception e) {
                logger.error("Erro ao processar desafio ID {}: {}", challenge.getId(), e.getMessage());
            }
        }
    }

    private void processStreakLogic(Challenge challenge, int focusMinutesToday) {
        int currentProgress = challenge.getProgressDays();
        int currentLives = challenge.getLivesRemaining();
        ChallengeStatus currentStatus = challenge.getStatus();

        if (focusMinutesToday >= challenge.getMinFocusMinutesPerDay()) {
            currentProgress++;
            if (currentProgress >= challenge.getDurationDays()) {
                currentStatus = ChallengeStatus.COMPLETED;
            }
        } else {
            currentLives--;
            if (currentLives < 0) {
                currentStatus = ChallengeStatus.FAILED;
            }
        }

        repository.updateProgress(challenge.getId(), currentProgress, currentLives, currentStatus.name());
    }

    public List<Challenge> getChallengesByStatus(Long profileId, ChallengeStatus status) {
        return repository.findAllByProfile(profileId).stream()
                .filter(c -> c.getStatus() == status)
                .collect(Collectors.toList());
    }

    public void deleteChallenge(Long id) {
        if (repository.findById(id).isEmpty()) {
            throw new ChallengeNotFoundException("Desafio não encontrado para exclusão.");
        }
        repository.delete(id);
    }

    private void validateChallengeData(Challenge challenge) {
        if (challenge.getType() == null) {
            throw new IllegalArgumentException("O tipo de desafio deve ser especificado.");
        }
        if (challenge.getDurationDays() <= 0) {
            throw new IllegalArgumentException("A duração deve ser maior que zero.");
        }
        if (challenge.getType() == ChallengeType.MILESTONE_CHALLENGE && challenge.getTargetTotalMinutes() <= 0) {
            throw new IllegalArgumentException("Desafios de Intensidade precisam de uma meta de minutos total.");
        }
    }

    public void addFocusMinutesToActiveChallenges(Long profileId, int minutes) {
        List<Challenge> activeChallenges = repository.findActiveByProfile(profileId);
        for (Challenge challenge : activeChallenges) {
            this.addFocusMinutes(challenge.getId(), minutes);
        }
    }
}