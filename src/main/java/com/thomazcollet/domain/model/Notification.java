package com.thomazcollet.domain.model;

import java.time.LocalDateTime;

/**
 * Representa uma mensagem de notificação, alerta ou conquista destinada ao
 * usuário.
 */
public class Notification {

    private Integer id;
    private final int profileId;
    private final String title;
    private final String message;
    private boolean isRead;
    private final LocalDateTime createdAt;

    /**
     * Construtor para criar uma nova notificação que ainda será salva (sem ID e com
     * data atual).
     */
    public Notification(int profileId, String title, String message) {
        this.profileId = profileId;
        this.title = title;
        this.message = message;
        this.isRead = false;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Construtor completo, utilizado pelo repositório ao carregar dados do SQLite.
     */
    public Notification(Integer id, int profileId, String title, String message, boolean isRead,
            LocalDateTime createdAt) {
        this.id = id;
        this.profileId = profileId;
        this.title = title;
        this.message = message;
        this.isRead = isRead;
        this.createdAt = createdAt;
    }

    // ==========================================================================
    // GETTERS E SETTERS
    // ==========================================================================

    public Integer getId() {
        return id;
    }

    public int getProfileId() {
        return profileId;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        this.isRead = read;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "Notification{" +
                "id=" + id +
                ", profileId=" + profileId +
                ", title='" + title + '\'' +
                ", isRead=" + isRead +
                ", createdAt=" + createdAt +
                '}';
    }
}