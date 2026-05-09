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
}