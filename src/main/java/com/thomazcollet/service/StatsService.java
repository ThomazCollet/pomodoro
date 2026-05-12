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
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

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
     *
     * @throws StatisticsComputationException se ocorrer erro no cálculo ou acesso
     *                                        aos dados.
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

            // 1. Cálculos de Tempo (Cards)
            long secondsToday = sessionRepository.sumDurationSecondsByProfileIdAndPeriod(profile.getId(), startOfDay,
                    endOfDay);
            long secondsThisWeek = sessionRepository.sumDurationSecondsByProfileIdAndPeriod(profile.getId(),
                    startOfWeek, endOfDay);

            // 2. Cálculo de Streak Atual
            int currentStreak = calculateCurrentStreak(profile.getId());

            // 3. Verificação de Recordes
            checkAndUpdateRecords(profile, (int) secondsToday, currentStreak);

            // 4. Dados para o Heatmap (Último ano)
            LocalDateTime oneYearAgo = now.minusYears(1).with(LocalTime.MIN);
            Map<LocalDate, Long> heatmapData = sessionRepository.getDailyFocusTime(profile.getId(), oneYearAgo);

            // 5. Distribuição para Gráficos
            Map<String, Double> dailyData = calculateRollingDailyDistribution(profile.getId());
            Map<String, Double> weeklyData = calculateEightWeeksDistribution(profile.getId());
            // Por enquanto, passamos um mapa vazio para o mensal até implementarmos a
            // lógica anual
            Map<String, Double> monthlyData = Collections.emptyMap();

            // 6. Mapeamento para DTO (Agora com os 9 parâmetros corretos)
            return new FocusStatistics(
                    currentStreak,
                    profile.getMaxStreak(),
                    formatDuration(secondsToday),
                    "Recorde: " + formatDuration(profile.getMaxFocusDaySeconds()),
                    formatDuration(secondsThisWeek),
                    heatmapData,
                    dailyData, // Adicionado: dailyDistribution
                    weeklyData, // Adicionado: weeklyDistribution
                    monthlyData // Adicionado: monthlyDistribution
            );
        } catch (Exception e) {
            logger.error("Falha crítica ao computar estatísticas para o perfil ID: {}", profile.getId(), e);
            throw new StatisticsComputationException("Erro ao processar métricas de foco.", e);
        }
    }

    /**
     * Calcula a distribuição de foco das últimas 8 semanas.
     * Retorna um Map onde a chave é o início da semana (dd/MM) e o valor é o total
     * em horas.
     */
    public Map<String, Double> calculateEightWeeksDistribution(Long profileId) {
        Map<String, Double> distribution = new LinkedHashMap<>();
        LocalDate now = LocalDate.now();

        for (int i = 7; i >= 0; i--) {
            LocalDate startOfWeek = now.minusWeeks(i).with(java.time.DayOfWeek.MONDAY);
            LocalDate endOfWeek = startOfWeek.plusDays(6);

            long seconds = sessionRepository.sumDurationSecondsByProfileIdAndPeriod(
                    profileId,
                    startOfWeek.atStartOfDay(),
                    endOfWeek.atTime(LocalTime.MAX));

            String label = String.format("%02d/%02d", startOfWeek.getDayOfMonth(), startOfWeek.getMonthValue());
            distribution.put(label, seconds / 3600.0);
        }
        return distribution;
    }

    /**
     * Calcula a distribuição de foco dos últimos 7 dias (Janela Deslizante).
     * Retorna um Map onde a chave é a inicial do dia da semana e o valor é o total
     * em horas.
     */
    public Map<String, Double> calculateRollingDailyDistribution(Long profileId) {
        Map<String, Double> distribution = new LinkedHashMap<>();
        LocalDate now = LocalDate.now();
        Locale ptBr = new Locale("pt", "BR");

        for (int i = 6; i >= 0; i--) {
            LocalDate date = now.minusDays(i);
            long seconds = sessionRepository.sumDurationSecondsByProfileIdAndPeriod(
                    profileId,
                    date.atStartOfDay(),
                    date.atTime(LocalTime.MAX));

            String dayLabel = date.getDayOfWeek()
                    .getDisplayName(TextStyle.SHORT, ptBr)
                    .replace(".", "")
                    .toLowerCase();

            distribution.put(dayLabel, seconds / 3600.0);
        }
        return distribution;
    }

    private int calculateCurrentStreak(Long profileId) {
        int streak = 0;
        LocalDate dateToCheck = LocalDate.now();

        if (!hasFocusOnDate(profileId, dateToCheck)) {
            dateToCheck = dateToCheck.minusDays(1);
            if (!hasFocusOnDate(profileId, dateToCheck)) {
                return 0;
            }
        }

        streak++;

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