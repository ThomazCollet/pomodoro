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

    // --- NOVOS ATRIBUTOS PARA ESTATÍSTICAS (MARCOS) ---
    private int maxFocusDaySeconds;
    private int totalFocusSessions;

    // ATRIBUTO DE PROGRESSÃO DE RANKING
    private int xp;

    // 🆕 METAS DE PRODUTIVIDADE
    private int dailyGoalSeconds;
    private int weeklyGoalSeconds;
    private int monthlyGoalSeconds;

    // 🆕 CONFIGURAÇÕES (Settings)
    private int audioVolume; // 0–100
    private boolean notificationsEnabled;
    private String language; // ex: "pt_BR", "en_US" — infraestrutura para tradução futura
    private StreakRule streakRule;

    public Profile() {
    }

    /**
     * Construtor completo para reconstrução de objetos vindos da camada de
     * persistência.
     */
    public Profile(Long id, String username, String imagePath, int workDuration, int shortBreak, int longBreak,
            int maxFocusDaySeconds, int totalFocusSessions, int xp,
            int dailyGoalSeconds, int weeklyGoalSeconds, int monthlyGoalSeconds,
            int audioVolume, boolean notificationsEnabled, String language, StreakRule streakRule,
            LocalDateTime createdAt) {
        this.id = id;
        this.createdAt = createdAt;
        this.setUsername(username);
        this.setImagePath(imagePath);
        this.setWorkDuration(workDuration);
        this.setShortBreak(shortBreak);
        this.setLongBreak(longBreak);
        this.setMaxFocusDaySeconds(maxFocusDaySeconds);
        this.setTotalFocusSessions(totalFocusSessions);
        this.setXp(xp);
        this.setDailyGoalSeconds(dailyGoalSeconds);
        this.setWeeklyGoalSeconds(weeklyGoalSeconds);
        this.setMonthlyGoalSeconds(monthlyGoalSeconds);
        this.setAudioVolume(audioVolume);
        this.setNotificationsEnabled(notificationsEnabled);
        this.setLanguage(language);
        this.setStreakRule(streakRule);
    }

    /**
     * Construtor para novos perfis criados pela aplicação com os defaults do banco.
     */
    public Profile(String username, int workDuration, int shortBreak, int longBreak) {
        this.setUsername(username);
        this.setWorkDuration(workDuration);
        this.setShortBreak(shortBreak);
        this.setLongBreak(longBreak);
        this.maxFocusDaySeconds = 0;
        this.totalFocusSessions = 0;
        this.xp = 0;
        this.dailyGoalSeconds = 5400;
        this.weeklyGoalSeconds = 27000;
        this.monthlyGoalSeconds = 108000;
        this.audioVolume = 100;
        this.notificationsEnabled = true;
        this.language = "pt_BR";
        this.streakRule = StreakRule.ALL_DAYS;
    }

    // --- COMPORTAMENTO DE DOMÍNIO RICO ---

    /**
     * Retorna o Ranking atual calculado dinamicamente com base no XP em memória.
     */
    public RankingType getRanking() {
        return RankingType.fromXp(this.xp);
    }

    /**
     * Método utilitário para incrementar o XP do usuário de forma segura.
     */
    public void addXp(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Não é possível adicionar uma quantidade negativa de XP.");
        }
        this.xp += amount;
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

    // --- SETTERS E GETTERS DOS MARCOS ---

    public int getMaxFocusDaySeconds() {
        return maxFocusDaySeconds;
    }

    public void setMaxFocusDaySeconds(int maxFocusDaySeconds) {
        if (maxFocusDaySeconds < 0)
            throw new IllegalArgumentException("Tempo recorde não pode ser negativo.");
        this.maxFocusDaySeconds = maxFocusDaySeconds;
    }

    public int getTotalFocusSessions() {
        return totalFocusSessions;
    }

    public void setTotalFocusSessions(int totalFocusSessions) {
        if (totalFocusSessions < 0)
            throw new IllegalArgumentException("Total de sessões não pode ser negativo.");
        this.totalFocusSessions = totalFocusSessions;
    }

    // GETTER E SETTER DO XP
    public int getXp() {
        return xp;
    }

    public void setXp(int xp) {
        if (xp < 0)
            throw new IllegalArgumentException("O XP acumulado não pode ser negativo.");
        this.xp = xp;
    }

    // 🆕 GETTERS E SETTERS DAS METAS CUSTOMIZÁVEIS (FAIL-FAST)
    public int getDailyGoalSeconds() {
        return dailyGoalSeconds;
    }

    public void setDailyGoalSeconds(int dailyGoalSeconds) {
        if (dailyGoalSeconds < 0) {
            throw new IllegalArgumentException("A meta diária de foco não pode ser negativa.");
        }
        this.dailyGoalSeconds = dailyGoalSeconds;
    }

    public int getWeeklyGoalSeconds() {
        return weeklyGoalSeconds;
    }

    public void setWeeklyGoalSeconds(int weeklyGoalSeconds) {
        if (weeklyGoalSeconds < 0) {
            throw new IllegalArgumentException("A meta semanal de foco não pode ser negativa.");
        }
        this.weeklyGoalSeconds = weeklyGoalSeconds;
    }

    public int getMonthlyGoalSeconds() {
        return monthlyGoalSeconds;
    }

    public void setMonthlyGoalSeconds(int monthlyGoalSeconds) {
        if (monthlyGoalSeconds < 0) {
            throw new IllegalArgumentException("A meta mensal de foco não pode ser negativa.");
        }
        this.monthlyGoalSeconds = monthlyGoalSeconds;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        if (createdAt != null && createdAt.isAfter(LocalDateTime.now().plusMinutes(1))) {
            throw new IllegalArgumentException("A data de criação não pode estar no futuro.");
        }
        this.createdAt = createdAt;
    }

    // --- GETTERS E SETTERS DE CONFIGURAÇÕES ---

    public int getAudioVolume() {
        return audioVolume;
    }

    public void setAudioVolume(int audioVolume) {
        if (audioVolume < 0 || audioVolume > 100) {
            throw new IllegalArgumentException("O volume deve estar entre 0 e 100.");
        }
        this.audioVolume = audioVolume;
    }

    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = (language != null && !language.isBlank()) ? language.trim() : "pt_BR";
    }

    public StreakRule getStreakRule() {
        return streakRule;
    }

    public void setStreakRule(StreakRule streakRule) {
        this.streakRule = (streakRule != null) ? streakRule : StreakRule.ALL_DAYS;
    }

    // --- MÉTODOS DE OBJETO ---

    @Override
    public String toString() {
        return "Profile{id=" + id + ", username='" + username + "', ranking=" + getRanking() + ", xp=" + xp +
                ", dailyGoal=" + dailyGoalSeconds + "s}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Profile profile = (Profile) o;
        return Objects.equals(id, profile.id);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : super.hashCode();
    }
}