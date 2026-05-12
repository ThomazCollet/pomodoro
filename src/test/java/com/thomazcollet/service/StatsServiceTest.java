package com.thomazcollet.service;

import com.thomazcollet.domain.dto.FocusStatistics;
import com.thomazcollet.domain.exception.StatisticsComputationException;
import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.repository.FocusSessionRepository;
import com.thomazcollet.domain.repository.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.AdditionalMatchers.gt;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

class StatsServiceTest {

    private StatsService statsService;

    @Mock
    private FocusSessionRepository sessionRepository;

    @Mock
    private ProfileRepository profileRepository;

    private Profile testProfile;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        statsService = new StatsService(sessionRepository, profileRepository);

        testProfile = new Profile();
        testProfile.setId(1L);
        testProfile.setUsername("Thomaz");
        testProfile.setMaxStreak(5);
        testProfile.setMaxFocusDaySeconds(7200); // 2h
    }

    @Test
    @DisplayName("Deve calcular estatísticas corretamente e formatar durações")
    void shouldCalculateStatisticsAndFormatDurations() {
        // Simula: 1h hoje, 5h na semana, e depois 0 para os cálculos de streak não
        // entrarem em loop
        when(sessionRepository.sumDurationSecondsByProfileIdAndPeriod(anyLong(), any(), any()))
                .thenReturn(3600L) // Hoje
                .thenReturn(18000L) // Semana
                .thenReturn(0L); // Ontem (quebra o streak em 1 para o teste terminar rápido)

        FocusStatistics stats = statsService.getUserStatistics(testProfile);

        assertEquals("01h 00m", stats.timeToday());
        assertEquals("05h 00m", stats.timeThisWeek());
    }

    @Test
    @DisplayName("Deve atualizar recorde de streak quando o atual for maior que o máximo")
    void shouldUpdateMaxStreakWhenCurrentIsHigher() {
        // GIVEN:
        // Usamos o stubbing consecutivo para garantir que:
        // 1. As chamadas iniciais (hoje/semana) retornem valor.
        // 2. As chamadas do loop de streak retornem valor por 10 vezes.
        // 3. Eventualmente retorne 0 para quebrar o loop.
        when(sessionRepository.sumDurationSecondsByProfileIdAndPeriod(anyLong(), any(), any()))
                .thenReturn(3600L, 3600L, 3600L, 3600L, 3600L, 3600L, 3600L, 3600L, 3600L, 3600L, 0L);

        // O recorde atual no testProfile é 5 (definido no setUp)
        // Então um streak de 10 chamadas certamente deve disparar o update.

        // WHEN
        statsService.getUserStatistics(testProfile);

        // THEN
        // Verificamos se o updateStats foi chamado com um valor > 5
        verify(profileRepository, atLeastOnce()).updateStats(
                eq(testProfile.getId()),
                gt(5),
                anyInt(),
                anyInt());
    }

    @Test
    @DisplayName("Deve incluir dados do heatmap no DTO de estatísticas")
    void shouldIncludeHeatmapDataInStatistics() {
        // GIVEN
        LocalDate today = LocalDate.now();
        Map<LocalDate, Long> mockHeatmap = Map.of(
                today, 3600L,
                today.minusDays(1), 7200L);

        when(sessionRepository.getDailyFocusTime(anyLong(), any())).thenReturn(mockHeatmap);
        when(sessionRepository.sumDurationSecondsByProfileIdAndPeriod(anyLong(), any(), any())).thenReturn(0L);

        // WHEN
        FocusStatistics stats = statsService.getUserStatistics(testProfile);

        // THEN
        assertNotNull(stats.annualHeatmap());
        assertEquals(2, stats.annualHeatmap().size());
        assertEquals(3600L, stats.annualHeatmap().get(today));
    }

    @Test
    @DisplayName("Deve lançar StatisticsComputationException quando ocorrer erro no repositório")
    void shouldThrowExceptionWhenRepositoryFails() {
        // GIVEN
        when(sessionRepository.sumDurationSecondsByProfileIdAndPeriod(anyLong(), any(), any()))
                .thenThrow(new RuntimeException("Erro de conexão com o banco"));

        // WHEN & THEN
        assertThrows(StatisticsComputationException.class, () -> {
            statsService.getUserStatistics(testProfile);
        });
    }

    @Test
    @DisplayName("Deve atualizar recorde de foco diário quando o tempo de hoje for superior")
    void shouldUpdateMaxFocusDayWhenTodayIsHigher() {
        // GIVEN
        // Perfil tem recorde de 2h (7200s). Vamos simular que hoje ele fez 3h (10800s).
        when(sessionRepository.sumDurationSecondsByProfileIdAndPeriod(anyLong(), any(), any()))
                .thenReturn(10800L) // Hoje (3h)
                .thenReturn(10800L) // Semana
                .thenReturn(0L); // Quebra streak

        // WHEN
        statsService.getUserStatistics(testProfile);

        // THEN
        // Verificamos se o updateStats foi chamado com o novo tempo recorde (10800)
        verify(profileRepository).updateStats(
                eq(testProfile.getId()),
                anyInt(),
                eq(10800), // Novo recorde diário
                anyInt());
    }

    @Test
    @DisplayName("Deve gerar distribuição diária com exatamente 7 dias e labels formatadas")
    void shouldGenerateDailyDistributionWithSevenDays() {
        // GIVEN
        when(sessionRepository.sumDurationSecondsByProfileIdAndPeriod(anyLong(), any(), any()))
                .thenReturn(3600L); // 1 hora para cada dia

        // WHEN
        Map<String, Double> daily = statsService.calculateRollingDailyDistribution(testProfile.getId());

        // THEN
        assertNotNull(daily);
        assertEquals(7, daily.size(), "A distribuição diária deve conter 7 dias");

        // Verifica se as horas foram convertidas corretamente (3600s = 1.0h)
        daily.values().forEach(value -> assertEquals(1.0, value));

        // Verifica se as labels não contêm pontos (ex: "seg" em vez de "seg.")
        daily.keySet().forEach(label -> assertFalse(label.contains("."), "Label não deve conter pontos"));
    }

    @Test
    @DisplayName("Deve gerar distribuição semanal com 8 semanas e formato dd/MM")
    void shouldGenerateWeeklyDistributionWithEightWeeks() {
        // GIVEN
        when(sessionRepository.sumDurationSecondsByProfileIdAndPeriod(anyLong(), any(), any()))
                .thenReturn(7200L); // 2 horas por semana

        // WHEN
        Map<String, Double> weekly = statsService.calculateEightWeeksDistribution(testProfile.getId());

        // THEN
        assertNotNull(weekly);
        assertEquals(8, weekly.size(), "A distribuição deve conter 8 semanas");

        // Verifica o formato da label (Ex: 12/05) usando Regex
        String firstLabel = weekly.keySet().iterator().next();
        assertTrue(firstLabel.matches("\\d{2}/\\d{2}"), "Label deve estar no formato dd/MM");

        // Verifica conversão para horas (7200s = 2.0h)
        assertEquals(2.0, weekly.get(firstLabel));
    }

    @Test
    @DisplayName("Deve retornar estatísticas completas incluindo novos gráficos no DTO")
    void shouldReturnCompleteStatisticsWithCharts() {
        // GIVEN
        when(sessionRepository.sumDurationSecondsByProfileIdAndPeriod(anyLong(), any(), any()))
                .thenReturn(0L);
        when(sessionRepository.getDailyFocusTime(anyLong(), any()))
                .thenReturn(Collections.emptyMap());

        // WHEN
        FocusStatistics stats = statsService.getUserStatistics(testProfile);

        // THEN
        assertNotNull(stats.dailyDistribution(), "Distribuição diária não deve ser nula");
        assertNotNull(stats.weeklyDistribution(), "Distribuição semanal não deve ser nula");
        assertEquals(7, stats.dailyDistribution().size());
        assertEquals(8, stats.weeklyDistribution().size());
    }
}