package com.thomazcollet.service;

import com.thomazcollet.domain.exception.ChallengeNotFoundException;
import com.thomazcollet.domain.model.Challenge;
import com.thomazcollet.domain.model.ChallengeStatus;
import com.thomazcollet.domain.repository.ChallengeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service responsável pela lógica de negócios de desafios (metas).
 * Implementa validações Fail-Fast e garante a integridade dos estados dos
 * desafios.
 */
public class ChallengeService {

    private static final Logger logger = LoggerFactory.getLogger(ChallengeService.class);
    private final ChallengeRepository repository;

    public ChallengeService(ChallengeRepository repository) {
        this.repository = repository;
    }

    public void createChallenge(Challenge challenge) {
        logger.info("Criando novo desafio: {}", challenge.getTitle());

        validateChallengeData(challenge);

        challenge.setStatus(ChallengeStatus.ACTIVE);
        challenge.setProgressDays(0);
        challenge.setLivesRemaining(challenge.getLivesTotal());

        repository.save(challenge);
    }

    public List<Challenge> getChallengesByStatus(Long profileId, ChallengeStatus status) {
        return repository.findAllByProfile(profileId).stream()
                .filter(c -> c.getStatus() == status)
                .collect(Collectors.toList());
    }

    public void deleteChallenge(Long id) {
        repository.findById(id)
                .orElseThrow(() -> new ChallengeNotFoundException("Desafio não encontrado para exclusão. ID: " + id));

        repository.delete(id);
        logger.info("Desafio ID {} excluído com sucesso.", id);
    }

    /**
     * Atualiza o progresso de todos os desafios ativos baseando-se no tempo de foco
     * do dia.
     */
    public void processDailyProgress(Long profileId, int focusMinutesToday) {
        List<Challenge> activeChallenges = repository.findActiveByProfile(profileId);
        logger.debug("Processando progresso diário para {} desafios ativos.", activeChallenges.size());

        for (Challenge challenge : activeChallenges) {
            try {
                updateSingleChallengeProgress(challenge, focusMinutesToday);
            } catch (Exception e) {
                logger.error("Falha crítica ao atualizar desafio ID {}: {}", challenge.getId(), e.getMessage());
                // O loop continua para não prejudicar o progresso dos outros desafios
            }
        }
    }

    private void updateSingleChallengeProgress(Challenge challenge, int focusMinutesToday) {
        int currentProgress = challenge.getProgressDays();
        int currentLives = challenge.getLivesRemaining();
        ChallengeStatus currentStatus = challenge.getStatus();

        if (focusMinutesToday >= challenge.getMinFocusMinutesPerDay()) {
            currentProgress++;
            if (currentProgress >= challenge.getDurationDays()) {
                currentStatus = ChallengeStatus.COMPLETED;
                logger.info("DESAFIO CONCLUÍDO: {}", challenge.getTitle());
            }
        } else {
            currentLives--;
            if (currentLives < 0) {
                currentStatus = ChallengeStatus.FAILED;
                logger.warn("DESAFIO FALHOU: {}", challenge.getTitle());
            }
        }

        repository.updateProgress(challenge.getId(), currentProgress, currentLives, currentStatus.name());
    }

    private void validateChallengeData(Challenge challenge) {
        if (challenge.getDurationDays() <= 0) {
            throw new IllegalArgumentException("A duração do desafio deve ser maior que zero dias.");
        }
        if (challenge.getMinFocusMinutesPerDay() < 0) {
            throw new IllegalArgumentException("O tempo mínimo de foco não pode ser negativo.");
        }
    }
}