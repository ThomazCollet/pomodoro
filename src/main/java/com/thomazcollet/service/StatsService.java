package com.thomazcollet.service;

import com.thomazcollet.domain.dto.FocusStatistics;
import com.thomazcollet.domain.dto.StreakRecord;
import com.thomazcollet.domain.exception.StatisticsComputationException;
import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.repository.FocusSessionRepository;
import com.thomazcollet.domain.repository.ProfileRepository;
import com.thomazcollet.domain.repository.StreakRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
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
    private static final DateTimeFormatter MONTH_YEAR_FORMATTER = DateTimeFormatter.ofPattern("MMM/yyyy", PT_BR);

    private final FocusSessionRepository sessionRepository;
    private final ProfileRepository profileRepository;
    private final StreakRecordRepository streakRepository;
    private final NotificationService notificationService; // nullable — retrocompatível

    // -----------------------------------------------------------------------
    // CONSTRUTORES
    // -----------------------------------------------------------------------

    public StatsService(FocusSessionRepository sessionRepository,
            ProfileRepository profileRepository,
            StreakRecordRepository streakRepository) {
        this(sessionRepository, profileRepository, streakRepository, null);
    }

    public StatsService(FocusSessionRepository sessionRepository,
            ProfileRepository profileRepository,
            StreakRecordRepository streakRepository,
            NotificationService notificationService) {
        this.sessionRepository = sessionRepository;
        this.profileRepository = profileRepository;
        this.streakRepository = streakRepository;
        this.notificationService = notificationService; // nullable
    }

    // ==========================================================================
    // CÁLCULO PRINCIPAL
    // ==========================================================================

    public FocusStatistics getUserStatistics(Profile profile) {
        Objects.requireNonNull(profile, "O perfil não pode ser nulo.");

        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startOfDay = now.with(LocalTime.MIN);
            LocalDateTime endOfDay = now.with(LocalTime.MAX);
            LocalDateTime startOfWeek = now.toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                    .atStartOfDay();
            LocalDateTime startOfMonth = now.toLocalDate()
                    .with(TemporalAdjusters.firstDayOfMonth())
                    .atStartOfDay();

            long secondsToday = sessionRepository.sumDurationSecondsByProfileIdAndPeriod(
                    profile.getId(), startOfDay, endOfDay);
            long secondsThisWeek = sessionRepository.sumDurationSecondsByProfileIdAndPeriod(
                    profile.getId(), startOfWeek, endOfDay);
            long secondsThisMonth = sessionRepository.sumDurationSecondsByProfileIdAndPeriod(
                    profile.getId(), startOfMonth, endOfDay);

            int currentStreak = calculateAndProcessCurrentStreak(profile.getId());
            checkAndUpdateRecords(profile, (int) secondsToday);

            // Verificação de metas — dispara notificações se alguma for atingida
            checkAndNotifyGoals(profile, secondsToday, secondsThisWeek, secondsThisMonth);

            int bestStreakDays = streakRepository.getTopStreaks(profile.getId(), 1)
                    .stream()
                    .mapToInt(r -> r.durationDays())
                    .findFirst()
                    .orElse(currentStreak);

            LocalDateTime oneYearAgo = now.minusYears(1).with(LocalTime.MIN);
            Map<LocalDate, Long> heatmapData = sessionRepository.getDailyFocusTime(profile.getId(), oneYearAgo);

            return new FocusStatistics(
                    currentStreak,
                    bestStreakDays,
                    formatDuration(secondsToday),
                    "Recorde: " + formatDuration(profile.getMaxFocusDaySeconds()),
                    formatDuration(secondsThisWeek),
                    heatmapData,
                    calculateRollingDailyDistribution(profile.getId()),
                    calculateEightWeeksDistribution(profile.getId()),
                    calculateFixedAnnualDistribution(profile.getId()),
                    buildDailyPodium(profile.getId()),
                    buildMonthlyPodium(profile.getId()),
                    buildStreakPodium(profile.getId()));

        } catch (Exception e) {
            logger.error("Erro ao processar métricas de foco para o perfil: {}", profile.getId(), e);
            throw new StatisticsComputationException("Falha na consolidação das estatísticas.", e);
        }
    }

    // ==========================================================================
    // NOTIFICAÇÕES DE METAS
    // ==========================================================================

    /**
     * Verifica se o usuário atingiu a meta diária, semanal ou mensal neste ciclo
     * de cálculo e dispara notificação via
     * {@link NotificationService#sendGoalNotification}
     * (que já possui proteção anti-spam por dia).
     */
    private void checkAndNotifyGoals(Profile profile,
            long secondsToday,
            long secondsThisWeek,
            long secondsThisMonth) {
        if (notificationService == null)
            return;

        int profileId = profile.getId().intValue();

        // Meta diária
        if (profile.getDailyGoalSeconds() > 0
                && secondsToday >= profile.getDailyGoalSeconds()) {
            notificationService.sendGoalNotification(
                    profileId,
                    "daily",
                    "☀️ Meta Diária Batida!",
                    "Você atingiu sua meta de foco do dia ("
                            + formatDuration(profile.getDailyGoalSeconds()) + "). "
                            + "Dia bem aproveitado! 🎯");
        }

        // Meta semanal
        if (profile.getWeeklyGoalSeconds() > 0
                && secondsThisWeek >= profile.getWeeklyGoalSeconds()) {
            notificationService.sendGoalNotification(
                    profileId,
                    "weekly",
                    "📅 Meta Semanal Batida!",
                    "Semana incrível! Você superou sua meta de foco semanal ("
                            + formatDuration(profile.getWeeklyGoalSeconds()) + "). "
                            + "Continue nesse ritmo! 💪");
        }

        // Meta mensal
        if (profile.getMonthlyGoalSeconds() > 0
                && secondsThisMonth >= profile.getMonthlyGoalSeconds()) {
            notificationService.sendGoalNotification(
                    profileId,
                    "monthly",
                    "🗓️ Meta Mensal Batida!",
                    "Mês extraordinário! Você completou sua meta de foco mensal ("
                            + formatDuration(profile.getMonthlyGoalSeconds()) + "). "
                            + "Você é imparável! 🚀");
        }
    }

    // ==========================================================================
    // PÓDIOS
    // ==========================================================================

    private List<String> buildDailyPodium(Long profileId) {
        List<FocusSessionRepository.DailyPodiumEntry> entries = sessionRepository.getTop3DailyFocusRecords(profileId);
        List<String> podium = new ArrayList<>();

        for (FocusSessionRepository.DailyPodiumEntry entry : entries) {
            LocalDate date = LocalDate.parse(entry.date());
            String monthName = date.getMonth().getDisplayName(TextStyle.SHORT, PT_BR).replace(".", "");
            String formattedDate = String.format("%02d/%s/%d",
                    date.getDayOfMonth(), capitalize(monthName), date.getYear());
            podium.add(formatDuration(entry.durationSeconds()) + " — " + formattedDate);
        }
        return podium;
    }

    private List<String> buildMonthlyPodium(Long profileId) {
        List<FocusSessionRepository.MonthlyPodiumEntry> entries = sessionRepository
                .getTop3MonthlyFocusRecords(profileId);
        List<String> podium = new ArrayList<>();

        for (FocusSessionRepository.MonthlyPodiumEntry entry : entries) {
            YearMonth ym = YearMonth.parse(entry.monthYear());
            String formattedMonth = ym.format(MONTH_YEAR_FORMATTER).toUpperCase().replace(".", "");
            podium.add(formatDuration(entry.durationSeconds()) + " — " + formattedMonth);
        }
        return podium;
    }

    private List<String> buildStreakPodium(Long profileId) {
        List<StreakRecord> topStreaks = streakRepository.getTopStreaks(profileId, 3);
        List<String> podium = new ArrayList<>();

        for (StreakRecord record : topStreaks) {
            String start = record.startDate().format(DATE_FORMATTER);
            String end = record.endDate().format(DATE_FORMATTER);
            String daysText = record.durationDays() == 1 ? " dia" : " dias";
            podium.add(String.format("%02d%s — %s a %s", record.durationDays(), daysText, start, end));
        }
        return podium;
    }

    // ==========================================================================
    // STREAK
    // ==========================================================================

    private int calculateAndProcessCurrentStreak(Long profileId) {
        LocalDate today = LocalDate.now();
        LocalDate dateToCheck = today;

        boolean focusedToday = hasFocusOnDate(profileId, today);

        if (!focusedToday && !hasFocusOnDate(profileId, today.minusDays(1))) {
            return 0;
        }

        if (!focusedToday) {
            dateToCheck = today.minusDays(1);
        }

        int streak = 0;
        LocalDate endDate = dateToCheck;
        LocalDate startDate = dateToCheck;

        while (hasFocusOnDate(profileId, dateToCheck)) {
            streak++;
            startDate = dateToCheck;
            dateToCheck = dateToCheck.minusDays(1);
        }

        if (!focusedToday && streak > 0) {
            processFinalizedStreakRanking(profileId, streak, startDate, endDate);
            return 0;
        }

        return streak;
    }

    private void processFinalizedStreakRanking(Long profileId, int finishedStreak,
            LocalDate startDate, LocalDate endDate) {
        int totalRecords = streakRepository.countRecords(profileId);

        if (totalRecords < 5) {
            streakRepository.save(new StreakRecord(null, profileId, finishedStreak, startDate, endDate));
        } else {
            StreakRecord minRecord = streakRepository.getMinStreak(profileId);
            if (minRecord != null && finishedStreak > minRecord.durationDays()) {
                streakRepository.delete(minRecord.id());
                streakRepository.save(new StreakRecord(null, profileId, finishedStreak, startDate, endDate));
            }
        }
    }

    // ==========================================================================
    // AUXILIARES
    // ==========================================================================

    private boolean hasFocusOnDate(Long profileId, LocalDate date) {
        return sessionRepository.sumDurationSecondsByProfileIdAndPeriod(
                profileId, date.atStartOfDay(), date.atTime(LocalTime.MAX)) > 0;
    }

    private void checkAndUpdateRecords(Profile profile, int secondsToday) {
        if (secondsToday > profile.getMaxFocusDaySeconds()) {
            profile.setMaxFocusDaySeconds(secondsToday);
            profileRepository.updateStats(profile.getId(),
                    profile.getMaxFocusDaySeconds(), profile.getTotalFocusSessions());
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty())
            return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    private String formatDuration(long totalSeconds) {
        return String.format("%02dh %02dm", totalSeconds / 3600, (totalSeconds % 3600) / 60);
    }

    // ==========================================================================
    // GRÁFICOS
    // ==========================================================================

    private Map<String, Double> calculateFixedAnnualDistribution(Long profileId) {
        Map<String, Double> distribution = new LinkedHashMap<>();
        int currentYear = LocalDate.now().getYear();

        for (int month = 1; month <= 12; month++) {
            LocalDate monthRef = LocalDate.of(currentYear, month, 1);
            LocalDateTime start = monthRef.atStartOfDay();
            LocalDateTime end = monthRef.with(TemporalAdjusters.lastDayOfMonth()).atTime(LocalTime.MAX);
            long seconds = sessionRepository.sumDurationSecondsByProfileIdAndPeriod(profileId, start, end);
            String label = monthRef.getMonth().getDisplayName(TextStyle.SHORT, PT_BR).replace(".", "").toUpperCase();
            distribution.put(label, seconds / 3600.0);
        }
        return distribution;
    }

    private Map<String, Double> calculateEightWeeksDistribution(Long profileId) {
        Map<String, Double> distribution = new LinkedHashMap<>();
        LocalDate now = LocalDate.now();

        for (int i = 7; i >= 0; i--) {
            LocalDate start = now.minusWeeks(i).with(java.time.DayOfWeek.MONDAY);
            LocalDate end = start.plusDays(6);
            long seconds = sessionRepository.sumDurationSecondsByProfileIdAndPeriod(
                    profileId, start.atStartOfDay(), end.atTime(LocalTime.MAX));
            String label = String.format("%s - %s",
                    start.format(DATE_FORMATTER), end.format(DATE_FORMATTER));
            distribution.put(label, seconds / 3600.0);
        }
        return distribution;
    }

    private Map<String, Double> calculateRollingDailyDistribution(Long profileId) {
        Map<String, Double> distribution = new LinkedHashMap<>();
        LocalDate now = LocalDate.now();

        for (int i = 6; i >= 0; i--) {
            LocalDate date = now.minusDays(i);
            long seconds = sessionRepository.sumDurationSecondsByProfileIdAndPeriod(
                    profileId, date.atStartOfDay(), date.atTime(LocalTime.MAX));
            String label = date.getDayOfWeek()
                    .getDisplayName(TextStyle.SHORT, PT_BR).replace(".", "").toUpperCase();
            distribution.put(label, seconds / 3600.0);
        }
        return distribution;
    }
}