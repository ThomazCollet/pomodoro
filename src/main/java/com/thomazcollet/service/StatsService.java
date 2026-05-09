package com.thomazcollet.service;

import com.thomazcollet.domain.dto.FocusStatistics;
import com.thomazcollet.domain.exception.StatisticsComputationException;
import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.repository.FocusSessionRepository;
import com.thomazcollet.domain.repository.ProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.Objects;

/**
 * Serviço responsável por processar e consolidar métricas de produtividade.
 */
public class StatsService {

    private static final Logger logger = LoggerFactory.getLogger(StatsService.class);

    private final FocusSessionRepository sessionRepository;
    private final ProfileRepository profileRepository;

    public StatsService(FocusSessionRepository sessionRepository, ProfileRepository profileRepository) {
        this.sessionRepository = sessionRepository;
        this.profileRepository = profileRepository;
    }

    /**
     * Consolida todas as estatísticas de foco para o perfil fornecido.
     * * @throws StatisticsComputationException se ocorrer erro no cálculo ou acesso
     * aos dados.
     */
    public FocusStatistics getUserStatistics(Profile profile) {
        Objects.requireNonNull(profile, "O perfil não pode ser nulo para o cálculo de estatísticas.");

        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startOfDay = now.with(LocalTime.MIN);
            LocalDateTime endOfDay = now.with(LocalTime.MAX);

            LocalDateTime startOfWeek = LocalDate.now()
                    .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                    .atStartOfDay();

            // 1. Cálculos de Tempo (Executados via Repositório)
            long secondsToday = sessionRepository.sumDurationSecondsByProfileIdAndPeriod(profile.getId(), startOfDay,
                    endOfDay);
            long secondsThisWeek = sessionRepository.sumDurationSecondsByProfileIdAndPeriod(profile.getId(),
                    startOfWeek, endOfDay);

            // 2. Cálculo de Streak Atual
            int currentStreak = calculateCurrentStreak(profile.getId());

            // 3. Verificação e Sincronização de Recordes
            checkAndUpdateRecords(profile, (int) secondsToday, currentStreak);

            // 4. Mapeamento para DTO
            return new FocusStatistics(
                    currentStreak,
                    profile.getMaxStreak(),
                    formatDuration(secondsToday),
                    "Recorde: " + formatDuration(profile.getMaxFocusDaySeconds()),
                    formatDuration(secondsThisWeek),
                    Collections.emptyMap(),
                    Collections.emptyMap());

        } catch (Exception e) {
            logger.error("Falha crítica ao computar estatísticas para o perfil ID: {}", profile.getId(), e);
            throw new StatisticsComputationException("Erro ao processar métricas de foco.", e);
        }
    }

    private int calculateCurrentStreak(Long profileId) {
        int streak = 0;
        LocalDate dateToCheck = LocalDate.now();

        // Verifica se houve foco hoje ou ontem para manter o streak ativo
        if (!hasFocusOnDate(profileId, dateToCheck)) {
            dateToCheck = dateToCheck.minusDays(1);
            if (!hasFocusOnDate(profileId, dateToCheck)) {
                return 0;
            }
        }

        streak++;

        // Regressão histórica para contagem do streak
        while (true) {
            dateToCheck = dateToCheck.minusDays(1);
            if (hasFocusOnDate(profileId, dateToCheck)) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    private boolean hasFocusOnDate(Long profileId, LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(LocalTime.MAX);
        return sessionRepository.sumDurationSecondsByProfileIdAndPeriod(profileId, start, end) > 0;
    }

    private void checkAndUpdateRecords(Profile profile, int secondsToday, int currentStreak) {
        boolean isNewDayRecord = secondsToday > profile.getMaxFocusDaySeconds();
        boolean isNewStreakRecord = currentStreak > profile.getMaxStreak();

        if (isNewDayRecord || isNewStreakRecord) {
            if (isNewDayRecord)
                profile.setMaxFocusDaySeconds(secondsToday);
            if (isNewStreakRecord)
                profile.setMaxStreak(currentStreak);

            profileRepository.updateStats(
                    profile.getId(),
                    profile.getMaxStreak(),
                    profile.getMaxFocusDaySeconds(),
                    profile.getTotalFocusSessions());
            logger.info("Recordes atualizados para o perfil {}: Streak={} FocoDiário={}s",
                    profile.getUsername(), profile.getMaxStreak(), profile.getMaxFocusDaySeconds());
        }
    }

    private String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        return String.format("%02dh %02dm", hours, minutes);
    }
}