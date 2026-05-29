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
     * 🆕 Atualiza as metas de foco (diária, semanal e mensal) de um perfil
     * específico.
     */
    void updateGoals(Long profileId, int dailySeconds, int weeklySeconds, int monthlySeconds);
}