package com.thomazcollet.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Representa um perfil de configuração do usuário para sessões de Pomodoro.
 * Esta classe é o espelho da tabela 'profiles' no banco de dados e garante
 * a integridade dos dados através de validações internas.
 */
public class Profile {

    private Long id;
    private String name;
    private int workDuration;
    private int shortBreak;
    private int longBreak;
    private LocalDateTime createdAt;

    public Profile() {
    }

    /**
     * Construtor completo para dados vindos do banco.
     * Note que o ID e a Data são atribuídos diretamente pois confiamos no banco,
     * mas os outros campos passam pela validação dos setters.
     */
    public Profile(Long id, String name, int workDuration, int shortBreak, int longBreak, LocalDateTime createdAt) {
        this.id = id;
        this.setCreatedAt(createdAt); // Valida se não está no futuro
        this.setName(name);
        this.setWorkDuration(workDuration);
        this.setShortBreak(shortBreak);
        this.setLongBreak(longBreak);
    }

    /**
     * Construtor para novos perfis.
     * O uso dos métodos SET garante o princípio Fail-Fast na criação do objeto.
     */
    public Profile(String name, int workDuration, int shortBreak, int longBreak) {
        this.setName(name);
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("O nome do perfil não pode estar vazio.");
        }
        if (name.length() > 20) {
            throw new IllegalArgumentException("O nome do perfil deve ter no máximo 20 caracteres.");
        }
        this.name = name.trim();
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
        if (createdAt != null && createdAt.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("A data de criação não pode estar no futuro.");
        }
        this.createdAt = createdAt;
    }

    // --- MÉTODOS DE OBJETO ---

    @Override
    public String toString() {
        return "Profile{id=" + id + ", name='" + name + "', work=" + workDuration + "m}";
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