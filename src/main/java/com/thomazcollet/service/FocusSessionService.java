package com.thomazcollet.service;

import com.thomazcollet.domain.model.FocusSession;
import com.thomazcollet.domain.model.SessionType;
import com.thomazcollet.domain.repository.FocusSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

public class FocusSessionService {

    private static final Logger logger = LoggerFactory.getLogger(FocusSessionService.class);
    private final FocusSessionRepository repository;
    private FocusSession currentActiveSession;

    public FocusSessionService(FocusSessionRepository repository) {
        this.repository = repository;
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
     * Finaliza a sessão atual, calculando duração e status de conclusão.
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
        
        currentActiveSession = null;
    }

    public List<FocusSession> getHistory(Long profileId) {
        return repository.findByProfileId(profileId);
    }

    public List<FocusSession> getRecentSessions(int limit) {
        return repository.findRecent(limit);
    }
}