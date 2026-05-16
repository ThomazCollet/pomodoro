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

    /**
     * Busca o recorde histórico de minutos focados em um único dia por um perfil.
     * Agrupa as sessões completadas do tipo 'FOCUS' por data e retorna a soma do
     * dia mais produtivo.
     */
    int findMaxFocusMinutesInAGivenDay(Long profileId);

    /**
     * Conta o total acumulado de sessões de foco completadas com sucesso por um
     * perfil.
     * Atende à linha: "Completar ciclos pomodoro completos".
     */
    int countCompletedSessionsByProfileId(Long profileId);

    /**
     * Conta em quantos dias distintos o usuário realizou pelo menos uma sessão de
     * foco completada.
     * Atende à linha: "Completar um total de X dias de foco".
     */
    int countDistinctDaysWithCompletedFocus(Long profileId);

    int sumTotalFocusMinutesByProfileId(Long profileId);

    int findCurrentStreakDaysByProfileId(Long profileId);

    int countTimesStreakTargetWasReached(Long profileId, int streakTarget);
}