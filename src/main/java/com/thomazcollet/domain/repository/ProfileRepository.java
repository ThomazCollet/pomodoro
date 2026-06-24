package com.thomazcollet.domain.repository;

import com.thomazcollet.domain.model.Profile;
import java.util.List;
import java.util.Optional;

public interface ProfileRepository {
    void save(Profile profile);

    Optional<Profile> findById(Long id);

    List<Profile> findAll();

    void delete(Long id);

    void updateStats(Long profileId, int maxFocusDaySeconds, int totalSessions);

    void updateXp(Long profileId, int newXp);

    /**
     * Atualiza as metas de foco (diária, semanal e mensal) de um perfil.
     */
    void updateGoals(Long profileId, int dailySeconds, int weeklySeconds, int monthlySeconds);

    /**
     * Atualiza as informações básicas de identidade do perfil.
     * Substitui o antigo save() para atualizações — evita o bug de INSERT
     * duplicado.
     */
    void updateProfileInfo(Long profileId, String username, String imagePath);

    /**
     * Atualiza as durações de foco e pausas do perfil.
     */
    void updateDurations(Long profileId, int workDuration, int shortBreak, int longBreak);

    /**
     * Atualiza as preferências de configuração do perfil:
     * volume de áudio, estado de notificações, idioma e regra de streak.
     */
    void updateSettings(Long profileId, int audioVolume, boolean notificationsEnabled,
            String language, String streakRule);
}