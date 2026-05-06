package com.thomazcollet.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Representa uma sessão de foco ou pausa concluída ou em andamento.
 * Mapeia a tabela 'focus_sessions' do banco de dados.
 */
public class FocusSession {

    private Long id;
    private Long profileId;
    private SessionType type;
    private LocalDateTime startTimestamp;
    private LocalDateTime endTimestamp;
    private int durationSeconds;
    private boolean completed;

    public FocusSession() {}

    /**
     * Construtor para reconstrução vinda do banco de dados.
     */
    public FocusSession(Long id, Long profileId, SessionType type, LocalDateTime startTimestamp, 
                        LocalDateTime endTimestamp, int durationSeconds, boolean completed) {
        this.id = id;
        this.profileId = profileId;
        this.type = type;
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
        this.durationSeconds = durationSeconds;
        this.completed = completed;
    }

    /**
     * Construtor para novas sessões criadas pelo Service.
     */
    public FocusSession(Long profileId, SessionType type, LocalDateTime startTimestamp) {
        if (profileId == null) throw new IllegalArgumentException("O ID do perfil é obrigatório.");
        if (type == null) throw new IllegalArgumentException("O tipo de sessão é obrigatório.");
        if (startTimestamp == null) throw new IllegalArgumentException("O timestamp de início é obrigatório.");
        
        this.profileId = profileId;
        this.type = type;
        this.startTimestamp = startTimestamp;
        this.completed = false;
    }

    // --- GETTERS E SETTERS ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProfileId() { return profileId; }

    public SessionType getType() { return type; }

    public LocalDateTime getStartTimestamp() { return startTimestamp; }

    public LocalDateTime getEndTimestamp() { return endTimestamp; }

    public void setEndTimestamp(LocalDateTime endTimestamp) {
        if (endTimestamp != null && endTimestamp.isBefore(this.startTimestamp)) {
            throw new IllegalArgumentException("O término não pode ser anterior ao início.");
        }
        this.endTimestamp = endTimestamp;
    }

    public int getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    @Override
    public String toString() {
        return "FocusSession{" + "type=" + type + ", duration=" + durationSeconds + "s, completed=" + completed + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FocusSession that = (FocusSession) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}