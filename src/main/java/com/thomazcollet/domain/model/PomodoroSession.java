package com.thomazcollet.domain.model;

import java.time.LocalDateTime;

/**
 * Representa uma sessão concluída de Pomodoro.
 * Definida como record para garantir imutabilidade e concisão.
 */
public record PomodoroSession(
    LocalDateTime startTime,
    int durationMinutes,
    SessionType type // Usando o Enum que sugerimos
) {
    // Você pode adicionar métodos lógicos aqui, se precisar
    public boolean isLongSession() {
        return durationMinutes >= 25;
    }
}