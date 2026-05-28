package com.thomazcollet.domain.repository;

import com.thomazcollet.domain.dto.StreakRecord;
import java.util.List;

public interface StreakRecordRepository {

    void save(StreakRecord record);

    void delete(Long id);

    List<StreakRecord> getTopStreaks(Long profileId, int limit);

    StreakRecord getMinStreak(Long profileId);

    /**
     * Conta a quantidade atual de recordes de streak salvos para o perfil.
     * Essencial para a lógica de controle do tamanho do pódio (ex: limite de 5).
     */
    int countRecords(Long profileId);
}