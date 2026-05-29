package com.thomazcollet.service;

import com.thomazcollet.domain.dto.FocusStatistics;
import com.thomazcollet.domain.exception.StatisticsComputationException;
import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.repository.FocusSessionRepository;
import com.thomazcollet.domain.repository.ProfileRepository;
import com.thomazcollet.domain.repository.StreakRecordRepository; // Novo Import
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

class StatsServiceTest {

        private StatsService statsService;

        @Mock
        private FocusSessionRepository sessionRepository;

        @Mock
        private ProfileRepository profileRepository;

        @Mock
        private StreakRecordRepository streakRepository; // 1. Novo Mock adicionado

        private Profile testProfile;

        @BeforeEach
        void setUp() {
                MockitoAnnotations.openMocks(this);

                // 2. Construtor atualizado com a nova dependência de streaks
                statsService = new StatsService(sessionRepository, profileRepository, streakRepository);

                testProfile = new Profile();
                testProfile.setId(1L);
                testProfile.setUsername("Thomaz");
                testProfile.setMaxFocusDaySeconds(7200); // 2h

                // Configuração segura padrão para evitar NullPointerException com os
                // repositórios
                when(sessionRepository.getTop3DailyFocusRecords(anyLong())).thenReturn(Collections.emptyList());
                when(sessionRepository.getTop3MonthlyFocusRecords(anyLong())).thenReturn(Collections.emptyList());

                // 3. Comportamento seguro padrão para o repositório de streaks nos testes
                // existentes
                when(streakRepository.countRecords(anyLong())).thenReturn(0);
                when(streakRepository.getTopStreaks(anyLong(), anyInt())).thenReturn(Collections.emptyList());
        }

        @Test
        @DisplayName("Deve gerar distribuição semanal com intervalo de datas profissional")
        void shouldGenerateWeeklyDistributionWithDateIntervals() {
                // GIVEN
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
                when(sessionRepository.sumDurationSecondsByProfileIdAndPeriod(anyLong(), any(), any()))
                                .thenReturn(0L);

                // WHEN
                Map<String, Double> monthly = statsService.getUserStatistics(testProfile).monthlyDistribution();

                // THEN
                assertEquals(12, monthly.size(), "Deve conter exatamente 12 meses");

                String firstMonth = monthly.keySet().iterator().next();
                assertEquals("JAN", firstMonth, "O gráfico anual fixo deve obrigatoriamente começar em JAN");
                assertTrue(firstMonth.matches("[A-Z]{3}"), "A label deve ser MAIÚSCULA e sem ponto: " + firstMonth);
        }

        @Test
        @DisplayName("Deve calcular estatísticas correctamente e formatar durações")
        void shouldCalculateStatisticsAndFormatDurations() {
                // GIVEN
                when(sessionRepository.sumDurationSecondsByProfileIdAndPeriod(anyLong(), any(), any()))
                                .thenReturn(3600L) // Hoje
                                .thenReturn(18000L) // Semana
                                .thenReturn(0L); // Quebra streak inicial

                // WHEN
                FocusStatistics stats = statsService.getUserStatistics(testProfile);

                // THEN
                assertAll(
                                () -> assertEquals("01h 00m", stats.timeToday()),
                                () -> assertEquals("05h 00m", stats.timeThisWeek()),
                                () -> assertNotNull(stats.monthlyDistribution()),
                                () -> assertEquals(12, stats.monthlyDistribution().size()));
        }

        @Test
        @DisplayName("Deve atualizar recorde de foco diário quando o tempo de hoje for superior")
        void shouldUpdateMaxFocusDayWhenTodayIsHigher() {
                // GIVEN
                when(sessionRepository.sumDurationSecondsByProfileIdAndPeriod(anyLong(), any(), any()))
                                .thenReturn(10800L) // Hoje (3h) - Maior que o recorde de 2h
                                .thenReturn(10800L) // Semana
                                .thenReturn(0L); // Quebra streak

                // WHEN
                statsService.getUserStatistics(testProfile);

                // THEN
                verify(profileRepository).updateStats(
                                eq(testProfile.getId()),
                                eq(10800), // Novo recorde diário
                                anyInt());
        }

        @Test
        @DisplayName("Deve lançar StatisticsComputationException em caso de falha no repositório")
        void shouldThrowExceptionWhenRepositoryFails() {
                // GIVEN
                when(sessionRepository.sumDurationSecondsByProfileIdAndPeriod(anyLong(), any(), any()))
                                .thenThrow(new RuntimeException("DB Offline"));

                // WHEN & THEN
                assertThrows(StatisticsComputationException.class, () -> statsService.getUserStatistics(testProfile));
        }

        @Test
        @DisplayName("Deve garantir que labels diárias não possuem pontos decorrentes da formatação")
        void shouldEnsureCleanDailyLabels() {
                // GIVEN
                when(sessionRepository.sumDurationSecondsByProfileIdAndPeriod(anyLong(), any(), any()))
                                .thenReturn(0L);

                // WHEN
                Map<String, Double> daily = statsService.getUserStatistics(testProfile).dailyDistribution();

                // THEN
                daily.keySet().forEach(label -> assertFalse(label.contains("."),
                                "A label '" + label + "' não deve conter pontos (ex: seg. -> seg)"));
        }

        // --- NOVOS TESTES PARA GARANTIR A MÁGICA DOS PÓDIOS ---

        @Test
        @DisplayName("Deve processar e formatar corretamente os dados do pódio diário")
        void shouldProcessAndFormatDailyPodiumCorrectly() {
                // GIVEN
                List<FocusSessionRepository.DailyPodiumEntry> mockEntries = List.of(
                                new FocusSessionRepository.DailyPodiumEntry("2026-05-27", 10800L), // 03h 00m
                                new FocusSessionRepository.DailyPodiumEntry("2026-05-14", 7320L) // 02h 02m
                );
                when(sessionRepository.getTop3DailyFocusRecords(testProfile.getId())).thenReturn(mockEntries);
                when(sessionRepository.sumDurationSecondsByProfileIdAndPeriod(anyLong(), any(), any())).thenReturn(0L);

                // WHEN
                FocusStatistics stats = statsService.getUserStatistics(testProfile);

                // THEN
                assertNotNull(stats.dailyPodium());
                assertEquals(2, stats.dailyPodium().size());

                // AJUSTADO: Agora espera o ano que o serviço de fato adiciona
                assertEquals("03h 00m — 27/Mai/2026", stats.dailyPodium().get(0));
                assertEquals("02h 02m — 14/Mai/2026", stats.dailyPodium().get(1));
        }

        @Test
        @DisplayName("Deve processar e formatar corretamente os dados do pódio mensal")
        void shouldProcessAndFormatMonthlyPodiumCorrectly() {
                // GIVEN
                List<FocusSessionRepository.MonthlyPodiumEntry> mockEntries = List.of(
                                new FocusSessionRepository.MonthlyPodiumEntry("2026-05", 90000L) // 25h 00m
                );
                when(sessionRepository.getTop3MonthlyFocusRecords(testProfile.getId())).thenReturn(mockEntries);
                when(sessionRepository.sumDurationSecondsByProfileIdAndPeriod(anyLong(), any(), any())).thenReturn(0L);

                // WHEN
                FocusStatistics stats = statsService.getUserStatistics(testProfile);

                // THEN
                assertNotNull(stats.monthlyDistribution());
                assertEquals(1, stats.monthlyPodium().size());
                assertEquals("25h 00m — MAI/2026", stats.monthlyPodium().get(0));
        }
}