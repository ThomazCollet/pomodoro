package com.thomazcollet.domain.dto;

import java.util.Map;

/**
 * DTO que consolida todos os dados necessários para a aba de Estatísticas.
 * Centraliza o que será exibido nos cards, no heatmap e nos gráficos.
 */
public record FocusStatistics(
    // Cards de Resumo
    int currentStreak,
    int maxStreak,
    String timeToday,        // Ex: "04h 20m"
    String recordDayTime,    // Ex: "Recorde: 08h 15m"
    String timeThisWeek,     // Ex: "28h 45m"
    
    // Gráfico de Barras (Distribuição de Tempo)
    // Chave: Dia da semana ("Seg", "Ter"), Valor: Horas focaodas
    Map<String, Double> weeklyDistribution,
    
    // Heatmap (Atividade Anual)
    // Chave: Data ISO ("2026-05-08"), Valor: Nível de intensidade (0 a 4 ou segundos)
    Map<String, Integer> annualHeatmap
) {}