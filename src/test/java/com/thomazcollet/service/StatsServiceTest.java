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
    @DisplayName("Deve gerar distribuição semanal com intervalo de datas profissional")
    void shouldGenerateWeeklyDistributionWithDateIntervals() {
        // GIVEN
        // Retorna 3600L para as primeiras chamadas (hoje, semana e distribuições)
        // E retorna 0L logo em seguida para quebrar o loop do streak e evitar o loop
        // infinito
        when(sessionRepository.sumDurationSecondsByProfileIdAndPeriod(anyLong(), any(), any()))
                .thenReturn(3600L, 3600L, 3600L, 3600L, 0L);

        // WHEN
        Map<String, Double> weekly = statsService.getUserStatistics(testProfile).weeklyDistribution();

        // THEN
        assertNotNull(weekly);
        assertEquals(8, weekly.size());

        String firstLabel = weekly.keySet().iterator().next();
        assertTrue(firstLabel.matches("\\d{2}/\\d{2} - \\d{2}/\\d{2}"),
                "A label semanal deve ser um intervalo de datas profissional: " + firstLabel);
    }

    @Test
    @DisplayName("Deve gerar distribuição anual fixa começando em JANEIRO")
    void shouldGenerateAnnualDistributionWithTwelveMonths() {
        // GIVEN
        // O stubbing precisa cobrir as chamadas dos cards + as distribuições
        when(sessionRepository.sumDurationSecondsByProfileIdAndPeriod(anyLong(), any(), any()))
                .thenReturn(0L);

        // WHEN
        Map<String, Double> monthly = statsService.getUserStatistics(testProfile).monthlyDistribution();

        // THEN
        assertEquals(12, monthly.size(), "Deve conter exatamente 12 meses");

        // VERIFICAÇÃO DE CICLO FIXO:
        String firstMonth = monthly.keySet().iterator().next();
        assertEquals("JAN", firstMonth, "O gráfico anual fixo deve obrigatoriamente começar em JAN");

        // Verifica o padrão visual (ex: MAI)
        assertTrue(firstMonth.matches("[A-Z]{3}"), "A label deve ser MAIÚSCULA e sem ponto: " + firstMonth);
    }

    @Test
    @DisplayName("Deve calcular estatísticas corretamente e formatar durações")
    void shouldCalculateStatisticsAndFormatDurations() {
        // Ajustado para garantir que o Mockito tenha respostas suficientes para todos
        // os métodos internos
        when(sessionRepository.sumDurationSecondsByProfileIdAndPeriod(anyLong(), any(), any()))
                .thenReturn(3600L) // Hoje
                .thenReturn(18000L) // Semana
                .thenReturn(0L); // Quebra streak inicial

        FocusStatistics stats = statsService.getUserStatistics(testProfile);

        assertAll(
                () -> assertEquals("01h 00m", stats.timeToday()),
                () -> assertEquals("05h 00m", stats.timeThisWeek()),
                () -> assertNotNull(stats.monthlyDistribution()),
                () -> assertEquals(12, stats.monthlyDistribution().size()));
    }

    @Test
    @DisplayName("Deve atualizar recorde de foco diário quando o tempo de hoje for superior")
    void shouldUpdateMaxFocusDayWhenTodayIsHigher() {
        when(sessionRepository.sumDurationSecondsByProfileIdAndPeriod(anyLong(), any(), any()))
                .thenReturn(10800L) // Hoje (3h) - Maior que o recorde de 2h
                .thenReturn(10800L) // Semana
                .thenReturn(0L); // Quebra streak

        statsService.getUserStatistics(testProfile);

        verify(profileRepository).updateStats(
                eq(testProfile.getId()),
                anyInt(),
                eq(10800), // Novo recorde diário
                anyInt());
    }

    @Test
    @DisplayName("Deve lançar StatisticsComputationException em caso de falha no repositório")
    void shouldThrowExceptionWhenRepositoryFails() {
        when(sessionRepository.sumDurationSecondsByProfileIdAndPeriod(anyLong(), any(), any()))
                .thenThrow(new RuntimeException("DB Offline"));

        assertThrows(StatisticsComputationException.class, () -> statsService.getUserStatistics(testProfile));
    }

    @Test
    @DisplayName("Deve garantir que labels diárias não possuem pontos decorrentes da formatação")
    void shouldEnsureCleanDailyLabels() {
        Map<String, Double> daily = statsService.getUserStatistics(testProfile).dailyDistribution();

        daily.keySet().forEach(label -> assertFalse(label.contains("."),
                "A label '" + label + "' não deve conter pontos (ex: seg. -> seg)"));
    }
}