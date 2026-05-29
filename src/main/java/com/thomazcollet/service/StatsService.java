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
    private final StreakRecordRepository streakRepository; // Adicionado

    public StatsService(FocusSessionRepository sessionRepository,
            ProfileRepository profileRepository,
            StreakRecordRepository streakRepository) {
        this.sessionRepository = sessionRepository;
        this.profileRepository = profileRepository;
        this.streakRepository = streakRepository;
    }

    public FocusStatistics getUserStatistics(Profile profile) {
        Objects.requireNonNull(profile, "O perfil não pode ser nulo.");

        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startOfDay = now.with(LocalTime.MIN);
            LocalDateTime endOfDay = now.with(LocalTime.MAX);

            LocalDateTime startOfWeek = now.toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                    .atStartOfDay();

            long secondsToday = sessionRepository.sumDurationSecondsByProfileIdAndPeriod(profile.getId(), startOfDay,
                    endOfDay);
            long secondsThisWeek = sessionRepository.sumDurationSecondsByProfileIdAndPeriod(profile.getId(),
                    startOfWeek, endOfDay);

            int currentStreak = calculateAndProcessCurrentStreak(profile.getId());
            checkAndUpdateRecords(profile, (int) secondsToday);

            // Melhor streak histórica: topo do pódio de streak_records
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

    // --- MÉTODOS DOS PÓDIOS ---

    private List<String> buildDailyPodium(Long profileId) {
        List<FocusSessionRepository.DailyPodiumEntry> entries = sessionRepository.getTop3DailyFocusRecords(profileId);
        List<String> podium = new ArrayList<>();

        for (FocusSessionRepository.DailyPodiumEntry entry : entries) {
            LocalDate date = LocalDate.parse(entry.date());

            // Cria formato "14/Mai/2026"
            String monthName = date.getMonth().getDisplayName(TextStyle.SHORT, PT_BR).replace(".", "");
            String formattedDate = String.format("%02d/%s/%d", date.getDayOfMonth(), capitalize(monthName),
                    date.getYear());

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
        List<StreakRecord> topStreaks = streakRepository.getTopStreaks(profileId, 3); // Top 3 para UI
        List<String> podium = new ArrayList<>();

        for (StreakRecord record : topStreaks) {
            String start = record.startDate().format(DATE_FORMATTER);
            String end = record.endDate().format(DATE_FORMATTER);
            String daysText = record.durationDays() == 1 ? " dia" : " dias";

            // Ex: "15 dias — 01/05 a 15/05"
            podium.add(String.format("%02d%s — %s a %s", record.durationDays(), daysText, start, end));
        }
        return podium;
    }

    // --- LÓGICA DE STREAK E PERSISTÊNCIA ---
    private int calculateAndProcessCurrentStreak(Long profileId) {
        LocalDate today = LocalDate.now();
        LocalDate dateToCheck = today;

        boolean focusedToday = hasFocusOnDate(profileId, today);

        // Se não focou hoje E também não focou ontem, a streak atual é zero e nenhuma
        // quebrou agora
        if (!focusedToday && !hasFocusOnDate(profileId, today.minusDays(1))) {
            return 0;
        }

        // Se não focou hoje, mas focou ontem, a streak quebrou hoje. Vamos contar a
        // partir de ontem.
        if (!focusedToday) {
            dateToCheck = today.minusDays(1);
        }

        int streak = 0;
        LocalDate endDate = dateToCheck; // O dia mais recente da sequência
        LocalDate startDate = dateToCheck;

        // Varre para trás contando os dias consecutivos de foco
        while (hasFocusOnDate(profileId, dateToCheck)) {
            streak++;
            startDate = dateToCheck; // Guarda o dia mais antigo até aqui
            dateToCheck = dateToCheck.minusDays(1); // Decremento correto para evitar loop!
        }

        // Se a streak quebrou hoje (!focusedToday), processamos e persistimos o pódio
        // se ela merecer vaga
        if (!focusedToday && streak > 0) {
            processFinalizedStreakRanking(profileId, streak, startDate, endDate);
            return 0; // Retorna 0 para a UI porque hoje a streak está zerada
        }

        // Se ele focou hoje, retorna o tamanho atual da sequência ativa
        return streak;
    }

    private void processFinalizedStreakRanking(Long profileId, int finishedStreak, LocalDate startDate,
            LocalDate endDate) {
        int totalRecords = streakRepository.countRecords(profileId);

        // Se a tabela tem posições vazias (menos de 5), salva direto
        if (totalRecords < 5) {
            streakRepository.save(new StreakRecord(null, profileId, finishedStreak, startDate, endDate));
        } else {
            // Se está cheia, comparamos com a pior sequência do pódio de recordes
            StreakRecord minRecord = streakRepository.getMinStreak(profileId);
            if (minRecord != null && finishedStreak > minRecord.durationDays()) {
                // Remove o pior registro antigo...
                streakRepository.delete(minRecord.id());
                // ...e dá a vaga para o novo recorde!
                streakRepository.save(new StreakRecord(null, profileId, finishedStreak, startDate, endDate));
            }
        }
    }

    // --- MÉTODOS AUXILIARES ---

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

    // --- GRÁFICOS (INTACTOS) ---

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
            String label = String.format("%s - %s", start.format(DATE_FORMATTER), end.format(DATE_FORMATTER));
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
            String label = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, PT_BR).replace(".", "").toUpperCase();
            distribution.put(label, seconds / 3600.0);
        }
        return distribution;
    }
}