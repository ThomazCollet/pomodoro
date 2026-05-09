package com.thomazcollet.domain.dto;

import java.time.LocalDate;
import java.util.Map;

/**
 * DTO que consolida todos os dados necessários para a aba de Estatísticas.
 */
public record FocusStatistics(
    // Cards de Resumo
    int currentStreak,
    int maxStreak,
    String timeToday,        // Ex: "04h 20m"
    String recordDayTime,    // Ex: "Recorde: 08h 15m"
    String timeThisWeek,     // Ex: "28h 45m"
    
    // Heatmap (Atividade Anual)
    // Chave: LocalDate, Valor: Total de segundos (Long)
    Map<LocalDate, Long> annualHeatmap,
    
    // Gráfico de Barras (Distribuição de Tempo) - Implementaremos no passo 3
    Map<String, Double> weeklyDistribution
) {}