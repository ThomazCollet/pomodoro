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

    // -----------------------------------------------------------------------
    // Record imutável que transporta o resumo de histórico para a UI.
    // Usado pela seção colapsável de "Desafios Concluídos".
    // -----------------------------------------------------------------------
    public record CompletedSummary(int totalCompleted, int totalFailed,
            int milestoneCompleted, int streakCompleted) {

        /** Texto amigável exibido na barra de resumo da seção de histórico. */
        public String toDisplayText() {
            if (totalCompleted == 0 && totalFailed == 0) {
                return "Nenhum desafio finalizado ainda. Complete seu primeiro para entrar para o histórico!";
            }
            StringBuilder sb = new StringBuilder("🏆 ");
            if (milestoneCompleted > 0) {
                sb.append(milestoneCompleted)
                        .append(milestoneCompleted == 1 ? " meta de intensidade" : " metas de intensidade");
            }
            if (milestoneCompleted > 0 && streakCompleted > 0) {
                sb.append(" e ");
            }
            if (streakCompleted > 0) {
                sb.append(streakCompleted)
                        .append(streakCompleted == 1 ? " constância mantida" : " constâncias mantidas");
            }
            sb.append(" — você chegou lá!");
            if (totalFailed > 0) {
                sb.append("  •  ").append(totalFailed)
                        .append(totalFailed == 1 ? " desafio não concluído" : " desafios não concluídos")
                        .append(" (cada tentativa conta 💪)");
            }
            return sb.toString();
        }
    }

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
                awardChallengeXp(challenge);
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
                if (challenge.getStatus() != ChallengeStatus.ACTIVE) {
                    continue;
                }

                if (challenge.getType() == ChallengeType.STREAK_CHALLENGE) {
                    processStreakLogic(challenge, challenge.getTodayFocusMinutes());
                } else if (challenge.getType() == ChallengeType.MILESTONE_CHALLENGE) {
                    processMilestoneLogic(challenge, challenge.getTodayFocusMinutes());
                }

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

        if (currentStatus == ChallengeStatus.COMPLETED) {
            awardChallengeXp(challenge);
        }
    }

    private void processMilestoneLogic(Challenge challenge, int focusMinutesToday) {
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

    private void awardChallengeXp(Challenge challenge) {
        int xpGained = 0;

        if (challenge.getType() == ChallengeType.STREAK_CHALLENGE) {
            xpGained = challenge.getDurationDays() * 10;
        } else if (challenge.getType() == ChallengeType.MILESTONE_CHALLENGE) {
            xpGained = challenge.getTargetTotalMinutes() / 10;
        }

        if (xpGained <= 0) {
            xpGained = 1;
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

    /**
     * Retorna todos os desafios finalizados (COMPLETED ou FAILED) de um perfil,
     * ordenados do mais recente para o mais antigo (por data de início desc).
     */
    public List<Challenge> getFinishedChallenges(Long profileId) {
        return repository.findAllByProfile(profileId).stream()
                .filter(c -> c.getStatus() == ChallengeStatus.COMPLETED
                        || c.getStatus() == ChallengeStatus.FAILED)
                .collect(Collectors.toList()); // findAllByProfile já retorna ORDER BY start_date DESC
    }

    /**
     * Constrói o resumo de histórico para exibição na barra da seção colapsável.
     */
    public CompletedSummary getCompletedSummary(Long profileId) {
        List<Challenge> finished = getFinishedChallenges(profileId);

        int totalCompleted = (int) finished.stream().filter(c -> c.getStatus() == ChallengeStatus.COMPLETED).count();
        int totalFailed = (int) finished.stream().filter(c -> c.getStatus() == ChallengeStatus.FAILED).count();
        int milestoneCompleted = (int) finished.stream()
                .filter(c -> c.getStatus() == ChallengeStatus.COMPLETED
                        && c.getType() == ChallengeType.MILESTONE_CHALLENGE)
                .count();
        int streakCompleted = (int) finished.stream()
                .filter(c -> c.getStatus() == ChallengeStatus.COMPLETED
                        && c.getType() == ChallengeType.STREAK_CHALLENGE)
                .count();

        return new CompletedSummary(totalCompleted, totalFailed, milestoneCompleted, streakCompleted);
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