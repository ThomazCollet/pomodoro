package com.thomazcollet.service;

import com.thomazcollet.domain.model.FocusSession;
import com.thomazcollet.domain.model.SessionType;
import com.thomazcollet.domain.repository.FocusSessionRepository;
import com.thomazcollet.domain.repository.ProfileRepository; // Importado
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class FocusSessionService {

    private static final Logger logger = LoggerFactory.getLogger(FocusSessionService.class);
    private final FocusSessionRepository repository;
    private final ProfileRepository profileRepository; // Injetado para as migalhinhas de XP
    private FocusSession currentActiveSession;

    public FocusSessionService(FocusSessionRepository repository, ProfileRepository profileRepository) {
        this.repository = repository;
        this.profileRepository = Objects.requireNonNull(profileRepository, "ProfileRepository não pode ser nulo");
    }

    /**
     * Inicia o registro de uma nova sessão no banco de dados.
     */
    public void startSession(Long profileId, SessionType type) {
        currentActiveSession = new FocusSession(profileId, type, LocalDateTime.now());
        repository.save(currentActiveSession);
        logger.info("Registro de sessão iniciado: {} (ID: {})", type, currentActiveSession.getId());
    }

    /**
     * Finaliza a sessão atual, calculando duração, status e distribuindo as
     * migalhinhas de XP.
     */
    public void finalizeCurrentSession(int durationSeconds, boolean completed) {
        if (currentActiveSession == null) {
            logger.warn("Tentativa de finalizar uma sessão sem nenhuma ativa em memória.");
            return;
        }

        currentActiveSession.setEndTimestamp(LocalDateTime.now());
        currentActiveSession.setDurationSeconds(durationSeconds);
        currentActiveSession.setCompleted(completed);

        repository.save(currentActiveSession);
        logger.info("Sessão finalizada e persistida: {} (ID: {}, Completada: {})",
                currentActiveSession.getType(), currentActiveSession.getId(), completed);

        // REGRA DAS MIGALHINHAS: Se for sessão de FOCUS e houver pelo menos 1 minuto
        // trabalhado
        if (currentActiveSession.getType() == SessionType.FOCUS && durationSeconds >= 60) {
            int xpGained = durationSeconds / 60; // 1 minuto = 1 XP
            awardFocusXp(currentActiveSession.getProfileId(), xpGained);
        }

        currentActiveSession = null;
    }

    /**
     * Entrega o XP ao perfil de forma isolada e segura
     */
    private void awardFocusXp(Long profileId, int xpGained) {
        profileRepository.findById(profileId).ifPresent(profile -> {
            profile.addXp(xpGained);
            profileRepository.updateXp(profile.getId(), profile.getXp());
            logger.info("🌱 Migalhinhas: +{} XP de foco diário creditado ao perfil {}.", xpGained, profileId);
        });
    }

    public List<FocusSession> getHistory(Long profileId) {
        return repository.findByProfileId(profileId);
    }

    public List<FocusSession> getRecentSessions(int limit) {
        return repository.findRecent(limit);
    }
}