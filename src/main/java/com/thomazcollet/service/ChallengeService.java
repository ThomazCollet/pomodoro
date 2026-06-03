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
    private final NotificationService notificationService; // nullable — retrocompatível

    // -----------------------------------------------------------------------
    // Record imutável que transporta o resumo de histórico para a UI.
    // -----------------------------------------------------------------------
    public record CompletedSummary(int totalCompleted, int totalFailed,
            int milestoneCompleted, int streakCompleted) {

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

    // -----------------------------------------------------------------------
    // CONSTRUTORES — sem NotificationService para manter compatibilidade com
    // os testes existentes que usam @InjectMocks.
    // -----------------------------------------------------------------------

    public ChallengeService(ChallengeRepository repository, ProfileRepository profileRepository) {
        this(repository, profileRepository, null);
    }

    public ChallengeService(ChallengeRepository repository,
            ProfileRepository profileRepository,
            NotificationService notificationService) {
        this.repository = repository;
        this.profileRepository = Objects.requireNonNull(profileRepository, "ProfileRepository não pode ser nulo");
        this.notificationService = notificationService; // nullable
    }

    // ==========================================================================
    // CRIAÇÃO
    // ==========================================================================

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

    // ==========================================================================
    // PROGRESSO EM TEMPO REAL
    // ==========================================================================

    public void addFocusMinutes(Long challengeId, int minutes) {
        Challenge challenge = repository.findById(challengeId)
                .orElseThrow(() -> new ChallengeNotFoundException("Desafio não encontrado. ID: " + challengeId));

        if (challenge.getStatus() != ChallengeStatus.ACTIVE)
            return;

        int newDailyFocus = challenge.getTodayFocusMinutes() + minutes;
        repository.updateDailyFocus(challengeId, newDailyFocus);

        if (challenge.getType() == ChallengeType.MILESTONE_CHALLENGE) {
            int newTotal = challenge.getAccumulatedMinutes() + minutes;
            ChallengeStatus newStatus = (newTotal >= challenge.getTargetTotalMinutes())
                    ? ChallengeStatus.COMPLETED
                    : ChallengeStatus.ACTIVE;

            repository.updateMilestoneProgress(challengeId, newTotal, newStatus.name());

            if (newStatus == ChallengeStatus.COMPLETED) {
                logger.info("MILESTONE CONCLUÍDO EM TEMPO REAL: {}", challenge.getTitle());
                awardChallengeXp(challenge);
                notifyChallengeCompleted(challenge);
            }
        }
    }

    // ==========================================================================
    // PROCESSAMENTO DIÁRIO
    // ==========================================================================

    public void processDailyProgress(Long profileId, int focusMinutesToday) {
        List<Challenge> activeChallenges = repository.findActiveByProfile(profileId);

        for (Challenge challenge : activeChallenges) {
            try {
                if (challenge.getStatus() != ChallengeStatus.ACTIVE)
                    continue;

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

        // Verifica se o perfil ficou sem nenhum desafio ativo após o processamento
        checkAndNotifyNoActiveChallenges(profileId);
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
            notifyChallengeCompleted(challenge);
        } else if (currentStatus == ChallengeStatus.FAILED) {
            notifyChallengeFailed(challenge);
        }
    }

    private void processMilestoneLogic(Challenge challenge, int focusMinutesToday) {
        if (challenge.getStatus() == ChallengeStatus.COMPLETED)
            return;

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
            notifyChallengeCompleted(challenge);
        }
    }

    // ==========================================================================
    // XP
    // ==========================================================================

    private void awardChallengeXp(Challenge challenge) {
        int xpGained = 0;

        if (challenge.getType() == ChallengeType.STREAK_CHALLENGE) {
            xpGained = challenge.getDurationDays() * 10;
        } else if (challenge.getType() == ChallengeType.MILESTONE_CHALLENGE) {
            xpGained = challenge.getTargetTotalMinutes() / 10;
        }

        if (xpGained <= 0)
            xpGained = 1;

        final int finalXp = xpGained;
        profileRepository.findById(challenge.getProfileId()).ifPresent(profile -> {
            profile.addXp(finalXp);
            profileRepository.updateXp(profile.getId(), profile.getXp());
            logger.info("✨ {} XP creditado ao perfil {} pela conclusão do desafio: '{}'",
                    finalXp, challenge.getProfileId(), challenge.getTitle());
        });
    }

    // ==========================================================================
    // NOTIFICAÇÕES
    // ==========================================================================

    /**
     * Envia notificação parabenizando o usuário por concluir um desafio.
     */
    private void notifyChallengeCompleted(Challenge challenge) {
        if (notificationService == null)
            return;

        int profileId = challenge.getProfileId().intValue();
        String typeLabel = (challenge.getType() == ChallengeType.STREAK_CHALLENGE)
                ? "constância"
                : "intensidade";

        String title = "🏆 Desafio Concluído!";
        String message = "Parabéns! Você finalizou o desafio de " + typeLabel
                + " \"" + challenge.getTitle() + "\" com sucesso! "
                + "Continue assim — cada vitória conta. 🎉";

        notificationService.send(profileId, title, message);
        logger.info("Notificação de desafio concluído enviada para o perfil {}.", profileId);
    }

    /**
     * Envia notificação informando que o usuário perdeu um desafio.
     */
    private void notifyChallengeFailed(Challenge challenge) {
        if (notificationService == null)
            return;

        int profileId = challenge.getProfileId().intValue();

        String title = "💪 Desafio Encerrado";
        String message = "O desafio \"" + challenge.getTitle()
                + "\" foi encerrado, mas cada tentativa te deixa mais forte. "
                + "Que tal criar um novo e ir ainda mais longe dessa vez? 🚀";

        notificationService.send(profileId, title, message);
        logger.info("Notificação de desafio falho enviada para o perfil {}.", profileId);
    }

    /**
     * Verifica se o perfil ficou sem desafios ativos e envia um lembrete
     * incentivando a criação de um novo. Usa proteção anti-spam via
     * {@link NotificationService#sendGoalNotification}.
     */
    public void checkAndNotifyNoActiveChallenges(Long profileId) {
        if (notificationService == null)
            return;

        List<Challenge> actives = repository.findActiveByProfile(profileId);
        if (!actives.isEmpty())
            return;

        notificationService.sendGoalNotification(
                profileId.intValue(),
                "no_active_challenges",
                "🎯 Nenhum Desafio Ativo",
                "Você não tem nenhum desafio em andamento no momento. "
                        + "Que tal criar um novo para manter o foco e a motivação? 💡");
    }

    // ==========================================================================
    // CONSULTAS
    // ==========================================================================

    public List<Challenge> getChallengesByStatus(Long profileId, ChallengeStatus status) {
        return repository.findAllByProfile(profileId).stream()
                .filter(c -> c.getStatus() == status)
                .collect(Collectors.toList());
    }

    public List<Challenge> getFinishedChallenges(Long profileId) {
        return repository.findAllByProfile(profileId).stream()
                .filter(c -> c.getStatus() == ChallengeStatus.COMPLETED
                        || c.getStatus() == ChallengeStatus.FAILED)
                .collect(Collectors.toList());
    }

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

    public void addFocusMinutesToActiveChallenges(Long profileId, int minutes) {
        List<Challenge> activeChallenges = repository.findActiveByProfile(profileId);
        for (Challenge challenge : activeChallenges) {
            this.addFocusMinutes(challenge.getId(), minutes);
        }
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
}