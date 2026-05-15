package com.thomazcollet.domain.model;

import java.time.LocalDate;

public class Challenge {
    private Long id;
    private Long profileId;
    private String title;
    private int durationDays;
    private int minFocusMinutesPerDay;
    private int livesTotal;
    private int livesRemaining;
    private ChallengeStatus status;
    private LocalDate startDate;
    private int progressDays;
    private ChallengeType type;
    private int targetTotalMinutes;
    private int accumulatedMinutes;
    private int todayFocusMinutes;

    // Getters e Setters (Mantidos como os seus)
    // ... [Seus getters e setters aqui] ...

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProfileId() {
        return profileId;
    }

    public void setProfileId(Long profileId) {
        this.profileId = profileId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getDurationDays() {
        return durationDays;
    }

    public void setDurationDays(int durationDays) {
        this.durationDays = durationDays;
    }

    public int getMinFocusMinutesPerDay() {
        return minFocusMinutesPerDay;
    }

    public void setMinFocusMinutesPerDay(int minFocusMinutesPerDay) {
        this.minFocusMinutesPerDay = minFocusMinutesPerDay;
    }

    public int getLivesTotal() {
        return livesTotal;
    }

    public void setLivesTotal(int livesTotal) {
        this.livesTotal = livesTotal;
    }

    public int getLivesRemaining() {
        return livesRemaining;
    }

    public void setLivesRemaining(int livesRemaining) {
        this.livesRemaining = livesRemaining;
    }

    public ChallengeStatus getStatus() {
        return status;
    }

    public void setStatus(ChallengeStatus status) {
        this.status = status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public int getProgressDays() {
        return progressDays;
    }

    public void setProgressDays(int progressDays) {
        this.progressDays = progressDays;
    }

    public ChallengeType getType() {
        return type;
    }

    public void setType(ChallengeType type) {
        this.type = type;
    }

    public int getTargetTotalMinutes() {
        return targetTotalMinutes;
    }

    public void setTargetTotalMinutes(int targetTotalMinutes) {
        this.targetTotalMinutes = targetTotalMinutes;
    }

    public int getAccumulatedMinutes() {
        return accumulatedMinutes;
    }

    public void setAccumulatedMinutes(int accumulatedMinutes) {
        this.accumulatedMinutes = accumulatedMinutes;
    }

    public int getTodayFocusMinutes() {
        return todayFocusMinutes;
    }

    public void setTodayFocusMinutes(int todayFocusMinutes) {
        this.todayFocusMinutes = todayFocusMinutes;
    }

    // --- MÉTODOS UTILITÁRIOS REFORMULADOS ---

    /**
     * Retorna o progresso baseado no tipo de desafio.
     * Para STREAK: baseado em dias concluídos.
     * Para MILESTONE: baseado em minutos acumulados vs alvo.
     */
    public double getProgressPercentage() {
        if (type == ChallengeType.MILESTONE_CHALLENGE) {
            if (targetTotalMinutes <= 0)
                return 0.0;
            return Math.min(1.0, (double) accumulatedMinutes / targetTotalMinutes);
        }
        // Padrão STREAK
        if (durationDays <= 0)
            return 0.0;
        return Math.min(1.0, (double) progressDays / durationDays);
    }

    public boolean isDailyGoalMet() {
        return todayFocusMinutes >= minFocusMinutesPerDay;
    }
}