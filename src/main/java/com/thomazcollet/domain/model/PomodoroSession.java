package com.thomazcollet.domain.model;

import java.time.LocalDateTime;

public class PomodoroSession {
    private LocalDateTime startTime;
    private int durationMinutes;
    private String type; // "FOCUS" ou "BREAK"

    public PomodoroSession(LocalDateTime startTime, int durationMinutes, String type) {
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
        this.type = type;
    }

    // Getters
    public LocalDateTime getStartTime() { return startTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public String getType() { return type; }
}