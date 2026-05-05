package com.thomazcollet.domain.model;

/**
 * Define os tipos de sessão possíveis no ciclo Pomodoro.
 */
public enum SessionType {
    FOCUS("Foco"),
    SHORT_BREAK("Pausa Curta"),
    LONG_BREAK("Pausa Longa");

    private final String description;

    SessionType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}