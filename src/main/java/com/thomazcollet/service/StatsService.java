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
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Serviço responsável por processar e consolidar métricas de produtividade.
 */
public class StatsService {

    private static final Logger logger = LoggerFactory.getLogger(StatsService.class);

    private static final Locale PT_BR = new Locale("pt", "BR");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM");

    private final FocusSessionRepository sessionRepository;
    private final ProfileRepository profileRepository;

    public StatsService(FocusSessionRepository sessionRepository, ProfileRepository profileRepository) {
        this.sessionRepository = sessionRepository;
        this.profileRepository = profileRepository;
    }

    public FocusStatistics getUserStatistics(Profile profile) {
        Objects.requireNonNull(profile, "O perfil não pode ser nulo.");

        try {
            LocalDateTime now = LocalDateTime.now();

            LocalDateTime startOfDay = now.with(LocalTime.MIN);
            LocalDateTime endOfDay = now.with(LocalTime.MAX);

            // Início da semana (Segunda-feira)
            LocalDateTime startOfWeek = now.toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                    .atStartOfDay();

            long secondsToday = sessionRepository.sumDurationSecondsByProfileIdAndPeriod(profile.getId(), startOfDay,
                    endOfDay);
            long secondsThisWeek = sessionRepository.sumDurationSecondsByProfileIdAndPeriod(profile.getId(),
                    startOfWeek, endOfDay);

            int currentStreak = calculateCurrentStreak(profile.getId());
            checkAndUpdateRecords(profile, (int) secondsToday, currentStreak);

            LocalDateTime oneYearAgo = now.minusYears(1).with(LocalTime.MIN);
            Map<LocalDate, Long> heatmapData = sessionRepository.getDailyFocusTime(profile.getId(), oneYearAgo);

            return new FocusStatistics(
                    currentStreak,
                    profile.getMaxStreak(),
                    formatDuration(secondsToday),
                    "Recorde: " + formatDuration(profile.getMaxFocusDaySeconds()),
                    formatDuration(secondsThisWeek),
                    heatmapData,
                    calculateRollingDailyDistribution(profile.getId()),
                    calculateEightWeeksDistribution(profile.getId()),
                    calculateFixedAnnualDistribution(profile.getId()));

        } catch (Exception e) {
            logger.error("Erro ao processar métricas de foco para o perfil: {}", profile.getId(), e);
            throw new StatisticsComputationException("Falha na consolidação das estatísticas.", e);
        }
    }

    /**
     * Distribuição anual FIXA (JAN a DEZ).
     */
    private Map<String, Double> calculateFixedAnnualDistribution(Long profileId) {
        Map<String, Double> distribution = new LinkedHashMap<>();
        int currentYear = LocalDate.now().getYear();

        for (int month = 1; month <= 12; month++) {
            LocalDate monthRef = LocalDate.of(currentYear, month, 1);
            LocalDateTime start = monthRef.atStartOfDay();
            LocalDateTime end = monthRef.with(TemporalAdjusters.lastDayOfMonth()).atTime(LocalTime.MAX);

            long seconds = sessionRepository.sumDurationSecondsByProfileIdAndPeriod(profileId, start, end);

            String label = monthRef.getMonth().getDisplayName(TextStyle.SHORT, PT_BR)
                    .replace(".", "").toUpperCase();

            distribution.put(label, seconds / 3600.0);
        }
        return distribution;
    }

    /**
     * Distribuição das últimas 8 semanas.
     */
    private Map<String, Double> calculateEightWeeksDistribution(Long profileId) {
        Map<String, Double> distribution = new LinkedHashMap<>();
        LocalDate now = LocalDate.now();

        for (int i = 7; i >= 0; i--) {
            LocalDate start = now.minusWeeks(i).with(java.time.DayOfWeek.MONDAY);
            LocalDate end = start.plusDays(6);

            long seconds = sessionRepository.sumDurationSecondsByProfileIdAndPeriod(
                    profileId, start.atStartOfDay(), end.atTime(LocalTime.MAX));

            String label = String.format("%s - %s", start.format(DATE_FORMATTER), end.format(DATE_FORMATTER));
            distribution.put(label, seconds / 3600.0);
        }
        return distribution;
    }

    /**
     * Distribuição dos últimos 7 dias.
     */
    private Map<String, Double> calculateRollingDailyDistribution(Long profileId) {
        Map<String, Double> distribution = new LinkedHashMap<>();
        LocalDate now = LocalDate.now();

        for (int i = 6; i >= 0; i--) {
            LocalDate date = now.minusDays(i);
            long seconds = sessionRepository.sumDurationSecondsByProfileIdAndPeriod(
                    profileId, date.atStartOfDay(), date.atTime(LocalTime.MAX));

            // Ajustado para MAIÚSCULAS para combinar com os outros gráficos
            String label = date.getDayOfWeek()
                    .getDisplayName(TextStyle.SHORT, PT_BR)
                    .replace(".", "").toUpperCase();

            distribution.put(label, seconds / 3600.0);
        }
        return distribution;
    }

    private int calculateCurrentStreak(Long profileId) {
        int streak = 0;
        LocalDate dateToCheck = LocalDate.now();

        if (!hasFocusOnDate(profileId, dateToCheck)) {
            dateToCheck = dateToCheck.minusDays(1);
            if (!hasFocusOnDate(profileId, dateToCheck))
                return 0;
        }

        while (hasFocusOnDate(profileId, dateToCheck)) {
            streak++;
            dateToCheck = dateToCheck.minusDays(1);
        }
        return streak;
    }

    private boolean hasFocusOnDate(Long profileId, LocalDate date) {
        return sessionRepository.sumDurationSecondsByProfileIdAndPeriod(
                profileId, date.atStartOfDay(), date.atTime(LocalTime.MAX)) > 0;
    }

    private void checkAndUpdateRecords(Profile profile, int secondsToday, int currentStreak) {
        boolean updated = false;

        if (secondsToday > profile.getMaxFocusDaySeconds()) {
            profile.setMaxFocusDaySeconds(secondsToday);
            updated = true;
        }

        if (currentStreak > profile.getMaxStreak()) {
            profile.setMaxStreak(currentStreak);
            updated = true;
        }

        if (updated) {
            profileRepository.updateStats(profile.getId(), profile.getMaxStreak(),
                    profile.getMaxFocusDaySeconds(), profile.getTotalFocusSessions());
        }
    }

    private String formatDuration(long totalSeconds) {
        return String.format("%02dh %02dm", totalSeconds / 3600, (totalSeconds % 3600) / 60);
    }
}