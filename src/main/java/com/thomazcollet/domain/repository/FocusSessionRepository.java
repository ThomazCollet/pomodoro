package com.thomazcollet.domain.repository;

import com.thomazcollet.domain.model.FocusSession;
import java.util.List;

public interface FocusSessionRepository {
    /**
     * Salva uma nova sessão ou atualiza uma existente (se tiver ID).
     */
    void save(FocusSession session);

    /**
     * Busca todas as sessões vinculadas a um perfil específico.
     */
    List<FocusSession> findByProfileId(Long profileId);

    /**
     * Retorna as últimas sessões realizadas para histórico.
     */
    List<FocusSession> findRecent(int limit);
}