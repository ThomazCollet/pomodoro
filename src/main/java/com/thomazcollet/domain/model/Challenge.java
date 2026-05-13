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

    // Getters e Setters
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

    // Método utilitário para a UI
    public double getProgressPercentage() {
        return (double) progressDays / durationDays;
    }
}