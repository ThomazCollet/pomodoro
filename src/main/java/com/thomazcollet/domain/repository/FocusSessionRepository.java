package com.thomazcollet.domain.repository;

import com.thomazcollet.domain.model.FocusSession;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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

    /**
     * Soma o total de segundos de foco para um perfil em um período específico.
     */
    long sumDurationSecondsByProfileIdAndPeriod(Long profileId, LocalDateTime start, LocalDateTime end);

    /**
     * Retorna um resumo diário de tempo focado (String para gráficos simples).
     */
    Map<String, Integer> getDailyFocusSummary(Long profileId, int daysToLookBack);

    /**
     * Retorna um resumo diário de tempo focado (LocalDate para o Heatmap).
     */
    Map<LocalDate, Long> getDailyFocusTime(Long profileId, LocalDateTime since);
}