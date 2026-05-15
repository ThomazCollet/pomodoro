package com.thomazcollet.domain.model;

import java.time.LocalDateTime;

/**
 * Representa uma conquista desbloqueada por um perfil de usuário.
 */
public class Achievement {

    private Long id;
    private Long profileId;
    private String achievementKey;
    private AchievementCategory category;
    private AchievementTier tier;
    private LocalDateTime unlockedAt;

    // Construtor padrão (Útil para mapeamento do JDBC/Repositories)
    public Achievement() {
    }

    // Construtor completo para criação facilitada
    public Achievement(Long id, Long profileId, String achievementKey, AchievementCategory category,
            AchievementTier tier, LocalDateTime unlockedAt) {
        this.id = id;
        this.profileId = profileId;
        this.achievementKey = achievementKey;
        this.category = category;
        this.tier = tier;
        this.unlockedAt = unlockedAt;
    }

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

    public String getAchievementKey() {
        return achievementKey;
    }

    public void setAchievementKey(String achievementKey) {
        this.achievementKey = achievementKey;
    }

    public AchievementCategory getCategory() {
        return category;
    }

    public void setCategory(AchievementCategory category) {
        this.category = category;
    }

    public AchievementTier getTier() {
        return tier;
    }

    public void setTier(AchievementTier tier) {
        this.tier = tier;
    }

    public LocalDateTime getUnlockedAt() {
        return unlockedAt;
    }

    public void setUnlockedAt(LocalDateTime unlockedAt) {
        this.unlockedAt = unlockedAt;
    }

    @Override
    public String toString() {
        return "Achievement{" +
                "id=" + id +
                ", profileId=" + profileId +
                ", achievementKey='" + achievementKey + '\'' +
                ", category=" + category +
                ", tier=" + tier +
                ", unlockedAt=" + unlockedAt +
                '}';
    }
}