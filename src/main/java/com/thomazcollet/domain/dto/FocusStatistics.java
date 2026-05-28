package com.thomazcollet.domain.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * DTO que consolida todos os dados necessários para a aba de Estatísticas.
 * Utiliza imutabilidade via Record para garantir integridade nos testes e na
 * UI.
 */
public record FocusStatistics(
        // Cards de Resumo
        int currentStreak,
        int maxStreak,
        String timeToday, // Ex: "04h 20m"
        String recordDayTime, // Ex: "Recorde: 08h 15m"
        String timeThisWeek, // Ex: "28h 45m"

        // Heatmap (Atividade Anual no estilo GitHub)
        Map<LocalDate, Long> annualHeatmap,

        // Gráfico de Barras - Visão Diária (Últimos 7 dias deslizantes)
        Map<String, Double> dailyDistribution,

        // Gráfico de Barras - Visão Semanal (Últimas 8 semanas)
        Map<String, Double> weeklyDistribution,

        // Gráfico de Barras - Visão Mensal (Janeiro a Dezembro)
        Map<String, Double> monthlyDistribution,

        // --- Novo Sistema de Pódios (Substituindo Categorias/Insights) ---
        // Listas ordenadas contendo o Top 3 já formatado para exibição (🥇, 🥈, 🥉)
        List<String> dailyPodium, // Ex: ["08h 15m — 14/Mai", "07h 10m — 22/Abr", ...]
        List<String> monthlyPodium // Ex: ["45h 20m — MAI/2026", "38h 15m — ABR/2026", ...]
) {
}