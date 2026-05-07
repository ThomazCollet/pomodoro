package com.thomazcollet.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Representa um perfil de configuração do usuário para sessões de Pomodoro.
 * Esta classe garante a integridade dos dados através de validações internas
 * seguindo o princípio Fail-Fast.
 */
public class Profile {

    private Long id;
    private String username;
    private String imagePath;
    private int workDuration;
    private int shortBreak;
    private int longBreak;
    private LocalDateTime createdAt;

    public Profile() {
    }

    /**
     * Construtor completo para reconstrução de objetos vindos da camada de
     * persistência.
     */
    public Profile(Long id, String username, String imagePath, int workDuration, int shortBreak, int longBreak,
            LocalDateTime createdAt) {
        this.id = id;
        this.createdAt = createdAt;
        this.setUsername(username);
        this.setImagePath(imagePath);
        this.setWorkDuration(workDuration);
        this.setShortBreak(shortBreak);
        this.setLongBreak(longBreak);
    }

    /**
     * Construtor para novos perfis criados pela aplicação.
     */
    public Profile(String username, int workDuration, int shortBreak, int longBreak) {
        this.setUsername(username);
        this.setWorkDuration(workDuration);
        this.setShortBreak(shortBreak);
        this.setLongBreak(longBreak);
    }

    // --- GETTERS E SETTERS COM VALIDAÇÃO ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("O nome de usuário não pode estar vazio.");
        }
        if (username.length() > 20) {
            throw new IllegalArgumentException("O nome de usuário deve ter no máximo 20 caracteres.");
        }
        this.username = username.trim();
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        // Por enquanto aceitamos null (que indicará o uso da letra inicial)
        this.imagePath = imagePath != null ? imagePath.trim() : null;
    }

    public int getWorkDuration() {
        return workDuration;
    }

    public void setWorkDuration(int workDuration) {
        if (workDuration < 1 || workDuration > 60) {
            throw new IllegalArgumentException("A duração do foco deve ser entre 1 e 60 minutos.");
        }
        this.workDuration = workDuration;
    }

    public int getShortBreak() {
        return shortBreak;
    }

    public void setShortBreak(int shortBreak) {
        if (shortBreak < 1 || shortBreak > 30) {
            throw new IllegalArgumentException("A pausa curta deve ser entre 1 e 30 minutos.");
        }
        this.shortBreak = shortBreak;
    }

    public int getLongBreak() {
        return longBreak;
    }

    public void setLongBreak(int longBreak) {
        if (longBreak < 1 || longBreak > 60) {
            throw new IllegalArgumentException("A pausa longa deve ser entre 1 e 60 minutos.");
        }
        if (longBreak <= this.shortBreak) {
            throw new IllegalArgumentException("A pausa longa deve ser maior que a pausa curta.");
        }
        this.longBreak = longBreak;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        // driver/banco
        if (createdAt != null && createdAt.isAfter(LocalDateTime.now().plusMinutes(1))) {
            throw new IllegalArgumentException("A data de criação não pode estar no futuro.");
        }
        this.createdAt = createdAt;
    }

    // --- MÉTODOS DE OBJETO ---

    @Override
    public String toString() {
        return "Profile{id=" + id + ", username='" + username + "', work=" + workDuration + "m}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Profile profile = (Profile) o;
        if (id == null || profile.id == null)
            return false;
        return Objects.equals(id, profile.id);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : super.hashCode();
    }
}