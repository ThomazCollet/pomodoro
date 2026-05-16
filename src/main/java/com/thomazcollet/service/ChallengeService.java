package com.thomazcollet.service;

import com.thomazcollet.domain.exception.ChallengeNotFoundException;
import com.thomazcollet.domain.model.Challenge;
import com.thomazcollet.domain.model.ChallengeStatus;
import com.thomazcollet.domain.model.ChallengeType;
import com.thomazcollet.domain.repository.ChallengeRepository;
import com.thomazcollet.domain.repository.ProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ChallengeService {

    private static final Logger logger = LoggerFactory.getLogger(ChallengeService.class);
    private final ChallengeRepository repository;
    private final ProfileRepository profileRepository;

    public ChallengeService(ChallengeRepository repository, ProfileRepository profileRepository) {
        this.repository = repository;
        this.profileRepository = Objects.requireNonNull(profileRepository, "ProfileRepository não pode ser nulo");
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
                logger.info("MILESTONE CONCLUÍDO EM TEMPO REAL: {}", challenge.getTitle());
                awardChallengeXp(challenge); // Distribui XP imediatamente
            }
        }
    }

    /**
     * Processamento de fim de dia (chamado geralmente à meia-noite ou ao iniciar o
     * app).
     */
    public void processDailyProgress(Long profileId, int focusMinutesToday) {
        List<Challenge> activeChallenges = repository.findActiveByProfile(profileId);

        for (Challenge challenge : activeChallenges) {
            try {
                // Fail-Fast estruturado
                if (challenge.getStatus() != ChallengeStatus.ACTIVE) {
                    continue;
                }

                if (challenge.getType() == ChallengeType.STREAK_CHALLENGE) {
                    processStreakLogic(challenge, challenge.getTodayFocusMinutes());
                } else if (challenge.getType() == ChallengeType.MILESTONE_CHALLENGE) {
                    processMilestoneLogic(challenge, challenge.getTodayFocusMinutes());
                }

                // Limpa o foco diário para o novo dia de forma segura
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
                logger.info("DESAFIO DE CONSTÂNCIA CONCLUÍDO: {}", challenge.getTitle());
            }
        } else {
            currentLives--;
            if (currentLives < 0) {
                currentStatus = ChallengeStatus.FAILED;
                logger.warn("DESAFIO DE CONSTÂNCIA FALHOU (Sem vidas): {}", challenge.getTitle());
            }
        }

        repository.updateProgress(challenge.getId(), currentProgress, currentLives, currentStatus.name());

        // Se mudou para concluído na virada do dia, entrega a recompensa
        if (currentStatus == ChallengeStatus.COMPLETED) {
            awardChallengeXp(challenge);
        }
    }

    private void processMilestoneLogic(Challenge challenge, int focusMinutesToday) {
        // Proteção: se o Milestone já bateu a meta e foi completado em tempo real,
        // ignora
        if (challenge.getStatus() == ChallengeStatus.COMPLETED) {
            return;
        }

        int newTotal = challenge.getAccumulatedMinutes();

        if (challenge.getTodayFocusMinutes() > 0 && newTotal < challenge.getTargetTotalMinutes()) {
            newTotal = repository.findById(challenge.getId())
                    .map(Challenge::getAccumulatedMinutes)
                    .orElse(challenge.getAccumulatedMinutes());
        }

        if (newTotal >= challenge.getTargetTotalMinutes()) {
            repository.updateMilestoneProgress(challenge.getId(), newTotal, ChallengeStatus.COMPLETED.name());
            logger.info("MILESTONE CONCLUÍDO NO PROCESSAMENTO DIÁRIO: {}", challenge.getTitle());
            awardChallengeXp(challenge);
        }
    }

    /**
     * Calcula e concede o XP dinamicamente de acordo com a nova Matriz de
     * Distribuição.
     */
    private void awardChallengeXp(Challenge challenge) {
        int xpGained = 0;

        if (challenge.getType() == ChallengeType.STREAK_CHALLENGE) {
            // Nova Regra: Duração em dias x 10 XP
            xpGained = challenge.getDurationDays() * 10;
        } else if (challenge.getType() == ChallengeType.MILESTONE_CHALLENGE) {
            // Nova Regra: Meta de minutos / 10 XP
            xpGained = challenge.getTargetTotalMinutes() / 10;
        }

        if (xpGained <= 0) {
            xpGained = 1; // Proteção mínima caso criem metas ínfimas
        }

        final int finalXp = xpGained;
        profileRepository.findById(challenge.getProfileId()).ifPresent(profile -> {
            profile.addXp(finalXp);
            profileRepository.updateXp(profile.getId(), profile.getXp());
            logger.info("✨ {} XP creditado ao perfil {} pela conclusão do desafio: '{}'",
                    finalXp, challenge.getProfileId(), challenge.getTitle());
        });
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